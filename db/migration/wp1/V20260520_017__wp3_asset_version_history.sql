-- WP3 asset version history and field diff ledger.
-- Tracks requirement and test case revisions without allowing in-place mutation.

alter table asset_requirement
    add column if not exists version int not null default 1;

alter table asset_test_case
    add column if not exists version int not null default 1;

do $$
begin
    if not exists (
        select 1
        from pg_constraint
        where conname = 'ck_asset_requirement_version_positive'
          and conrelid = 'asset_requirement'::regclass
    ) then
        alter table asset_requirement
            add constraint ck_asset_requirement_version_positive check (version > 0);
    end if;

    if not exists (
        select 1
        from pg_constraint
        where conname = 'ck_asset_test_case_version_positive'
          and conrelid = 'asset_test_case'::regclass
    ) then
        alter table asset_test_case
            add constraint ck_asset_test_case_version_positive check (version > 0);
    end if;
end
$$;

create table if not exists asset_version_history (
    id uuid primary key default gen_random_uuid(),
    project_id varchar(64) not null,
    asset_type varchar(32) not null,
    asset_id uuid not null,
    version int not null,
    change_type varchar(32) not null,
    actor varchar(128) not null,
    changed_fields text not null default '',
    diff_json jsonb not null default '{}',
    snapshot_json jsonb not null default '{}',
    trace_id varchar(64),
    created_at timestamptz not null default now(),
    constraint ck_asset_version_history_asset_type check (asset_type in ('REQUIREMENT','TEST_CASE')),
    constraint ck_asset_version_history_change_type check (change_type in ('CREATE','UPDATE','UPSERT','STEPS_UPDATE')),
    constraint ck_asset_version_history_version_positive check (version > 0)
);

create unique index if not exists uk_asset_version_history_asset_version
    on asset_version_history (asset_type, asset_id, version);
create index if not exists idx_asset_version_history_asset_created
    on asset_version_history (asset_type, asset_id, created_at desc);
create index if not exists idx_asset_version_history_project_created
    on asset_version_history (project_id, created_at desc);
create index if not exists idx_asset_version_history_trace
    on asset_version_history (trace_id) where trace_id is not null;

create or replace function wp3_prevent_asset_version_history_mutation()
returns trigger
language plpgsql
as $$
begin
    raise exception 'asset_version_history is append-only and cannot be %', tg_op
        using errcode = '55000';
end;
$$;

drop trigger if exists trg_asset_version_history_prevent_update_delete on asset_version_history;

create trigger trg_asset_version_history_prevent_update_delete
before update or delete on asset_version_history
for each row
execute function wp3_prevent_asset_version_history_mutation();

comment on table asset_version_history is 'WP3 append-only asset revision ledger for requirements and test cases.';
comment on column asset_version_history.diff_json is 'Changed field diff using API field names; values must not contain secrets.';
comment on column asset_version_history.snapshot_json is 'Full whitelisted asset snapshot for the saved version, including test case steps.';
