#!/usr/bin/env bash

# resolve-release-version.test.sh - Pruebas de resolve-release-version.sh (#639).
#
# El script recibe el nombre de la rama de origen de un PR mergeado a main y devuelve la versión
# del tag que crea release-tag.yml. Estas pruebas cubren los nombres válidos, los inválidos y los
# que parecen una inyección de comandos: en ningún caso el argumento se ejecuta.
# Solo usa bash y coreutils; corre igual en macOS que en el runner de CI o en un contenedor:
#   bash scripts/resolve-release-version.test.sh
#   docker run --rm -v "$PWD:/repo:ro" ubuntu:24.04 bash /repo/scripts/resolve-release-version.test.sh

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly RESOLVE="${SCRIPT_DIR}/resolve-release-version.sh"

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
FAILURES=0
CASES=0

# --- Arnés -----------------------------------------------------------------------------------

# run_resolve [args...]: ejecuta el script desde una carpeta vacía y separa stdout de stderr
run_resolve() {
    set +e
    STDOUT="$(cd "$WORK" && bash "$RESOLVE" "$@" 2> "${WORK}/stderr")"
    RC=$?
    set -e
    STDERR="$(cat "${WORK}/stderr")"
}

# --- Aserciones ------------------------------------------------------------------------------

fail() {
    echo "  FALLO: $1"
    FAILURES=$((FAILURES + 1))
}

case_start() {
    CASES=$((CASES + 1))
    echo "- $1"
    rm -f "${WORK}/pwned"
}

# expect_version <rama> <versión>: sale con 0, imprime solo la versión y nada en stderr
expect_version() {
    case_start "'$1' -> $2"
    run_resolve "$1"
    [[ "$RC" == "0" ]] || fail "código de salida $RC, se esperaba 0. stderr: ${STDERR}"
    [[ "$STDOUT" == "$2" ]] || fail "stdout '${STDOUT}', se esperaba '$2'"
    [[ -z "$STDERR" ]] || fail "stderr no vacío: ${STDERR}"
}

# expect_rejected <descripción> [args...]: sale distinto de 0, sin stdout, con un mensaje en stderr
expect_rejected() {
    local description="$1"
    shift
    case_start "$description"
    run_resolve "$@"
    [[ "$RC" != "0" ]] || fail "salió con 0 y debía fallar. stdout: '${STDOUT}'"
    [[ -z "$STDOUT" ]] || fail "stdout no vacío: '${STDOUT}'"
    [[ "$STDERR" == *"release/X.Y.Z"* ]] || fail "stderr no explica el formato esperado: '${STDERR}'"
    [[ ! -e "${WORK}/pwned" ]] || fail "el argumento se ejecutó"
}

# --- Casos válidos ---------------------------------------------------------------------------

expect_version "release/0.4.0" "0.4.0"
expect_version "release/1.2.3" "1.2.3"
expect_version "hotfix/0.2.1" "0.2.1"
expect_version "release/10.20.30" "10.20.30"

# --- Casos inválidos -------------------------------------------------------------------------

expect_rejected "sin argumentos"
expect_rejected "argumento vacío" ""
expect_rejected "dos argumentos" "release/1.2.3" "release/1.2.4"
expect_rejected "versión que no es numérica" "release/foo"
expect_rejected "versión con dos números" "release/0.4"
expect_rejected "versión con cuatro números" "release/0.4.0.1"
expect_rejected "versión con sufijo (los pre-releases son manuales)" "release/0.4.0-rc.1"
expect_rejected "versión con prefijo v" "release/v0.4.0"
expect_rejected "rama feature" "feature/x"
expect_rejected "rama develop" "develop"
expect_rejected "prefijo sin versión" "release/"
expect_rejected "segmento extra" "release/0.4.0/x"
expect_rejected "prefijo anidado" "x/release/0.4.0"
expect_rejected "mayúsculas en el prefijo" "Release/0.4.0"
expect_rejected "espacio al principio" " release/0.4.0"
expect_rejected "espacio al final" "release/0.4.0 "
expect_rejected "espacio en medio" "release/0.4. 0"
expect_rejected "salto de línea al final" $'release/0.4.0\n'
expect_rejected "salto de línea con una segunda versión" $'release/0.4.0\nrelease/0.5.0'
expect_rejected "punto y coma con un comando" "release/1.2.3; touch ${WORK}/pwned"
expect_rejected "sustitución de comandos" "release/\$(touch ${WORK}/pwned)"
expect_rejected "sustitución de comandos sola" "\$(id)"
expect_rejected "comillas invertidas" "release/1.2.3\`touch ${WORK}/pwned\`"
expect_rejected "tubería" "release/1.2.3|touch ${WORK}/pwned"
expect_rejected "comodín" "release/*"
expect_rejected "dígitos que no son ASCII" "release/١.٢.٣"

echo
if [[ "$FAILURES" -gt 0 ]]; then
    echo "${FAILURES} fallos en ${CASES} casos"
    exit 1
fi
echo "OK: ${CASES} casos"
