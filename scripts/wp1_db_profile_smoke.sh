#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
if [[ "${WP1_DB_PROFILE_SMOKE_SEED_SUPER_ADMIN:-1}" == "1" ]]; then
  WP1_SUPER_ADMIN_USERNAME="${WP1_SUPER_ADMIN_USERNAME:-${WP_ALL_ADMIN_USERNAME:-admin}}" \
  WP1_SUPER_ADMIN_PASSWORD="${WP1_SUPER_ADMIN_PASSWORD:-${WP_ALL_ADMIN_PASSWORD:-AdminPass12345}}" \
  WP1_SUPER_ADMIN_DISPLAY_NAME="${WP1_SUPER_ADMIN_DISPLAY_NAME:-SuperAdmin}" \
  WP1_SUPER_ADMIN_EMAIL="${WP1_SUPER_ADMIN_EMAIL:-admin@example.com}" \
    "$SCRIPT_DIR/wp1_seed_super_admin.sh"
fi

echo "WP1 db-profile smoke is covered by the unified WP1-WP4 integration smoke."
exec "$SCRIPT_DIR/wp_all_integration_test.sh" "$@"
