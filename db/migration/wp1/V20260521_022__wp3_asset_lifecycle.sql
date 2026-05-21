-- WP3 asset lifecycle state.
-- Keeps business status separate from archive/delete state so trace links and audit history stay intact.

alter table asset_requirement
    add column if not exists lifecycle_status varchar(32) not null default 'ACTIVE',
    add column if not exists archived_by uuid,
    add column if not exists archived_at timestamptz;

alter table asset_api
    add column if not exists lifecycle_status varchar(32) not null default 'ACTIVE',
    add column if not exists archived_by uuid,
    add column if not exists archived_at timestamptz;

alter table asset_page
    add column if not exists lifecycle_status varchar(32) not null default 'ACTIVE',
    add column if not exists archived_by uuid,
    add column if not exists archived_at timestamptz;

alter table asset_business_flow
    add column if not exists lifecycle_status varchar(32) not null default 'ACTIVE',
    add column if not exists archived_by uuid,
    add column if not exists archived_at timestamptz;

alter table asset_test_case
    add column if not exists lifecycle_status varchar(32) not null default 'ACTIVE',
    add column if not exists archived_by uuid,
    add column if not exists archived_at timestamptz;

update asset_requirement
set lifecycle_status = 'DELETED'
where deleted_at is not null
  and lifecycle_status <> 'DELETED';

update asset_api
set lifecycle_status = 'DELETED'
where deleted_at is not null
  and lifecycle_status <> 'DELETED';

update asset_page
set lifecycle_status = 'DELETED'
where deleted_at is not null
  and lifecycle_status <> 'DELETED';

update asset_business_flow
set lifecycle_status = 'DELETED'
where deleted_at is not null
  and lifecycle_status <> 'DELETED';

update asset_test_case
set lifecycle_status = 'DELETED'
where deleted_at is not null
  and lifecycle_status <> 'DELETED';

do $$
begin
    if not exists (
        select 1
        from pg_constraint
        where conname = 'ck_asset_requirement_lifecycle_status'
          and conrelid = 'asset_requirement'::regclass
    ) then
        alter table asset_requirement
            add constraint ck_asset_requirement_lifecycle_status
            check (lifecycle_status in ('ACTIVE','ARCHIVED','DELETED'));
    end if;

    if not exists (
        select 1
        from pg_constraint
        where conname = 'ck_asset_api_lifecycle_status'
          and conrelid = 'asset_api'::regclass
    ) then
        alter table asset_api
            add constraint ck_asset_api_lifecycle_status
            check (lifecycle_status in ('ACTIVE','ARCHIVED','DELETED'));
    end if;

    if not exists (
        select 1
        from pg_constraint
        where conname = 'ck_asset_page_lifecycle_status'
          and conrelid = 'asset_page'::regclass
    ) then
        alter table asset_page
            add constraint ck_asset_page_lifecycle_status
            check (lifecycle_status in ('ACTIVE','ARCHIVED','DELETED'));
    end if;

    if not exists (
        select 1
        from pg_constraint
        where conname = 'ck_asset_business_flow_lifecycle_status'
          and conrelid = 'asset_business_flow'::regclass
    ) then
        alter table asset_business_flow
            add constraint ck_asset_business_flow_lifecycle_status
            check (lifecycle_status in ('ACTIVE','ARCHIVED','DELETED'));
    end if;

    if not exists (
        select 1
        from pg_constraint
        where conname = 'ck_asset_test_case_lifecycle_status'
          and conrelid = 'asset_test_case'::regclass
    ) then
        alter table asset_test_case
            add constraint ck_asset_test_case_lifecycle_status
            check (lifecycle_status in ('ACTIVE','ARCHIVED','DELETED'));
    end if;
end
$$;

create index if not exists idx_asset_requirement_project_lifecycle
    on asset_requirement (project_id, lifecycle_status, updated_at desc);
create index if not exists idx_asset_api_project_lifecycle
    on asset_api (project_id, lifecycle_status, updated_at desc);
create index if not exists idx_asset_page_project_lifecycle
    on asset_page (project_id, lifecycle_status, updated_at desc);
create index if not exists idx_asset_business_flow_project_lifecycle
    on asset_business_flow (project_id, lifecycle_status, updated_at desc);
create index if not exists idx_asset_test_case_project_lifecycle
    on asset_test_case (project_id, lifecycle_status, updated_at desc);

do $$
begin
    if exists (
        select 1
        from pg_constraint
        where conname = 'ck_asset_version_history_change_type'
          and conrelid = 'asset_version_history'::regclass
    ) then
        alter table asset_version_history
            drop constraint ck_asset_version_history_change_type;
    end if;

    alter table asset_version_history
        add constraint ck_asset_version_history_change_type
        check (change_type in ('CREATE','UPDATE','UPSERT','STEPS_UPDATE','ARCHIVE','SOFT_DELETE','RESTORE'));
end
$$;

comment on column asset_requirement.lifecycle_status is 'WP3 lifecycle state: ACTIVE, ARCHIVED, or DELETED. Business status remains in status.';
comment on column asset_api.lifecycle_status is 'WP3 lifecycle state: ACTIVE, ARCHIVED, or DELETED. Business status remains in status.';
comment on column asset_page.lifecycle_status is 'WP3 lifecycle state: ACTIVE, ARCHIVED, or DELETED. Business status remains in status.';
comment on column asset_business_flow.lifecycle_status is 'WP3 lifecycle state: ACTIVE, ARCHIVED, or DELETED. Business status remains in status.';
comment on column asset_test_case.lifecycle_status is 'WP3 lifecycle state: ACTIVE, ARCHIVED, or DELETED. Business status remains in status.';
