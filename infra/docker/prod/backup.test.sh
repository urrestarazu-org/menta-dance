#!/usr/bin/env bash

# backup.test.sh - Pruebas de backup.sh con un `docker` y un `curl` falsos (#594).
#
# No toca Docker, ni el VPS, ni la red: reemplaza `docker` y `curl` por scripts que registran las
# llamadas y responden según archivos de estado. Prueba la lógica de backup.sh (verificación de los
# volcados, retención, orden de los pasos, aviso al monitor, lock); no prueba MySQL ni rclone reales.
# Necesita las herramientas de GNU/Linux (flock, find, stat -c): corre en el runner de CI o en un
# contenedor ubuntu:
#   docker run --rm -v "$PWD:/repo:ro" ubuntu:24.04 bash /repo/infra/docker/prod/backup.test.sh

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly BACKUP="${SCRIPT_DIR}/backup.sh"
readonly PING_URL="https://monitor.example/ping/abc"

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
PATH_ORIGINAL="$PATH"
FAILURES=0
CASES=0

# --- Arnés -----------------------------------------------------------------------------------

new_sandbox() {
    rm -rf "${WORK:?}/menta" "${WORK:?}/fake" "${WORK:?}/bin"
    mkdir -p "${WORK}/menta" "${WORK}/fake" "${WORK}/bin"
    export MENTA_HOME="${WORK}/menta"
    export FAKE="${WORK}/fake"
    export PATH="${WORK}/bin:${PATH_ORIGINAL}"
    : > "${FAKE}/calls.log"
    touch "${FAKE}/running.menta-mysql" "${FAKE}/running.menta-api"
    write_fakes
    write_backup_settings
}

write_backup_settings() {
    cat > "${MENTA_HOME}/backup.env" <<EOF
RCLONE_CONFIG_OFFSITE_TYPE=s3
BACKUP_PING_URL=${PING_URL}
$*
EOF
}

# `docker` y `curl` falsos: registran cada llamada y responden según los archivos de $FAKE
write_fakes() {
    cat > "${WORK}/bin/docker" <<'FAKE_DOCKER'
#!/usr/bin/env bash
echo "docker $*" >> "${FAKE}/calls.log"
case "$1" in
    inspect)
        [[ -f "${FAKE}/running.${*: -1}" ]] && echo true || echo false
        ;;
    exec)
        case "$2" in
            menta-mysql)
                [[ ! -f "${FAKE}/dump_fails" ]] || exit 1
                echo "-- MySQL dump"
                echo "CREATE TABLE t (id int);"
                [[ -f "${FAKE}/dump_truncated" ]] || echo "-- Dump completed on 2026-10-17"
                ;;
            menta-api)
                [[ ! -f "${FAKE}/proofs_fail" ]] || exit 1
                echo "contenido de los comprobantes" | gzip
                ;;
        esac
        ;;
    run)
        # rclone: el subcomando es la primera palabra que sigue a la imagen
        for arg in "$@"; do
            case "$arg" in
                copy) [[ ! -f "${FAKE}/rclone_copy_fails" ]] || exit 1; break ;;
                check) [[ ! -f "${FAKE}/rclone_check_fails" ]] || exit 1; break ;;
                delete) [[ ! -f "${FAKE}/rclone_delete_fails" ]] || exit 1; break ;;
            esac
        done
        ;;
esac
exit 0
FAKE_DOCKER
    cat > "${WORK}/bin/curl" <<'FAKE_CURL'
#!/usr/bin/env bash
echo "curl ${*: -1}" >> "${FAKE}/pings.log"
[[ ! -f "${FAKE}/curl_fails" ]] || exit 22
exit 0
FAKE_CURL
    chmod +x "${WORK}/bin/docker" "${WORK}/bin/curl"
    : > "${FAKE}/pings.log"
}

run_backup() {
    set +e
    OUTPUT="$(bash "$BACKUP" 2>&1)"
    RC=$?
    set -e
}

# --- Aserciones ------------------------------------------------------------------------------

fail() {
    echo "  FALLO: $1"
    FAILURES=$((FAILURES + 1))
}

assert_rc() {
    [[ "$RC" == "$1" ]] || fail "código de salida $RC, se esperaba $1. Salida:"$'\n'"${OUTPUT}"
}

assert_output_has() {
    [[ "$OUTPUT" == *"$1"* ]] || fail "la salida no contiene '$1'. Salida:"$'\n'"${OUTPUT}"
}

assert_calls_have() {
    grep -qF -- "$1" "${FAKE}/calls.log" || fail "docker no recibió '$1'"
}

assert_calls_lack() {
    if grep -qF -- "$1" "${FAKE}/calls.log"; then fail "docker recibió '$1' y no debía"; fi
}

assert_pings() {
    [[ "$(cat "${FAKE}/pings.log")" == "$1" ]] || fail "avisos al monitor: '$(cat "${FAKE}/pings.log")', se esperaba '$1'"
}

daily_files() {
    find "${MENTA_HOME}/backups/daily" -maxdepth 1 -type f 2> /dev/null | sort
}

count_files() {
    daily_files | grep -c "$1" || true
}

case_start() {
    CASES=$((CASES + 1))
    echo "- $1"
    new_sandbox
}

# --- Casos -----------------------------------------------------------------------------------

case_start "sin backup.env: falla con un mensaje claro y no toca Docker"
rm "${MENTA_HOME}/backup.env"
run_backup
assert_rc 1
assert_output_has "Falta ${MENTA_HOME}/backup.env"
assert_calls_lack "docker"

case_start "camino feliz: volcado, comprobantes, copia cifrada, verificación, retención remota y aviso"
run_backup
assert_rc 0
[[ "$(count_files 'menta-.*\.sql\.gz$')" == "1" ]] || fail "se esperaba 1 volcado"
[[ "$(count_files 'proofs-.*\.tar\.gz$')" == "1" ]] || fail "se esperaba 1 archivo de comprobantes"
[[ "$(count_files '\.tmp$')" == "0" ]] || fail "quedaron archivos .tmp"
dump="$(daily_files | grep 'menta-.*\.sql\.gz$')"
[[ "$(gzip -dc "$dump")" == *"Dump completed"* ]] || fail "el volcado no está completo"
[[ "$(stat -c %a "$dump")" == "600" ]] || fail "el volcado no es 600"
[[ "$(stat -c %a "$(daily_files | grep proofs)")" == "600" ]] || fail "los comprobantes no son 600"
stamp="$(basename "$dump" .sql.gz)"; stamp="${stamp#menta-}"
assert_calls_have "copy /data offsitecrypt:daily --include menta-${stamp}.sql.gz --include proofs-${stamp}.tar.gz"
assert_calls_have "check /data offsitecrypt:daily --one-way --size-only"
assert_calls_have "delete offsitecrypt:daily --min-age 30d --exclude menta-${stamp}.sql.gz --exclude proofs-${stamp}.tar.gz"
assert_calls_have "--env-file ${MENTA_HOME}/backup.env"
assert_calls_have "-v ${MENTA_HOME}/backups/daily:/data:ro rclone/rclone:1.75.2@sha256:"
assert_pings "curl ${PING_URL}"
assert_output_has "Backup completado"

case_start "el orden importa: la copia fuera del VPS va después del volcado y de los comprobantes"
run_backup
assert_rc 0
dump_line="$(grep -n 'docker exec menta-mysql' "${FAKE}/calls.log" | head -1 | cut -d: -f1)"
proofs_line="$(grep -n 'docker exec menta-api' "${FAKE}/calls.log" | head -1 | cut -d: -f1)"
copy_line="$(grep -n ' copy ' "${FAKE}/calls.log" | head -1 | cut -d: -f1)"
delete_line="$(grep -n ' delete ' "${FAKE}/calls.log" | head -1 | cut -d: -f1)"
[[ "$dump_line" -lt "$proofs_line" && "$proofs_line" -lt "$copy_line" && "$copy_line" -lt "$delete_line" ]] \
    || fail "orden incorrecto: dump=${dump_line} proofs=${proofs_line} copy=${copy_line} delete=${delete_line}"

case_start "MySQL detenido: falla, no copia nada y avisa del fallo"
rm "${FAKE}/running.menta-mysql"
run_backup
assert_rc 1
assert_output_has "menta-mysql no está corriendo"
assert_calls_lack " copy "
assert_pings "curl ${PING_URL}/fail"

case_start "API detenida: falla (hacen falta los comprobantes) y avisa del fallo"
rm "${FAKE}/running.menta-api"
run_backup
assert_rc 1
assert_output_has "menta-api no está corriendo"
assert_pings "curl ${PING_URL}/fail"

case_start "falla mysqldump: no deja archivos, no copia y avisa del fallo"
touch "${FAKE}/dump_fails"
run_backup
assert_rc 1
assert_output_has "mysqldump falló"
[[ -z "$(daily_files)" ]] || fail "quedaron archivos: $(daily_files)"
assert_calls_lack " copy "
assert_pings "curl ${PING_URL}/fail"

case_start "volcado truncado: se rechaza, no deja archivos y no copia"
touch "${FAKE}/dump_truncated"
run_backup
assert_rc 1
assert_output_has "incompleto"
[[ -z "$(daily_files)" ]] || fail "quedaron archivos: $(daily_files)"
assert_calls_lack " copy "

case_start "fallan los comprobantes: no copia nada fuera del VPS"
touch "${FAKE}/proofs_fail"
run_backup
assert_rc 1
assert_output_has "comprobantes"
assert_calls_lack " copy "
[[ "$(count_files '\.tmp$')" == "0" ]] || fail "quedaron archivos .tmp"
assert_pings "curl ${PING_URL}/fail"

case_start "falla la copia fuera del VPS: conserva lo local, no borra nada remoto y avisa del fallo"
touch "${FAKE}/rclone_copy_fails"
run_backup
assert_rc 1
assert_output_has "La copia fuera del VPS falló"
[[ "$(count_files 'menta-.*\.sql\.gz$')" == "1" ]] || fail "se perdió el volcado local"
assert_calls_lack " delete "
assert_pings "curl ${PING_URL}/fail"

case_start "la verificación remota no coincide: no aplica la retención remota"
touch "${FAKE}/rclone_check_fails"
run_backup
assert_rc 1
assert_output_has "no coincide"
assert_calls_lack " delete "
assert_pings "curl ${PING_URL}/fail"

case_start "retención local: conserva los BACKUP_LOCAL_KEEP más nuevos de cada tipo"
write_backup_settings "BACKUP_LOCAL_KEEP=3"
mkdir -p "${MENTA_HOME}/backups/daily"
for n in 01 02 03 04 05; do
    echo x > "${MENTA_HOME}/backups/daily/menta-202601${n}T000000Z.sql.gz"
    echo x > "${MENTA_HOME}/backups/daily/proofs-202601${n}T000000Z.tar.gz"
done
run_backup
assert_rc 0
[[ "$(count_files 'menta-.*\.sql\.gz$')" == "3" ]] || fail "se esperaban 3 volcados y hay $(count_files 'menta-.*\.sql\.gz$')"
[[ "$(count_files 'proofs-.*\.tar\.gz$')" == "3" ]] || fail "se esperaban 3 archivos de comprobantes"
[[ ! -e "${MENTA_HOME}/backups/daily/menta-20260101T000000Z.sql.gz" ]] || fail "no borró el más viejo"
[[ -e "${MENTA_HOME}/backups/daily/menta-20260105T000000Z.sql.gz" ]] || fail "borró uno reciente"

case_start "retención remota configurable"
write_backup_settings "BACKUP_REMOTE_RETENTION_DAYS=45"
run_backup
assert_rc 0
assert_calls_have "delete offsitecrypt:daily --min-age 45d"

case_start "retención remota de 0 días: se rechaza antes de borrar nada"
write_backup_settings "BACKUP_REMOTE_RETENTION_DAYS=0"
run_backup
assert_rc 1
assert_output_has "BACKUP_REMOTE_RETENTION_DAYS"
assert_calls_lack "docker"

case_start "retención local de 1 copia: se rechaza"
write_backup_settings "BACKUP_LOCAL_KEEP=1"
run_backup
assert_rc 1
assert_output_has "BACKUP_LOCAL_KEEP"

case_start "sin BACKUP_PING_URL: no llama al monitor"
cat > "${MENTA_HOME}/backup.env" <<'EOF'
RCLONE_CONFIG_OFFSITE_TYPE=s3
EOF
run_backup
assert_rc 0
assert_pings ""

case_start "el aviso al monitor falla: el backup igual termina bien"
touch "${FAKE}/curl_fails"
run_backup
assert_rc 0
assert_output_has "No se pudo avisar al monitor"
assert_output_has "Backup completado"

case_start "otro backup en curso: no corre en paralelo"
( exec 8> "${MENTA_HOME}/backup.lock"; flock -n 8 && sleep 5 ) &
holder=$!
sleep 1
run_backup
assert_rc 1
assert_output_has "otro backup en curso"
assert_pings "curl ${PING_URL}/fail"
kill "$holder" 2> /dev/null || true
wait "$holder" 2> /dev/null || true

echo
if [[ "$FAILURES" -gt 0 ]]; then
    echo "${FAILURES} fallos en ${CASES} casos"
    exit 1
fi
echo "OK: ${CASES} casos"
