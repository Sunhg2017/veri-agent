#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ROLE="${WP1_RELEASE_APP_ROLE:-wp1_app}"

if [[ -z "${WP1_RELEASE_DATABASE_URL:-}" ]]; then
  echo "WP1_RELEASE_DATABASE_URL is required, for example: postgres://user:pass@host:5432/db" >&2
  exit 64
fi

if ! command -v psql >/dev/null 2>&1; then
  echo "psql is required for release role validation" >&2
  exit 127
fi

psql "$WP1_RELEASE_DATABASE_URL" \
  -v ON_ERROR_STOP=1 \
  -v "WP1_RELEASE_APP_ROLE=$ROLE" \
  -f "$ROOT_DIR/db/validation/wp1_release_role_validation.sql"
