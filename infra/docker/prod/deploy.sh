#!/usr/bin/env bash

# deploy.sh - Despliega API y BFF por digest en el VPS (#593, ADR-0018, ADR-0045).
#
# Se instala en /opt/menta/deploy.sh y es el único comando que puede ejecutar la clave SSH de
# despliegue (command="/opt/menta/deploy.sh" en authorized_keys). Docs: docs/32-VPS-DONWEB-RUNBOOK.md.
#
# Uso:
#   Desde release.yml, por la clave con comando forzado (los digests llegan en SSH_ORIGINAL_COMMAND
#   y la configuración del stack, como tar.gz, por la entrada estándar):
#     tar -czf - -C infra/docker prod nginx/conf/conf.d | ssh deploy@host "<api-digest> <bff-digest>"
#   A mano, p. ej. para volver a la versión anterior (sin entrada estándar usa el stack ya instalado):
#     /opt/menta/deploy.sh $(cat /opt/menta/previous-digests)
#
# Pasos: valida los digests -> toma el lock -> actualiza la configuración del stack -> mysqldump ->
# descarga las imágenes -> escribe los digests en .env -> docker compose up -> espera el health check
# -> deja solo la versión actual y la anterior. Si algo falla el script termina con error y no hace
# rollback: la base de datos no se puede revertir (Flyway solo avanza), así que la vuelta atrás es
# manual y la decide quien opera.
#
# Variables opcionales (para pruebas): MENTA_HOME (por defecto /opt/menta), HEALTH_TIMEOUT y
# HEALTH_INTERVAL en segundos, BACKUP_KEEP (cantidad de backups que se conservan).

set -euo pipefail

readonly MENTA_HOME="${MENTA_HOME:-/opt/menta}"
readonly ENV_FILE="${MENTA_HOME}/.env"
readonly STACK_DIR="${MENTA_HOME}/stack"
readonly BACKUP_DIR="${MENTA_HOME}/backups"
readonly PREVIOUS_FILE="${MENTA_HOME}/previous-digests"
readonly STACK_HASH_FILE="${MENTA_HOME}/stack.sha256"
readonly LOCK_FILE="${MENTA_HOME}/deploy.lock"
readonly COMPOSE_FILE="${STACK_DIR}/prod/docker-compose.prod.yml"

readonly API_REPOSITORY="ghcr.io/urrestarazu-org/menta-api"
readonly BFF_REPOSITORY="ghcr.io/urrestarazu-org/menta-bff"
readonly DIGEST_PATTERN='^sha256:[0-9a-f]{64}$'

readonly HEALTH_TIMEOUT="${HEALTH_TIMEOUT:-240}"
readonly HEALTH_INTERVAL="${HEALTH_INTERVAL:-5}"
readonly BACKUP_KEEP="${BACKUP_KEEP:-14}"
# Tope del tar.gz de configuración que llega por la entrada estándar (unos pocos KB en la práctica)
readonly MAX_ARCHIVE_BYTES=$((5 * 1024 * 1024))

TMP_ARCHIVE=""

cleanup() {
    if [[ -n "$TMP_ARCHIVE" ]]; then
        rm -f "$TMP_ARCHIVE"
    fi
}

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

compose() {
    docker compose \
        --project-directory "${STACK_DIR}/prod" \
        --env-file "$ENV_FILE" \
        --file "$COMPOSE_FILE" \
        "$@"
}

# Valida un digest antes de que toque cualquier comando: es lo único que viaja desde el exterior.
require_digest() {
    local name="$1" value="$2"
    if [[ ! "$value" =~ $DIGEST_PATTERN ]]; then
        log_error "${name} inválido: se esperaba sha256:<64 hex>"
        exit 2
    fi
}

read_env_value() {
    local key="$1"
    { grep -E "^${key}=" "$ENV_FILE" || true; } | tail -n 1 | cut -d= -f2-
}

# Reescribe una variable de .env sin perder el resto ni los permisos (el archivo trae secretos).
set_env_value() {
    local key="$1" value="$2" tmp
    tmp="$(mktemp "${MENTA_HOME}/.env.XXXXXX")"
    awk -v key="$key" -v value="$value" '
        index($0, key "=") == 1 { print key "=" value; done = 1; next }
        { print }
        END { if (!done) print key "=" value }
    ' "$ENV_FILE" > "$tmp"
    chmod 600 "$tmp"
    mv "$tmp" "$ENV_FILE"
}

# Digests desde los argumentos (uso manual) o desde SSH_ORIGINAL_COMMAND (clave con comando forzado).
parse_digests() {
    local -a parts=()
    if [[ $# -gt 0 ]]; then
        parts=("$@")
    elif [[ -n "${SSH_ORIGINAL_COMMAND:-}" ]]; then
        read -r -a parts <<< "$SSH_ORIGINAL_COMMAND"
    fi

    if [[ ${#parts[@]} -ne 2 ]]; then
        log_error "Uso: deploy.sh <api-digest> <bff-digest>"
        exit 2
    fi

    require_digest "Digest de la API" "${parts[0]}"
    require_digest "Digest del BFF" "${parts[1]}"
    NEW_API_DIGEST="${parts[0]}"
    NEW_BFF_DIGEST="${parts[1]}"
}

# Recibe el tar.gz con la configuración del stack (compose, nginx.conf, conf.d) por la entrada
# estándar. Solo se aceptan archivos regulares y directorios dentro de las rutas esperadas.
install_stack() {
    local archive entries entry type
    archive="$(mktemp)"
    TMP_ARCHIVE="$archive"

    head -c "$((MAX_ARCHIVE_BYTES + 1))" > "$archive"
    local size
    size="$(wc -c < "$archive")"
    if [[ "$size" -eq 0 ]]; then
        return 1
    fi
    [[ "$size" -le "$MAX_ARCHIVE_BYTES" ]] || die "La configuración del stack supera ${MAX_ARCHIVE_BYTES} bytes"

    entries="$(tar -tvzf "$archive")" || die "La configuración del stack no es un tar.gz válido"
    while IFS= read -r entry; do
        type="${entry:0:1}"
        if [[ "$type" != "-" && "$type" != "d" ]]; then
            die "La configuración del stack trae algo que no es archivo ni carpeta: ${entry}"
        fi
    done <<< "$entries"

    local path
    while IFS= read -r path; do
        if [[ "$path" == *..* ]] || [[ ! "$path" =~ ^(prod(/.*)?|nginx/?|nginx/conf/?|nginx/conf/conf\.d(/.*)?)$ ]]; then
            die "Ruta inesperada en la configuración del stack: ${path}"
        fi
    done < <(tar -tzf "$archive")

    # Se extrae aparte y se intercambia: una carpeta a medio escribir nunca queda como stack
    local staging="${STACK_DIR}.new"
    rm -rf "$staging"
    mkdir -p "$staging"
    tar -xzf "$archive" -C "$staging" --no-same-owner --no-same-permissions
    [[ -f "${staging}/prod/docker-compose.prod.yml" ]] || die "La configuración del stack no trae prod/docker-compose.prod.yml"
    rm -rf "${STACK_DIR}.old"
    if [[ -d "$STACK_DIR" ]]; then
        mv "$STACK_DIR" "${STACK_DIR}.old"
    fi
    mv "$staging" "$STACK_DIR"
    rm -rf "${STACK_DIR}.old"

    warn_if_script_outdated
    return 0
}

# El script instalado es lo que ejecuta la clave: el workflow no lo reemplaza. Se avisa si el release
# trae una versión distinta, para que el operador la reinstale.
warn_if_script_outdated() {
    local shipped="${STACK_DIR}/prod/deploy.sh" installed="${BASH_SOURCE[0]}"
    if [[ -f "$shipped" ]] && ! cmp -s "$shipped" "$installed"; then
        log_info "AVISO: ${installed} difiere del deploy.sh de este release; reinstalalo (install -m 750 ${shipped} ${installed})"
    fi
}

stack_hash() {
    (cd "$STACK_DIR" && find . -type f -print0 | sort -z | xargs -0 sha256sum | sha256sum | cut -d' ' -f1)
}

container_running() {
    [[ "$(docker inspect --format '{{.State.Running}}' "$1" 2>/dev/null || true)" == "true" ]]
}

# mysqldump antes de cada deploy: Flyway no tiene rollback, el backup es la única vuelta atrás de los datos.
backup_database() {
    if ! container_running menta-mysql; then
        log_info "MySQL no está corriendo (primer despliegue): sin backup previo"
        return 0
    fi

    mkdir -p "$BACKUP_DIR"
    local target
    target="${BACKUP_DIR}/menta-$(date -u +%Y%m%dT%H%M%SZ).sql.gz"
    log_info "Backup de MySQL en ${target}"

    (
        umask 077
        # La contraseña sale del entorno del propio contenedor: no pasa por la línea de comandos
        docker exec menta-mysql sh -c \
            'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysqldump --user=root --single-transaction --routines --triggers --events --default-character-set=utf8mb4 menta' \
            | gzip > "${target}.tmp"
    ) || { rm -f "${target}.tmp"; die "mysqldump falló: no se despliega"; }

    # mysqldump cierra el volcado con "Dump completed": sin esa línea el archivo está truncado
    if ! gzip -t "${target}.tmp" || ! gzip -dc "${target}.tmp" | tail -n 5 | grep -q 'Dump completed'; then
        rm -f "${target}.tmp"
        die "El backup de MySQL está incompleto: no se despliega"
    fi
    mv "${target}.tmp" "$target"

    local -a backups=()
    mapfile -t backups < <(find "$BACKUP_DIR" -maxdepth 1 -name 'menta-*.sql.gz' | sort)
    local excess=$(( ${#backups[@]} - BACKUP_KEEP ))
    if [[ "$excess" -gt 0 ]]; then
        rm -f -- "${backups[@]:0:excess}"
    fi
}

# Responde 0 cuando el actuator del contenedor informa status UP (el status del conjunto va primero en el JSON)
service_is_up() {
    local container="$1" port="$2" body
    body="$(docker exec "$container" wget -qO- "http://localhost:${port}/actuator/health" 2>/dev/null || true)"
    [[ "$body" == '{"status":"UP"'* ]]
}

stack_is_healthy() {
    service_is_up menta-api 8081 \
        && service_is_up menta-bff 8080 \
        && [[ "$(docker exec menta-nginx wget -qO- http://127.0.0.1/health 2>/dev/null || true)" == "ok" ]]
}

wait_for_health() {
    local waited=0
    log_info "Esperando que API, BFF y Nginx respondan UP (hasta ${HEALTH_TIMEOUT}s)"
    until stack_is_healthy; do
        if [[ "$waited" -ge "$HEALTH_TIMEOUT" ]]; then
            return 1
        fi
        sleep "$HEALTH_INTERVAL"
        waited=$((waited + HEALTH_INTERVAL))
    done
    log_info "Stack saludable tras ${waited}s"
}

# Deja de cada repositorio solo la imagen actual y la anterior: alcanza para volver atrás sin descargar
prune_images() {
    local repository="$1" current="$2" previous="$3" id digest
    while read -r id digest; do
        if [[ "$digest" == "$current" || "$digest" == "$previous" ]]; then
            continue
        fi
        docker image rm "$id" > /dev/null 2>&1 || true
    done < <(docker image ls --digests --no-trunc --format '{{.ID}} {{.Digest}}' "$repository")
}

main() {
    trap cleanup EXIT
    parse_digests "$@"

    [[ -d "$MENTA_HOME" ]] || die "No existe ${MENTA_HOME}"
    [[ -f "$ENV_FILE" ]] || die "Falta ${ENV_FILE} (ver infra/docker/prod/production.env.example)"

    # Un solo deploy a la vez: el lock se libera solo cuando el proceso termina, aunque muera mal
    exec 9> "$LOCK_FILE"
    flock -n 9 || die "Hay otro deploy en curso"

    local current_api current_bff
    current_api="$(read_env_value API_DIGEST)"
    current_bff="$(read_env_value BFF_DIGEST)"

    local stack_changed=false previous_hash=""
    [[ -f "$STACK_HASH_FILE" ]] && previous_hash="$(cat "$STACK_HASH_FILE")"
    if [[ ! -t 0 ]] && install_stack; then
        local new_hash
        new_hash="$(stack_hash)"
        if [[ "$new_hash" != "$previous_hash" ]]; then
            stack_changed=true
        fi
        echo "$new_hash" > "$STACK_HASH_FILE"
        log_info "Configuración del stack actualizada"
    fi
    [[ -f "$COMPOSE_FILE" ]] || die "No hay configuración del stack en ${STACK_DIR}: enviá el tar.gz por la entrada estándar"

    log_info "Desplegando API ${NEW_API_DIGEST} y BFF ${NEW_BFF_DIGEST}"
    backup_database

    # Las variables del entorno pisan a --env-file: se descargan las imágenes nuevas sin tocar .env.
    # Si la descarga falla, nada cambió.
    API_DIGEST="$NEW_API_DIGEST" BFF_DIGEST="$NEW_BFF_DIGEST" compose pull api bff \
        || die "No se pudieron descargar las imágenes (¿venció el token de GHCR? ver runbook, sección 2.7)"

    if [[ -n "$current_api" && -n "$current_bff" ]] \
        && [[ "$current_api" != "$NEW_API_DIGEST" || "$current_bff" != "$NEW_BFF_DIGEST" ]]; then
        (umask 077; echo "${current_api} ${current_bff}" > "$PREVIOUS_FILE")
    fi
    set_env_value API_DIGEST "$NEW_API_DIGEST"
    set_env_value BFF_DIGEST "$NEW_BFF_DIGEST"

    compose up -d --remove-orphans || die "docker compose up falló"
    if [[ "$stack_changed" == "true" ]] && container_running menta-nginx; then
        # nginx.conf se monta como archivo: el contenedor sigue viendo el anterior hasta recrearse
        log_info "La configuración cambió: se recrea Nginx"
        compose up -d --force-recreate --no-deps nginx || die "No se pudo recrear Nginx"
    fi

    if ! wait_for_health; then
        log_error "El stack no respondió UP en ${HEALTH_TIMEOUT}s. Logs: docker compose --env-file ${ENV_FILE} -f ${COMPOSE_FILE} logs --tail 100 api bff"
        if [[ -f "$PREVIOUS_FILE" ]]; then
            log_error "Para volver a la versión anterior: ${BASH_SOURCE[0]} $(cat "$PREVIOUS_FILE")"
        fi
        log_error "Los datos no se revierten solos; el backup previo está en ${BACKUP_DIR}"
        exit 1
    fi

    # [0] = API, [1] = BFF; vacíos si es el primer despliegue
    local -a previous=("" "")
    if [[ -f "$PREVIOUS_FILE" ]]; then
        read -r -a previous < "$PREVIOUS_FILE"
    fi
    prune_images "$API_REPOSITORY" "$NEW_API_DIGEST" "${previous[0]:-}"
    prune_images "$BFF_REPOSITORY" "$NEW_BFF_DIGEST" "${previous[1]:-}"
    docker image prune -f > /dev/null

    log_info "Deploy completado"
}

# Permite cargar las funciones en las pruebas sin ejecutar el deploy
if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
    main "$@"
fi
