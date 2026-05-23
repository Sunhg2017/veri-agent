-- WP3 trace, rollback, impact-analysis and prototype-sync hardening.

alter table asset_version_history
    drop constraint if exists ck_asset_version_history_change_type;

alter table asset_version_history
    add constraint ck_asset_version_history_change_type
    check (change_type in ('CREATE','UPDATE','UPSERT','STEPS_UPDATE','ARCHIVE','SOFT_DELETE','RESTORE','ROLLBACK'));

create unique index if not exists uk_asset_page_project_source_ref
    on asset_page (project_id, source, source_ref)
    where deleted_at is null and source_ref is not null;

create index if not exists idx_asset_link_requirement_page
    on asset_link (source_id, target_id)
    where deleted_at is null and source_type = 'REQUIREMENT' and target_type = 'PAGE';

create index if not exists idx_asset_link_requirement_flow
    on asset_link (source_id, target_id)
    where deleted_at is null and source_type = 'REQUIREMENT' and target_type = 'FLOW';

comment on index uk_asset_page_project_source_ref is 'WP3 prototype sync idempotency key for Figma/Lanhu/Axure page assets.';
