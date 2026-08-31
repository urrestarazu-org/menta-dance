#!/usr/bin/env bash

# Reproducible local-only Bunny.net E2E lifecycle (issue #129). It composes
# the local Bunny.net signature adapter (ADR-0040), the D7 unplanned-course
# denial (ADR-0041), and the local Mercado Pago simulator to prove all three
# acceptance scenarios end to end, with no Bunny.net credential and no
# outbound call to api.mercadopago.com. It deliberately owns a separate
# Compose project so reset/clean never removes a developer stack or the
# catalog-content E2E stack.
set -euo pipefail

readonly PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
readonly COMPOSE_FILE="${PROJECT_ROOT}/infra/docker/database/docker-compose.yml"
readonly ENV_FILE="${PROJECT_ROOT}/.env"
readonly COMPOSE_PROJECT="menta-e2e-bunny-net"
readonly CONTAINER_PREFIX="menta-e2e-bunny-net"
readonly API_PORT="18082"
readonly MYSQL_PORT="33307"
readonly REDIS_PORT="36380"
readonly MAILPIT_PORT="38026"
readonly API_HEALTH_URL="http://localhost:${API_PORT}/actuator/health"
readonly API_LOG="${PROJECT_ROOT}/.dev-logs/e2e-bunny-net-api.log"
readonly API_PID_FILE="${PROJECT_ROOT}/.dev-pids/e2e-bunny-net-api.pid"
readonly BRUNO_FOLDER="E2E/bunny-net"
readonly BRUNO_ENV="e2e-bunny-net"
readonly BUNNY_NET_PLANNED_PLAN_ID="00000000-0000-0000-0000-000000000128"
# Must equal BillingConfiguration.DEV_DEFAULT_WEBHOOK_HMAC_SECRET verbatim (it
# is used as opaque HMAC key bytes, never base64-decoded) so
# ReceiveWebhookUseCaseImpl and LocalWebhookPreparationService agree on the
# same secret. LocalWebhookPreparationService has no built-in default and
# fails to start without this property set. (Same value as catalog-content.sh
# — both runners are dev-only and this is not a production secret.)
readonly MERCADOPAGO_DEV_WEBHOOK_HMAC_SECRET="ZGV2LW9ubHktd2ViaG9vay1zZWNyZXQtbm90LWZvci1wcm9kdWN0aW9uLXVzZQ=="
# BillingConfiguration's checkout use case defaults this to "" while
# LocalWebhookPreparationService defaults it to "local-merchant" — pin both
# to the same explicit value so Payment.matchesExpected agrees on the
# approved-path merchantAccountId.
readonly MERCADOPAGO_MERCHANT_ACCOUNT_ID="local-merchant"
# ADR-0040: the local Bunny.net adapter's signed-URL contract. Public,
# non-secret placeholders — the "invalid" TLD is RFC 2606 reserved and never
# resolves, so no request can ever leave this machine.
readonly BUNNY_NET_PULL_ZONE_HOSTNAME="https://local-bunny-net.invalid"
readonly BUNNY_NET_VIDEO_LIBRARY_ID="e2e-library"

api_pid=""

usage() {
    cat <<'EOF'
Usage: scripts/e2e/bunny-net.sh [--clean] [--help]

Starts an isolated local Bunny.net E2E stack, runs the versioned Bruno
journey (preview stream, D7 denial, premium grant after checkout), and
leaves only its Compose project running for diagnosis.
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
        REDIS_HOST_PORT="$REDIS_PORT" SMTP_HOST_PORT=31026 MAILPIT_WEB_HOST_PORT="$MAILPIT_PORT" \
        OTEL_GRPC_HOST_PORT=34319 OTEL_HTTP_HOST_PORT=34320 GRAFANA_HOST_PORT=33001 docker compose \
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
    SPRING_PROFILES_ACTIVE=e2e-bunny-net,e2e-catalog-content,e2e-mercadopago SERVER_PORT="$API_PORT" \
        MYSQL_URL="jdbc:mysql://localhost:${MYSQL_PORT}/menta" \
        SPRING_DATA_REDIS_PORT="$REDIS_PORT" SMTP_PORT=31026 \
        OTEL_EXPORTER_OTLP_ENDPOINT="http://localhost:34320" \
        BILLING_WEBHOOK_MERCADOPAGO_HMAC_SECRET="$MERCADOPAGO_DEV_WEBHOOK_HMAC_SECRET" \
        BILLING_MERCADOPAGO_MERCHANT_ACCOUNT_ID="$MERCADOPAGO_MERCHANT_ACCOUNT_ID" \
        APP_CDN_BUNNYNET_PULLZONEHOSTNAME="$BUNNY_NET_PULL_ZONE_HOSTNAME" \
        APP_CDN_BUNNYNET_VIDEOLIBRARYID="$BUNNY_NET_VIDEO_LIBRARY_ID" \
        "$PROJECT_ROOT/gradlew" --no-daemon :api:app:bootRun >"$API_LOG" 2>&1 &
    api_pid=$!
    printf '%s\n' "$api_pid" >"$API_PID_FILE"
    wait_for "API" "curl --fail --silent --show-error --max-time 2 '$API_HEALTH_URL'"
}

# Runs the local Bunny.net journey (issue #129): login, unplanned-course
# preview stream (200), unplanned-course protected stream (403, D7 — no
# videoId or signed URL), planned-course protected stream (403, pre-checkout),
# checkout, signed approved webhook, poll activation, planned-course
# protected stream (200, deterministic local signed URL). Composes profiles
# e2e-bunny-net + e2e-catalog-content + e2e-mercadopago against the same API
# instance (design.md "Journey order").
run_bunny_net_journey() {
    local student_email="bunnynet.e2e.student.${RANDOM}.$$@menta.local"
    local student_password="BunnyNetE2eStudent123!"
    local idempotency_key="bunny-net-${RANDOM}.$$"
    (
        cd "$PROJECT_ROOT/bruno"
        npx --yes @usebruno/cli run "$BRUNO_FOLDER/01-registration" -r --env "$BRUNO_ENV" \
            --env-var "studentEmail=$student_email" --env-var "studentPassword=$student_password" \
            --reporter-skip-body
        activation_token="$(await_activation_token "$student_email")"
        npx --yes @usebruno/cli run "$BRUNO_FOLDER/02-journey" -r --env "$BRUNO_ENV" \
            --env-var "studentEmail=$student_email" --env-var "studentPassword=$student_password" \
            --env-var "activationToken=$activation_token" \
            --env-var "planId=$BUNNY_NET_PLANNED_PLAN_ID" \
            --env-var "subscriptionIdempotencyKey=$idempotency_key" \
            --reporter-skip-body
    )
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
    run_bunny_net_journey
}

main "$@"
