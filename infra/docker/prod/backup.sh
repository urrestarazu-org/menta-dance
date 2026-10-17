#!/usr/bin/env bash

# backup.sh - Backup diario de MySQL y de los comprobantes de pago, con copia cifrada fuera del VPS
# (#594, ADR-0045). Docs: docs/32-VPS-DONWEB-RUNBOOK.md, sección 2.11.
#
# Lo dispara el timer de systemd menta-backup.timer, como el usuario deploy. A mano:
#   /opt/menta/backup.sh
#
# Pasos: mysqldump verificado -> tar de los comprobantes -> retención local -> copia cifrada con
# rclone (crypt) al almacenamiento S3-compatible -> verificación -> retención remota -> aviso de éxito.
# Cualquier fallo termina con código distinto de 0 y avisa al monitor (BACKUP_PING_URL/fail), así un
# backup que deja de correr se nota sin tener que mirar el servidor.
#
# Credenciales y claves de cifrado: /opt/menta/backup.env (chmod 600, nunca en el repositorio; los
# nombres están en backup.env.example). Los backups previos a cada deploy los hace deploy.sh en
# /opt/menta/backups; este script usa /opt/menta/backups/daily.
#
# Variables opcionales (para pruebas): MENTA_HOME (por defecto /opt/menta).

set -euo pipefail

readonly MENTA_HOME="${MENTA_HOME:-/opt/menta}"
readonly BACKUP_ENV="${MENTA_HOME}/backup.env"
readonly DAILY_DIR="${MENTA_HOME}/backups/daily"
readonly LOCK_FILE="${MENTA_HOME}/backup.lock"

# rclone como imagen oficial con versión exacta y digest, igual que Trivy: no hay nada que instalar
# en el servidor. Para actualizarla: elegir un release, `docker pull rclone/rclone:<versión>` y copiar
# el digest de `docker buildx imagetools inspect`.
readonly RCLONE_IMAGE="rclone/rclone:1.75.2@sha256:2687085f718d3c628f7fdfb77c52a1d344332aed543110bb88088f2c70d43eb5"
# Remote cifrado definido por las variables RCLONE_CONFIG_OFFSITECRYPT_* de backup.env
readonly REMOTE="offsitecrypt:daily"

readonly PROOFS_PATH_IN_API="/var/menta-dance/payment-proofs"

LOCAL_KEEP=7
REMOTE_RETENTION_DAYS=30
PING_URL=""

log_info() {
    echo "[INFO] $1"
}

log_error() {
    echo "[ERROR] $1" >&2
}

die() {
    log_error "$1"
    exit 1
}

# Valor de una variable de backup.env (el archivo es para `docker --env-file`: sin comillas ni `export`)
read_env_value() {
    local key="$1"
    { grep -E "^${key}=" "$BACKUP_ENV" || true; } | tail -n 1 | cut -d= -f2-
}

# Avisa al monitor externo (healthchecks.io u otro con la misma convención: URL = éxito, URL/fail = fallo).
# Un fallo del aviso no hace fallar el backup.
ping_monitor() {
    local suffix="$1"
    if [[ -z "$PING_URL" ]]; then
        return 0
    fi
    curl --fail --silent --show-error --max-time 10 --retry 3 "${PING_URL}${suffix}" > /dev/null \
        || log_error "No se pudo avisar al monitor"
}

on_exit() {
    local status=$?
    find "$DAILY_DIR" -maxdepth 1 -name '*.tmp' -delete 2> /dev/null || true
    if [[ "$status" -ne 0 ]]; then
        log_error "El backup falló (código ${status})"
        ping_monitor "/fail"
    fi
}

load_settings() {
    [[ -f "$BACKUP_ENV" ]] || die "Falta ${BACKUP_ENV} (ver infra/docker/prod/backup.env.example)"

    local value
    value="$(read_env_value BACKUP_LOCAL_KEEP)"
    LOCAL_KEEP="${value:-$LOCAL_KEEP}"
    value="$(read_env_value BACKUP_REMOTE_RETENTION_DAYS)"
    REMOTE_RETENTION_DAYS="${value:-$REMOTE_RETENTION_DAYS}"
    PING_URL="$(read_env_value BACKUP_PING_URL)"

    # Una retención de 0 días o de 0 copias borraría todo: se exige un piso
    [[ "$LOCAL_KEEP" =~ ^[0-9]+$ && "$LOCAL_KEEP" -ge 2 ]] || die "BACKUP_LOCAL_KEEP debe ser un número mayor o igual a 2"
    [[ "$REMOTE_RETENTION_DAYS" =~ ^[0-9]+$ && "$REMOTE_RETENTION_DAYS" -ge 7 ]] \
        || die "BACKUP_REMOTE_RETENTION_DAYS debe ser un número mayor o igual a 7"
}

container_running() {
    [[ "$(docker inspect --format '{{.State.Running}}' "$1" 2>/dev/null || true)" == "true" ]]
}

rclone() {
    docker run --rm --env-file "$BACKUP_ENV" -v "${DAILY_DIR}:/data:ro" "$RCLONE_IMAGE" "$@"
}

dump_database() {
    local target="$1"
    log_info "mysqldump de MySQL en ${target}"
    (
        umask 077
        # La contraseña sale del entorno del propio contenedor: no pasa por la línea de comandos
        docker exec menta-mysql sh -c \
            'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysqldump --user=root --single-transaction --routines --triggers --events --default-character-set=utf8mb4 menta' \
            | gzip > "${target}.tmp"
    ) || die "mysqldump falló"

    # mysqldump cierra el volcado con "Dump completed": sin esa línea el archivo está truncado
    gzip -t "${target}.tmp" || die "El volcado de MySQL está corrupto"
    gzip -dc "${target}.tmp" | tail -n 5 | grep -q 'Dump completed' || die "El volcado de MySQL está incompleto"
    mv "${target}.tmp" "$target"
}

archive_payment_proofs() {
    local target="$1"
    log_info "tar de los comprobantes de pago en ${target}"
    (
        umask 077
        docker exec menta-api tar -czf - -C "$PROOFS_PATH_IN_API" . > "${target}.tmp"
    ) || die "No se pudieron archivar los comprobantes de pago"

    gzip -t "${target}.tmp" || die "El archivo de comprobantes está corrupto"
    mv "${target}.tmp" "$target"
}

# Conserva los LOCAL_KEEP más nuevos de cada tipo (los nombres llevan la fecha: ordenan por antigüedad)
prune_local() {
    local pattern excess
    local -a files=()
    for pattern in 'menta-*.sql.gz' 'proofs-*.tar.gz'; do
        mapfile -t files < <(find "$DAILY_DIR" -maxdepth 1 -name "$pattern" | sort)
        excess=$(( ${#files[@]} - LOCAL_KEEP ))
        if [[ "$excess" -gt 0 ]]; then
            rm -f -- "${files[@]:0:excess}"
        fi
    done
}

upload_offsite() {
    local stamp="$1"
    log_info "Copia cifrada fuera del VPS (${REMOTE})"
    rclone copy /data "$REMOTE" \
        --include "menta-${stamp}.sql.gz" --include "proofs-${stamp}.tar.gz" \
        || die "La copia fuera del VPS falló"

    # Que el remoto tenga los dos archivos, con el tamaño correcto
    rclone check /data "$REMOTE" --one-way --size-only \
        --include "menta-${stamp}.sql.gz" --include "proofs-${stamp}.tar.gz" \
        || die "La copia fuera del VPS no coincide con la local"

    # El backup recién subido se excluye de la retención: aunque el almacenamiento informara mal la fecha
    # de un objeto, esta corrida nunca puede borrar el backup que acaba de hacer
    rclone delete "$REMOTE" --min-age "${REMOTE_RETENTION_DAYS}d" \
        --exclude "menta-${stamp}.sql.gz" --exclude "proofs-${stamp}.tar.gz" \
        || die "No se pudo aplicar la retención remota"
}

main() {
    [[ -d "$MENTA_HOME" ]] || die "No existe ${MENTA_HOME}"
    install -d -m 700 "$DAILY_DIR"
    trap on_exit EXIT
    load_settings

    # Un solo backup a la vez: el lock se libera solo cuando el proceso termina
    exec 9> "$LOCK_FILE"
    flock -n 9 || die "Hay otro backup en curso"

    container_running menta-mysql || die "menta-mysql no está corriendo"
    container_running menta-api || die "menta-api no está corriendo (hace falta para leer los comprobantes)"

    local stamp
    stamp="$(date -u +%Y%m%dT%H%M%SZ)"
    dump_database "${DAILY_DIR}/menta-${stamp}.sql.gz"
    archive_payment_proofs "${DAILY_DIR}/proofs-${stamp}.tar.gz"
    prune_local
    upload_offsite "$stamp"

    ping_monitor ""
    log_info "Backup completado (${stamp})"
}

# Permite cargar las funciones en las pruebas sin ejecutar el backup
if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
    main "$@"
fi
