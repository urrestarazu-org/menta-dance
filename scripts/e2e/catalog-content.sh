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
readonly BRUNO_FOLDER="E2E/catalog-content"
readonly BRUNO_ENV="e2e-catalog-content"
readonly MERCADOPAGO_BRUNO_FOLDER="E2E/mercadopago"
readonly MERCADOPAGO_PLAN_ID="00000000-0000-0000-0000-000000000128"
# Must equal BillingConfiguration.DEV_DEFAULT_WEBHOOK_HMAC_SECRET verbatim (it
# is used as opaque HMAC key bytes, never base64-decoded) so
# ReceiveWebhookUseCaseImpl and LocalWebhookPreparationService agree on the
# same secret. LocalWebhookPreparationService has no built-in default and
# fails to start without this property set.
readonly MERCADOPAGO_DEV_WEBHOOK_HMAC_SECRET="ZGV2LW9ubHktd2ViaG9vay1zZWNyZXQtbm90LWZvci1wcm9kdWN0aW9uLXVzZQ=="
# BillingConfiguration's checkout use case defaults this to "" while
# LocalWebhookPreparationService defaults it to "local-merchant" — pin both
# to the same explicit value so Payment.matchesExpected agrees on the
# approved-path merchantAccountId.
readonly MERCADOPAGO_MERCHANT_ACCOUNT_ID="local-merchant"

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

await_activation_token() {
    local student_email=$1
    local attempt=0
    local messages message_id message_body token
    while (( attempt < 60 )); do
        messages="$(curl --fail --silent --show-error --max-time 2 \
            "http://localhost:${MAILPIT_PORT}/api/v1/messages?limit=50")" || messages=""
        message_id="$(jq -r --arg email "$student_email" '
            .messages[]? | select(any(.To[]?; (.Address // .address // "") == $email)) | (.ID // .id)
        ' <<<"$messages" | head -n 1)"
        if [[ -n "$message_id" && "$message_id" != "null" ]]; then
            message_body="$(curl --fail --silent --show-error --max-time 2 \
                "http://localhost:${MAILPIT_PORT}/api/v1/message/${message_id}")" || message_body=""
            token="$(jq -r '.Text // .text // .HTML // .html // ""' <<<"$message_body" \
                | grep -Eo '/api/v1/auth/activate/[A-Za-z0-9_-]+' | head -n 1 | sed 's#.*/##')"
            if [[ -n "$token" ]]; then
                printf '%s' "$token"
                return 0
            fi
        fi
        attempt=$((attempt + 1))
        sleep 2
    done
    fail "Activation email did not arrive in Mailpit for $student_email"
}

stop_api() {
    if [[ -n "$api_pid" ]] && kill -0 "$api_pid" 2>/dev/null; then
        kill "$api_pid" 2>/dev/null || true
        wait "$api_pid" 2>/dev/null || true
    fi
}

start_api() {
    mkdir -p "$(dirname "$API_LOG")" "$(dirname "$API_PID_FILE")"
    SPRING_PROFILES_ACTIVE=e2e-catalog-content,e2e-mercadopago SERVER_PORT="$API_PORT" \
        MYSQL_URL="jdbc:mysql://localhost:${MYSQL_PORT}/menta" \
        SPRING_DATA_REDIS_PORT="$REDIS_PORT" SMTP_PORT=31025 \
        OTEL_EXPORTER_OTLP_ENDPOINT="http://localhost:34318" \
        BILLING_WEBHOOK_MERCADOPAGO_HMAC_SECRET="$MERCADOPAGO_DEV_WEBHOOK_HMAC_SECRET" \
        BILLING_MERCADOPAGO_MERCHANT_ACCOUNT_ID="$MERCADOPAGO_MERCHANT_ACCOUNT_ID" \
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
    compose up -d
    wait_for "MySQL" "docker inspect --format '{{.State.Health.Status}}' '${CONTAINER_PREFIX}-mysql' | grep -qx healthy"
    wait_for "Redis" "docker inspect --format '{{.State.Health.Status}}' '${CONTAINER_PREFIX}-redis' | grep -qx healthy"
    wait_for "Mailpit" "docker inspect --format '{{.State.Health.Status}}' '${CONTAINER_PREFIX}-mailpit' | grep -qx healthy"
    start_api
    local student_email="catalog.e2e.student.${RANDOM}.$$@menta.local"
    local student_password="CatalogE2eStudent123!"
    (
        cd "$PROJECT_ROOT/bruno"
        npx --yes @usebruno/cli run "$BRUNO_FOLDER/01-registration" -r --env "$BRUNO_ENV" \
            --env-var "studentEmail=$student_email" --env-var "studentPassword=$student_password" \
            --reporter-skip-body
        activation_token="$(await_activation_token "$student_email")"
        npx --yes @usebruno/cli run "$BRUNO_FOLDER/02-journey" -r --env "$BRUNO_ENV" \
            --env-var "studentEmail=$student_email" --env-var "studentPassword=$student_password" \
            --env-var "activationToken=$activation_token" --reporter-skip-body
    )
    run_mercadopago_journey
}

# Local Mercado Pago simulator journey (issue #128): checkout, signed
# webhook, worker fulfillment, duplicate idempotency and an
# error/reconciliation case — all through local, profile-gated endpoints.
# Runs against the same API instance as the catalog-content journey
# (e2e-mercadopago is active alongside e2e-catalog-content), but with its own
# dedicated student so the two journeys never share subscription state.
run_mercadopago_journey() {
    # Two independent students: a mismatched provider result leaves
    # PaymentStatus.ReconciliationRequired, which is NOT terminal, so its
    # Subscription stays PENDING (occupying the user's slot) rather than
    # being cancelled — sharing one account would block the approved-path
    # checkout with 409 SUBSCRIPTION_ALREADY_ACTIVE.
    local student_email="mercadopago.e2e.student.${RANDOM}.$$@menta.local"
    local student_password="MercadoPagoE2eStudent123!"
    local mismatch_student_email="mercadopago.e2e.mismatch.${RANDOM}.$$@menta.local"
    local mismatch_student_password="MercadoPagoE2eMismatch123!"
    local approved_key="approved-${RANDOM}.$$"
    local mismatch_key="mismatch-${RANDOM}.$$"
    (
        cd "$PROJECT_ROOT/bruno"
        npx --yes @usebruno/cli run "$MERCADOPAGO_BRUNO_FOLDER/01-registration" -r --env "$BRUNO_ENV" \
            --env-var "studentEmail=$student_email" --env-var "studentPassword=$student_password" \
            --env-var "mismatchStudentEmail=$mismatch_student_email" \
            --env-var "mismatchStudentPassword=$mismatch_student_password" \
            --reporter-skip-body
        activation_token="$(await_activation_token "$student_email")"
        mismatch_activation_token="$(await_activation_token "$mismatch_student_email")"
        npx --yes @usebruno/cli run "$MERCADOPAGO_BRUNO_FOLDER/02-journey" -r --env "$BRUNO_ENV" \
            --env-var "studentEmail=$student_email" --env-var "studentPassword=$student_password" \
            --env-var "activationToken=$activation_token" \
            --env-var "mismatchStudentEmail=$mismatch_student_email" \
            --env-var "mismatchStudentPassword=$mismatch_student_password" \
            --env-var "mismatchActivationToken=$mismatch_activation_token" \
            --env-var "planId=$MERCADOPAGO_PLAN_ID" \
            --env-var "subscriptionIdempotencyKey=$approved_key" \
            --env-var "mismatchSubscriptionIdempotencyKey=$mismatch_key" \
            --reporter-skip-body
    )
}

main "$@"
