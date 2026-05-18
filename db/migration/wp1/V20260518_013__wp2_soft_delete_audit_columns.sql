-- WP2: Add soft-delete columns, version (optimistic lock), and audit trail columns
-- to all three WP2 core tables, bringing them into consistency with WP1 conventions.
--
-- WP1 tables consistently use: created_by, updated_by, deleted_by, deleted_at, version
-- WP2 was missing all of these, making it impossible to track who changed what.

-- ============================================================
-- 1. ma_model_provider
-- ============================================================
alter table ma_model_provider
    add column if not exists created_by uuid,
    add column if not exists updated_by uuid,
    add column if not exists deleted_by uuid,
    add column if not exists deleted_at timestamptz,
    add column if not exists version bigint not null default 0;

create index if not exists idx_ma_model_provider_deleted
    on ma_model_provider (deleted_at)
    where deleted_at is not null;

-- ============================================================
-- 2. ma_prompt_template
-- ============================================================
alter table ma_prompt_template
    add column if not exists created_by uuid,
    add column if not exists updated_by uuid,
    add column if not exists deleted_by uuid,
    add column if not exists deleted_at timestamptz,
    add column if not exists version bigint not null default 0;

create index if not exists idx_ma_prompt_template_deleted
    on ma_prompt_template (deleted_at)
    where deleted_at is not null;

-- ============================================================
-- 3. ma_invocation_log
-- ============================================================
-- Invocation logs are append-only, so we only add created_by and version,
-- not soft-delete columns (logs should never be deleted).
alter table ma_invocation_log
    add column if not exists created_by uuid,
    add column if not exists version bigint not null default 0;

-- ============================================================
-- 4. Add gen_random_uuid() defaults where missing
-- ============================================================
-- WP2 tables were created without default UUID generation
-- (unlike WP1 which consistently uses default gen_random_uuid()).
-- We use a DO block to alter the column defaults.

do $$
begin
    -- ma_model_provider
    if not exists (
        select 1
        from information_schema.columns
        where table_name = 'ma_model_provider'
          and column_name = 'id'
          and column_default is not null
    ) then
        alter table ma_model_provider alter column id set default gen_random_uuid();
    end if;

    -- ma_prompt_template
    if not exists (
        select 1
        from information_schema.columns
        where table_name = 'ma_prompt_template'
          and column_name = 'id'
          and column_default is not null
    ) then
        alter table ma_prompt_template alter column id set default gen_random_uuid();
    end if;

    -- ma_invocation_log
    if not exists (
        select 1
        from information_schema.columns
        where table_name = 'ma_invocation_log'
          and column_name = 'id'
          and column_default is not null
    ) then
        alter table ma_invocation_log alter column id set default gen_random_uuid();
    end if;
end $$;
