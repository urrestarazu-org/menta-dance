#!/usr/bin/env bash

# deploy.test.sh - Pruebas de deploy.sh con un `docker` falso (#593).
#
# No toca Docker ni el VPS: reemplaza `docker` por un script que registra las llamadas y responde
# según archivos de estado. Prueba la lógica del script (validación, backup, digests, rollback,
# poda, lock y archivo de configuración); no prueba Compose ni los healthchecks reales.
# Necesita las herramientas de GNU/Linux (flock, tar, sha256sum): corre en el runner de CI o en un
# contenedor ubuntu:
#   docker run --rm -v "$PWD:/repo:ro" ubuntu:24.04 bash /repo/infra/docker/prod/deploy.test.sh

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly DEPLOY="${SCRIPT_DIR}/deploy.sh"

# Digest de prueba: "sha256:" más 64 veces el mismo carácter hexadecimal
fake_digest() {
    printf 'sha256:'
    printf "$1%.0s" {1..64}
}

OLD_API="$(fake_digest a)"
OLD_BFF="$(fake_digest b)"
NEW_API="$(fake_digest c)"
NEW_BFF="$(fake_digest d)"
OTHER_API="$(fake_digest e)"
OTHER_BFF="$(fake_digest f)"
readonly OLD_API OLD_BFF NEW_API NEW_BFF OTHER_API OTHER_BFF

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
FAILURES=0
CASES=0

# --- Arnés -----------------------------------------------------------------------------------

new_sandbox() {
    rm -rf "${WORK:?}/menta" "${WORK:?}/fake" "${WORK:?}/bin" "${WORK:?}/config"
    mkdir -p "${WORK}/menta" "${WORK}/fake" "${WORK}/bin"
    export MENTA_HOME="${WORK}/menta"
    export FAKE="${WORK}/fake"
    export PATH="${WORK}/bin:${PATH_ORIGINAL}"
    export HEALTH_TIMEOUT=2 HEALTH_INTERVAL=1
    unset SSH_ORIGINAL_COMMAND
    : > "${FAKE}/calls.log"
    write_fake_docker
    printf 'MYSQL_ROOT_PASSWORD=secreto\nAPI_DIGEST=\nBFF_DIGEST=\nSMTP_HOST=smtp.example\n' > "${MENTA_HOME}/.env"
    chmod 600 "${MENTA_HOME}/.env"
    make_config_archive
}

# `docker` falso: registra cada llamada y responde según los archivos de $FAKE
write_fake_docker() {
    cat > "${WORK}/bin/docker" <<'FAKE_DOCKER'
#!/usr/bin/env bash
echo "docker $*" >> "${FAKE}/calls.log"
case "$1" in
    compose)
        if [[ " $* " == *" pull "* ]]; then
            echo "pull-env API_DIGEST=${API_DIGEST:-} BFF_DIGEST=${BFF_DIGEST:-}" >> "${FAKE}/calls.log"
            [[ ! -f "${FAKE}/pull_fails" ]] || exit 1
        fi
        ;;
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
            menta-api | menta-bff)
                [[ ! -f "${FAKE}/${2}_down" ]] || exit 1
                echo '{"status":"UP","components":{"db":{"status":"UP"}}}'
                ;;
            menta-nginx)
                echo ok
                ;;
        esac
        ;;
    image)
        case "$2" in
            ls)
                # $FAKE/images.<repositorio>: líneas "<id> <digest>"
                repo="${*: -1}"
                [[ ! -f "${FAKE}/images.${repo##*/}" ]] || cat "${FAKE}/images.${repo##*/}"
                ;;
        esac
        ;;
esac
exit 0
FAKE_DOCKER
    chmod +x "${WORK}/bin/docker"
}

# tar.gz de configuración como lo arma release.yml: tar -czf - -C infra/docker prod nginx/conf/conf.d
make_config_archive() {
    local src="${WORK}/config/src"
    rm -rf "${WORK}/config"
    mkdir -p "${src}/prod/nginx" "${src}/nginx/conf/conf.d"
    echo "name: menta" > "${src}/prod/docker-compose.prod.yml"
    echo "worker_processes 1;" > "${src}/prod/nginx/nginx.conf"
    echo "# upstreams" > "${src}/nginx/conf/conf.d/upstreams.conf"
    cp "$DEPLOY" "${src}/prod/deploy.sh"
    tar -czf "${WORK}/config/stack.tar.gz" -C "$src" prod nginx/conf/conf.d
}

# run_deploy [args...]: ejecuta deploy.sh; la entrada estándar es $STDIN_FILE o /dev/null
run_deploy() {
    local stdin_file="${STDIN_FILE:-/dev/null}"
    set +e
    OUTPUT="$(bash "$DEPLOY" "$@" < "$stdin_file" 2>&1)"
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

assert_env_value() {
    local actual
    actual="$(grep -E "^$1=" "${MENTA_HOME}/.env" | cut -d= -f2-)"
    [[ "$actual" == "$2" ]] || fail ".env: $1='${actual}', se esperaba '$2'"
}

assert_file_missing() {
    [[ ! -e "$1" ]] || fail "existe $1 y no debía"
}

assert_file_has() {
    [[ -f "$1" ]] || { fail "falta $1"; return; }
    [[ "$(cat "$1")" == "$2" ]] || fail "$1 contiene '$(cat "$1")', se esperaba '$2'"
}

case_start() {
    CASES=$((CASES + 1))
    echo "- $1"
    new_sandbox
}

PATH_ORIGINAL="$PATH"

# --- Casos -----------------------------------------------------------------------------------

case_start "sin digests: sale con 2 y no toca Docker"
run_deploy
assert_rc 2
assert_output_has "Uso: deploy.sh"
assert_calls_lack "docker"

case_start "digest con formato inválido: sale con 2 y no toca Docker"
run_deploy "sha256:corto" "$NEW_BFF"
assert_rc 2
assert_output_has "Digest de la API inválido"
assert_calls_lack "docker"

case_start "SSH_ORIGINAL_COMMAND con un comando inyectado: se rechaza"
export SSH_ORIGINAL_COMMAND="${NEW_API} ${NEW_BFF}; touch ${WORK}/pwned"
run_deploy
assert_rc 2
assert_file_missing "${WORK}/pwned"
assert_calls_lack "docker"

case_start "SSH_ORIGINAL_COMMAND con un tercer argumento: se rechaza"
export SSH_ORIGINAL_COMMAND="${NEW_API} ${NEW_BFF} ${OTHER_API}"
run_deploy
assert_rc 2

case_start "primer despliegue: instala la configuración, escribe los digests y no hay backup ni anterior"
export SSH_ORIGINAL_COMMAND="${NEW_API} ${NEW_BFF}"
STDIN_FILE="${WORK}/config/stack.tar.gz" run_deploy
assert_rc 0
assert_env_value API_DIGEST "$NEW_API"
assert_env_value BFF_DIGEST "$NEW_BFF"
assert_env_value SMTP_HOST "smtp.example"
assert_env_value MYSQL_ROOT_PASSWORD "secreto"
[[ "$(stat -c %a "${MENTA_HOME}/.env")" == "600" ]] || fail ".env perdió el modo 600"
assert_file_has "${MENTA_HOME}/stack/nginx/conf/conf.d/upstreams.conf" "# upstreams"
assert_file_missing "${MENTA_HOME}/previous-digests"
assert_file_missing "${MENTA_HOME}/backups"
assert_calls_have "pull-env API_DIGEST=${NEW_API} BFF_DIGEST=${NEW_BFF}"
assert_calls_have " up -d --remove-orphans"
assert_calls_lack "--force-recreate"
assert_output_has "Deploy completado"

case_start "segundo despliegue: backup, digests anteriores guardados y poda de imágenes"
sed -i "s/^API_DIGEST=.*/API_DIGEST=${OLD_API}/; s/^BFF_DIGEST=.*/BFF_DIGEST=${OLD_BFF}/" "${MENTA_HOME}/.env"
touch "${FAKE}/running.menta-mysql"
printf 'id-nueva %s\nid-vieja %s\nid-ajena %s\n' "$NEW_API" "$OLD_API" "$OTHER_API" > "${FAKE}/images.menta-api"
printf 'id-bff-nueva %s\nid-bff-vieja %s\nid-bff-ajena %s\n' "$NEW_BFF" "$OLD_BFF" "$OTHER_BFF" > "${FAKE}/images.menta-bff"
export SSH_ORIGINAL_COMMAND="${NEW_API} ${NEW_BFF}"
STDIN_FILE="${WORK}/config/stack.tar.gz" run_deploy
assert_rc 0
assert_file_has "${MENTA_HOME}/previous-digests" "${OLD_API} ${OLD_BFF}"
assert_env_value API_DIGEST "$NEW_API"
mapfile -t dumps < <(find "${MENTA_HOME}/backups" -name 'menta-*.sql.gz')
[[ ${#dumps[@]} -eq 1 ]] || fail "se esperaba 1 backup y hay ${#dumps[@]}"
[[ "$(gzip -dc "${dumps[0]}")" == *"Dump completed"* ]] || fail "el backup no tiene el volcado"
[[ "$(stat -c %a "${dumps[0]}")" == "600" ]] || fail "el backup no es 600"
assert_calls_have "docker image rm id-ajena"
assert_calls_have "docker image rm id-bff-ajena"
assert_calls_lack "docker image rm id-nueva"
assert_calls_lack "docker image rm id-vieja"
assert_calls_lack "docker image rm id-bff-vieja"

case_start "mismos digests otra vez: no pisa los anteriores"
sed -i "s/^API_DIGEST=.*/API_DIGEST=${NEW_API}/; s/^BFF_DIGEST=.*/BFF_DIGEST=${NEW_BFF}/" "${MENTA_HOME}/.env"
echo "${OLD_API} ${OLD_BFF}" > "${MENTA_HOME}/previous-digests"
export SSH_ORIGINAL_COMMAND="${NEW_API} ${NEW_BFF}"
STDIN_FILE="${WORK}/config/stack.tar.gz" run_deploy
assert_rc 0
assert_file_has "${MENTA_HOME}/previous-digests" "${OLD_API} ${OLD_BFF}"

case_start "el health check falla: sale con 1, indica cómo volver y no poda"
sed -i "s/^API_DIGEST=.*/API_DIGEST=${OLD_API}/; s/^BFF_DIGEST=.*/BFF_DIGEST=${OLD_BFF}/" "${MENTA_HOME}/.env"
touch "${FAKE}/menta-api_down"
printf 'id-ajena %s\n' "$OTHER_API" > "${FAKE}/images.menta-api"
STDIN_FILE="${WORK}/config/stack.tar.gz" run_deploy "$NEW_API" "$NEW_BFF"
assert_rc 1
assert_output_has "no respondió UP"
assert_output_has "Para volver a la versión anterior: ${DEPLOY} ${OLD_API} ${OLD_BFF}"
assert_file_has "${MENTA_HOME}/previous-digests" "${OLD_API} ${OLD_BFF}"
assert_calls_lack "docker image rm"

case_start "falla mysqldump: no despliega y deja .env intacto"
touch "${FAKE}/running.menta-mysql" "${FAKE}/dump_fails"
STDIN_FILE="${WORK}/config/stack.tar.gz" run_deploy "$NEW_API" "$NEW_BFF"
assert_rc 1
assert_output_has "mysqldump falló"
assert_env_value API_DIGEST ""
assert_calls_lack "pull"
assert_file_missing "${MENTA_HOME}/previous-digests"

case_start "backup truncado: no despliega y no deja archivos a medias"
touch "${FAKE}/running.menta-mysql" "${FAKE}/dump_truncated"
STDIN_FILE="${WORK}/config/stack.tar.gz" run_deploy "$NEW_API" "$NEW_BFF"
assert_rc 1
assert_output_has "incompleto"
[[ -z "$(find "${MENTA_HOME}/backups" -type f)" ]] || fail "quedaron archivos de backup"
assert_calls_lack "pull"

case_start "falla la descarga de imágenes: no cambia .env"
touch "${FAKE}/pull_fails"
STDIN_FILE="${WORK}/config/stack.tar.gz" run_deploy "$NEW_API" "$NEW_BFF"
assert_rc 1
assert_output_has "No se pudieron descargar"
assert_env_value API_DIGEST ""
assert_calls_lack " up "

case_start "retención de backups: conserva los últimos BACKUP_KEEP"
mkdir -p "${MENTA_HOME}/backups"
for n in 01 02 03 04; do echo x > "${MENTA_HOME}/backups/menta-2026010${n}T000000Z.sql.gz"; done
touch "${FAKE}/running.menta-mysql"
BACKUP_KEEP=2 STDIN_FILE="${WORK}/config/stack.tar.gz" run_deploy "$NEW_API" "$NEW_BFF"
assert_rc 0
mapfile -t kept < <(find "${MENTA_HOME}/backups" -name 'menta-*.sql.gz')
[[ ${#kept[@]} -eq 2 ]] || fail "se esperaban 2 backups y hay ${#kept[@]}"
assert_file_missing "${MENTA_HOME}/backups/menta-20260101T000000Z.sql.gz"

case_start "configuración con una ruta fuera de lo permitido: se rechaza y no cambia el stack"
STDIN_FILE="${WORK}/config/stack.tar.gz" run_deploy "$NEW_API" "$NEW_BFF"
assert_rc 0
tar -czf "${WORK}/config/evil.tar.gz" -C "${WORK}/config/src" --transform 's,^,../,' prod
STDIN_FILE="${WORK}/config/evil.tar.gz" run_deploy "$OTHER_API" "$OTHER_BFF"
assert_rc 1
assert_output_has "Ruta inesperada"
assert_file_has "${MENTA_HOME}/stack/nginx/conf/conf.d/upstreams.conf" "# upstreams"

case_start "configuración con un enlace simbólico: se rechaza"
ln -s /etc "${WORK}/config/src/prod/enlace"
tar -czf "${WORK}/config/symlink.tar.gz" -C "${WORK}/config/src" prod nginx/conf/conf.d
STDIN_FILE="${WORK}/config/symlink.tar.gz" run_deploy "$NEW_API" "$NEW_BFF"
assert_rc 1
assert_output_has "no es archivo ni carpeta"
assert_file_missing "${MENTA_HOME}/stack"

case_start "el tar.gz que arma release.yml con los archivos reales del repo pasa la validación"
tar --create --gzip --owner=0 --group=0 --numeric-owner --file "${WORK}/config/real.tar.gz" \
    --directory "${SCRIPT_DIR}/.." prod nginx/conf/conf.d
STDIN_FILE="${WORK}/config/real.tar.gz" run_deploy "$NEW_API" "$NEW_BFF"
assert_rc 0
[[ -f "${MENTA_HOME}/stack/prod/docker-compose.prod.yml" ]] || fail "no se instaló el compose de producción"
[[ -f "${MENTA_HOME}/stack/prod/nginx/nginx.conf" ]] || fail "no se instaló nginx.conf"
[[ -f "${MENTA_HOME}/stack/nginx/conf/conf.d/upstreams.conf" ]] || fail "no se instaló conf.d/upstreams.conf"

case_start "configuración que no es un tar.gz: se rechaza"
echo "no soy un tar" > "${WORK}/config/basura"
STDIN_FILE="${WORK}/config/basura" run_deploy "$NEW_API" "$NEW_BFF"
assert_rc 1
assert_output_has "no es un tar.gz válido"

case_start "sin configuración nueva usa el stack ya instalado (rollback a mano)"
STDIN_FILE="${WORK}/config/stack.tar.gz" run_deploy "$NEW_API" "$NEW_BFF"
assert_rc 0
: > "${FAKE}/calls.log"
run_deploy "$OLD_API" "$OLD_BFF"
assert_rc 0
assert_env_value API_DIGEST "$OLD_API"
assert_file_has "${MENTA_HOME}/previous-digests" "${NEW_API} ${NEW_BFF}"
assert_calls_have "pull-env API_DIGEST=${OLD_API} BFF_DIGEST=${OLD_BFF}"

case_start "sin configuración y sin stack instalado: pide el tar.gz"
run_deploy "$NEW_API" "$NEW_BFF"
assert_rc 1
assert_output_has "enviá el tar.gz"

case_start "cambio en la configuración: recrea Nginx; sin cambios, no"
STDIN_FILE="${WORK}/config/stack.tar.gz" run_deploy "$NEW_API" "$NEW_BFF"
assert_rc 0
assert_calls_lack "--force-recreate"
STDIN_FILE="${WORK}/config/stack.tar.gz" run_deploy "$OTHER_API" "$OTHER_BFF"
assert_rc 0
assert_calls_lack "--force-recreate"
touch "${FAKE}/running.menta-nginx"
echo "worker_processes 2;" > "${WORK}/config/src/prod/nginx/nginx.conf"
tar -czf "${WORK}/config/stack2.tar.gz" -C "${WORK}/config/src" prod nginx/conf/conf.d
STDIN_FILE="${WORK}/config/stack2.tar.gz" run_deploy "$NEW_API" "$NEW_BFF"
assert_rc 0
assert_calls_have "--force-recreate --no-deps nginx"

case_start "otro deploy en curso: no corre en paralelo"
STDIN_FILE="${WORK}/config/stack.tar.gz" run_deploy "$NEW_API" "$NEW_BFF"
assert_rc 0
( exec 8> "${MENTA_HOME}/deploy.lock"; flock -n 8 && sleep 5 ) &
holder=$!
sleep 1
run_deploy "$OTHER_API" "$OTHER_BFF"
assert_rc 1
assert_output_has "otro deploy en curso"
kill "$holder" 2>/dev/null || true
wait "$holder" 2>/dev/null || true

echo
if [[ "$FAILURES" -gt 0 ]]; then
    echo "${FAILURES} fallos en ${CASES} casos"
    exit 1
fi
echo "OK: ${CASES} casos"
