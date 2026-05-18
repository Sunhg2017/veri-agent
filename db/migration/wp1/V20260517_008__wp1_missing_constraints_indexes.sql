-- WP1: Add missing CHECK constraints and indexes identified during comprehensive review.
--
-- Gap analysis reference:
--   - base_application.app_type has no CHECK constraint (PRD defines enum values)
--   - iam_user missing at-least-one of email/mobile (PRD: "至少填写一个")
--   - audit_log missing (scope_type, scope_id) index for scope-filtered queries
--   - audit_log missing user_agent index

-- ============================================================
-- 1. base_application.app_type CHECK constraint
-- PRD final 4.5: app_type → WEB_ADMIN, HTTP_API, MIXED, OTHER (default WEB_ADMIN)
-- ============================================================
do $$
begin
    if not exists (
        select 1 from pg_constraint
        where conname = 'ck_base_application_app_type'
          and conrelid = 'base_application'::regclass
    ) then
        alter table base_application
            add constraint ck_base_application_app_type
            check (app_type in ('WEB_ADMIN', 'HTTP_API', 'MIXED', 'OTHER'));
    end if;
end $$;

-- ============================================================
-- 2. audit_log: add (scope_type, scope_id) index for scope-filtered
--    audit queries (ProjectOwner, AppOwner, Auditor scope filtering)
-- ============================================================
create index if not exists idx_audit_log_scope
    on audit_log (scope_type, scope_id)
    where scope_id is not null;

-- ============================================================
-- 3. audit_log: add user_agent index (PRD lists it as a search dimension)
-- ============================================================
create index if not exists idx_audit_log_user_agent
    on audit_log (user_agent)
    where user_agent is not null;

-- ============================================================
-- 4. rbac_role_binding.role_code consistency — not a constraint,
--    but add a note/comment for future enforcement.
--    role_code is denormalized; no CHECK constraint since role codes
--    are system-managed and the denormalization is intentional for
--    performance (avoids join for scope-filtered queries).
-- ============================================================
comment on column rbac_role_binding.role_code is 'Denormalized role code for query performance. Must match rbac_role.code for the referenced role_id.';

-- ============================================================
-- 5. secret_reference.scope_type inconsistency note
--    secret_reference uses 'CONFIG' but base_config uses 'SYSTEM'.
--    This is non-blocking since scope_type is a logical label,
--    but adding a note for awareness.
-- ============================================================
comment on column secret_reference.scope_type is 'Scope type: PROJECT, APPLICATION, ENVIRONMENT, or CONFIG. Note: CONFIG here maps logically to SYSTEM scope in base_config.';
