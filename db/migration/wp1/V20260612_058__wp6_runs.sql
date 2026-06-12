-- WP6 API automation controlled runner runs and case-level result summaries.
-- Raw base URLs, request/response bodies, stdout/stderr and secret values are intentionally not stored.

create table if not exists api_automation_run (
    id uuid primary key default gen_random_uuid(),
    project_id varchar(64) not null,
    bundle_id uuid not null references api_automation_script_bundle(id) on delete restrict,
    environment_id varchar(128),
    base_url_digest varchar(64) not null,
    base_url_host varchar(255) not null,
    status varchar(32) not null default 'QUEUED',
    timeout_seconds int not null,
    case_count int not null default 0,
    trace_id varchar(64),
    runner_mode varchar(32) not null default 'DISABLED',
    error_code varchar(64),
    error_summary varchar(512),
    created_by varchar(128),
    updated_by varchar(128),
    started_at timestamptz,
    completed_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint ck_api_automation_run_status check (
        status in ('BLOCKED','QUEUED','RUNNING','PASSED','FAILED','TIMEOUT','CANCELED')
    ),
    constraint ck_api_automation_run_digest check (base_url_digest ~ '^[0-9a-f]{64}$'),
    constraint ck_api_automation_run_counts check (
        timeout_seconds between 1 and 3600
        and case_count >= 0
    ),
    constraint ck_api_automation_run_runner_mode check (runner_mode in ('DISABLED','NOOP','MANAGED','EXTERNAL')),
    constraint ck_api_automation_run_error_code check (
        error_code is null
        or error_code in (
            'RUNNER_DISABLED',
            'RUNNER_TARGET_BLOCKED',
            'RUNNER_CASE_LIMIT_EXCEEDED',
            'RUNNER_BUNDLE_NOT_APPROVED',
            'RUNNER_CASE_NOT_FOUND',
            'SCRIPT_STATIC_CHECK_FAILED',
            'RUNNER_FAILED',
            'RUNNER_TIMEOUT',
            'RUNNER_CANCELED'
        )
    )
);

create table if not exists api_automation_run_result (
    id uuid primary key default gen_random_uuid(),
    run_id uuid not null references api_automation_run(id) on delete cascade,
    case_id uuid not null references api_automation_case(id) on delete restrict,
    status varchar(32) not null,
    duration_ms int not null default 0,
    assertion_summary_json jsonb not null default '{}'::jsonb,
    error_code varchar(64),
    error_summary varchar(512),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint ck_api_automation_run_result_status check (
        status in ('PASSED','FAILED','SKIPPED','ERROR','TIMEOUT','BLOCKED')
    ),
    constraint ck_api_automation_run_result_duration check (duration_ms >= 0),
    constraint ck_api_automation_run_result_json check (jsonb_typeof(assertion_summary_json) = 'object'),
    constraint uk_api_automation_run_result_case unique (run_id, case_id)
);

create index if not exists idx_api_automation_run_project_status
    on api_automation_run (project_id, status, created_at desc);
create index if not exists idx_api_automation_run_bundle_created
    on api_automation_run (bundle_id, created_at desc);
create index if not exists idx_api_automation_run_trace
    on api_automation_run (trace_id)
    where trace_id is not null;
create index if not exists idx_api_automation_run_base_host
    on api_automation_run (base_url_host, created_at desc);

create index if not exists idx_api_automation_run_result_run
    on api_automation_run_result (run_id, status);
create index if not exists idx_api_automation_run_result_case
    on api_automation_run_result (case_id, created_at desc);

do $$
begin
    if to_regrole('wp1_app') is not null then
        grant select, insert, update on
            api_automation_run,
            api_automation_run_result
        to wp1_app;
    end if;

    if to_regrole('wp1_readonly') is not null then
        grant select on
            api_automation_run,
            api_automation_run_result
        to wp1_readonly;
    end if;

    if to_regrole('wp1_migration') is not null then
        grant all privileges on
            api_automation_run,
            api_automation_run_result
        to wp1_migration;
    end if;
end
$$;

comment on table api_automation_run is 'WP6 controlled API automation runner task metadata.';
comment on column api_automation_run.id is 'Runner run ID.';
comment on column api_automation_run.project_id is 'Owning project scope ID.';
comment on column api_automation_run.bundle_id is 'Approved script bundle ID used for this run.';
comment on column api_automation_run.environment_id is 'Optional environment reference supplied by the caller.';
comment on column api_automation_run.base_url_digest is 'SHA-256 digest of normalized base URL; raw URL is not stored.';
comment on column api_automation_run.base_url_host is 'Normalized host name used for policy review and filtering.';
comment on column api_automation_run.status is 'Run lifecycle status.';
comment on column api_automation_run.timeout_seconds is 'Effective timeout used by the runner.';
comment on column api_automation_run.case_count is 'Number of selected cases.';
comment on column api_automation_run.trace_id is 'Request trace ID for audit correlation.';
comment on column api_automation_run.runner_mode is 'Runner adapter mode: DISABLED, NOOP, MANAGED or EXTERNAL.';
comment on column api_automation_run.error_code is 'Stable sanitized runner error code when blocked or failed.';
comment on column api_automation_run.error_summary is 'Bounded sanitized runner error summary.';
comment on column api_automation_run.created_by is 'Actor that created the run.';
comment on column api_automation_run.updated_by is 'Actor that last updated the run.';
comment on column api_automation_run.started_at is 'Run start timestamp, if execution was attempted.';
comment on column api_automation_run.completed_at is 'Run completion timestamp.';
comment on column api_automation_run.created_at is 'Run creation timestamp.';
comment on column api_automation_run.updated_at is 'Run update timestamp.';

comment on table api_automation_run_result is 'WP6 controlled API automation case-level result summaries.';
comment on column api_automation_run_result.id is 'Run result ID.';
comment on column api_automation_run_result.run_id is 'Owning runner run ID.';
comment on column api_automation_run_result.case_id is 'Automation case ID.';
comment on column api_automation_run_result.status is 'Case-level runner result status.';
comment on column api_automation_run_result.duration_ms is 'Case duration in milliseconds.';
comment on column api_automation_run_result.assertion_summary_json is 'Aggregate assertion result summary; raw request/response is not stored.';
comment on column api_automation_run_result.error_code is 'Stable sanitized result error code.';
comment on column api_automation_run_result.error_summary is 'Bounded sanitized result error summary.';
comment on column api_automation_run_result.created_at is 'Run result creation timestamp.';
comment on column api_automation_run_result.updated_at is 'Run result update timestamp.';
