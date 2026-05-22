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

## Prerequisites

- PostgreSQL 15+.
- Migration account can run `create extension if not exists pgcrypto`.
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
