#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
OUT_DIR="${WP1_DB_VALIDATION_OUT_DIR:-$ROOT_DIR/build/wp1-db-validation}"
IMAGE="${WP1_DB_VALIDATION_IMAGE:-postgres:15-alpine}"
CONTAINER="${WP1_DB_VALIDATION_CONTAINER:-veri-agent-wp1-pg-ci}"
DB_NAME="${WP1_DB_VALIDATION_DB:-veri_agent_wp1_test}"
DB_USER="${WP1_DB_VALIDATION_USER:-wp1user}"
DB_PASSWORD="${WP1_DB_VALIDATION_PASSWORD:-wp1pass}"
KEEP_CONTAINER="${WP1_KEEP_CONTAINER:-0}"
SUPER_ADMIN_SEED_SQL="$ROOT_DIR/db/seed/wp1_super_admin.sql"
SUPER_ADMIN_SEED_VALIDATION_SQL="$ROOT_DIR/db/validation/wp1_super_admin_seed_validation.sql"

MIGRATIONS=()
while IFS= read -r migration_file; do
  MIGRATIONS+=("$migration_file")
done < <(find "$ROOT_DIR/db/migration/wp1" -maxdepth 1 -type f -name 'V*.sql' | sort)

VALIDATIONS=(
  "$ROOT_DIR/db/validation/wp1_schema_validation.sql"
  "$ROOT_DIR/db/validation/wp1_seed_validation.sql"
  "$ROOT_DIR/db/validation/wp1_security_validation.sql"
  "$ROOT_DIR/db/validation/wp_all_schema_validation.sql"
  "$ROOT_DIR/db/validation/wp4_document_input_validation.sql"
  "$ROOT_DIR/db/validation/wp5_test_design_validation.sql"
)

run_psql_file() {
  local file="$1"
  docker exec -i "$CONTAINER" psql -v ON_ERROR_STOP=1 -U "$DB_USER" -d "$DB_NAME" < "$file"
}

run_psql_inline() {
  docker exec -i "$CONTAINER" psql -v ON_ERROR_STOP=1 -U "$DB_USER" -d "$DB_NAME"
}

run_release_role_validation() {
  local out_file="$1"
  docker exec -i "$CONTAINER" psql -v ON_ERROR_STOP=1 -U "$DB_USER" -d "$DB_NAME" \
    -v WP1_RELEASE_SCHEMA=public \
    -v WP1_RELEASE_APP_ROLE=wp1_app \
    -v WP1_RELEASE_READONLY_ROLE=wp1_readonly \
    -v WP1_RELEASE_MIGRATION_ROLE=wp1_migration \
    < "$ROOT_DIR/db/validation/wp1_release_role_validation.sql" > "$out_file" 2>&1
}

cleanup() {
  if [[ "$KEEP_CONTAINER" != "1" ]]; then
    docker rm -f "$CONTAINER" >/dev/null 2>&1 || true
  fi
}

require_docker() {
  if ! command -v docker >/dev/null 2>&1; then
    echo "docker is required to run WP1 database validation" >&2
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
  local prefix="$2"
  : > "$log_file"
  for file in "${MIGRATIONS[@]}"; do
    echo "== ${prefix}${file#"$ROOT_DIR/"} ==" | tee -a "$log_file"
    run_psql_file "$file" >> "$log_file" 2>&1
  done
}

run_super_admin_seed() {
  local log_file="$1"
  local prefix="$2"
  echo "== ${prefix}db/seed/wp1_super_admin.sql ==" | tee -a "$log_file"
  docker exec -i "$CONTAINER" psql -v ON_ERROR_STOP=1 -U "$DB_USER" -d "$DB_NAME" \
    -v WP1_SUPER_ADMIN_USERNAME=admin \
    -v 'WP1_SUPER_ADMIN_PASSWORD=AdminPass12345!' \
    -v 'WP1_SUPER_ADMIN_DISPLAY_NAME=SuperAdmin' \
    -v 'WP1_SUPER_ADMIN_EMAIL=admin@example.com' \
    < "$SUPER_ADMIN_SEED_SQL" >> "$log_file" 2>&1
}

run_super_admin_seed_validation() {
  local out_file="$1"
  echo "== validating db/validation/wp1_super_admin_seed_validation.sql ==" > "$out_file"
  run_psql_file "$SUPER_ADMIN_SEED_VALIDATION_SQL" >> "$out_file" 2>&1
}

apply_runtime_role_policy() {
  local log_file="$1"
  echo "== applying WP1 runtime role policy for validation ==" | tee -a "$log_file"
  run_psql_inline >> "$log_file" 2>&1 <<'SQL'
do $$
begin
    if not exists (select 1 from pg_roles where rolname = 'wp1_app') then
        create role wp1_app login password 'wp1_app_validation_pass';
    end if;
    if not exists (select 1 from pg_roles where rolname = 'wp1_readonly') then
        create role wp1_readonly login password 'wp1_readonly_validation_pass';
    end if;
    if not exists (select 1 from pg_roles where rolname = 'wp1_migration') then
        create role wp1_migration login password 'wp1_migration_validation_pass';
    end if;
end
$$;

grant usage on schema public to wp1_app, wp1_readonly, wp1_migration;
grant create on schema public to wp1_migration;

grant select, insert, update on
    base_department,
    base_department_manager,
    base_department_member,
    iam_user,
    iam_session,
    base_project,
    base_project_department,
    base_project_member,
    base_application,
    base_environment,
    base_environment_variable,
    base_config,
    rbac_role,
    rbac_role_permission,
    rbac_role_binding,
    ma_model_provider,
    ma_prompt_template,
    ma_invocation_log,
    ma_invocation_job,
    asset_requirement,
    asset_api,
    asset_page,
    asset_business_flow,
    asset_test_case,
    asset_test_step,
    asset_link,
    secret_provider,
    secret_reference,
    secret_local_store,
    document_input_field_mapping,
    document_input_source,
    document_input_import,
    document_input_candidate,
    document_input_parse_feedback_sample,
    document_input_webhook_event,
    test_design_task,
    test_design_candidate,
    test_design_review_record,
    test_design_publish_record,
    test_design_report_manifest,
    test_design_context_policy_override,
    test_design_release_readiness_approval,
    audit_outbox
to wp1_app;

grant select on rbac_permission to wp1_app;
grant select, insert on audit_log to wp1_app;
revoke update, delete, truncate on audit_log from wp1_app;
grant execute on function wp1_cleanup_audit_log_before(timestamptz, integer) to wp1_app;
grant select, insert on asset_version_history to wp1_app;
revoke update, delete, truncate on asset_version_history from wp1_app;
grant select, insert on
    test_design_context_policy_note,
    test_design_release_readiness_note
to wp1_app;
revoke update, delete, truncate on
    test_design_context_policy_note,
    test_design_release_readiness_note
from wp1_app;

revoke all on secret_local_store from public;

grant select on all tables in schema public to wp1_readonly;
revoke insert, update, delete, truncate on all tables in schema public from wp1_readonly;
revoke select on secret_local_store from wp1_readonly;

grant all privileges on all tables in schema public to wp1_migration;
grant all privileges on all sequences in schema public to wp1_migration;
SQL
}

run_validations() {
  local output_prefix="$1"
  local label="$2"
  for file in "${VALIDATIONS[@]}"; do
    local base
    base="$(basename "$file")"
    local out_file="$OUT_DIR/${output_prefix}${base%.sql}.out"
    echo "== ${label}${file#"$ROOT_DIR/"} ==" > "$out_file"
    run_psql_file "$file" >> "$out_file" 2>&1
  done
}

assert_no_failures() {
  if grep -R -E '\|[[:space:]]*FAIL[[:space:]]*\|' "$OUT_DIR" >/dev/null 2>&1; then
    echo "WP1 database validation found FAIL rows. See $OUT_DIR" >&2
    grep -R -n -E '\|[[:space:]]*FAIL[[:space:]]*\|' "$OUT_DIR" >&2 || true
    exit 2
  fi
}

assert_no_warnings() {
  if grep -R -E '\|[[:space:]]*WARN[[:space:]]*\|' "$OUT_DIR" >/dev/null 2>&1; then
    echo "WP1 database validation found WARN rows. See $OUT_DIR" >&2
    grep -R -n -E '\|[[:space:]]*WARN[[:space:]]*\|' "$OUT_DIR" >&2 || true
    if [[ "${WP1_ALLOW_DB_VALIDATION_WARN:-0}" != "1" ]]; then
      exit 3
    fi
    echo "WARN rows allowed by WP1_ALLOW_DB_VALIDATION_WARN=1."
  fi
}

main() {
  require_docker
  mkdir -p "$OUT_DIR"
  trap cleanup EXIT

  start_postgres
  run_migrations "$OUT_DIR/migration.log" "running "
  run_super_admin_seed "$OUT_DIR/migration.log" "running "
  apply_runtime_role_policy "$OUT_DIR/migration.log"
  run_release_role_validation "$OUT_DIR/wp1_release_role_validation.out"
  run_super_admin_seed_validation "$OUT_DIR/wp1_super_admin_seed_validation.out"
  run_validations "" "validating "
  run_migrations "$OUT_DIR/migration-rerun.log" "rerun "
  run_super_admin_seed "$OUT_DIR/migration-rerun.log" "rerun "
  apply_runtime_role_policy "$OUT_DIR/migration-rerun.log"
  run_release_role_validation "$OUT_DIR/rerun-wp1_release_role_validation.out"
  run_super_admin_seed_validation "$OUT_DIR/rerun-wp1_super_admin_seed_validation.out"
  run_validations "rerun-" "rerun validating "
  assert_no_failures
  assert_no_warnings

  echo "WP1 database migration and validation passed."
  echo "Logs: $OUT_DIR"
}

main "$@"
