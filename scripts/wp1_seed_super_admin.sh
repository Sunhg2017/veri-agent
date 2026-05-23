#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SEED_SQL="$ROOT_DIR/db/seed/wp1_super_admin.sql"

SUPER_ADMIN_USERNAME="${WP1_SUPER_ADMIN_USERNAME:-admin}"
SUPER_ADMIN_PASSWORD="${WP1_SUPER_ADMIN_PASSWORD:-}"
SUPER_ADMIN_DISPLAY_NAME="${WP1_SUPER_ADMIN_DISPLAY_NAME:-SuperAdmin}"
SUPER_ADMIN_EMAIL="${WP1_SUPER_ADMIN_EMAIL:-}"

DB_URL="${WP1_SEED_DATABASE_URL:-}"
JDBC_URL="${WP1_DATASOURCE_URL:-}"
DB_HOST="${WP1_SEED_DB_HOST:-${PGHOST:-localhost}}"
DB_PORT="${WP1_SEED_DB_PORT:-${PGPORT:-5432}}"
DB_NAME="${WP1_SEED_DB_NAME:-${PGDATABASE:-veri_agent}}"
DB_USERNAME="${WP1_SEED_DB_USERNAME:-${WP1_DATASOURCE_USERNAME:-${PGUSER:-veri_agent}}}"
DB_PASSWORD="${WP1_SEED_DB_PASSWORD:-${WP1_DATASOURCE_PASSWORD:-${PGPASSWORD:-}}}"

if [[ -z "$SUPER_ADMIN_PASSWORD" ]]; then
  echo "WP1_SUPER_ADMIN_PASSWORD is required; no default password is embedded in the repo." >&2
  exit 64
fi

if ! command -v psql >/dev/null 2>&1; then
  echo "psql is required to seed SuperAdmin." >&2
  exit 127
fi

if [[ -z "$DB_URL" && "$JDBC_URL" == jdbc:postgresql://* ]]; then
  DB_URL="${JDBC_URL#jdbc:}"
fi

export PGPASSWORD="$DB_PASSWORD"

psql_args=()
if [[ -n "$DB_URL" ]]; then
  psql_args=(-d "$DB_URL" -U "$DB_USERNAME")
else
  psql_args=(-h "$DB_HOST" -p "$DB_PORT" -U "$DB_USERNAME" -d "$DB_NAME")
fi

psql \
  -v ON_ERROR_STOP=1 \
  "${psql_args[@]}" \
  -v WP1_SUPER_ADMIN_USERNAME="$SUPER_ADMIN_USERNAME" \
  -v WP1_SUPER_ADMIN_PASSWORD="$SUPER_ADMIN_PASSWORD" \
  -v WP1_SUPER_ADMIN_DISPLAY_NAME="$SUPER_ADMIN_DISPLAY_NAME" \
  -v WP1_SUPER_ADMIN_EMAIL="$SUPER_ADMIN_EMAIL" \
  < "$SEED_SQL"

echo "WP1 SuperAdmin seed completed for username=$SUPER_ADMIN_USERNAME"
