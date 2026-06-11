-- WP6 API automation script bundle metadata, static check evidence and review workflow.
-- Stores only bundle summaries and digests; generated source files, request bodies and secret values are not persisted.

create table if not exists api_automation_script_bundle (
    id uuid primary key default gen_random_uuid(),
    project_id varchar(64) not null,
    task_id uuid not null references api_automation_generation_task(id) on delete cascade,
    status varchar(32) not null default 'DRAFT',
    bundle_digest varchar(64) not null,
    file_count int not null default 0,
    file_tree_summary_json jsonb not null default '{}'::jsonb,
    dependency_summary_json jsonb not null default '{}'::jsonb,
    static_check_status varchar(32) not null default 'PENDING',
    static_check_summary_json jsonb not null default '{}'::jsonb,
    review_note varchar(512),
    submitted_by varchar(128),
    approved_by varchar(128),
    submitted_at timestamptz,
    approved_at timestamptz,
    rejected_at timestamptz,
    created_by varchar(128),
    updated_by varchar(128),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint ck_api_automation_script_bundle_status check (
        status in ('DRAFT','REVIEWING','APPROVED','REJECTED','ARCHIVED')
    ),
    constraint ck_api_automation_script_bundle_digest check (bundle_digest ~ '^[0-9a-f]{64}$'),
    constraint ck_api_automation_script_bundle_counts check (file_count >= 0),
    constraint ck_api_automation_script_bundle_static_status check (
        static_check_status in ('PENDING','PASSED','SCRIPT_STATIC_CHECK_FAILED')
    ),
    constraint ck_api_automation_script_bundle_json check (
        jsonb_typeof(file_tree_summary_json) = 'object'
        and jsonb_typeof(dependency_summary_json) = 'object'
        and jsonb_typeof(static_check_summary_json) = 'object'
    )
);

create unique index if not exists uk_api_automation_script_bundle_task_active
    on api_automation_script_bundle (task_id)
    where status <> 'ARCHIVED';
create index if not exists idx_api_automation_script_bundle_project_status
    on api_automation_script_bundle (project_id, status, created_at desc);
create index if not exists idx_api_automation_script_bundle_digest
    on api_automation_script_bundle (bundle_digest);
create index if not exists idx_api_automation_script_bundle_static_check
    on api_automation_script_bundle (static_check_status, created_at desc);

do $$
begin
    if to_regrole('wp1_app') is not null then
        grant select, insert, update on api_automation_script_bundle to wp1_app;
    end if;

    if to_regrole('wp1_readonly') is not null then
        grant select on api_automation_script_bundle to wp1_readonly;
    end if;

    if to_regrole('wp1_migration') is not null then
        grant all privileges on api_automation_script_bundle to wp1_migration;
    end if;
end
$$;

comment on table api_automation_script_bundle is 'WP6 generated Pytest script bundle metadata and review state.';
comment on column api_automation_script_bundle.id is 'Script bundle ID.';
comment on column api_automation_script_bundle.project_id is 'Owning project scope ID.';
comment on column api_automation_script_bundle.task_id is 'Source API automation generation task ID.';
comment on column api_automation_script_bundle.status is 'Review lifecycle status: DRAFT, REVIEWING, APPROVED, REJECTED or ARCHIVED.';
comment on column api_automation_script_bundle.bundle_digest is 'SHA-256 digest of normalized script file summaries and dependency metadata.';
comment on column api_automation_script_bundle.file_count is 'Number of generated files in the bundle summary.';
comment on column api_automation_script_bundle.file_tree_summary_json is 'File tree summary with per-file digests; source content is not persisted.';
comment on column api_automation_script_bundle.dependency_summary_json is 'Dependency metadata such as pytest/httpx version ranges.';
comment on column api_automation_script_bundle.static_check_status is 'Static check status for generated Python templates.';
comment on column api_automation_script_bundle.static_check_summary_json is 'Static check aggregate evidence without source content.';
comment on column api_automation_script_bundle.review_note is 'Bounded human review note; no secrets or runtime values should be entered.';
comment on column api_automation_script_bundle.submitted_by is 'Actor that submitted the bundle for review.';
comment on column api_automation_script_bundle.approved_by is 'Actor that approved the bundle, if approved.';
comment on column api_automation_script_bundle.submitted_at is 'Review submission timestamp.';
comment on column api_automation_script_bundle.approved_at is 'Approval timestamp.';
comment on column api_automation_script_bundle.rejected_at is 'Rejection timestamp.';
comment on column api_automation_script_bundle.created_by is 'Actor that created the bundle.';
comment on column api_automation_script_bundle.updated_by is 'Actor that last updated the bundle.';
comment on column api_automation_script_bundle.created_at is 'Script bundle creation timestamp.';
comment on column api_automation_script_bundle.updated_at is 'Script bundle update timestamp.';
