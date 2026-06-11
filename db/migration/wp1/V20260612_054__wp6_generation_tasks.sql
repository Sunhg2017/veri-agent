-- WP6 API automation generation task and deterministic fallback case drafts.
-- Stores only aggregate inputs and generated metadata; raw model prompts, request bodies and secrets are excluded.

create table if not exists api_automation_generation_task (
    id uuid primary key default gen_random_uuid(),
    project_id varchar(64) not null,
    spec_id uuid not null references api_automation_spec(id) on delete cascade,
    request_key varchar(128),
    request_digest varchar(64) not null,
    generation_mode varchar(32) not null,
    coverage_types_json jsonb not null default '[]'::jsonb,
    status varchar(32) not null default 'COMPLETED',
    prompt_key varchar(128),
    prompt_version varchar(64),
    model_invocation_id varchar(128),
    fallback_used boolean not null default false,
    api_count int not null default 0,
    case_count int not null default 0,
    input_summary_json jsonb not null default '{}'::jsonb,
    error_summary text,
    created_by varchar(128),
    updated_by varchar(128),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint ck_api_automation_generation_digest check (request_digest ~ '^[0-9a-f]{64}$'),
    constraint ck_api_automation_generation_mode check (
        generation_mode in ('FALLBACK_ONLY','MODEL_WITH_FALLBACK')
    ),
    constraint ck_api_automation_generation_status check (
        status in ('COMPLETED','FAILED','BLOCKED')
    ),
    constraint ck_api_automation_generation_counts check (api_count >= 0 and case_count >= 0),
    constraint ck_api_automation_generation_json check (
        jsonb_typeof(coverage_types_json) = 'array'
        and jsonb_typeof(input_summary_json) = 'object'
    )
);

create table if not exists api_automation_case (
    id uuid primary key default gen_random_uuid(),
    task_id uuid not null references api_automation_generation_task(id) on delete cascade,
    project_id varchar(64) not null,
    spec_id uuid not null references api_automation_spec(id) on delete cascade,
    endpoint_snapshot_id uuid not null references api_automation_endpoint_snapshot(id) on delete cascade,
    asset_api_id uuid references asset_api(id) on delete set null,
    asset_test_case_id uuid references asset_test_case(id) on delete set null,
    title varchar(256) not null,
    http_method varchar(16) not null,
    path varchar(512) not null,
    coverage_type varchar(32) not null,
    expected_status int not null,
    assertion_summary_json jsonb not null default '{}'::jsonb,
    request_template_json jsonb not null default '{}'::jsonb,
    source varchar(32) not null default 'FALLBACK',
    status varchar(32) not null default 'DRAFT',
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint ck_api_automation_case_method check (
        http_method in ('GET','POST','PUT','PATCH','DELETE','HEAD','OPTIONS')
    ),
    constraint ck_api_automation_case_coverage check (
        coverage_type in ('SMOKE','FUNCTIONAL','EXCEPTION')
    ),
    constraint ck_api_automation_case_expected_status check (expected_status between 100 and 599),
    constraint ck_api_automation_case_source check (source in ('FALLBACK','MODEL')),
    constraint ck_api_automation_case_status check (status in ('DRAFT','BLOCKED','ARCHIVED')),
    constraint ck_api_automation_case_json check (
        jsonb_typeof(assertion_summary_json) = 'object'
        and jsonb_typeof(request_template_json) = 'object'
    )
);

create unique index if not exists uk_api_automation_generation_project_digest
    on api_automation_generation_task (project_id, request_digest);
create unique index if not exists uk_api_automation_generation_project_key
    on api_automation_generation_task (project_id, request_key)
    where request_key is not null;
create index if not exists idx_api_automation_generation_spec_created
    on api_automation_generation_task (spec_id, created_at desc);
create index if not exists idx_api_automation_generation_project_status
    on api_automation_generation_task (project_id, status, created_at desc);

create index if not exists idx_api_automation_case_task
    on api_automation_case (task_id, coverage_type, path);
create index if not exists idx_api_automation_case_asset_api
    on api_automation_case (asset_api_id)
    where asset_api_id is not null;
create index if not exists idx_api_automation_case_endpoint
    on api_automation_case (endpoint_snapshot_id);

comment on table api_automation_generation_task is 'WP6 API automation generation task metadata and aggregate-safe input summary.';
comment on column api_automation_generation_task.id is 'Generation task ID.';
comment on column api_automation_generation_task.project_id is 'Owning project scope ID.';
comment on column api_automation_generation_task.spec_id is 'Source OpenAPI spec ID.';
comment on column api_automation_generation_task.request_key is 'Optional caller idempotency key within project scope.';
comment on column api_automation_generation_task.request_digest is 'SHA-256 digest of normalized generation request metadata.';
comment on column api_automation_generation_task.generation_mode is 'Generation mode: FALLBACK_ONLY or MODEL_WITH_FALLBACK.';
comment on column api_automation_generation_task.coverage_types_json is 'Requested coverage type list without raw payload bodies.';
comment on column api_automation_generation_task.status is 'Generation task lifecycle status.';
comment on column api_automation_generation_task.prompt_key is 'WP2 prompt key reference for model-capable generation.';
comment on column api_automation_generation_task.prompt_version is 'Prompt version reference when model generation is used.';
comment on column api_automation_generation_task.model_invocation_id is 'WP2 model invocation ID reference when available.';
comment on column api_automation_generation_task.fallback_used is 'Whether deterministic fallback generated the case drafts.';
comment on column api_automation_generation_task.api_count is 'Number of API endpoints included in generation.';
comment on column api_automation_generation_task.case_count is 'Number of automation case drafts generated.';
comment on column api_automation_generation_task.input_summary_json is 'Aggregate input summary; excludes prompt body, request body, response body and secrets.';
comment on column api_automation_generation_task.error_summary is 'Sanitized generation error summary.';
comment on column api_automation_generation_task.created_by is 'Actor that created the generation task.';
comment on column api_automation_generation_task.updated_by is 'Actor that last updated the generation task.';
comment on column api_automation_generation_task.created_at is 'Generation task creation timestamp.';
comment on column api_automation_generation_task.updated_at is 'Generation task update timestamp.';

comment on table api_automation_case is 'WP6 generated API automation case draft metadata.';
comment on column api_automation_case.id is 'Automation case draft ID.';
comment on column api_automation_case.task_id is 'Owning generation task ID.';
comment on column api_automation_case.project_id is 'Owning project scope ID.';
comment on column api_automation_case.spec_id is 'Source OpenAPI spec ID.';
comment on column api_automation_case.endpoint_snapshot_id is 'Source endpoint snapshot ID.';
comment on column api_automation_case.asset_api_id is 'Matched WP3 API asset ID when available.';
comment on column api_automation_case.asset_test_case_id is 'Optional source WP3 test case reference.';
comment on column api_automation_case.title is 'Generated automation case title.';
comment on column api_automation_case.http_method is 'HTTP method under test.';
comment on column api_automation_case.path is 'API path template under test.';
comment on column api_automation_case.coverage_type is 'Coverage type: SMOKE, FUNCTIONAL or EXCEPTION.';
comment on column api_automation_case.expected_status is 'Expected HTTP status code.';
comment on column api_automation_case.assertion_summary_json is 'Aggregate assertion summary without raw request or response bodies.';
comment on column api_automation_case.request_template_json is 'Sanitized request template metadata; no secret values or body examples.';
comment on column api_automation_case.source is 'Generation source: FALLBACK or MODEL.';
comment on column api_automation_case.status is 'Automation case draft status.';
comment on column api_automation_case.created_at is 'Automation case creation timestamp.';
comment on column api_automation_case.updated_at is 'Automation case update timestamp.';
