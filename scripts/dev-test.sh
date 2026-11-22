#!/usr/bin/env bash
# Tests for the helpers in scripts/dev.sh (environment preflight, startup-failure
# detection, jar staging, .env loading and process ownership). They never start
# Docker, the real Gradle build or the real API/BFF: Gradle is a stub, `java` is
# a fake that only sleeps, and health checks target a closed port.
# shellcheck disable=SC2016  # single quotes keep '$' literal on purpose (no-expansion tests)
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TMP_DIR="$(mktemp -d)"

# Kills any fake service a failed test left behind, then removes the temp dir.
cleanup() {
    local pid_file pgid
    for pid_file in "$TMP_DIR"/*.pid; do
        [[ -s "$pid_file" ]] || continue
        read -r _ pgid < "$pid_file" || true
        if [[ "${pgid:-}" =~ ^[0-9]+$ ]]; then
            kill -KILL -- "-${pgid}" 2>/dev/null || true
        fi
    done
    # Fake services are identified by the unique temp dir in their command line.
    pkill -TERM -f "${TMP_DIR}/fakebin/java" 2>/dev/null || true
    rm -rf "$TMP_DIR"
}
trap cleanup EXIT

# dev.sh only runs main() when executed directly, so sourcing it is safe.
export DEV_ENV_FILE="$TMP_DIR/.env"
export DEV_RUN_DIR="$TMP_DIR/.dev-run"
export DEV_OTEL_AGENT="$TMP_DIR/observability/opentelemetry-javaagent.jar"
export DEV_GRADLEW="$TMP_DIR/gradlew-stub"
# shellcheck source=scripts/dev.sh
source "$ROOT/scripts/dev.sh"

failures=0

pass() { printf 'ok   - %s\n' "$1"; }
fail() { printf 'FAIL - %s\n' "$1" >&2; failures=$((failures + 1)); }

# Prints a file's content, or nothing when it does not exist yet.
slurp() {
    if [[ -r "$1" ]]; then
        printf '%s' "$(<"$1")"
    fi
}

write_env() {
    printf '%s\n' "MYSQL_ROOT_PASSWORD=root-pass" "MYSQL_APP_PASSWORD=app-pass" "$@" > "$DEV_ENV_FILE"
}

# Runs validate_environment in a clean environment and captures its stderr.
run_validation() {
    VALIDATION_OUTPUT=$(
        unset BILLING_BANK_TRANSFER_PROOF_TOKEN_SECRET SPRING_PROFILES_ACTIVE
        "$@"
        validate_environment 2>&1
    ) && VALIDATION_STATUS=0 || VALIDATION_STATUS=$?
}

# 1. Missing proof-token secret outside the demo profile fails with a remedy.
write_env
run_validation true
if [[ $VALIDATION_STATUS -ne 0 \
    && "$VALIDATION_OUTPUT" == *"BILLING_BANK_TRANSFER_PROOF_TOKEN_SECRET"* \
    && "$VALIDATION_OUTPUT" == *"demo"* ]]; then
    pass "missing proof-token secret is reported with both remedies"
else
    fail "missing proof-token secret must fail and mention the variable and the demo profile"
fi

# 2. A secret in .env satisfies the check.
write_env "BILLING_BANK_TRANSFER_PROOF_TOKEN_SECRET=local-secret"
run_validation true
[[ $VALIDATION_STATUS -eq 0 ]] && pass "secret in .env is accepted" || fail "secret in .env must be accepted"

# 3. An empty value in .env does not count.
write_env "BILLING_BANK_TRANSFER_PROOF_TOKEN_SECRET="
run_validation true
[[ $VALIDATION_STATUS -ne 0 ]] && pass "empty secret in .env is rejected" || fail "empty secret in .env must be rejected"

# 4. A secret exported in the shell satisfies the check.
write_env
run_validation export BILLING_BANK_TRANSFER_PROOF_TOKEN_SECRET=shell-secret
[[ $VALIDATION_STATUS -eq 0 ]] && pass "secret in the environment is accepted" || fail "secret in the environment must be accepted"

# 5. The demo profile supplies its own secret, from the shell or from .env.
write_env
run_validation export SPRING_PROFILES_ACTIVE=demo
[[ $VALIDATION_STATUS -eq 0 ]] && pass "demo profile from the environment is accepted" || fail "demo profile from the environment must be accepted"
write_env "SPRING_PROFILES_ACTIVE=demo"
run_validation true
[[ $VALIDATION_STATUS -eq 0 ]] && pass "demo profile from .env is accepted" || fail "demo profile from .env must be accepted"

# 6. A profile list that includes demo counts; a different profile does not.
write_env
run_validation export SPRING_PROFILES_ACTIVE=local,demo
[[ $VALIDATION_STATUS -eq 0 ]] && pass "demo inside a profile list is accepted" || fail "demo inside a profile list must be accepted"
run_validation export SPRING_PROFILES_ACTIVE=demonstration
[[ $VALIDATION_STATUS -ne 0 ]] && pass "a profile that merely contains 'demo' is rejected" || fail "'demonstration' must not count as the demo profile"

# 7. The existing MySQL credential check still applies.
printf '%s\n' "BILLING_BANK_TRANSFER_PROOF_TOKEN_SECRET=x" > "$DEV_ENV_FILE"
run_validation true
if [[ $VALIDATION_STATUS -ne 0 && "$VALIDATION_OUTPUT" == *"MYSQL_ROOT_PASSWORD"* ]]; then
    pass "MySQL credentials are still required"
else
    fail "MySQL credentials must still be required"
fi

# 8. Startup-failure signatures in a service log are detected.
clean_log="$TMP_DIR/clean.log"
printf '%s\n' "Started MentaDanceApplication in 8.2 seconds" > "$clean_log"
if startup_failure_in_log "$clean_log"; then
    fail "a healthy startup log must not be flagged"
else
    pass "a healthy startup log is not flagged"
fi

for signature in "BUILD FAILED in 21s" "APPLICATION FAILED TO START" "Application run failed"; do
    failed_log="$TMP_DIR/failed.log"
    printf '%s\n' "some output" "$signature" > "$failed_log"
    if startup_failure_in_log "$failed_log"; then
        pass "'$signature' is detected"
    else
        fail "'$signature' must be detected"
    fi
done

if startup_failure_in_log "$TMP_DIR/does-not-exist.log"; then
    fail "a missing log must not be flagged"
else
    pass "a missing log is not flagged"
fi

# 9. The report surfaces the root cause, not just the tail.
cause_log="$TMP_DIR/cause.log"
{
    printf '%s\n' "Caused by: org.springframework.util.PlaceholderResolutionException: Could not resolve placeholder 'X'"
    for i in $(seq 1 50); do printf 'noise line %s\n' "$i"; done
    printf '%s\n' "BUILD FAILED in 21s"
} > "$cause_log"
report=$(report_startup_failure "API" "$cause_log" 2>&1)
if [[ "$report" == *"Could not resolve placeholder 'X'"* ]]; then
    pass "the root cause line is reported"
else
    fail "the root cause line must be reported even when it is far from the tail"
fi

# 10. The .env loader mirrors the Gradle bootRun parser: blank and '#' lines are
# skipped, the split is on the first '=', key and value are trimmed, and no shell
# expansion happens, so '$' and backticks stay literal.
dotenv="$TMP_DIR/parse.env"
printf '%s\n' \
    '# a comment' '' '   ' \
    'PLAIN=value' \
    '  SPACED_KEY  =  spaced value  ' \
    'EQ_KEY=a=b=c' \
    'LITERAL=$HOME `echo hi` $(echo x)' \
    'EMPTY_KEY=' \
    'NO_EQUALS_LINE' \
    'BAD KEY=x' \
    '#COMMENTED=yes' \
    $'CRLF_KEY=crlf\r' > "$dotenv"

# Prints the value of a variable after loading the file (or "<unset>").
dotenv_value() {
    (
        unset PLAIN SPACED_KEY EQ_KEY LITERAL EMPTY_KEY NO_EQUALS_LINE COMMENTED CRLF_KEY
        if [[ -n "${2:-}" ]]; then export "$1=$2"; fi
        export_env_file "$dotenv" 2>/dev/null
        printf '%s' "${!1-<unset>}"
    )
}

check_dotenv() {
    local description=$1 key=$2 expected=$3 preset=${4:-}
    local actual
    actual=$(dotenv_value "$key" "$preset" 2>&1 || true)
    if [[ "$actual" == "$expected" ]]; then
        pass "$description"
    else
        fail "$description (expected '$expected', got '$actual')"
    fi
}

check_dotenv ".env: a plain pair is exported" PLAIN "value"
check_dotenv ".env: key and value are trimmed" SPACED_KEY "spaced value"
check_dotenv ".env: only the first '=' splits" EQ_KEY "a=b=c"
check_dotenv ".env: \$ and backticks stay literal" LITERAL '$HOME `echo hi` $(echo x)'
check_dotenv ".env: an empty value is exported as empty" EMPTY_KEY ""
check_dotenv ".env: a line without '=' is ignored" NO_EQUALS_LINE "<unset>"
check_dotenv ".env: a commented line is ignored" COMMENTED "<unset>"
check_dotenv ".env: a CRLF line ending is stripped" CRLF_KEY "crlf"
check_dotenv ".env: a non-empty shell value wins over the file" PLAIN "from-shell" "from-shell"
if (export_env_file "$dotenv" >/dev/null 2>&1); then
    pass ".env: an invalid key name does not abort the loader"
else
    fail ".env: an invalid key name must be skipped, not fatal"
fi

# 11. The java command line: OpenTelemetry flags only for the API, and only when
# the agent file exists; everything runs as `java ... -jar <jar>`.
rm -f "$DEV_OTEL_AGENT"
if build_java_command API "/x/api.jar" 2>/dev/null \
    && [[ "${JAVA_COMMAND[*]}" == "java -jar /x/api.jar" ]]; then
    pass "java command has no agent flags when the agent file is missing"
else
    fail "java command must be 'java -jar <jar>' without the agent file (got: ${JAVA_COMMAND[*]:-<unset>})"
fi

mkdir -p "$(dirname "$DEV_OTEL_AGENT")"
: > "$DEV_OTEL_AGENT"
expected_api="java -javaagent:${DEV_OTEL_AGENT} -Dotel.service.name=menta-dance-api"
expected_api+=" -Dotel.exporter.otlp.endpoint=http://localhost:4318 -Dotel.exporter.otlp.protocol=http/protobuf"
expected_api+=" -Dotel.logs.exporter=otlp -Dotel.metrics.exporter=none -Dotel.traces.exporter=none -jar /x/api.jar"
if build_java_command API "/x/api.jar" 2>/dev/null && [[ "${JAVA_COMMAND[*]}" == "$expected_api" ]]; then
    pass "API java command carries exactly the bootRun OpenTelemetry flags"
else
    fail "API java command must carry the bootRun OpenTelemetry flags (got: ${JAVA_COMMAND[*]:-<unset>})"
fi
if build_java_command BFF "/x/bff.jar" 2>/dev/null && [[ "${JAVA_COMMAND[*]}" == "java -jar /x/bff.jar" ]]; then
    pass "BFF java command never gets the OpenTelemetry agent"
else
    fail "BFF java command must be 'java -jar <jar>' (got: ${JAVA_COMMAND[*]:-<unset>})"
fi

# 12. Jar staging copies the built jar to the run dir without touching a file a
# running JVM may hold open (write a temp file, then rename over it).
built_jar="$TMP_DIR/built/menta-dance-api.jar"
run_jar="$TMP_DIR/staged/menta-dance-api.jar"
mkdir -p "$(dirname "$built_jar")"
printf 'v1' > "$built_jar"
if stage_jar "$built_jar" "$run_jar" >/dev/null 2>&1 && [[ "$(<"$run_jar")" == "v1" ]]; then
    pass "stage_jar copies the built jar into the run dir"
else
    fail "stage_jar must create the run dir and copy the jar"
fi
inode_before=$(stat -c %i "$run_jar" 2>/dev/null || stat -f %i "$run_jar")
printf 'v2' > "$built_jar"
stage_jar "$built_jar" "$run_jar" >/dev/null 2>&1 || true
inode_after=$(stat -c %i "$run_jar" 2>/dev/null || stat -f %i "$run_jar")
if [[ "$(<"$run_jar")" == "v2" && "$inode_before" != "$inode_after" ]]; then
    pass "restaging replaces the jar through a rename, not an in-place write"
else
    fail "restaging must replace the jar by rename so a running JVM keeps its old file"
fi
if [[ "$(fd -t f . "$(dirname "$run_jar")")" == "$run_jar" ]]; then
    pass "stage_jar leaves no temp file behind"
else
    fail "stage_jar must not leave temp files in the run dir"
fi
if stage_jar "$TMP_DIR/no-such.jar" "$TMP_DIR/staged/other.jar" >/dev/null 2>&1; then
    fail "stage_jar must fail when the built jar is missing"
else
    pass "stage_jar fails when the built jar is missing"
fi

# 13. The jar build: a Gradle stub records its arguments. A failing build is a
# startup failure reported from the log; a missing jar after a green build too.
cat > "$DEV_GRADLEW" <<'STUB'
#!/usr/bin/env bash
printf '%s\n' "$@" > "$STUB_GRADLE_ARGS"
if [[ "${STUB_GRADLE_FAIL:-}" == 1 ]]; then
    echo "> Task :api:app:bootJar FAILED"
    echo "BUILD FAILED in 1s"
    exit 1
fi
echo "BUILD SUCCESSFUL in 1s"
STUB
chmod +x "$DEV_GRADLEW"
export STUB_GRADLE_ARGS="$TMP_DIR/gradle.args"
: > "$STUB_GRADLE_ARGS"
build_log="$TMP_DIR/build.log"

: > "$build_log"
if STUB_GRADLE_FAIL=1 build_service_jar "API" ":api:app:bootJar" "$built_jar" "$build_log" >"$TMP_DIR/build.out" 2>&1; then
    fail "a failing bootJar build must fail the service startup"
elif [[ "$(<"$TMP_DIR/build.out")" == *"failed during startup"* && "$(<"$build_log")" == *"BUILD FAILED"* ]]; then
    pass "a failing bootJar build is reported as a startup failure"
else
    fail "a failing bootJar build must be reported through report_startup_failure"
fi

: > "$build_log"
if build_service_jar "API" ":api:app:bootJar" "$built_jar" "$build_log" >/dev/null 2>&1 \
    && [[ "$(<"$STUB_GRADLE_ARGS")" == $'--no-daemon\n:api:app:bootJar' ]]; then
    pass "the jar build runs 'gradlew --no-daemon <task>' and logs to the service log"
else
    fail "the jar build must run 'gradlew --no-daemon <task>' (args: $(<"$STUB_GRADLE_ARGS"))"
fi

if build_service_jar "API" ":api:app:bootJar" "$TMP_DIR/never-built.jar" "$build_log" >/dev/null 2>&1; then
    fail "a green build that produced no jar must fail"
else
    pass "a green build that produced no jar fails"
fi

# 14. Launch, ownership and stop against a fake `java` (sleeps; never the real
# services). Health targets a closed port, so the wait ends in a timeout while
# the fake process stays alive: the state the ownership checks care about.
fakebin="$TMP_DIR/fakebin"
mkdir -p "$fakebin"
cat > "$fakebin/java" <<'FAKE'
#!/bin/bash
printf '%s\n' "$PWD" "$@" > "$FAKE_JAVA_RECORD"
printf '%s' "${LAUNCH_SECRET:-<unset>}" > "$FAKE_JAVA_RECORD.secret"
while :; do sleep 1; done
FAKE
chmod +x "$fakebin/java"
export FAKE_JAVA_RECORD="$TMP_DIR/java.record"

write_env 'LAUNCH_SECRET=s3cr3t$value'
workdir="$TMP_DIR/workdir"
mkdir -p "$workdir"
service_jar="$DEV_RUN_DIR/menta-dance-api.jar"
pid_file="$TMP_DIR/api.pid"
service_log="$TMP_DIR/api.log"
printf 'jar' > "$built_jar"
unset LAUNCH_SECRET

# Run in a subshell, like a separate `dev.sh start` invocation: the service is
# then orphaned and a later stop does not depend on this shell's job table.
(
    PATH="$fakebin:$PATH"
    launch_status=0
    launch_service "API" ":api:app:bootJar" "$built_jar" "$service_jar" "$workdir" \
        "http://127.0.0.1:9/actuator/health" "$pid_file" "$service_log" 2 \
        >"$TMP_DIR/launch.out" 2>&1 || launch_status=$?
    printf '%s %s\n' "$LAST_SERVICE_STARTED" "$launch_status" > "$TMP_DIR/launch.state"
)

[[ -f "$service_jar" ]] && pass "launch copies the jar into the run dir" || fail "launch must stage the jar in the run dir"
if [[ "$(<"$TMP_DIR/launch.state")" == "true 1" && -s "$pid_file" ]]; then
    pass "launch records the process group and reports the unhealthy wait"
else
    fail "launch must record ownership (state: $(<"$TMP_DIR/launch.state"))"
fi

for _ in 1 2 3 4 5; do [[ -s "$FAKE_JAVA_RECORD.secret" ]] && break; sleep 1; done
record=$(slurp "$FAKE_JAVA_RECORD")
if [[ "$record" == "$workdir"* && "$record" == *$'\n-jar\n'"$service_jar" ]]; then
    pass "java runs in the module directory as 'java -jar <run dir jar>'"
else
    fail "java must run from the module dir with '-jar <run dir jar>' (record: $record)"
fi
if [[ "$(slurp "$FAKE_JAVA_RECORD.secret")" == 's3cr3t$value' ]]; then
    pass ".env values reach the java process"
else
    fail ".env values must be exported to the java process"
fi
if [[ -z "${LAUNCH_SECRET:-}" ]]; then
    pass ".env values do not leak into the calling shell"
else
    fail ".env values must only be exported inside the launched process"
fi
if [[ "$(<"$service_log")" != *"s3cr3t"* && "$(<"$TMP_DIR/launch.out")" != *"s3cr3t"* ]]; then
    pass ".env values are not printed to the log or the terminal"
else
    fail ".env values must never be printed"
fi

if owned_process_is_running "$pid_file" "$service_jar"; then
    pass "the process is owned by its jar path marker"
else
    fail "the launched process must be recognised by the jar path marker"
fi
if owned_process_is_running "$pid_file" ":api:app:bootRun"; then
    fail "the retired Gradle task marker must no longer match"
else
    pass "the retired Gradle task marker no longer matches"
fi

read -r live_pid live_pgid < "$pid_file"
stop_service "API" "$DEV_RUN_DIR/menta-dance-bff.jar" "$pid_file" >/dev/null 2>&1 || true
if kill -0 "$live_pid" 2>/dev/null; then
    pass "stop leaves alone a process group that does not carry its marker"
else
    fail "stop must not kill a process group it does not own"
fi
printf '%s %s\n' "$live_pid" "$live_pgid" > "$pid_file"
stop_service "API" "$service_jar" "$pid_file" >/dev/null 2>&1 || true
if ! kill -0 "$live_pid" 2>/dev/null && [[ ! -s "$pid_file" ]]; then
    pass "stop terminates the owned process group and clears the pid file"
else
    fail "stop must terminate the owned process group and clear its state"
fi

# A failing jar build never leaves a process or a pid file behind.
failed_pid_file="$TMP_DIR/failed.pid"
(
    PATH="$fakebin:$PATH"
    STUB_GRADLE_FAIL=1
    export STUB_GRADLE_FAIL
    launch_status=0
    launch_service "API" ":api:app:bootJar" "$built_jar" "$service_jar" "$workdir" \
        "http://127.0.0.1:9/actuator/health" "$failed_pid_file" "$TMP_DIR/failed-api.log" 2 \
        >"$TMP_DIR/launch-failed.out" 2>&1 || launch_status=$?
    printf '%s %s\n' "$LAST_SERVICE_STARTED" "$launch_status" > "$TMP_DIR/launch-failed.state"
)
if [[ "$(<"$TMP_DIR/launch-failed.state")" == "false 1" && ! -s "$failed_pid_file" \
    && "$(<"$TMP_DIR/launch-failed.out")" == *"failed during startup"* ]]; then
    pass "a failing build aborts the launch with nothing to roll back"
else
    fail "a failing build must fail the launch without a process (state: $(<"$TMP_DIR/launch-failed.state"))"
fi

# 14b. Race: between the fork and the exec of java, the launched process still
# carries the parent's command line (no jar path). A slow .env export widens that
# window; the launch must wait for the marker instead of reporting "exited".
slow_pid_file="$TMP_DIR/slow.pid"
(
    PATH="$fakebin:$PATH"
    export_env_file() { sleep 1; }
    launch_status=0
    launch_service "API" ":api:app:bootJar" "$built_jar" "$service_jar" "$workdir" \
        "http://127.0.0.1:9/actuator/health" "$slow_pid_file" "$TMP_DIR/slow-api.log" 2 \
        >"$TMP_DIR/launch-slow.out" 2>&1 || launch_status=$?
    printf '%s %s\n' "$LAST_SERVICE_STARTED" "$launch_status" > "$TMP_DIR/launch-slow.state"
)
if [[ "$(slurp "$TMP_DIR/launch-slow.out")" != *"exited before becoming healthy"* \
    && "$(slurp "$TMP_DIR/launch-slow.out")" == *"did not become healthy"* ]]; then
    pass "a slow pre-exec phase is not reported as an early exit"
else
    fail "a slow pre-exec phase must not be reported as 'exited' ($(slurp "$TMP_DIR/launch-slow.out"))"
fi
if owned_process_is_running "$slow_pid_file" "$service_jar"; then
    pass "the slow-starting process ends up owned by its jar marker"
else
    fail "the slow-starting process must end up owned by its jar marker"
fi
stop_service "API" "$service_jar" "$slow_pid_file" >/dev/null 2>&1 || true

# A process that really dies right after launch is still reported as exited,
# with its log tail, and does not stay owned.
deadbin="$TMP_DIR/deadbin"
mkdir -p "$deadbin"
cat > "$deadbin/java" <<'DEAD'
#!/bin/bash
echo "boom: fake JVM crash"
exit 1
DEAD
chmod +x "$deadbin/java"
dead_pid_file="$TMP_DIR/dead.pid"
(
    PATH="$deadbin:$PATH"
    launch_status=0
    launch_service "API" ":api:app:bootJar" "$built_jar" "$service_jar" "$workdir" \
        "http://127.0.0.1:9/actuator/health" "$dead_pid_file" "$TMP_DIR/dead-api.log" 3 \
        >"$TMP_DIR/launch-dead.out" 2>&1 || launch_status=$?
    printf '%s %s\n' "$LAST_SERVICE_STARTED" "$launch_status" > "$TMP_DIR/launch-dead.state"
)
if [[ "$(slurp "$TMP_DIR/launch-dead.out")" == *"exited before becoming healthy"* \
    && "$(slurp "$TMP_DIR/launch-dead.out")" == *"boom: fake JVM crash"* \
    && "$(<"$TMP_DIR/launch-dead.state")" == "true 1" && ! -s "$dead_pid_file" ]]; then
    pass "a process that dies early is reported as exited with its log tail"
else
    fail "an early death must be reported as exited ($(slurp "$TMP_DIR/launch-dead.out"))"
fi

# The .env loader must not fork per line (it ran for every line before exec).
big_env="$TMP_DIR/big.env"
for i in $(seq 1 300); do printf 'KEY_%s=value %s\n' "$i" "$i"; done > "$big_env"
start_seconds=$SECONDS
( export_env_file "$big_env" ) >/dev/null 2>&1
if (( SECONDS - start_seconds <= 1 )); then
    pass "the .env loader handles 300 lines in about a second or less"
else
    fail "the .env loader is too slow ($((SECONDS - start_seconds))s for 300 lines)"
fi

# 15. The help text tells developers where the services run from.
help_text=$(usage)
if [[ "$help_text" == *".dev-run/"* && "$help_text" == *"restart"* ]]; then
    pass "usage explains the .dev-run jars and restarting after code changes"
else
    fail "usage must mention .dev-run/ and restart"
fi

if [[ $failures -gt 0 ]]; then
    printf '\n%s test(s) failed\n' "$failures" >&2
    exit 1
fi
printf '\nAll dev.sh helper tests passed\n'
