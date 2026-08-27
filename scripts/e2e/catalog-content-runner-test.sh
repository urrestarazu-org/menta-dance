#!/usr/bin/env bash
set -euo pipefail

readonly ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
readonly RUNNER="$ROOT/scripts/e2e/catalog-content.sh"

assert_contains() {
    grep -Fq -- "$2" "$1" >/dev/null || {
        echo "Expected '$2' in $1" >&2
        exit 1
    }
}

help_output="$(mktemp)"
"$RUNNER" --help >"$help_output"
assert_contains "$help_output" "Usage: scripts/e2e/catalog-content.sh [--clean] [--help]"

# Safety is static and executable: all Compose invocations are scoped by the
# dedicated project, and cleanup is available only through that scoped helper.
assert_contains "$RUNNER" 'readonly COMPOSE_PROJECT="menta-e2e-catalog-content"'
assert_contains "$RUNNER" '--project-name "$COMPOSE_PROJECT"'
assert_contains "$RUNNER" 'compose down --volumes --remove-orphans'
if grep -F 'docker compose down' "$RUNNER" >/dev/null; then
    echo "Runner must not clean the default Compose project" >&2
    exit 1
fi
