#!/usr/bin/env bash

# Local development lifecycle for Bruno testing.
# Starts Docker infrastructure plus the API and BFF. Each service runs from a
# copy of its boot jar under .dev-run/, never from build/, so a later Gradle
# recompile (clean, --rerun-tasks, a branch switch) cannot break a running JVM.
# After changing code, run `restart` to rebuild and relaunch the jars.

set -euo pipefail

readonly SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
readonly SCRIPT_PATH="${SCRIPT_DIR}/$(basename "${BASH_SOURCE[0]}")"
readonly ENV_FILE="${DEV_ENV_FILE:-${PROJECT_ROOT}/.env}"
readonly INFRA_COMPOSE_FILE="${PROJECT_ROOT}/infra/docker/database/docker-compose.yml"
readonly INFRA_COMPOSE_PROJECT="menta-dance"
readonly GRADLEW="${DEV_GRADLEW:-${PROJECT_ROOT}/gradlew}"
readonly SDKMAN_ENV_FILE="${PROJECT_ROOT}/.sdkmanrc"
readonly SDKMAN_INIT_SCRIPT="${SDKMAN_DIR:-${HOME}/.sdkman}/bin/sdkman-init.sh"

readonly STATE_DIR="${PROJECT_ROOT}/.dev-pids"
readonly RUN_DIR="${DEV_RUN_DIR:-${PROJECT_ROOT}/.dev-run}"
readonly LOG_DIR="${PROJECT_ROOT}/.dev-logs"
readonly API_PID_FILE="${STATE_DIR}/api.pid"
readonly BFF_PID_FILE="${STATE_DIR}/bff.pid"
readonly API_LOG="${LOG_DIR}/api.log"
readonly BFF_LOG="${LOG_DIR}/bff.log"

# Boot jars: built by Gradle, then copied to .dev-run/ and run from there. The
# jar path under .dev-run/ is also the process-ownership marker: it appears in
# the java command line of the launched service.
readonly API_BUILD_TASK=":api:app:bootJar"
readonly BFF_BUILD_TASK=":bff:bootJar"
readonly API_BUILT_JAR="${PROJECT_ROOT}/api/app/build/libs/menta-dance-api.jar"
readonly BFF_BUILT_JAR="${PROJECT_ROOT}/bff/build/libs/menta-dance-bff.jar"
readonly API_RUN_JAR="${RUN_DIR}/menta-dance-api.jar"
readonly BFF_RUN_JAR="${RUN_DIR}/menta-dance-bff.jar"
# Gradle's bootRun ran each service from its module directory; keep that.
readonly API_WORKDIR="${PROJECT_ROOT}/api/app"
readonly BFF_WORKDIR="${PROJECT_ROOT}/bff"
readonly OTEL_AGENT="${DEV_OTEL_AGENT:-${PROJECT_ROOT}/observability/opentelemetry-javaagent.jar}"
readonly API_HEALTH_URL="http://localhost:8081/actuator/health"
readonly BFF_HEALTH_URL="http://localhost:8080/actuator/health"
# Seconds to wait for each service to report healthy, counted from the java
# launch: the jar build runs (and is reported) before that wait starts. A cold CI
# runner boots slowly, so the workflow raises it; local runs keep the default.
HEALTH_TIMEOUT_SECONDS="${DEV_HEALTH_TIMEOUT:-120}"
[[ "$HEALTH_TIMEOUT_SECONDS" =~ ^[1-9][0-9]*$ ]] || HEALTH_TIMEOUT_SECONDS=120
readonly HEALTH_TIMEOUT_SECONDS

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
  start             Build the boot jars, then start infrastructure, API, and BFF for Bruno
  stop              Stop API, BFF, and local infrastructure
  restart           Stop and start all services (run it after changing code)
  status            Show process and health status
  logs <service>    Follow logs for api, bff, mysql, redis, otel, loki, or grafana
  help              Show this help

Bruno endpoints:
  API: http://localhost:8081
  BFF: http://localhost:8080

API and BFF run from copies of their boot jars in .dev-run/, so recompiling with
Gradle does not affect running services. To pick up code changes run: restart.
API/BFF output (including the jar build) is saved under .dev-logs/.
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

activate_sdkman_java() {
    [[ -f "$SDKMAN_ENV_FILE" && -f "$SDKMAN_INIT_SCRIPT" ]] || return 0

    # SDKMAN references unset shell-version variables during initialization.
    set +u
    local sdkman_init_status=0
    source "$SDKMAN_INIT_SCRIPT" || sdkman_init_status=$?
    set -u
    if [[ $sdkman_init_status -ne 0 ]]; then
        log_error "Unable to initialize SDKMAN from ${SDKMAN_INIT_SCRIPT}."
        return "$sdkman_init_status"
    fi

    local java_candidate
    java_candidate=$(sed -nE 's/^java=(.+)$/\1/p' "$SDKMAN_ENV_FILE" | head -n 1)
    [[ -n "$java_candidate" ]] || return 0

    set +u
    local sdk_use_status=0
    sdk use java "$java_candidate" >/dev/null || sdk_use_status=$?
    set -u
    if [[ $sdk_use_status -ne 0 ]]; then
        log_error "SDKMAN JDK '${java_candidate}' is required. Install it with: sdk install java ${java_candidate}"
        return "$sdk_use_status"
    fi

    log_info "Using SDKMAN JDK ${java_candidate}"
}

check_java_21() {
    local java_bin
    local version_output

    if [[ -n "${JAVA_HOME:-}" && -x "${JAVA_HOME}/bin/java" ]]; then
        java_bin="${JAVA_HOME}/bin/java"
        export PATH="${JAVA_HOME}/bin:${PATH}"
    else
        java_bin=$(command -v java) || {
            log_error "Java is not available on PATH. Install a JDK 21 distribution."
            return 1
        }
    fi

    version_output=$("$java_bin" -version 2>&1) || {
        log_error "Java is installed but '${java_bin} -version' failed."
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

    # The API refuses to start without this secret unless the demo profile
    # (which ships its own demo-only value) is active.
    if ! demo_profile_active && [[ -z "$(env_setting BILLING_BANK_TRANSFER_PROOF_TOKEN_SECRET)" ]]; then
        log_error "BILLING_BANK_TRANSFER_PROOF_TOKEN_SECRET is required and has no default; the API would fail to start."
        log_error "Fix it in one of two ways:"
        log_error "  1. Set it in ${ENV_FILE} to any long random local value (see .env.example)."
        log_error "  2. Start with the demo profile, which seeds demo data and brings its own value:"
        log_error "     SPRING_PROFILES_ACTIVE=demo ${SCRIPT_PATH} start"
        return 1
    fi
}

# Effective value of a setting: the shell environment wins, then the last
# assignment in the .env file. Prints nothing when it is unset or empty.
env_setting() {
    local key=$1
    local value=${!key:-}

    if [[ -z "$value" && -r "$ENV_FILE" ]]; then
        value=$(sed -n "s/^[[:space:]]*${key}=//p" "$ENV_FILE" | tail -n 1)
    fi
    printf '%s' "$value"
}

demo_profile_active() {
    local profiles
    profiles=$(env_setting SPRING_PROFILES_ACTIVE)
    case ",${profiles// /}," in
        *,demo,*) return 0 ;;
    esac
    return 1
}

# True when a service log already shows a fatal startup failure, so waiting for
# the health endpoint any longer is pointless.
startup_failure_in_log() {
    local log_file=$1

    [[ -r "$log_file" ]] || return 1
    grep -Eq 'BUILD FAILED|APPLICATION FAILED TO START|Application run failed' "$log_file"
}

report_startup_failure() {
    local display_name=$1
    local log_file=$2
    local root_cause

    log_error "${display_name} failed during startup."
    root_cause=$(grep 'Caused by:' "$log_file" | tail -n 1 || true)
    if [[ -n "$root_cause" ]]; then
        log_error "Root cause: ${root_cause}"
    fi
    log_error "Last log lines:"
    tail -n 15 "$log_file" >&2 || true
    log_error "Full log: ${log_file}"
}

preflight_start() {
    local failed=0

    require_command docker "Install Docker Desktop and start it." || failed=1
    require_command curl "Install curl and make it available on PATH." || failed=1
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

    activate_sdkman_java
    require_command java "Install a JDK 21 distribution." || return 1
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
    docker compose \
        --project-name "$INFRA_COMPOSE_PROJECT" \
        --env-file "$ENV_FILE" \
        -f "$INFRA_COMPOSE_FILE" \
        "$@"
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

# Trims leading and trailing whitespace into TRIMMED. Parameter expansion only,
# so callers pay no subshell per call.
trim_whitespace() {
    TRIMMED=$1
    TRIMMED=${TRIMMED#"${TRIMMED%%[![:space:]]*}"}
    TRIMMED=${TRIMMED%"${TRIMMED##*[![:space:]]}"}
}

# Exports the pairs of a .env file into the current shell, parsed like the
# bootRun blocks of the Gradle build did: blank lines and lines starting with '#'
# are skipped, the split is on the first '=', and key and value are trimmed. The
# file is never sourced or evaluated, so '$', backticks and quotes stay literal.
# Two deliberate differences: a variable already set (non-empty) in the shell is
# kept, matching env_setting, and keys that are not valid variable names are
# skipped. Call it only inside the subshell that launches the service, and never
# print the values.
export_env_file() {
    local env_file=$1
    local line
    local key
    local value

    [[ -r "$env_file" ]] || return 0
    while IFS= read -r line || [[ -n "$line" ]]; do
        line=${line%$'\r'}
        if [[ -z "${line//[[:space:]]/}" || "$line" == \#* || "$line" != *=* ]]; then
            continue
        fi
        trim_whitespace "${line%%=*}"
        key=$TRIMMED
        trim_whitespace "${line#*=}"
        value=$TRIMMED
        if [[ ! "$key" =~ ^[A-Za-z_][A-Za-z0-9_]*$ || -n "${!key:-}" ]]; then
            continue
        fi
        export "${key}=${value}"
    done < "$env_file"
    return 0
}

# True while the process is alive and not a zombie (a crashed child of this
# script stays a zombie until reaped, and kill -0 still succeeds on it).
process_is_alive() {
    local pid=$1
    local state

    kill -0 "$pid" 2>/dev/null || return 1
    state=$(ps -o stat= -p "$pid" 2>/dev/null) || return 1
    [[ -n "$state" && "$state" != Z* ]]
}

# Between the fork and the exec of java the launched process still carries the
# parent's command line, without the jar path marker. Wait until the process
# group shows the marker so the health wait never mistakes that window for an
# early exit. Returns early if the process dies; gives up after ~10s and lets the
# health wait report whatever state it finds.
wait_for_process_marker() {
    local pid=$1
    local pgid=$2
    local marker=$3
    local attempt=0

    while [[ $attempt -lt 100 ]]; do
        if process_group_has_marker "$pgid" "$marker"; then
            return 0
        fi
        process_is_alive "$pid" || return 1
        attempt=$((attempt + 1))
        sleep 0.1
    done
    return 1
}

# Fills JAVA_COMMAND with the command that runs a service from its jar. The API
# also gets the OpenTelemetry agent flags bootRun used, when the agent file exists.
build_java_command() {
    local display_name=$1
    local run_jar=$2

    JAVA_COMMAND=(java)
    if [[ "$display_name" == "API" && -f "$OTEL_AGENT" ]]; then
        JAVA_COMMAND+=(
            "-javaagent:${OTEL_AGENT}"
            "-Dotel.service.name=menta-dance-api"
            "-Dotel.exporter.otlp.endpoint=http://localhost:4318"
            "-Dotel.exporter.otlp.protocol=http/protobuf"
            "-Dotel.logs.exporter=otlp"
            "-Dotel.metrics.exporter=none"
            "-Dotel.traces.exporter=none"
        )
    fi
    JAVA_COMMAND+=(-jar "$run_jar")
}

# Copies the built jar to its run location through a temp file and a rename, so
# a JVM still holding the previous jar open keeps a consistent file.
stage_jar() {
    local built_jar=$1
    local run_jar=$2
    local temp_jar="${run_jar}.tmp.$$"

    if [[ ! -f "$built_jar" ]]; then
        log_error "Built jar not found: ${built_jar}"
        return 1
    fi
    if ! mkdir -p "$(dirname "$run_jar")" || ! cp "$built_jar" "$temp_jar" || ! mv -f "$temp_jar" "$run_jar"; then
        rm -f "$temp_jar"
        log_error "Unable to copy ${built_jar} to ${run_jar}."
        return 1
    fi
}

# Builds the boot jar. Its output goes to the service log, so a failed build is
# reported as that service's startup failure.
build_service_jar() {
    local display_name=$1
    local build_task=$2
    local built_jar=$3
    local log_file=$4

    log_step "Building ${display_name} jar (gradlew --no-daemon ${build_task}); this can take a while..."
    if ! (cd "$PROJECT_ROOT" && "$GRADLEW" --no-daemon "$build_task" >> "$log_file" 2>&1); then
        report_startup_failure "$display_name" "$log_file"
        return 1
    fi
    if [[ ! -f "$built_jar" ]]; then
        log_error "${display_name} build succeeded but ${built_jar} was not produced. Full log: ${log_file}"
        return 1
    fi
}

launch_service() {
    local display_name=$1
    local build_task=$2
    local built_jar=$3
    local run_jar=$4
    local workdir=$5
    local health_url=$6
    local pid_file=$7
    local log_file=$8
    local max_attempts=$9
    local job_pid
    local pgid

    LAST_SERVICE_STARTED=false

    if owned_process_is_running "$pid_file" "$run_jar"; then
        log_warning "${display_name} is already managed by this script. Waiting for health."
        wait_for_http_service "$display_name" "$run_jar" "$health_url" "$pid_file" "$log_file" "$max_attempts"
        return
    fi

    if [[ -e "$pid_file" ]]; then
        clear_process_state "$pid_file"
    fi

    if health_is_up "$health_url"; then
        log_warning "${display_name} is healthy but is not managed by this script; leaving it unchanged."
        return 0
    fi

    : > "$log_file"
    # Build before launching: a build failure is a startup failure with nothing
    # to roll back, and the health wait below only has to cover the JVM boot.
    build_service_jar "$display_name" "$build_task" "$built_jar" "$log_file" || return 1
    stage_jar "$built_jar" "$run_jar" || return 1
    build_java_command "$display_name" "$run_jar"

    log_step "Starting ${display_name} (${run_jar})..."
    # Job control gives the JVM a dedicated process group. Keep its output in
    # the service log so the startup terminal remains concise; failures still
    # print the final log lines below. The .env pairs are exported inside this
    # subshell only, and exec makes the JVM itself the group leader.
    set -m
    (
        set +m
        cd "$workdir" || exit 1
        export_env_file "$ENV_FILE"
        exec "${JAVA_COMMAND[@]}" >> "$log_file" 2>&1
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

    wait_for_process_marker "$job_pid" "$pgid" "$run_jar" || true
    wait_for_http_service "$display_name" "$run_jar" "$health_url" "$pid_file" "$log_file" "$max_attempts"
}

wait_for_http_service() {
    local display_name=$1
    local marker=$2
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
        if startup_failure_in_log "$log_file"; then
            printf '\n'
            report_startup_failure "$display_name" "$log_file"
            return 1
        fi
        if ! owned_process_is_running "$pid_file" "$marker"; then
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
    local marker=$2
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

    if ! owned_process_is_running "$pid_file" "$marker"; then
        log_warning "Ignoring stale ${display_name} state; it does not match the expected jar."
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
    # Keep the Compose project stable even though its file lives below infra/.
    # Otherwise Compose derives the project name as `database`, which conflicts
    # with the fixed menta-* container names used by local development.
    if ! compose_infra up -d --remove-orphans; then
        log_error "Infrastructure startup failed. Resolve the Docker Compose error before starting API or BFF."
        return 1
    fi
    wait_for_container menta-mysql MySQL 45 || return 1
    wait_for_container menta-redis Redis 20 || return 1
    wait_for_container menta-mailpit Mailpit 20 || return 1
    wait_for_container menta-otel-collector "OTEL Collector" 20 || return 1
    wait_for_container menta-loki Loki 20 || return 1
    wait_for_container menta-grafana Grafana 20 || return 1
}

infrastructure_is_running() {
    local container
    local running
    local containers=(
        menta-mysql
        menta-redis
        menta-mailpit
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
        stop_service "BFF" "$BFF_RUN_JAR" "$BFF_PID_FILE" || log_warning "Could not fully roll back BFF."
    fi
    if [[ "$api_started" == true ]]; then
        stop_service "API" "$API_RUN_JAR" "$API_PID_FILE" || log_warning "Could not fully roll back API."
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
    local marker=$2
    local health_url=$3
    local pid_file=$4
    local state=""
    local pid=""
    local pgid=""

    state=$(read_process_state "$pid_file" || true)
    if [[ -n "$state" ]]; then
        read -r pid pgid <<< "$state"
    fi

    if owned_process_is_running "$pid_file" "$marker"; then
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
    if ! launch_service "API" "$API_BUILD_TASK" "$API_BUILT_JAR" "$API_RUN_JAR" "$API_WORKDIR" "$API_HEALTH_URL" "$API_PID_FILE" "$API_LOG" "$HEALTH_TIMEOUT_SECONDS"; then
        api_started=$LAST_SERVICE_STARTED
        trap - INT TERM
        rollback_start false "$api_started" "$infrastructure_started"
        return 1
    fi
    api_started=$LAST_SERVICE_STARTED

    start_phase="bff"
    if ! launch_service "BFF" "$BFF_BUILD_TASK" "$BFF_BUILT_JAR" "$BFF_RUN_JAR" "$BFF_WORKDIR" "$BFF_HEALTH_URL" "$BFF_PID_FILE" "$BFF_LOG" "$HEALTH_TIMEOUT_SECONDS"; then
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
    stop_service "BFF" "$BFF_RUN_JAR" "$BFF_PID_FILE"
    stop_service "API" "$API_RUN_JAR" "$API_PID_FILE"
    stop_infrastructure
}

cmd_status() {
    ensure_state_dirs
    printf '\n%bLocal Bruno service status%b\n\n' "$CYAN" "$NC"
    if command -v docker >/dev/null 2>&1 && docker info >/dev/null 2>&1; then
        print_container_status menta-mysql MySQL
        print_container_status menta-redis Redis
        print_container_status menta-mailpit Mailpit
        print_container_status menta-otel-collector "OTEL Collector"
        print_container_status menta-loki Loki
        print_container_status menta-grafana Grafana
    else
        printf '  %-18s %bUNAVAILABLE%b (Docker daemon)\n' "Infrastructure" "$YELLOW" "$NC"
    fi
    print_application_status API "$API_RUN_JAR" "$API_HEALTH_URL" "$API_PID_FILE"
    print_application_status BFF "$BFF_RUN_JAR" "$BFF_HEALTH_URL" "$BFF_PID_FILE"
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

# Run only when executed directly so tests can source the helpers.
if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
    main "$@"
fi
