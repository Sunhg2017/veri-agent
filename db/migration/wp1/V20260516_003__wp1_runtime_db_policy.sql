-- WP1 runtime database policy template.
-- Replace role names before running in each environment:
--   :WP1_APP_ROLE       application runtime role
--   :WP1_READONLY_ROLE  readonly/reporting role
--   :WP1_MIGRATION_ROLE migration/DDL owner role
--
-- Example with psql variables:
--   psql -v WP1_APP_ROLE=wp1_app -v WP1_READONLY_ROLE=wp1_readonly -v WP1_MIGRATION_ROLE=wp1_migration -f V20260516_003__wp1_runtime_db_policy.sql

-- The following executable block is intentionally disabled by default because
-- database role names are environment-specific. Uncomment and adapt after roles
-- are created by the DBA or infrastructure pipeline.

/*
grant usage on schema public to :WP1_APP_ROLE, :WP1_READONLY_ROLE;

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
    secret_provider,
    secret_reference,
    secret_local_store,
    audit_outbox
to :WP1_APP_ROLE;

grant select on rbac_permission to :WP1_APP_ROLE;

-- Audit logs are append-only for the application role.
grant select, insert on audit_log to :WP1_APP_ROLE;
revoke update, delete, truncate on audit_log from :WP1_APP_ROLE;

-- Secret ciphertext rows are write/read only through WP1 service code. No public
-- grants should be added. Sensitive material remains encrypted and never stores
-- plaintext values.
revoke all on secret_local_store from public;

grant select on all tables in schema public to :WP1_READONLY_ROLE;
revoke insert, update, delete, truncate on all tables in schema public from :WP1_READONLY_ROLE;
revoke select on secret_local_store from :WP1_READONLY_ROLE;

grant all privileges on all tables in schema public to :WP1_MIGRATION_ROLE;
grant all privileges on all sequences in schema public to :WP1_MIGRATION_ROLE;
*/

comment on table audit_log is 'WP1 append-only audit log. Runtime application role may insert/select only; update/delete/truncate must remain reserved for break-glass maintenance.';
comment on table audit_outbox is 'WP1 audit compensation outbox. Application role may update processing state; payload must not contain sensitive plaintext.';
comment on table secret_local_store is 'LOCAL_ENCRYPTED ciphertext store. Contains encrypted material only: cipher_text, iv, auth_tag, algorithm, and master key version.';
comment on column secret_provider.config_json is 'Provider configuration must contain non-sensitive metadata only. Credentials are supplied by environment variables or external KMS/Vault.';
comment on column secret_local_store.cipher_text is 'Encrypted secret value. Plaintext secret values must never be stored in WP1 tables.';

-- Suggested operational guardrails:
-- 1. Use a dedicated migration role for DDL and seed scripts.
-- 2. Use a dedicated runtime application role with no DDL privileges.
-- 3. Keep audit_log append-only for runtime; any correction should be a new audit event.
-- 4. Run destructive maintenance only through audited DBA break-glass procedures.
-- 5. Keep authorization checks in WP1 APIs; MVP P0 no longer creates instance-specific database policies.
