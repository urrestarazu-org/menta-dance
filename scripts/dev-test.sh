#!/usr/bin/env bash
# Tests for the pure helpers in scripts/dev.sh (environment preflight and
# startup-failure detection). They never start Docker, Gradle or any service.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT

# dev.sh only runs main() when executed directly, so sourcing it is safe.
export DEV_ENV_FILE="$TMP_DIR/.env"
# shellcheck source=scripts/dev.sh
source "$ROOT/scripts/dev.sh"

failures=0

pass() { printf 'ok   - %s\n' "$1"; }
fail() { printf 'FAIL - %s\n' "$1" >&2; failures=$((failures + 1)); }

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

if [[ $failures -gt 0 ]]; then
    printf '\n%s test(s) failed\n' "$failures" >&2
    exit 1
fi
printf '\nAll dev.sh helper tests passed\n'
