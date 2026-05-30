-- WP5 task-report manifest persistence.
-- Stores aggregate reconciliation metadata only; report rows and row-level identifiers stay out of the database.

create table if not exists test_design_report_manifest (
    id uuid primary key default gen_random_uuid(),
    task_id uuid not null references test_design_task(id) on delete cascade,
    project_id varchar(64) not null,
    schema_version varchar(64) not null,
    field_set_version varchar(64) not null,
    manifest_mode varchar(64) not null,
    row_count_before_manifest bigint not null,
    report_row_count bigint not null,
    aggregate_only boolean not null default true,
    detail_rows_exported boolean not null default false,
    manifest_status varchar(32) not null,
    content_digest varchar(64) not null,
    generated_at timestamptz not null,
    created_at timestamptz not null default now(),
    constraint ck_test_design_report_manifest_row_counts check (
        row_count_before_manifest >= 0
        and report_row_count >= row_count_before_manifest
    ),
    constraint ck_test_design_report_manifest_mode check (manifest_mode in ('AGGREGATE_RECONCILIATION')),
    constraint ck_test_design_report_manifest_status check (manifest_status in ('COMPLETE')),
    constraint ck_test_design_report_manifest_aggregate_only check (
        aggregate_only = true
        and detail_rows_exported = false
    ),
    constraint ck_test_design_report_manifest_digest check (content_digest ~ '^[0-9a-f]{64}$')
);

create unique index if not exists uk_test_design_report_manifest_content_digest
    on test_design_report_manifest (task_id, schema_version, field_set_version, content_digest);

create index if not exists idx_test_design_report_manifest_task_created
    on test_design_report_manifest (task_id, created_at desc);

create index if not exists idx_test_design_report_manifest_project_created
    on test_design_report_manifest (project_id, created_at desc);

comment on table test_design_report_manifest is 'WP5 task-report aggregate manifest records for archive reconciliation; raw report rows and row identifiers are not stored.';
comment on column test_design_report_manifest.id is 'Manifest record ID.';
comment on column test_design_report_manifest.task_id is 'WP5 task ID whose aggregate report was exported.';
comment on column test_design_report_manifest.project_id is 'Owning project ID used for scoped operations queries.';
comment on column test_design_report_manifest.schema_version is 'Task report schema version used by the exported CSV.';
comment on column test_design_report_manifest.field_set_version is 'Aggregate-only field-set version used by the exported CSV.';
comment on column test_design_report_manifest.manifest_mode is 'Manifest reconciliation mode; currently aggregate reconciliation only.';
comment on column test_design_report_manifest.row_count_before_manifest is 'Data row count captured before manifest rows were appended.';
comment on column test_design_report_manifest.report_row_count is 'Data row count after manifest rows were appended.';
comment on column test_design_report_manifest.aggregate_only is 'Whether the exported report is aggregate-only.';
comment on column test_design_report_manifest.detail_rows_exported is 'Whether detail rows were exported; constrained to false for WP5 reports.';
comment on column test_design_report_manifest.manifest_status is 'Manifest completion status for archive reconciliation.';
comment on column test_design_report_manifest.content_digest is 'SHA-256 hex digest of the returned CSV content; raw report content is not stored.';
comment on column test_design_report_manifest.generated_at is 'Timestamp embedded in the exported task report.';
comment on column test_design_report_manifest.created_at is 'Manifest persistence timestamp.';
