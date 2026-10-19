#!/usr/bin/env bash
set -euo pipefail

readonly ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
readonly RUNNER="$ROOT/scripts/e2e/bunny-net.sh"

assert_contains() {
    grep -Fq -- "$2" "$1" >/dev/null || {
        echo "Expected '$2' in $1" >&2
        exit 1
    }
}

[[ -x "$RUNNER" ]] || {
    echo "Runner not found or not executable: $RUNNER" >&2
    exit 1
}

help_output="$(mktemp)"
"$RUNNER" --help >"$help_output"
assert_contains "$help_output" "Usage: scripts/e2e/bunny-net.sh [--clean] [--help]"

# An unknown argument must exit non-zero instead of silently falling through
# to the happy path.
if "$RUNNER" --not-a-real-flag >/dev/null 2>&1; then
    echo "Runner must exit non-zero for an unknown argument" >&2
    exit 1
fi

# Safety is static and executable: all Compose invocations are scoped by the
# dedicated project, and cleanup is available only through that scoped helper.
assert_contains "$RUNNER" 'set -euo pipefail'
assert_contains "$RUNNER" 'readonly COMPOSE_PROJECT="menta-e2e-bunny-net"'
assert_contains "$RUNNER" '--project-name "$COMPOSE_PROJECT"'
assert_contains "$RUNNER" 'compose down --volumes --remove-orphans'
assert_contains "$RUNNER" "trap stop_api EXIT INT TERM"
assert_contains "$RUNNER" 'readonly API_PORT="18082"'
assert_contains "$RUNNER" 'readonly MYSQL_PORT="33307"'
assert_contains "$RUNNER" 'readonly REDIS_PORT="36380"'
assert_contains "$RUNNER" 'readonly MAILPIT_PORT="38026"'
assert_contains "$RUNNER" 'SMTP_HOST_PORT=31026'
assert_contains "$RUNNER" 'OTEL_GRPC_HOST_PORT=34319'
assert_contains "$RUNNER" 'OTEL_HTTP_HOST_PORT=34320'
assert_contains "$RUNNER" 'GRAFANA_HOST_PORT=33001'
assert_contains "$RUNNER" 'SPRING_PROFILES_ACTIVE=e2e-bunny-net,e2e-catalog-content,e2e-mercadopago'
assert_contains "$RUNNER" 'APP_CDN_BUNNYNET_PULLZONEHOSTNAME="$BUNNY_NET_PULL_ZONE_HOSTNAME"'
assert_contains "$RUNNER" 'APP_CDN_BUNNYNET_VIDEOLIBRARYID="$BUNNY_NET_VIDEO_LIBRARY_ID"'
assert_contains "$RUNNER" 'readonly BUNNY_NET_PULL_ZONE_HOSTNAME="https://local-bunny-net.invalid"'
assert_contains "$RUNNER" 'readonly BUNNY_NET_VIDEO_LIBRARY_ID="e2e-library"'
assert_contains "$RUNNER" 'readonly BRUNO_ENV="e2e-bunny-net"'
assert_contains "$RUNNER" 'readonly BRUNO_FOLDER="E2E/bunny-net"'
assert_contains "$RUNNER" 'cd "$PROJECT_ROOT/bruno"'
assert_contains "$RUNNER" 'run "$BRUNO_FOLDER/01-registration" -r --env "$BRUNO_ENV"'
assert_contains "$RUNNER" 'run "$BRUNO_FOLDER/02-journey" -r --env "$BRUNO_ENV"'
assert_contains "$RUNNER" 'await_activation_token'

if grep -F 'docker compose down' "$RUNNER" >/dev/null; then
    echo "Runner must not clean the default Compose project" >&2
    exit 1
fi

if [[ "$(grep -Fc 'compose down --volumes --remove-orphans' "$RUNNER")" -ne 1 ]]; then
    echo "Only --clean may reset the dedicated E2E state" >&2
    exit 1
fi

# --clean must isolate the dedicated Compose project: it may only ever
# invoke `compose down`, which is always scoped by --project-name above,
# never a bare/global docker command that could reach another project.
if grep -E 'docker (system|volume|network) (prune|rm)' "$RUNNER" >/dev/null; then
    echo "--clean must not use a global docker prune/rm command" >&2
    exit 1
fi

# catalog-content.sh's ports/project must stay disjoint from this runner so
# both stacks can run in parallel without colliding.
readonly CATALOG_CONTENT_RUNNER="$ROOT/scripts/e2e/catalog-content.sh"
if [[ -f "$CATALOG_CONTENT_RUNNER" ]]; then
    for port in 18082 33307 36380 38026 31026 34319 34320 33001; do
        if grep -Fq "\"$port\"" "$CATALOG_CONTENT_RUNNER"; then
            echo "Port $port must not be reused by catalog-content.sh" >&2
            exit 1
        fi
    done
fi

if grep -F 'activationToken:' "$ROOT/bruno/environments/e2e-bunny-net.bru" >/dev/null; then
    echo "Activation tokens must not be stored in a versioned Bruno environment" >&2
    exit 1
fi
