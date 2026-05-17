#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
OUT_DIR="${WP2_DB_VALIDATION_OUT_DIR:-$ROOT_DIR/build/wp2-db-validation}"
IMAGE="${WP2_DB_VALIDATION_IMAGE:-postgres:15-alpine}"
CONTAINER="${WP2_DB_VALIDATION_CONTAINER:-veri-agent-wp2-pg-ci}"
DB_NAME="${WP2_DB_VALIDATION_DB:-veri_agent_wp2_test}"
DB_USER="${WP2_DB_VALIDATION_USER:-wp2user}"
DB_PASSWORD="${WP2_DB_VALIDATION_PASSWORD:-wp2pass}"
KEEP_CONTAINER="${WP2_KEEP_CONTAINER:-0}"

MIGRATION_DIR="$ROOT_DIR/db/migration/wp2"
MIGRATIONS=()
while IFS= read -r migration_file; do
  MIGRATIONS+=("$migration_file")
done < <(find "$MIGRATION_DIR" -maxdepth 1 -type f -name 'V*.sql' | sort)

VALIDATION="$ROOT_DIR/db/validation/wp2_model_access_validation.sql"

run_psql_file() {
  local file="$1"
  docker exec -i "$CONTAINER" psql -v ON_ERROR_STOP=1 -U "$DB_USER" -d "$DB_NAME" < "$file"
}

cleanup() {
  if [[ "$KEEP_CONTAINER" != "1" ]]; then
    docker rm -f "$CONTAINER" >/dev/null 2>&1 || true
  fi
}

require_docker() {
  if ! command -v docker >/dev/null 2>&1; then
    echo "docker is required to run WP2 database validation" >&2
    exit 127
  fi
}

start_postgres() {
  docker rm -f "$CONTAINER" >/dev/null 2>&1 || true
  docker run -d \
    --name "$CONTAINER" \
    -e "POSTGRES_DB=$DB_NAME" \
    -e "POSTGRES_USER=$DB_USER" \
    -e "POSTGRES_PASSWORD=$DB_PASSWORD" \
    "$IMAGE" >/dev/null

  for _ in $(seq 1 60); do
    if docker exec "$CONTAINER" pg_isready -U "$DB_USER" -d "$DB_NAME" >/dev/null 2>&1; then
      return 0
    fi
    sleep 1
  done

  echo "PostgreSQL container did not become ready in time" >&2
  docker logs "$CONTAINER" >&2 || true
  exit 1
}

run_migrations() {
  local log_file="$1"
  : > "$log_file"
  for file in "${MIGRATIONS[@]}"; do
    echo "== running ${file#"$ROOT_DIR/"} ==" | tee -a "$log_file"
    run_psql_file "$file" >> "$log_file" 2>&1
  done
}

run_validation() {
  local out_file="$1"
  echo "== validating ${VALIDATION#"$ROOT_DIR/"} ==" > "$out_file"
  run_psql_file "$VALIDATION" >> "$out_file" 2>&1
}

assert_no_failures() {
  if grep -R -E '\|[[:space:]]*FAIL[[:space:]]*\|' "$OUT_DIR" >/dev/null 2>&1; then
    echo "WP2 database validation found FAIL rows. See $OUT_DIR" >&2
    grep -R -n -E '\|[[:space:]]*FAIL[[:space:]]*\|' "$OUT_DIR" >&2 || true
    exit 2
  fi
}

main() {
  require_docker
  mkdir -p "$OUT_DIR"
  trap cleanup EXIT

  start_postgres
  run_migrations "$OUT_DIR/migration.log"
  run_validation "$OUT_DIR/wp2_model_access_validation.out"
  run_migrations "$OUT_DIR/migration-rerun.log"
  run_validation "$OUT_DIR/rerun-wp2_model_access_validation.out"
  assert_no_failures

  echo "WP2 database migration and validation passed."
  echo "Logs: $OUT_DIR"
}

main "$@"
