#!/usr/bin/env bash

# Local Swagger UI for the hand-written OpenAPI contracts in api/openapi/.
#
# It only serves the YAML files for reading: the API does not expose Swagger
# (there is no springdoc) and this script never starts, stops or calls the API,
# the BFF or the Docker Compose infrastructure. The container is independent,
# read-only over api/openapi/, and published on 127.0.0.1 only.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
SCRIPT_PATH="${SCRIPT_DIR}/$(basename "${BASH_SOURCE[0]}")"
readonly SCRIPT_DIR PROJECT_ROOT SCRIPT_PATH
readonly SPEC_DIR="${API_DOCS_SPEC_DIR:-${PROJECT_ROOT}/api/openapi}"

readonly CONTAINER_NAME="menta-api-docs"
# Pinned on purpose: `latest` would change the UI under our feet.
readonly IMAGE="swaggerapi/swagger-ui:v5.34.1"
readonly CONTAINER_SPEC_PATH="/usr/share/nginx/html/spec"
# 8090 is free in this repo: the API, BFF, MySQL, Redis, Grafana, Loki, OTel and
# Mailpit already use 8081, 8080, 3306, 6379, 3000, 3100, 4317/4318 and 1025/8025.
readonly DEFAULT_PORT=8090
readonly RESERVED_PORTS=(80 443 1025 3000 3100 3306 4317 4318 6379 8025 8080 8081)
readonly READY_TIMEOUT_SECONDS=30

if [[ -t 1 && -z "${NO_COLOR:-}" ]]; then
    readonly RED=$'\033[0;31m'
    readonly GREEN=$'\033[0;32m'
    readonly YELLOW=$'\033[1;33m'
    readonly BLUE=$'\033[0;34m'
    readonly CYAN=$'\033[0;36m'
    readonly NC=$'\033[0m'
else
    readonly RED="" GREEN="" YELLOW="" BLUE="" CYAN="" NC=""
fi

log_info() { printf '%b[INFO]%b %s\n' "$BLUE" "$NC" "$*"; }
log_success() { printf '%b[OK]%b %s\n' "$GREEN" "$NC" "$*"; }
log_warning() { printf '%b[WARN]%b %s\n' "$YELLOW" "$NC" "$*" >&2; }
log_error() { printf '%b[ERROR]%b %s\n' "$RED" "$NC" "$*" >&2; }
log_step() { printf '%b==>%b %s\n' "$CYAN" "$NC" "$*"; }

usage() {
    cat <<EOF
Usage: ${SCRIPT_PATH} {start|stop|restart|status|help}

Commands:
  start     Serve the OpenAPI contracts in a local Swagger UI (idempotent)
  stop      Remove the Swagger UI container (idempotent)
  restart   Stop and start
  status    Show whether the Swagger UI is running
  help      Show this help

Swagger UI: http://localhost:${API_DOCS_PORT:-${DEFAULT_PORT}} (override with API_DOCS_PORT)
Contracts:  ${SPEC_DIR}/*-v1.yaml, mounted read-only. Requires Docker.

The viewer is read-only ("Try it out" is disabled) and never talks to the API.
It does not start or stop the API, the BFF or the infrastructure.
EOF
}

require_docker() {
    if ! command -v docker >/dev/null 2>&1; then
        log_error "Docker is required but 'docker' was not found. Install Docker Desktop and start it."
        return 1
    fi
    if ! docker info >/dev/null 2>&1; then
        log_error "Docker daemon is unavailable. Start Docker Desktop and retry."
        return 1
    fi
}

# Validates API_DOCS_PORT and prints it. Rejects non-numeric values and any
# port already used by this project so the UI never competes with the API,
# the BFF or the infrastructure.
resolve_port() {
    local port=${API_DOCS_PORT:-$DEFAULT_PORT}
    local reserved

    if [[ ! "$port" =~ ^[1-9][0-9]*$ ]] || (( port > 65535 )); then
        log_error "API_DOCS_PORT must be a number between 1 and 65535 (got '${port}')."
        return 1
    fi
    for reserved in "${RESERVED_PORTS[@]}"; do
        if [[ "$port" == "$reserved" ]]; then
            log_error "Port ${port} is already used by this project. Choose another API_DOCS_PORT."
            return 1
        fi
    done
    printf '%s' "$port"
}

# Builds the Swagger UI `URLS` selector from the contracts found on disk, so a
# new api/openapi/<name>-v1.yaml shows up without editing this script.
contract_urls_json() {
    local file
    local name
    local entries=""

    shopt -s nullglob
    for file in "$SPEC_DIR"/*-v1.yaml; do
        name=$(basename "$file" -v1.yaml)
        entries+="${entries:+,}{\"url\":\"/spec/$(basename "$file")\",\"name\":\"${name}\"}"
    done
    shopt -u nullglob

    if [[ -z "$entries" ]]; then
        log_error "No *-v1.yaml contract found in ${SPEC_DIR}."
        return 1
    fi
    printf '[%s]' "$entries"
}

container_is_running() {
    [[ "$(docker inspect --format '{{.State.Running}}' "$CONTAINER_NAME" 2>/dev/null || true)" == "true" ]]
}

wait_until_ready() {
    local url=$1
    local attempt=0
    local code

    while (( attempt < READY_TIMEOUT_SECONDS )); do
        code=$(curl --silent --output /dev/null --write-out '%{http_code}' --max-time 2 "$url" 2>/dev/null || true)
        [[ "$code" == "200" ]] && return 0
        attempt=$((attempt + 1))
        sleep 1
    done
    return 1
}

print_endpoints() {
    local port=$1

    printf '  Swagger UI: http://localhost:%s\n' "$port"
    printf '  Contratos:  %s/*-v1.yaml (solo lectura)\n' "$SPEC_DIR"
}

cmd_start() {
    local port
    local urls

    require_docker
    port=$(resolve_port)

    if container_is_running; then
        log_warning "Swagger UI is already running; leaving it unchanged."
        print_endpoints "$port"
        return 0
    fi

    urls=$(contract_urls_json)

    # A stopped or half-created container would block the fixed name.
    docker rm -f "$CONTAINER_NAME" >/dev/null 2>&1 || true

    log_step "Starting Swagger UI (${IMAGE}) on 127.0.0.1:${port}..."
    if ! docker run -d --rm \
        --name "$CONTAINER_NAME" \
        -p "127.0.0.1:${port}:8080" \
        -v "${SPEC_DIR}:${CONTAINER_SPEC_PATH}:ro" \
        -e "URLS=${urls}" \
        -e "SUPPORTED_SUBMIT_METHODS=[]" \
        "$IMAGE" >/dev/null; then
        log_error "Could not start Swagger UI. Is port ${port} already in use? Set another API_DOCS_PORT."
        return 1
    fi

    if command -v curl >/dev/null 2>&1; then
        log_info "Waiting for http://localhost:${port}..."
        if ! wait_until_ready "http://localhost:${port}/"; then
            log_error "Swagger UI did not answer within ${READY_TIMEOUT_SECONDS} seconds. Inspect it with: docker logs ${CONTAINER_NAME}"
            return 1
        fi
    fi

    printf '\n'
    log_success "Swagger UI is ready"
    print_endpoints "$port"
    printf '\nStop it with: %s stop\n' "$SCRIPT_PATH"
}

cmd_stop() {
    require_docker

    if ! docker inspect "$CONTAINER_NAME" >/dev/null 2>&1; then
        log_info "Swagger UI is not running."
        return 0
    fi
    log_step "Stopping Swagger UI..."
    docker rm -f "$CONTAINER_NAME" >/dev/null
    log_success "Swagger UI stopped"
}

cmd_status() {
    local port

    require_docker
    port=$(resolve_port)
    if container_is_running; then
        printf '  %-12s %bUP%b (http://localhost:%s)\n' "Swagger UI" "$GREEN" "$NC" "$port"
    else
        printf '  %-12s %bDOWN%b\n' "Swagger UI" "$RED" "$NC"
        return 1
    fi
}

main() {
    local command=${1:-}

    case "$command" in
        start|stop|restart|status)
            [[ $# -eq 1 ]] || { usage >&2; return 1; }
            ;;
    esac

    case "$command" in
        start) cmd_start ;;
        stop) cmd_stop ;;
        restart)
            cmd_stop
            cmd_start
            ;;
        status) cmd_status ;;
        help|-h|--help) usage ;;
        "")
            usage >&2
            return 1
            ;;
        *)
            log_error "Unknown command: ${command}"
            usage >&2
            return 1
            ;;
    esac
}

# Run only when executed directly so tests can source the helpers.
if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
    main "$@"
fi
