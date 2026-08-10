#!/usr/bin/env bash

# Local development lifecycle for Bruno testing.
# Starts Docker infrastructure plus the API and BFF from the Gradle workspace.

set -euo pipefail

readonly SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
readonly SCRIPT_PATH="${SCRIPT_DIR}/$(basename "${BASH_SOURCE[0]}")"
readonly ENV_FILE="${PROJECT_ROOT}/.env"
readonly INFRA_COMPOSE_FILE="${PROJECT_ROOT}/infra/docker/database/docker-compose.yml"
readonly GRADLEW="${PROJECT_ROOT}/gradlew"

readonly STATE_DIR="${PROJECT_ROOT}/.dev-pids"
readonly LOG_DIR="${PROJECT_ROOT}/.dev-logs"
readonly API_PID_FILE="${STATE_DIR}/api.pid"
readonly BFF_PID_FILE="${STATE_DIR}/bff.pid"
readonly API_LOG="${LOG_DIR}/api.log"
readonly BFF_LOG="${LOG_DIR}/bff.log"

readonly API_TASK=":api:app:bootRun"
readonly BFF_TASK=":bff:bootRun"
readonly API_HEALTH_URL="http://localhost:8081/actuator/health"
readonly BFF_HEALTH_URL="http://localhost:8080/actuator/health"

# Set by launch_service so cmd_start can roll back only processes created by
# the current invocation. Existing managed or external services remain intact.
LAST_SERVICE_STARTED=false

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
Usage: ${SCRIPT_PATH} {start|stop|restart|status|logs|help} [service]

Commands:
  start             Start infrastructure, API, and BFF for Bruno
  stop              Stop API, BFF, and local infrastructure
  restart           Stop and start all services
  status            Show process and health status
  logs <service>    Follow logs for api, bff, mysql, redis, otel, loki, or grafana
  help              Show this help

Bruno endpoints:
  API: http://localhost:8081
  BFF: http://localhost:8080

API/BFF output remains visible during startup and is also saved under .dev-logs/.
EOF
}

ensure_state_dirs() {
    mkdir -p "$STATE_DIR" "$LOG_DIR"
}

require_command() {
    local command_name=$1
    local install_hint=$2

    if ! command -v "$command_name" >/dev/null 2>&1; then
        log_error "Required command '${command_name}' was not found. ${install_hint}"
        return 1
    fi
}

check_java_21() {
    local version_output
    version_output=$(java -version 2>&1) || {
        log_error "Java is installed but 'java -version' failed."
        return 1
    }

    if ! grep -Eq 'version "21([._][0-9]+)*([+-][^"]*)?"|openjdk 21([._][0-9]+)*' <<< "$version_output"; then
        log_error "JDK 21 is required. Current version: $(printf '%s\n' "$version_output" | sed -n '1p')"
        return 1
    fi
}

validate_environment() {
    if [[ ! -f "$ENV_FILE" ]]; then
        log_error "Missing ${ENV_FILE}. Copy .env.example to .env and set local credentials."
        return 1
    fi

    if [[ ! -r "$ENV_FILE" ]]; then
        log_error "Cannot read ${ENV_FILE}. Check its permissions."
        return 1
    fi

    local required_key
    for required_key in MYSQL_ROOT_PASSWORD MYSQL_APP_PASSWORD; do
        if ! grep -Eq "^[[:space:]]*${required_key}=.+" "$ENV_FILE"; then
            log_error "${required_key} must have a non-empty value in ${ENV_FILE}."
            return 1
        fi
    done
}

preflight_start() {
    local failed=0

    require_command docker "Install Docker Desktop and start it." || failed=1
    require_command curl "Install curl and make it available on PATH." || failed=1
    require_command java "Install a JDK 21 distribution." || failed=1
    require_command ps "Install the standard process utilities for your OS." || failed=1
    require_command tee "Install the standard core utilities for your OS." || failed=1

    if [[ ! -x "$GRADLEW" ]]; then
        log_error "Gradle wrapper is missing or not executable: ${GRADLEW}"
        failed=1
    fi
    if [[ ! -f "$INFRA_COMPOSE_FILE" ]]; then
        log_error "Infrastructure Compose file not found: ${INFRA_COMPOSE_FILE}"
        failed=1
    fi
    validate_environment || failed=1

    if [[ $failed -ne 0 ]]; then
        return 1
    fi

    check_java_21

    if ! docker compose version >/dev/null 2>&1; then
        log_error "Docker Compose v2 is required ('docker compose'). Update Docker Desktop."
        return 1
    fi
    if ! docker info >/dev/null 2>&1; then
        log_error "Docker daemon is unavailable. Start Docker Desktop and retry."
        return 1
    fi
    if ! docker compose --env-file "$ENV_FILE" -f "$INFRA_COMPOSE_FILE" config --quiet; then
        log_error "Docker Compose configuration is invalid. Check ${ENV_FILE} and ${INFRA_COMPOSE_FILE}."
        return 1
    fi
}

compose_infra() {
    docker compose --env-file "$ENV_FILE" -f "$INFRA_COMPOSE_FILE" "$@"
}

health_is_up() {
    local url=$1
    local response

    response=$(curl --fail --silent --show-error --max-time 2 "$url" 2>/dev/null) || return 1
    grep -Eq '"status"[[:space:]]*:[[:space:]]*"UP"' <<< "$response"
}

container_health() {
    local container=$1
    docker inspect --format '{{if .State.Running}}{{if .State.Health}}{{.State.Health.Status}}{{else}}running{{end}}{{else}}stopped{{end}}' "$container" 2>/dev/null
}

wait_for_container() {
    local container=$1
    local display_name=$2
    local max_attempts=$3
    local attempt=0
    local current_status="missing"
    local log_service

    log_service=$(printf '%s' "$display_name" | tr '[:upper:]' '[:lower:]')

    log_step "Waiting for ${display_name}..."
    while [[ $attempt -lt $max_attempts ]]; do
        current_status=$(container_health "$container" || printf 'missing')
        if [[ "$current_status" == "healthy" || "$current_status" == "running" ]]; then
            log_success "${display_name} is ready"
            return 0
        fi
        if [[ "$current_status" == "unhealthy" || "$current_status" == "stopped" ]]; then
            log_error "${display_name} is ${current_status}. Run: ${SCRIPT_PATH} logs ${log_service}"
            return 1
        fi
        attempt=$((attempt + 1))
        sleep 2
    done

    log_error "${display_name} did not become healthy within $((max_attempts * 2)) seconds."
    log_error "Inspect it with: docker logs ${container}"
    return 1
}

read_process_state() {
    local pid_file=$1
    local pid=""
    local pgid=""
    local extra=""

    [[ -s "$pid_file" ]] || return 1
    read -r pid pgid extra < "$pid_file" || return 1
    [[ -z "$extra" && "$pid" =~ ^[0-9]+$ && "$pgid" =~ ^[0-9]+$ ]] || return 1
    printf '%s %s\n' "$pid" "$pgid"
}

process_group_has_marker() {
    local pgid=$1
    local marker=$2
    local snapshot
    local candidate_pgid
    local command_line

    snapshot=$(ps ax -o pgid= -o command= 2>/dev/null) || return 1
    while read -r candidate_pgid command_line; do
        if [[ "$candidate_pgid" == "$pgid" && "$command_line" == *"$marker"* ]]; then
            return 0
        fi
    done <<< "$snapshot"
    return 1
}

owned_process_is_running() {
    local pid_file=$1
    local marker=$2
    local state
    local pid
    local pgid

    state=$(read_process_state "$pid_file") || return 1
    read -r pid pgid <<< "$state"
    kill -0 "$pid" 2>/dev/null || return 1
    kill -0 -- "-${pgid}" 2>/dev/null || return 1
    process_group_has_marker "$pgid" "$marker"
}

clear_process_state() {
    local pid_file=$1
    if [[ -e "$pid_file" ]]; then
        : > "$pid_file"
    fi
    return 0
}

launch_service() {
    local display_name=$1
    local task=$2
    local health_url=$3
    local pid_file=$4
    local log_file=$5
    local max_attempts=$6
    local job_pid
    local pgid

    LAST_SERVICE_STARTED=false

    if owned_process_is_running "$pid_file" "$task"; then
        log_warning "${display_name} is already managed by this script. Waiting for health."
        wait_for_http_service "$display_name" "$task" "$health_url" "$pid_file" "$log_file" "$max_attempts"
        return
    fi

    if [[ -e "$pid_file" ]]; then
        clear_process_state "$pid_file"
    fi

    if health_is_up "$health_url"; then
        log_warning "${display_name} is healthy but is not managed by this script; leaving it unchanged."
        return 0
    fi

    log_step "Starting ${display_name} (${task})..."
    : > "$log_file"

    # Job control gives the supervisor a dedicated process group. The inner
    # pipeline deliberately runs with job control disabled so Gradle and tee
    # remain in that group. We persist both PID and PGID and stop the whole
    # group, rather than accidentally treating tee's PID as the application.
    set -m
    (
        set +m
        set -o pipefail
        cd "$PROJECT_ROOT"
        "$GRADLEW" --no-daemon "$task" 2>&1 | tee -a "$log_file"
    ) &
    job_pid=$!
    set +m
    # Keep the process group alive after this non-interactive shell exits and
    # avoid asynchronous job-control notifications in later rollback output.
    disown "$job_pid" 2>/dev/null || true

    # With monitor mode enabled for the launch, Bash makes the background job
    # leader the process-group leader. Avoid platform-specific ps flags here.
    pgid=$job_pid
    printf '%s %s\n' "$job_pid" "$pgid" > "$pid_file"
    LAST_SERVICE_STARTED=true

    wait_for_http_service "$display_name" "$task" "$health_url" "$pid_file" "$log_file" "$max_attempts"
}

wait_for_http_service() {
    local display_name=$1
    local task=$2
    local health_url=$3
    local pid_file=$4
    local log_file=$5
    local max_attempts=$6
    local attempt=0
    local log_service

    log_service=$(printf '%s' "$display_name" | tr '[:upper:]' '[:lower:]')

    log_info "Waiting for ${display_name}: ${health_url}"
    while [[ $attempt -lt $max_attempts ]]; do
        if health_is_up "$health_url"; then
            log_success "${display_name} is healthy"
            return 0
        fi
        if ! owned_process_is_running "$pid_file" "$task"; then
            clear_process_state "$pid_file"
            log_error "${display_name} exited before becoming healthy. Last log lines:"
            tail -n 30 "$log_file" >&2 || true
            log_error "Full log: ${log_file}"
            return 1
        fi
        attempt=$((attempt + 1))
        if (( attempt % 5 == 0 )); then
            printf '.'
        fi
        sleep 1
    done
    printf '\n'
    log_error "${display_name} did not become healthy within ${max_attempts} seconds."
    log_error "The process is still running. Inspect it with: ${SCRIPT_PATH} logs ${log_service}"
    return 1
}

stop_service() {
    local display_name=$1
    local task=$2
    local pid_file=$3
    local state
    local pid
    local pgid
    local attempt=0

    state=$(read_process_state "$pid_file") || {
        clear_process_state "$pid_file"
        log_info "${display_name} is not managed by this script."
        return 0
    }
    read -r pid pgid <<< "$state"

    if ! owned_process_is_running "$pid_file" "$task"; then
        log_warning "Ignoring stale ${display_name} state; it does not match the expected Gradle task."
        clear_process_state "$pid_file"
        return 0
    fi

    log_step "Stopping ${display_name} process group ${pgid}..."
    kill -TERM -- "-${pgid}" 2>/dev/null || true
    while kill -0 -- "-${pgid}" 2>/dev/null && [[ $attempt -lt 10 ]]; do
        attempt=$((attempt + 1))
        sleep 1
    done
    if kill -0 -- "-${pgid}" 2>/dev/null; then
        log_warning "${display_name} did not stop gracefully; sending SIGKILL."
        kill -KILL -- "-${pgid}" 2>/dev/null || true
    fi
    # Reap the supervisor during same-invocation rollback. In a later stop
    # invocation it is not our child, so wait fails harmlessly.
    wait "$pid" 2>/dev/null || true
    clear_process_state "$pid_file"
    log_success "${display_name} stopped"
}

start_infrastructure() {
    log_step "Starting MySQL, Redis, and observability infrastructure..."
    # Bruno requires MySQL and Redis. Compose may still start the optional
    # observability services, but a failure in those services is not decisive:
    # the required container health checks below remain the source of truth.
    if ! compose_infra up -d --remove-orphans; then
        log_warning "Docker Compose reported a startup error; verifying required MySQL and Redis independently."
    fi
    wait_for_container menta-mysql MySQL 45 || return 1
    wait_for_container menta-redis Redis 20 || return 1
}

infrastructure_is_running() {
    local container
    local running
    local containers=(
        menta-mysql
        menta-redis
        menta-otel-collector
        menta-loki
        menta-grafana
    )

    for container in "${containers[@]}"; do
        running=$(docker inspect --format '{{.State.Running}}' "$container" 2>/dev/null || printf 'false')
        if [[ "$running" == "true" ]]; then
            return 0
        fi
    done
    return 1
}

rollback_start() {
    local bff_started=$1
    local api_started=$2
    local infrastructure_started=$3

    log_warning "Startup failed; rolling back services started by this invocation."
    if [[ "$bff_started" == true ]]; then
        stop_service "BFF" "$BFF_TASK" "$BFF_PID_FILE" || log_warning "Could not fully roll back BFF."
    fi
    if [[ "$api_started" == true ]]; then
        stop_service "API" "$API_TASK" "$API_PID_FILE" || log_warning "Could not fully roll back API."
    fi
    if [[ "$infrastructure_started" == true ]]; then
        stop_infrastructure || log_warning "Could not fully roll back infrastructure."
    fi
}

handle_start_signal() {
    local signal_name=$1
    local exit_code=143

    trap - INT TERM
    if [[ "$signal_name" == "INT" ]]; then
        exit_code=130
    fi

    # launch_service records ownership before waiting for health. Fold that
    # state into the active transaction when a signal interrupts the wait.
    case "$start_phase" in
        api)
            if [[ "$LAST_SERVICE_STARTED" == true ]]; then
                api_started=true
            fi
            ;;
        bff)
            if [[ "$LAST_SERVICE_STARTED" == true ]]; then
                bff_started=true
            fi
            ;;
    esac

    log_warning "Received ${signal_name}; cancelling startup."
    rollback_start "$bff_started" "$api_started" "$infrastructure_started"
    exit "$exit_code"
}

stop_infrastructure() {
    if ! command -v docker >/dev/null 2>&1; then
        log_warning "Docker is unavailable; infrastructure was not stopped."
        return 0
    fi
    if ! docker info >/dev/null 2>&1; then
        log_warning "Docker daemon is unavailable; infrastructure was not stopped."
        return 0
    fi
    if [[ ! -f "$ENV_FILE" ]]; then
        log_warning "${ENV_FILE} is missing; infrastructure was not stopped."
        return 0
    fi

    log_step "Stopping local infrastructure..."
    compose_infra down
    log_success "Infrastructure stopped"
}

print_container_status() {
    local container=$1
    local display_name=$2
    local current_status

    current_status=$(container_health "$container" || printf 'unavailable')
    case "$current_status" in
        healthy|running)
            printf '  %-18s %bUP%b (%s)\n' "$display_name" "$GREEN" "$NC" "$current_status"
            ;;
        starting)
            printf '  %-18s %bSTARTING%b\n' "$display_name" "$YELLOW" "$NC"
            ;;
        *)
            printf '  %-18s %bDOWN%b (%s)\n' "$display_name" "$RED" "$NC" "$current_status"
            ;;
    esac
}

print_application_status() {
    local display_name=$1
    local task=$2
    local health_url=$3
    local pid_file=$4
    local state=""
    local pid=""
    local pgid=""

    state=$(read_process_state "$pid_file" || true)
    if [[ -n "$state" ]]; then
        read -r pid pgid <<< "$state"
    fi

    if owned_process_is_running "$pid_file" "$task"; then
        if health_is_up "$health_url"; then
            printf '  %-18s %bUP%b (PID %s, PGID %s)\n' "$display_name" "$GREEN" "$NC" "$pid" "$pgid"
        else
            printf '  %-18s %bSTARTING/UNHEALTHY%b (PID %s, PGID %s)\n' "$display_name" "$YELLOW" "$NC" "$pid" "$pgid"
        fi
    elif health_is_up "$health_url"; then
        printf '  %-18s %bUP%b (externally managed)\n' "$display_name" "$GREEN" "$NC"
    else
        [[ -e "$pid_file" ]] && clear_process_state "$pid_file"
        printf '  %-18s %bDOWN%b\n' "$display_name" "$RED" "$NC"
    fi
}

cmd_start() {
    local infrastructure_preexisting=false
    local infrastructure_started=false
    local api_started=false
    local bff_started=false
    local start_phase="preflight"

    ensure_state_dirs
    preflight_start

    if infrastructure_is_running; then
        infrastructure_preexisting=true
    fi
    if [[ "$infrastructure_preexisting" == false ]]; then
        # Set ownership before Compose runs because an interrupted or failed
        # Compose invocation may have created only a subset. If anything was
        # already running, preserve the entire partial state conservatively.
        infrastructure_started=true
    fi

    trap 'handle_start_signal INT' INT
    trap 'handle_start_signal TERM' TERM
    start_phase="infrastructure"
    if ! start_infrastructure; then
        trap - INT TERM
        rollback_start false false "$infrastructure_started"
        return 1
    fi

    start_phase="api"
    if ! launch_service "API" "$API_TASK" "$API_HEALTH_URL" "$API_PID_FILE" "$API_LOG" 120; then
        api_started=$LAST_SERVICE_STARTED
        trap - INT TERM
        rollback_start false "$api_started" "$infrastructure_started"
        return 1
    fi
    api_started=$LAST_SERVICE_STARTED

    start_phase="bff"
    if ! launch_service "BFF" "$BFF_TASK" "$BFF_HEALTH_URL" "$BFF_PID_FILE" "$BFF_LOG" 120; then
        bff_started=$LAST_SERVICE_STARTED
        trap - INT TERM
        rollback_start "$bff_started" "$api_started" "$infrastructure_started"
        return 1
    fi

    trap - INT TERM
    start_phase="complete"

    printf '\n'
    log_success "Services are ready for Bruno"
    printf '  API:     http://localhost:8081\n'
    printf '  BFF:     http://localhost:8080\n'
    printf '  Grafana: http://localhost:3000\n'
    printf '\nFollow logs with: %s logs {api|bff}\n' "$SCRIPT_PATH"
}

cmd_stop() {
    ensure_state_dirs
    stop_service "BFF" "$BFF_TASK" "$BFF_PID_FILE"
    stop_service "API" "$API_TASK" "$API_PID_FILE"
    stop_infrastructure
}

cmd_status() {
    ensure_state_dirs
    printf '\n%bLocal Bruno service status%b\n\n' "$CYAN" "$NC"
    if command -v docker >/dev/null 2>&1 && docker info >/dev/null 2>&1; then
        print_container_status menta-mysql MySQL
        print_container_status menta-redis Redis
        print_container_status menta-otel-collector "OTEL Collector"
        print_container_status menta-loki Loki
        print_container_status menta-grafana Grafana
    else
        printf '  %-18s %bUNAVAILABLE%b (Docker daemon)\n' "Infrastructure" "$YELLOW" "$NC"
    fi
    print_application_status API "$API_TASK" "$API_HEALTH_URL" "$API_PID_FILE"
    print_application_status BFF "$BFF_TASK" "$BFF_HEALTH_URL" "$BFF_PID_FILE"
    printf '\n'
}

cmd_logs() {
    local service=${1:-}
    ensure_state_dirs

    case "$service" in
        api)
            [[ -f "$API_LOG" ]] || { log_error "No API log exists yet. Run '${SCRIPT_PATH} start' first."; return 1; }
            tail -n 100 -f "$API_LOG"
            ;;
        bff)
            [[ -f "$BFF_LOG" ]] || { log_error "No BFF log exists yet. Run '${SCRIPT_PATH} start' first."; return 1; }
            tail -n 100 -f "$BFF_LOG"
            ;;
        mysql|redis|otel|loki|grafana)
            require_command docker "Install Docker Desktop and start it."
            local container="menta-${service}"
            [[ "$service" == "otel" ]] && container="menta-otel-collector"
            docker logs --tail 100 -f "$container"
            ;;
        *)
            log_error "Unknown log service '${service:-<missing>}'."
            printf 'Usage: %s logs {api|bff|mysql|redis|otel|loki|grafana}\n' "$SCRIPT_PATH" >&2
            return 1
            ;;
    esac
}

main() {
    local command=${1:-}
    case "$command" in
        start)
            [[ $# -eq 1 ]] || { usage >&2; return 1; }
            cmd_start
            ;;
        stop)
            [[ $# -eq 1 ]] || { usage >&2; return 1; }
            cmd_stop
            ;;
        restart)
            [[ $# -eq 1 ]] || { usage >&2; return 1; }
            cmd_stop
            sleep 2
            cmd_start
            ;;
        status)
            [[ $# -eq 1 ]] || { usage >&2; return 1; }
            cmd_status
            ;;
        logs)
            [[ $# -eq 2 ]] || { usage >&2; return 1; }
            cmd_logs "$2"
            ;;
        help|-h|--help)
            usage
            ;;
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

main "$@"
