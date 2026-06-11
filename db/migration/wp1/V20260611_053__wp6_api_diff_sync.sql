-- WP6 API diff/sync state.
-- Extends endpoint snapshots with WP3 API asset matching and sync evidence.

alter table api_automation_endpoint_snapshot
    add column if not exists asset_api_id uuid,
    add column if not exists diff_summary_json jsonb not null default '{}'::jsonb,
    add column if not exists last_diff_at timestamptz,
    add column if not exists synced_at timestamptz,
    add column if not exists sync_error_summary text;

do $$
begin
    if not exists (
        select 1
        from pg_constraint
        where conname = 'fk_api_automation_endpoint_asset_api'
          and conrelid = 'api_automation_endpoint_snapshot'::regclass
    ) then
        alter table api_automation_endpoint_snapshot
            add constraint fk_api_automation_endpoint_asset_api
            foreign key (asset_api_id) references asset_api(id) on delete set null;
    end if;
end $$;

do $$
begin
    if not exists (
        select 1
        from pg_constraint
        where conname = 'ck_api_automation_endpoint_diff_summary_object'
          and conrelid = 'api_automation_endpoint_snapshot'::regclass
    ) then
        alter table api_automation_endpoint_snapshot
            add constraint ck_api_automation_endpoint_diff_summary_object
            check (jsonb_typeof(diff_summary_json) = 'object');
    end if;
end $$;

create index if not exists idx_api_automation_endpoint_asset_api
    on api_automation_endpoint_snapshot (asset_api_id)
    where asset_api_id is not null;
create index if not exists idx_api_automation_endpoint_last_diff
    on api_automation_endpoint_snapshot (spec_id, last_diff_at desc);

comment on column api_automation_endpoint_snapshot.asset_api_id is 'Matched or synced WP3 asset_api ID.';
comment on column api_automation_endpoint_snapshot.diff_summary_json is 'Sanitized diff reason and preview payload summary.';
comment on column api_automation_endpoint_snapshot.last_diff_at is 'Last WP3 diff evaluation timestamp.';
comment on column api_automation_endpoint_snapshot.synced_at is 'Last successful WP3 API sync timestamp.';
comment on column api_automation_endpoint_snapshot.sync_error_summary is 'Sanitized latest sync error summary.';
