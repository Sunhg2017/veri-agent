# PostgreSQL Migrations

All WP1-WP4 migrations currently live in `db/migration/wp1` because the MVP deploys a single `platform-api` and one PostgreSQL schema.

## Script Order

1. `wp1/V20260516_001__wp1_base_schema.sql` creates extensions, tables, indexes, check constraints, foreign keys, and soft-delete partial unique indexes.
2. `wp1/V20260516_002__wp1_seed_permissions_roles.sql` seeds P0 permissions, 8 built-in roles, role-permission mappings, system defaults, and the default `LOCAL_ENCRYPTED` provider.
3. `wp1/V20260516_003__wp1_runtime_db_policy.sql` documents runtime grant/revoke policy and adds table/column comments. Its grant template must be adapted to environment-specific role names before execution.
4. `wp1/V20260517_004__wp1_account_lifecycle.sql` seeds account lifecycle permissions.
5. `wp1/V20260518_009__wp2_model_access_schema.sql` creates WP2 model provider, prompt template, and invocation log tables.
6. `wp1/V20260518_010__wp2_default_seed_data.sql` seeds the local echo provider and default test-case-design prompt for db profile smoke usage.
7. Later `wp1/V20260518_*` and `wp1/V20260520_*` migrations add WP2 hardening, WP3 assets, WP4 document input, and WP3 asset version history.
8. `wp1/V20260522_026__wp2_prompt_review_approval.sql` adds WP2 high-risk Prompt review and approval metadata.
9. `wp1/V20260529_039__wp5_model_prompt_seed.sql` seeds the WP5 model-backed test design prompt used when `WP5` generation mode is switched from rules to WP2 model invocation.

## SuperAdmin Seed

The first `SuperAdmin` is initialized by `db/seed/wp1_super_admin.sql` through `scripts/wp1_seed_super_admin.sh`, not by an HTTP or UI bootstrap endpoint. The script requires `WP1_SUPER_ADMIN_PASSWORD`, stores only a BCrypt hash, binds the `SuperAdmin` platform role, writes seed audit rows, and sets `must_change_password=true` so first login is forced through password change.

`SuperAdmin` baseline permissions are seeded with the built-in role definitions in `wp1/V20260516_002__wp1_seed_permissions_roles.sql` and extended only by feature-owned permission migrations. Retired HTTP/UI bootstrap migrations must not be kept in the active Flyway directory.

If an environment has already recorded a retired migration in `flyway_schema_history`, coordinate a DBA-approved Flyway repair or forward-fix plan before deploying this cleaned migration set.

## Prerequisites

- PostgreSQL 15+.
- Migration account can run `create extension if not exists pgcrypto` and `create extension if not exists pg_trgm`.
- Migration account owns or can create objects in the target schema.
- Run scripts in lexical order with Flyway, Liquibase, or `psql`.

## Secret Key

Set `WP1_LOCAL_SECRET_MASTER_KEY` in the WP1 service runtime environment before using the default local provider. The database stores only:

- provider metadata pointing to the environment variable name;
- secret references and masked values;
- encrypted ciphertext, IV, auth tag, algorithm, and master key version.

Plaintext secrets must never be inserted into migration scripts, audit JSON, config JSON, logs, or exported files.

## Rollback Strategy

- Prefer forward fixes for production schema changes.
- Do not hard-delete seed roles, permissions, or audit rows; disable or supersede them in a later migration so historical audit remains explainable.
- For failed initial deployment on an empty database, restore from snapshot or drop the empty schema only through an approved DBA process.
- For constraint/index issues, repair data first, then apply a new migration.

## Notes

- WP1 is a single-platform deployment. Isolation boundaries are department, project, application, environment, role scope, and audit scope.
- `audit_log` should be append-only for the runtime application role.
- Department tree cycle checks and `scope_id` resource-type validation are service-layer rules in MVP P0.
- Production large-table indexes may need `create index concurrently` in a dedicated migration.
