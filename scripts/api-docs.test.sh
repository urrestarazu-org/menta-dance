#!/usr/bin/env bash
# Tests for scripts/api-docs.sh (local Swagger UI for the hand-written OpenAPI
# contracts). They use a fake docker/curl on PATH: no real Docker, no network,
# and never the API, the BFF or any port.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SCRIPT="$ROOT/scripts/api-docs.sh"
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT

BASH_BIN="$(command -v bash)"
FAKE_BIN="$TMP_DIR/bin"
FAKE_STATE="$TMP_DIR/state"
DOCKER_LOG="$TMP_DIR/docker.log"
mkdir -p "$FAKE_BIN" "$FAKE_STATE"

# Fake docker. State lives in files so consecutive script runs see each other.
cat > "$FAKE_BIN/docker" <<'FAKE'
#!/usr/bin/env bash
printf '%s\n' "$*" >> "$FAKE_DOCKER_LOG"
case "$1" in
    info) exit "${FAKE_DOCKER_INFO_STATUS:-0}" ;;
    inspect)
        [[ -e "$FAKE_DOCKER_STATE/running" ]] && { printf 'true\n'; exit 0; }
        exit 1
        ;;
    run) : > "$FAKE_DOCKER_STATE/running"; printf 'fake-container-id\n' ;;
    rm) rm -f "$FAKE_DOCKER_STATE/running" ;;
    *) exit 0 ;;
esac
FAKE
cat > "$FAKE_BIN/curl" <<'FAKE'
#!/usr/bin/env bash
printf '200'
FAKE
chmod +x "$FAKE_BIN/docker" "$FAKE_BIN/curl"

failures=0
pass() { printf 'ok   - %s\n' "$1"; }
fail() { printf 'FAIL - %s\n' "$1" >&2; failures=$((failures + 1)); }

reset_fake() {
    rm -f "$FAKE_STATE/running" "$DOCKER_LOG"
    : > "$DOCKER_LOG"
}

# Runs the script with the fake docker first on PATH. Captures output and status.
run_script() {
    OUTPUT=$(
        PATH="$FAKE_BIN:$PATH" \
        FAKE_DOCKER_LOG="$DOCKER_LOG" FAKE_DOCKER_STATE="$FAKE_STATE" \
        "$BASH_BIN" "$SCRIPT" "$@" 2>&1
    ) && STATUS=0 || STATUS=$?
}

count_in_log() { grep -c -- "$1" "$DOCKER_LOG" || true; }

# 1. help lists the subcommands and does not need Docker.
run_script help
if [[ $STATUS -eq 0 && "$OUTPUT" == *start* && "$OUTPUT" == *stop* && "$OUTPUT" == *status* ]]; then
    pass "help lists start, stop and status"
else
    fail "help must exit 0 and list start, stop and status"
fi

# 2. Unknown or missing commands fail.
run_script bogus
if [[ $STATUS -ne 0 ]]; then pass "unknown command is rejected"; else fail "unknown command must fail"; fi
run_script
if [[ $STATUS -ne 0 ]]; then pass "missing command is rejected"; else fail "missing command must fail"; fi

# 3. Docker not installed: clear error, no crash.
# PATH holds only the coreutils the script needs, so `docker` really is missing.
NO_DOCKER_BIN="$TMP_DIR/no-docker-bin"
mkdir -p "$NO_DOCKER_BIN"
for tool in dirname basename; do ln -s "$(command -v "$tool")" "$NO_DOCKER_BIN/$tool"; done
OUTPUT=$(PATH="$NO_DOCKER_BIN" "$BASH_BIN" "$SCRIPT" start 2>&1) && STATUS=0 || STATUS=$?
if [[ $STATUS -ne 0 && "$OUTPUT" == *"Docker"* ]]; then
    pass "start without docker on PATH fails with a Docker message"
else
    fail "start without docker must fail and mention Docker (status=$STATUS)"
fi

# 4. Docker installed but the daemon is down: clear error, nothing is run.
reset_fake
export FAKE_DOCKER_INFO_STATUS=1
run_script start
if [[ $STATUS -ne 0 && "$OUTPUT" == *"Docker"* && "$(count_in_log '^run')" -eq 0 ]]; then
    pass "start with the daemon down fails with a Docker message and runs nothing"
else
    fail "start with the daemon down must fail before docker run (status=$STATUS)"
fi
unset FAKE_DOCKER_INFO_STATUS

# 5. The selector lists every *-v1.yaml of the spec dir and nothing else.
SPEC_DIR="$TMP_DIR/spec"
mkdir -p "$SPEC_DIR"
for name in auth billing catalog; do printf 'openapi: 3.0.3\n' > "$SPEC_DIR/$name-v1.yaml"; done
printf 'x\n' > "$SPEC_DIR/README.md"
reset_fake
OUTPUT=$(
    PATH="$FAKE_BIN:$PATH" FAKE_DOCKER_LOG="$DOCKER_LOG" FAKE_DOCKER_STATE="$FAKE_STATE" \
    API_DOCS_SPEC_DIR="$SPEC_DIR" "$BASH_BIN" "$SCRIPT" start 2>&1
) && STATUS=0 || STATUS=$?
run_line=$(grep '^run' "$DOCKER_LOG" || true)
if [[ $STATUS -eq 0 \
    && "$run_line" == *'"url":"/spec/auth-v1.yaml","name":"auth"'* \
    && "$run_line" == *'"url":"/spec/billing-v1.yaml","name":"billing"'* \
    && "$run_line" == *'"url":"/spec/catalog-v1.yaml","name":"catalog"'* \
    && "$run_line" != *README* ]]; then
    pass "URLS selector lists each *-v1.yaml contract and ignores other files"
else
    fail "URLS selector must list exactly the *-v1.yaml files (status=$STATUS): $run_line"
fi

# 6. docker run is pinned, read-only, local-only and named.
if [[ "$run_line" =~ swaggerapi/swagger-ui:v[0-9]+\.[0-9]+\.[0-9]+ && "$run_line" != *":latest"* ]]; then
    pass "the image uses a pinned version tag, not latest"
else
    fail "the image must be swaggerapi/swagger-ui:vX.Y.Z, never latest: $run_line"
fi
if [[ "$run_line" == *"$SPEC_DIR:/usr/share/nginx/html/spec:ro"* ]]; then
    pass "the contracts are mounted read-only"
else
    fail "the spec dir must be mounted read-only: $run_line"
fi
if [[ "$run_line" == *"127.0.0.1:8090:8080"* && "$run_line" == *"--name menta-api-docs"* ]]; then
    pass "the UI is published on 127.0.0.1:8090 under a fixed container name"
else
    fail "the UI must bind 127.0.0.1:8090 and be named menta-api-docs: $run_line"
fi
if [[ "$run_line" == *"SUPPORTED_SUBMIT_METHODS=[]"* ]]; then
    pass "Try it out is disabled (read-only viewer, never calls the real API)"
else
    fail "SUPPORTED_SUBMIT_METHODS=[] must be set: $run_line"
fi

# 7. start is idempotent: the second run does not launch another container.
reset_fake
run_script start
first_runs=$(count_in_log '^run')
run_script start
second_runs=$(count_in_log '^run')
if [[ $STATUS -eq 0 && "$first_runs" -eq 1 && "$second_runs" -eq 1 && "$OUTPUT" == *"http://localhost:8090"* ]]; then
    pass "starting twice launches one container and still prints the URL"
else
    fail "second start must be a no-op (runs: $first_runs then $second_runs, status=$STATUS)"
fi

# 8. status reflects the state.
run_script status
if [[ $STATUS -eq 0 && "$OUTPUT" == *"UP"* ]]; then pass "status reports UP while running"; else fail "status must report UP"; fi

# 9. stop removes the container and is idempotent. Clear the log so the
# cleanup `rm -f` that start performs is not counted.
: > "$DOCKER_LOG"
run_script stop
if [[ $STATUS -eq 0 && "$(count_in_log '^rm -f menta-api-docs')" -eq 1 ]]; then
    pass "stop removes the container"
else
    fail "stop must run 'docker rm -f menta-api-docs' (status=$STATUS)"
fi
run_script stop
if [[ $STATUS -eq 0 && "$(count_in_log '^rm -f')" -eq 1 ]]; then
    pass "stopping again is a harmless no-op"
else
    fail "stop on an absent container must succeed without calling docker rm"
fi
run_script status
if [[ $STATUS -ne 0 && "$OUTPUT" == *"DOWN"* ]]; then pass "status reports DOWN after stop"; else fail "status must report DOWN after stop"; fi

# 10. Port validation: numeric, and never the ports the project already uses.
for bad_port in abc 0 80 8080 8081 3306 3000 6379 99999; do
    reset_fake
    OUTPUT=$(
        PATH="$FAKE_BIN:$PATH" FAKE_DOCKER_LOG="$DOCKER_LOG" FAKE_DOCKER_STATE="$FAKE_STATE" \
        API_DOCS_PORT="$bad_port" "$BASH_BIN" "$SCRIPT" start 2>&1
    ) && STATUS=0 || STATUS=$?
    if [[ $STATUS -ne 0 && "$(count_in_log '^run')" -eq 0 ]]; then
        pass "port '$bad_port' is rejected"
    else
        fail "port '$bad_port' must be rejected before docker run"
    fi
done

# 11. The script never touches the API, the BFF or the compose infrastructure.
reset_fake
run_script start
run_script stop
if ! grep -Eq 'compose|menta-mysql|menta-redis|menta-api |menta-bff|8081' "$DOCKER_LOG"; then
    pass "docker is only used for the menta-api-docs container"
else
    fail "api-docs must not touch compose, the API or the BFF: $(<"$DOCKER_LOG")"
fi

if [[ $failures -gt 0 ]]; then
    printf '\n%s test(s) failed\n' "$failures" >&2
    exit 1
fi
printf '\nAll api-docs.sh tests passed\n'
