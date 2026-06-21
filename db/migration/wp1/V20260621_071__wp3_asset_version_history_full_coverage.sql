-- WP3 asset version history now covers API/Page/BusinessFlow in addition to requirement/test case.

alter table asset_version_history
    drop constraint if exists ck_asset_version_history_asset_type;

alter table asset_version_history
    add constraint ck_asset_version_history_asset_type
    check (asset_type in ('REQUIREMENT', 'API', 'PAGE', 'BUSINESS_FLOW', 'TEST_CASE'));

comment on table asset_version_history is 'WP3 append-only asset revision ledger for requirement, API, page, business flow and test case assets.';
