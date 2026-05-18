#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
echo "WP1 db-profile smoke is now covered by the unified WP1-WP3 integration smoke."
exec "$SCRIPT_DIR/wp_all_integration_test.sh" "$@"
