#!/usr/bin/env bash

# Reproducible local-only catalog/content E2E lifecycle. It deliberately owns
# a separate Compose project so reset/clean never removes a developer stack.
set -euo pipefail

readonly PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
readonly COMPOSE_FILE="${PROJECT_ROOT}/infra/docker/database/docker-compose.yml"
readonly ENV_FILE="${PROJECT_ROOT}/.env"
readonly COMPOSE_PROJECT="menta-e2e-catalog-content"
readonly CONTAINER_PREFIX="menta-e2e-catalog-content"
readonly API_PORT="18081"
readonly MYSQL_PORT="33306"
readonly REDIS_PORT="36379"
readonly MAILPIT_PORT="38025"
readonly API_HEALTH_URL="http://localhost:${API_PORT}/actuator/health"
readonly API_LOG="${PROJECT_ROOT}/.dev-logs/e2e-catalog-content-api.log"
readonly API_PID_FILE="${PROJECT_ROOT}/.dev-pids/e2e-catalog-content-api.pid"
readonly BRUNO_FOLDER="bruno/E2E/catalog-content"

api_pid=""

usage() {
    cat <<'EOF'
Usage: scripts/e2e/catalog-content.sh [--clean] [--help]

Starts an isolated local catalog/content E2E stack, runs the versioned Bruno
journey, and leaves only its Compose project running for diagnosis.
  --clean  stop and remove only the dedicated E2E Compose project
EOF
}

fail() {
    printf '[ERROR] %s\n' "$*" >&2
    exit 1
}

require_command() {
    command -v "$1" >/dev/null 2>&1 || fail "Required command not found: $1"
}

require_node_20_11_1() {
    local version
    version="$(node --version)" || fail "Unable to determine Node version"
    [[ "$version" == "v20.11.1" ]] || fail "Node 20.11.1 is required; found $version"
}

require_java_21() {
    java -version 2>&1 | head -1 | grep -Eq 'version "21([._][0-9]+)*([+-][^"]*)?"|openjdk 21' \
        || fail "JDK 21 is required"
}

compose() {
    MENTA_CONTAINER_PREFIX="$CONTAINER_PREFIX" MYSQL_HOST_PORT="$MYSQL_PORT" \
        REDIS_HOST_PORT="$REDIS_PORT" SMTP_HOST_PORT=31025 MAILPIT_WEB_HOST_PORT="$MAILPIT_PORT" \
        OTEL_GRPC_HOST_PORT=34317 OTEL_HTTP_HOST_PORT=34318 GRAFANA_HOST_PORT=33000 docker compose \
        --project-name "$COMPOSE_PROJECT" --env-file "$ENV_FILE" -f "$COMPOSE_FILE" "$@"
}

clean() {
    require_command docker
    [[ -f "$ENV_FILE" ]] || fail "Missing $ENV_FILE"
    compose down --volumes --remove-orphans
}

wait_for() {
    local description=$1
    local check_command=$2
    local attempt=0
    while (( attempt < 60 )); do
        if eval "$check_command" >/dev/null 2>&1; then
            return 0
        fi
        attempt=$((attempt + 1))
        sleep 2
    done
    fail "$description did not become ready; inspect: compose logs"
}

stop_api() {
    if [[ -n "$api_pid" ]] && kill -0 "$api_pid" 2>/dev/null; then
        kill "$api_pid" 2>/dev/null || true
        wait "$api_pid" 2>/dev/null || true
    fi
}

start_api() {
    mkdir -p "$(dirname "$API_LOG")" "$(dirname "$API_PID_FILE")"
    SPRING_PROFILES_ACTIVE=e2e-catalog-content SERVER_PORT="$API_PORT" \
        MYSQL_URL="jdbc:mysql://localhost:${MYSQL_PORT}/menta" \
        SPRING_DATA_REDIS_PORT="$REDIS_PORT" SMTP_PORT=31025 \
        "$PROJECT_ROOT/gradlew" --no-daemon :api:app:bootRun >"$API_LOG" 2>&1 &
    api_pid=$!
    printf '%s\n' "$api_pid" >"$API_PID_FILE"
    wait_for "API" "curl --fail --silent --show-error --max-time 2 '$API_HEALTH_URL'"
}

main() {
    case "${1:-}" in
        --help|-h) usage; return 0 ;;
        --clean) clean; return 0 ;;
        '') ;;
        *) usage >&2; fail "Unknown argument: $1" ;;
    esac

    require_command docker
    require_command curl
    require_command jq
    require_command node
    require_command java
    require_node_20_11_1
    require_java_21
    [[ -x "$PROJECT_ROOT/gradlew" ]] || fail "Gradle wrapper is missing or not executable"
    [[ -f "$ENV_FILE" ]] || fail "Missing $ENV_FILE"
    docker compose version >/dev/null 2>&1 || fail "Docker Compose v2 is required"
    docker info >/dev/null 2>&1 || fail "Docker daemon is unavailable"

    trap stop_api EXIT INT TERM
    compose down --volumes --remove-orphans
    compose up -d
    wait_for "MySQL" "docker inspect --format '{{.State.Health.Status}}' '${CONTAINER_PREFIX}-mysql' | grep -qx healthy"
    wait_for "Redis" "docker inspect --format '{{.State.Health.Status}}' '${CONTAINER_PREFIX}-redis' | grep -qx healthy"
    wait_for "Mailpit" "docker inspect --format '{{.State.Health.Status}}' '${CONTAINER_PREFIX}-mailpit' | grep -qx healthy"
    start_api
    (
        cd "$PROJECT_ROOT"
        npx --yes @usebruno/cli run "$BRUNO_FOLDER" --env local
    )
}

main "$@"
