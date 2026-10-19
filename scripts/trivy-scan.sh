#!/usr/bin/env bash

# trivy-scan.sh - Escanea una imagen Docker local con Trivy (#192, ADR-0042).
#
# Uso: scripts/trivy-scan.sh <imagen>
#   scripts/trivy-scan.sh menta-api:ci
#
# Política: falla (exit 1) ante vulnerabilidades CRITICAL o HIGH con fix disponible.
# MEDIUM y las que no tienen fix solo se informan en el reporte. Las excepciones viven en
# .trivyignore.yaml, cada una con "statement" y "expired_at".
#
# Variables opcionales:
#   TRIVY_CACHE_DIR   cache de la base de vulnerabilidades (por defecto ~/.cache/trivy)
#   TRIVY_REPORT_DIR  si se define, guarda ahí el reporte completo (CRITICAL, HIGH y MEDIUM)
#
# Trivy corre como imagen oficial con versión exacta y digest, igual que Gitleaks y ShellCheck
# en el CI: no depende de los tags de una acción de terceros y se reproduce en local.

set -euo pipefail

# Para actualizarla: elegir un release de https://github.com/aquasecurity/trivy/releases,
# hacer `docker pull aquasec/trivy:<versión>` y copiar el digest de `docker inspect`.
readonly TRIVY_IMAGE="aquasec/trivy:0.75.0@sha256:af6acf9a6b85dfe389a1941505c0ce9efef52a4719635e1a962f022a3d855daa"

# Mirror de public.ecr.aws como respaldo si ghcr.io limita las descargas de la base de datos
readonly DB_REPOSITORY="ghcr.io/aquasecurity/trivy-db:2,public.ecr.aws/aquasecurity/trivy-db:2"
readonly JAVA_DB_REPOSITORY="ghcr.io/aquasecurity/trivy-java-db:1,public.ecr.aws/aquasecurity/trivy-java-db:1"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
IGNORE_FILE="${PROJECT_ROOT}/.trivyignore.yaml"

log_info() {
    echo "[INFO] $1"
}

log_error() {
    echo "[ERROR] $1" >&2
}

# Cada excepción exige motivo y fecha de revisión: al vencer, Trivy deja de aplicarla y el
# escaneo vuelve a fallar. Trivy no obliga a escribirlos, así que se verifica aquí.
validate_ignore_file() {
    if [[ ! -f "$IGNORE_FILE" ]]; then
        log_error "Falta ${IGNORE_FILE}"
        exit 2
    fi

    local ids statements expirations
    ids="$(grep -cE '^[[:space:]]*-[[:space:]]+id:' "$IGNORE_FILE" || true)"
    statements="$(grep -cE '^[[:space:]]+statement:[[:space:]]*[^[:space:]]' "$IGNORE_FILE" || true)"
    expirations="$(grep -cE '^[[:space:]]+expired_at:[[:space:]]*[0-9]{4}-[0-9]{2}-[0-9]{2}' "$IGNORE_FILE" || true)"

    if [[ "$statements" != "$ids" ]] || [[ "$expirations" != "$ids" ]]; then
        log_error ".trivyignore.yaml: ${ids} excepciones, ${statements} con statement y ${expirations} con expired_at (YYYY-MM-DD). Todas necesitan ambos."
        exit 2
    fi
}

main() {
    local image="${1:-}"
    if [[ -z "$image" ]]; then
        log_error "Uso: scripts/trivy-scan.sh <imagen>"
        exit 2
    fi

    validate_ignore_file

    local cache_dir="${TRIVY_CACHE_DIR:-${HOME}/.cache/trivy}"
    mkdir -p "$cache_dir"

    local -a trivy_run=(
        docker run --rm
        -v /var/run/docker.sock:/var/run/docker.sock:ro
        -v "${cache_dir}:/root/.cache/trivy"
        -v "${IGNORE_FILE}:/.trivyignore.yaml:ro"
        "$TRIVY_IMAGE"
    )
    local -a common=(
        image
        --scanners vuln
        --ignorefile /.trivyignore.yaml
        --db-repository "$DB_REPOSITORY"
        --java-db-repository "$JAVA_DB_REPOSITORY"
        --no-progress
    )

    # 1. Reporte completo, informativo: nunca hace fallar el script
    if [[ -n "${TRIVY_REPORT_DIR:-}" ]]; then
        mkdir -p "$TRIVY_REPORT_DIR"
        local report="${TRIVY_REPORT_DIR}/trivy-${image//[\/:]/_}.txt"
        log_info "Reporte completo de ${image} en ${report}"
        "${trivy_run[@]}" "${common[@]}" --severity CRITICAL,HIGH,MEDIUM --format table "$image" > "$report"
    fi

    # 2. Gate: CRITICAL y HIGH con fix disponible
    # --exit-code 10 distingue "hay hallazgos" de un error del propio Trivy (que sale con 1)
    log_info "Escaneando ${image} (CRITICAL y HIGH con fix disponible)"
    local status=0
    "${trivy_run[@]}" "${common[@]}" --severity CRITICAL,HIGH --ignore-unfixed --exit-code 10 "$image" || status=$?
    case "$status" in
        0)
            log_info "${image}: sin vulnerabilidades CRITICAL/HIGH con fix"
            ;;
        10)
            log_error "${image}: hay vulnerabilidades CRITICAL/HIGH con fix. Ver docs/16-CICD-PIPELINE.md para triarlas."
            exit 1
            ;;
        *)
            log_error "${image}: Trivy falló (código ${status}); no es un hallazgo de seguridad."
            exit "$status"
            ;;
    esac
}

main "$@"
