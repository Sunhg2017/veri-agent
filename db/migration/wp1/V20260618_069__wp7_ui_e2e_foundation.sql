-- WP7 UI/E2E control-plane foundation.
-- Stores scene, bundle, run, artifact and flaky metadata only; credentials, raw DOM, cookies and raw artifacts are not persisted.

create table if not exists ui_e2e_scene (
    id uuid primary key default gen_random_uuid(),
    project_id varchar(64) not null,
    application_id varchar(64),
    environment_id varchar(64),
    code varchar(128) not null,
    name varchar(128) not null,
    status varchar(32) not null default 'DRAFT',
    risk_level varchar(32) not null default 'MEDIUM',
    source_summary_json jsonb not null default '{}'::jsonb,
    tags_json jsonb not null default '[]'::jsonb,
    created_by varchar(128),
    updated_by varchar(128),
    archived_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint ck_ui_e2e_scene_status check (
        status in ('DRAFT','REVIEWING','APPROVED','DISABLED','ARCHIVED')
    ),
    constraint ck_ui_e2e_scene_risk_level check (
        risk_level in ('LOW','MEDIUM','HIGH','CRITICAL')
    ),
    constraint ck_ui_e2e_scene_code check (code ~ '^[A-Za-z0-9_-]{1,128}$'),
    constraint ck_ui_e2e_scene_source_summary_json check (
        jsonb_typeof(source_summary_json) = 'object'
    ),
    constraint ck_ui_e2e_scene_tags_json check (jsonb_typeof(tags_json) = 'array'),
    constraint uk_ui_e2e_scene_project_code unique (project_id, code)
);

create table if not exists ui_e2e_scene_step (
    id uuid primary key default gen_random_uuid(),
    scene_id uuid not null references ui_e2e_scene(id) on delete cascade,
    project_id varchar(64) not null,
    step_order int not null,
    step_type varchar(32) not null,
    action_summary_json jsonb not null default '{}'::jsonb,
    locator_strategy_json jsonb not null default '{}'::jsonb,
    assertion_summary_json jsonb not null default '{}'::jsonb,
    wait_policy_json jsonb not null default '{}'::jsonb,
    created_by varchar(128),
    updated_by varchar(128),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint ck_ui_e2e_scene_step_order check (step_order >= 1),
    constraint ck_ui_e2e_scene_step_type check (
        step_type in ('LOGIN','NAVIGATE','QUERY','FORM_FILL','CLICK','ASSERT','WAIT','APPROVAL','EXPORT','CUSTOM')
    ),
    constraint ck_ui_e2e_scene_step_json check (
        jsonb_typeof(action_summary_json) = 'object'
        and jsonb_typeof(locator_strategy_json) = 'object'
        and jsonb_typeof(assertion_summary_json) = 'object'
        and jsonb_typeof(wait_policy_json) = 'object'
    ),
    constraint uk_ui_e2e_scene_step_scene_order unique (scene_id, step_order)
);

create table if not exists ui_e2e_bundle (
    id uuid primary key default gen_random_uuid(),
    scene_id uuid not null references ui_e2e_scene(id) on delete cascade,
    project_id varchar(64) not null,
    status varchar(32) not null default 'DRAFT',
    bundle_digest varchar(64) not null,
    spec_summary_json jsonb not null default '{}'::jsonb,
    fixture_summary_json jsonb not null default '{}'::jsonb,
    static_check_summary_json jsonb not null default '{}'::jsonb,
    submitted_by varchar(128),
    approved_by varchar(128),
    submitted_at timestamptz,
    approved_at timestamptz,
    rejected_at timestamptz,
    created_by varchar(128),
    updated_by varchar(128),
    archived_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint ck_ui_e2e_bundle_status check (
        status in ('DRAFT','STATIC_CHECK_FAILED','REVIEWING','APPROVED','REJECTED','ARCHIVED')
    ),
    constraint ck_ui_e2e_bundle_digest check (bundle_digest ~ '^[0-9a-f]{64}$'),
    constraint ck_ui_e2e_bundle_json check (
        jsonb_typeof(spec_summary_json) = 'object'
        and jsonb_typeof(fixture_summary_json) = 'object'
        and jsonb_typeof(static_check_summary_json) = 'object'
    )
);

create table if not exists ui_e2e_bundle_review (
    id uuid primary key default gen_random_uuid(),
    bundle_id uuid not null references ui_e2e_bundle(id) on delete cascade,
    project_id varchar(64) not null,
    review_status varchar(32) not null default 'SUBMITTED',
    review_comment varchar(1000),
    reviewed_by varchar(128),
    reviewed_at timestamptz,
    created_by varchar(128),
    updated_by varchar(128),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint ck_ui_e2e_bundle_review_status check (
        review_status in ('SUBMITTED','APPROVED','REJECTED')
    )
);

create table if not exists ui_e2e_run (
    id uuid primary key default gen_random_uuid(),
    scene_id uuid not null references ui_e2e_scene(id) on delete restrict,
    bundle_id uuid not null references ui_e2e_bundle(id) on delete restrict,
    project_id varchar(64) not null,
    status varchar(32) not null default 'QUEUED',
    request_key varchar(128),
    runner_mode varchar(32) not null default 'DISABLED',
    base_url_digest varchar(64),
    account_lease_ref varchar(128) not null,
    account_summary_json jsonb not null default '{}'::jsonb,
    failure_code varchar(64),
    failure_summary varchar(512),
    trace_id varchar(64),
    created_by varchar(128),
    started_at timestamptz,
    finished_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint ck_ui_e2e_run_status check (
        status in ('QUEUED','RUNNING','SUCCEEDED','FAILED','TIMEOUT','CANCELED','BLOCKED')
    ),
    constraint ck_ui_e2e_run_request_key check (
        request_key is null or request_key ~ '^[A-Za-z0-9_.:-]{1,128}$'
    ),
    constraint ck_ui_e2e_run_runner_mode check (
        runner_mode in ('DISABLED','MANAGED','HTTP_ADAPTER')
    ),
    constraint ck_ui_e2e_run_base_url_digest check (
        base_url_digest is null or base_url_digest ~ '^[0-9a-f]{64}$'
    ),
    constraint ck_ui_e2e_run_account_lease_ref check (
        account_lease_ref ~ '^[A-Za-z0-9_.:-]{1,128}$'
    ),
    constraint ck_ui_e2e_run_account_summary_json check (
        jsonb_typeof(account_summary_json) = 'object'
    )
);

create table if not exists ui_e2e_run_step_result (
    id uuid primary key default gen_random_uuid(),
    run_id uuid not null references ui_e2e_run(id) on delete cascade,
    scene_step_id uuid references ui_e2e_scene_step(id) on delete set null,
    step_order int not null,
    status varchar(32) not null default 'PENDING',
    duration_ms int not null default 0,
    failure_bucket varchar(64),
    error_code varchar(64),
    summary_json jsonb not null default '{}'::jsonb,
    created_by varchar(128),
    updated_by varchar(128),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint ck_ui_e2e_run_step_result_status check (
        status in ('PENDING','RUNNING','SUCCEEDED','FAILED','SKIPPED','BLOCKED','TIMEOUT','CANCELED')
    ),
    constraint ck_ui_e2e_run_step_result_order check (step_order >= 1),
    constraint ck_ui_e2e_run_step_result_duration check (duration_ms >= 0),
    constraint ck_ui_e2e_run_step_result_failure_bucket check (
        failure_bucket is null
        or failure_bucket in ('LOCATOR','AUTHORIZATION','ENVIRONMENT_TIMEOUT','ACCOUNT','TEST_DATA','RUNNER','ASSERTION','UNKNOWN')
    ),
    constraint ck_ui_e2e_run_step_result_summary_json check (
        jsonb_typeof(summary_json) = 'object'
    ),
    constraint uk_ui_e2e_run_step_result_run_order unique (run_id, step_order)
);

create table if not exists ui_e2e_artifact_manifest (
    id uuid primary key default gen_random_uuid(),
    run_id uuid not null references ui_e2e_run(id) on delete cascade,
    artifact_type varchar(32) not null,
    storage_ref varchar(512),
    artifact_digest varchar(64),
    size_bytes bigint not null default 0,
    redaction_flags_json jsonb not null default '{}'::jsonb,
    capture_status varchar(32) not null default 'PENDING',
    created_by varchar(128),
    updated_by varchar(128),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint ck_ui_e2e_artifact_manifest_type check (
        artifact_type in ('SCREENSHOT','VIDEO','TRACE','LOG','HAR','JUNIT_XML')
    ),
    constraint ck_ui_e2e_artifact_manifest_digest check (
        artifact_digest is null or artifact_digest ~ '^[0-9a-f]{64}$'
    ),
    constraint ck_ui_e2e_artifact_manifest_size_bytes check (size_bytes >= 0),
    constraint ck_ui_e2e_artifact_manifest_redaction_flags_json check (
        jsonb_typeof(redaction_flags_json) = 'object'
    ),
    constraint ck_ui_e2e_artifact_manifest_capture_status check (
        capture_status in ('PENDING','CAPTURED','REDACTED','BLOCKED','FAILED','SKIPPED')
    ),
    constraint ck_ui_e2e_artifact_manifest_storage check (
        capture_status <> 'CAPTURED'
        or (storage_ref is not null and artifact_digest is not null)
    )
);

create table if not exists ui_e2e_flaky_mark (
    id uuid primary key default gen_random_uuid(),
    project_id varchar(64) not null,
    scene_id uuid references ui_e2e_scene(id) on delete cascade,
    run_id uuid references ui_e2e_run(id) on delete set null,
    status varchar(32) not null default 'FLAKY_CANDIDATE',
    reason_code varchar(64),
    reason_summary varchar(512),
    created_by varchar(128),
    updated_by varchar(128),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint ck_ui_e2e_flaky_mark_status check (
        status in ('NONE','FLAKY_CANDIDATE','CONFIRMED_FLAKY','WAIVED')
    ),
    constraint ck_ui_e2e_flaky_mark_ref check (scene_id is not null or run_id is not null),
    constraint ck_ui_e2e_flaky_mark_reason_code check (
        reason_code is null or reason_code ~ '^[A-Z0-9_]{1,64}$'
    )
);

create index if not exists idx_ui_e2e_scene_project_status
    on ui_e2e_scene (project_id, status, updated_at desc);
create index if not exists idx_ui_e2e_scene_project_app_env
    on ui_e2e_scene (project_id, application_id, environment_id, updated_at desc)
    where application_id is not null or environment_id is not null;

create index if not exists idx_ui_e2e_scene_step_scene_order
    on ui_e2e_scene_step (scene_id, step_order);
create index if not exists idx_ui_e2e_scene_step_project_type
    on ui_e2e_scene_step (project_id, step_type);

create unique index if not exists uk_ui_e2e_bundle_scene_digest
    on ui_e2e_bundle (scene_id, bundle_digest);
create index if not exists idx_ui_e2e_bundle_scene_status
    on ui_e2e_bundle (scene_id, status, updated_at desc);
create index if not exists idx_ui_e2e_bundle_digest
    on ui_e2e_bundle (bundle_digest);

create index if not exists idx_ui_e2e_bundle_review_bundle_status
    on ui_e2e_bundle_review (bundle_id, review_status, reviewed_at desc);
create index if not exists idx_ui_e2e_bundle_review_project_reviewed
    on ui_e2e_bundle_review (project_id, reviewed_at desc);

create unique index if not exists uk_ui_e2e_run_project_scene_request_key
    on ui_e2e_run (project_id, scene_id, request_key)
    where request_key is not null;
create index if not exists idx_ui_e2e_run_project_status
    on ui_e2e_run (project_id, status, created_at desc);
create index if not exists idx_ui_e2e_run_scene_created
    on ui_e2e_run (scene_id, created_at desc);
create index if not exists idx_ui_e2e_run_bundle_created
    on ui_e2e_run (bundle_id, created_at desc);
create index if not exists idx_ui_e2e_run_trace
    on ui_e2e_run (trace_id)
    where trace_id is not null;

create index if not exists idx_ui_e2e_run_step_result_run_status
    on ui_e2e_run_step_result (run_id, status, step_order);
create index if not exists idx_ui_e2e_run_step_result_scene_step
    on ui_e2e_run_step_result (scene_step_id)
    where scene_step_id is not null;

create index if not exists idx_ui_e2e_artifact_manifest_run_type
    on ui_e2e_artifact_manifest (run_id, artifact_type, created_at desc);
create index if not exists idx_ui_e2e_artifact_manifest_digest
    on ui_e2e_artifact_manifest (artifact_digest)
    where artifact_digest is not null;

create index if not exists idx_ui_e2e_flaky_mark_project_status
    on ui_e2e_flaky_mark (project_id, status, updated_at desc);
create index if not exists idx_ui_e2e_flaky_mark_scene_status
    on ui_e2e_flaky_mark (scene_id, status, updated_at desc)
    where scene_id is not null;
create index if not exists idx_ui_e2e_flaky_mark_run_status
    on ui_e2e_flaky_mark (run_id, status, updated_at desc)
    where run_id is not null;

insert into rbac_permission (code, resource_type, action, scope_mask, description)
values
    ('uiE2e:read', 'ui_e2e', 'read', 'PLATFORM,PROJECT,APPLICATION,ENVIRONMENT', '查看 WP7 UI/E2E 场景、脚本包、运行和证据摘要'),
    ('uiE2e:manage', 'ui_e2e', 'manage', 'PLATFORM,PROJECT,APPLICATION,ENVIRONMENT', '创建、更新和归档 WP7 UI/E2E 场景与步骤模板'),
    ('uiE2e:review', 'ui_e2e', 'review', 'PLATFORM,PROJECT,APPLICATION,ENVIRONMENT', '提交评审、审批或驳回 WP7 场景和脚本包'),
    ('uiE2e:execute', 'ui_e2e', 'execute', 'PLATFORM,PROJECT,APPLICATION,ENVIRONMENT', '发起或取消 WP7 UI/E2E 运行并查看凭据策略摘要'),
    ('uiE2e:export', 'ui_e2e', 'export', 'PLATFORM,PROJECT,APPLICATION', '导出 WP7 UI/E2E 场景、运行和证据脱敏摘要'),
    ('uiE2e:flaky', 'ui_e2e', 'flaky', 'PLATFORM,PROJECT,APPLICATION,ENVIRONMENT', '标记和治理 WP7 UI/E2E Flaky 状态')
on conflict (code) do update set
    resource_type = excluded.resource_type,
    action = excluded.action,
    scope_mask = excluded.scope_mask,
    description = excluded.description,
    status = 'ENABLED',
    updated_at = now();

with role_permissions(role_code, permission_code) as (
    values
        ('SuperAdmin', 'uiE2e:read'),
        ('SuperAdmin', 'uiE2e:manage'),
        ('SuperAdmin', 'uiE2e:review'),
        ('SuperAdmin', 'uiE2e:execute'),
        ('SuperAdmin', 'uiE2e:export'),
        ('SuperAdmin', 'uiE2e:flaky'),
        ('PlatformAdmin', 'uiE2e:read'),
        ('PlatformAdmin', 'uiE2e:manage'),
        ('PlatformAdmin', 'uiE2e:review'),
        ('PlatformAdmin', 'uiE2e:execute'),
        ('PlatformAdmin', 'uiE2e:export'),
        ('PlatformAdmin', 'uiE2e:flaky'),
        ('ProjectOwner', 'uiE2e:read'),
        ('ProjectOwner', 'uiE2e:manage'),
        ('ProjectOwner', 'uiE2e:review'),
        ('ProjectOwner', 'uiE2e:execute'),
        ('ProjectOwner', 'uiE2e:export'),
        ('ProjectOwner', 'uiE2e:flaky'),
        ('AppOwner', 'uiE2e:read'),
        ('AppOwner', 'uiE2e:manage'),
        ('AppOwner', 'uiE2e:review'),
        ('AppOwner', 'uiE2e:execute'),
        ('AppOwner', 'uiE2e:export'),
        ('AppOwner', 'uiE2e:flaky'),
        ('Tester', 'uiE2e:read'),
        ('Tester', 'uiE2e:manage'),
        ('Tester', 'uiE2e:execute'),
        ('Tester', 'uiE2e:flaky'),
        ('Developer', 'uiE2e:read'),
        ('Auditor', 'uiE2e:read'),
        ('Auditor', 'uiE2e:export')
)
insert into rbac_role_permission (
    role_id,
    permission_id,
    created_at
)
select
    r.id,
    p.id,
    now()
from role_permissions rp
join rbac_role r on r.code = rp.role_code and r.deleted_at is null
join rbac_permission p on p.code = rp.permission_code and p.status = 'ENABLED'
where not exists (
    select 1
    from rbac_role_permission existing
    where existing.role_id = r.id
      and existing.permission_id = p.id
      and existing.deleted_at is null
);

with configs(config_key, value_json) as (
    values
        ('ui_e2e.audit_events', '[
            "ui_e2e.scene.created",
            "ui_e2e.scene.updated",
            "ui_e2e.scene.archived",
            "ui_e2e.bundle.created",
            "ui_e2e.bundle.reviewed",
            "ui_e2e.run.created",
            "ui_e2e.run.started",
            "ui_e2e.run.completed",
            "ui_e2e.run.canceled",
            "ui_e2e.run.exported",
            "ui_e2e.flaky.marked"
        ]'::jsonb),
        ('ui_e2e.enabled', 'true'::jsonb),
        ('ui_e2e.runner_enabled', 'false'::jsonb),
        ('ui_e2e.export_enabled', 'true'::jsonb)
)
insert into base_config (scope_type, scope_id, config_key, value_kind, value_json, status)
select 'SYSTEM', null, c.config_key, 'PLAIN', c.value_json, 'ENABLED'
from configs c
where not exists (
    select 1
    from base_config bc
    where bc.scope_type = 'SYSTEM'
      and bc.scope_id is null
      and bc.config_key = c.config_key
      and bc.deleted_at is null
);

do $$
begin
    if to_regrole('wp1_app') is not null then
        grant select, insert, update on
            ui_e2e_scene,
            ui_e2e_scene_step,
            ui_e2e_bundle,
            ui_e2e_bundle_review,
            ui_e2e_run,
            ui_e2e_run_step_result,
            ui_e2e_artifact_manifest,
            ui_e2e_flaky_mark
        to wp1_app;
    end if;

    if to_regrole('wp1_readonly') is not null then
        grant select on
            ui_e2e_scene,
            ui_e2e_scene_step,
            ui_e2e_bundle,
            ui_e2e_bundle_review,
            ui_e2e_run,
            ui_e2e_run_step_result,
            ui_e2e_artifact_manifest,
            ui_e2e_flaky_mark
        to wp1_readonly;
    end if;

    if to_regrole('wp1_migration') is not null then
        grant all privileges on
            ui_e2e_scene,
            ui_e2e_scene_step,
            ui_e2e_bundle,
            ui_e2e_bundle_review,
            ui_e2e_run,
            ui_e2e_run_step_result,
            ui_e2e_artifact_manifest,
            ui_e2e_flaky_mark
        to wp1_migration;
    end if;
end
$$;

comment on table ui_e2e_scene is 'WP7 UI/E2E scene control-plane metadata.';
comment on column ui_e2e_scene.id is 'UI/E2E scene ID.';
comment on column ui_e2e_scene.project_id is 'Owning project scope ID.';
comment on column ui_e2e_scene.application_id is 'Optional application scope ID.';
comment on column ui_e2e_scene.environment_id is 'Optional environment scope ID.';
comment on column ui_e2e_scene.code is 'Stable scene code within a project.';
comment on column ui_e2e_scene.name is 'Human-readable scene name.';
comment on column ui_e2e_scene.status is 'Scene lifecycle status.';
comment on column ui_e2e_scene.risk_level is 'Declared scene risk level.';
comment on column ui_e2e_scene.source_summary_json is 'Aggregate WP3/WP5 source summary without raw payload.';
comment on column ui_e2e_scene.tags_json is 'Scene tags for search and governance.';
comment on column ui_e2e_scene.created_by is 'Actor that created the scene.';
comment on column ui_e2e_scene.updated_by is 'Actor that last updated the scene.';
comment on column ui_e2e_scene.archived_at is 'Archive timestamp.';
comment on column ui_e2e_scene.created_at is 'Scene creation timestamp.';
comment on column ui_e2e_scene.updated_at is 'Scene update timestamp.';

comment on table ui_e2e_scene_step is 'WP7 structured UI/E2E scene step template.';
comment on column ui_e2e_scene_step.id is 'Scene step ID.';
comment on column ui_e2e_scene_step.scene_id is 'Owning scene ID.';
comment on column ui_e2e_scene_step.project_id is 'Owning project scope ID.';
comment on column ui_e2e_scene_step.step_order is 'Stable execution order within a scene.';
comment on column ui_e2e_scene_step.step_type is 'Structured UI step type.';
comment on column ui_e2e_scene_step.action_summary_json is 'Aggregate action summary without raw script bodies.';
comment on column ui_e2e_scene_step.locator_strategy_json is 'Preferred and fallback locator strategy summary.';
comment on column ui_e2e_scene_step.assertion_summary_json is 'Aggregate assertion summary.';
comment on column ui_e2e_scene_step.wait_policy_json is 'Wait and retry policy summary.';
comment on column ui_e2e_scene_step.created_by is 'Actor that created the scene step.';
comment on column ui_e2e_scene_step.updated_by is 'Actor that last updated the scene step.';
comment on column ui_e2e_scene_step.created_at is 'Scene step creation timestamp.';
comment on column ui_e2e_scene_step.updated_at is 'Scene step update timestamp.';

comment on table ui_e2e_bundle is 'WP7 Playwright bundle aggregate metadata and static-check summary.';
comment on column ui_e2e_bundle.id is 'Bundle ID.';
comment on column ui_e2e_bundle.scene_id is 'Source scene ID.';
comment on column ui_e2e_bundle.project_id is 'Owning project scope ID.';
comment on column ui_e2e_bundle.status is 'Bundle lifecycle and review status.';
comment on column ui_e2e_bundle.bundle_digest is 'SHA-256 digest of normalized bundle metadata.';
comment on column ui_e2e_bundle.spec_summary_json is 'Aggregate bundle spec summary.';
comment on column ui_e2e_bundle.fixture_summary_json is 'Aggregate fixture and dependency summary.';
comment on column ui_e2e_bundle.static_check_summary_json is 'Static check result summary without raw source.';
comment on column ui_e2e_bundle.submitted_by is 'Actor that submitted the bundle for review.';
comment on column ui_e2e_bundle.approved_by is 'Actor that approved the bundle.';
comment on column ui_e2e_bundle.submitted_at is 'Bundle review submission timestamp.';
comment on column ui_e2e_bundle.approved_at is 'Bundle approval timestamp.';
comment on column ui_e2e_bundle.rejected_at is 'Bundle rejection timestamp.';
comment on column ui_e2e_bundle.created_by is 'Actor that created the bundle record.';
comment on column ui_e2e_bundle.updated_by is 'Actor that last updated the bundle record.';
comment on column ui_e2e_bundle.archived_at is 'Archive timestamp.';
comment on column ui_e2e_bundle.created_at is 'Bundle creation timestamp.';
comment on column ui_e2e_bundle.updated_at is 'Bundle update timestamp.';

comment on table ui_e2e_bundle_review is 'WP7 bundle review trail.';
comment on column ui_e2e_bundle_review.id is 'Bundle review record ID.';
comment on column ui_e2e_bundle_review.bundle_id is 'Reviewed bundle ID.';
comment on column ui_e2e_bundle_review.project_id is 'Owning project scope ID.';
comment on column ui_e2e_bundle_review.review_status is 'Review action status.';
comment on column ui_e2e_bundle_review.review_comment is 'Sanitized review comment.';
comment on column ui_e2e_bundle_review.reviewed_by is 'Actor that reviewed the bundle.';
comment on column ui_e2e_bundle_review.reviewed_at is 'Bundle review timestamp.';
comment on column ui_e2e_bundle_review.created_by is 'Actor that created the review record.';
comment on column ui_e2e_bundle_review.updated_by is 'Actor that last updated the review record.';
comment on column ui_e2e_bundle_review.created_at is 'Review record creation timestamp.';
comment on column ui_e2e_bundle_review.updated_at is 'Review record update timestamp.';

comment on table ui_e2e_run is 'WP7 aggregate UI/E2E run metadata.';
comment on column ui_e2e_run.id is 'UI/E2E run ID.';
comment on column ui_e2e_run.scene_id is 'Source scene ID.';
comment on column ui_e2e_run.bundle_id is 'Source bundle ID.';
comment on column ui_e2e_run.project_id is 'Owning project scope ID.';
comment on column ui_e2e_run.status is 'Run lifecycle status.';
comment on column ui_e2e_run.request_key is 'Manual idempotency key.';
comment on column ui_e2e_run.runner_mode is 'Runner mode selected for this run.';
comment on column ui_e2e_run.base_url_digest is 'SHA-256 digest of resolved base URL metadata.';
comment on column ui_e2e_run.account_lease_ref is 'Stable WP8 account lease reference only; token/plaintext is not stored.';
comment on column ui_e2e_run.account_summary_json is 'Aggregate account summary with secretRef digest only.';
comment on column ui_e2e_run.failure_code is 'Stable sanitized run failure code.';
comment on column ui_e2e_run.failure_summary is 'Bounded sanitized run failure summary.';
comment on column ui_e2e_run.trace_id is 'Request trace ID for audit correlation.';
comment on column ui_e2e_run.created_by is 'Actor that created the run.';
comment on column ui_e2e_run.started_at is 'Run start timestamp.';
comment on column ui_e2e_run.finished_at is 'Run finish timestamp.';
comment on column ui_e2e_run.created_at is 'Run creation timestamp.';
comment on column ui_e2e_run.updated_at is 'Run update timestamp.';

comment on table ui_e2e_run_step_result is 'WP7 step-level run result summary.';
comment on column ui_e2e_run_step_result.id is 'Run step result ID.';
comment on column ui_e2e_run_step_result.run_id is 'Owning run ID.';
comment on column ui_e2e_run_step_result.scene_step_id is 'Source scene step ID when still available.';
comment on column ui_e2e_run_step_result.step_order is 'Step order within the run.';
comment on column ui_e2e_run_step_result.status is 'Step execution status.';
comment on column ui_e2e_run_step_result.duration_ms is 'Step duration in milliseconds.';
comment on column ui_e2e_run_step_result.failure_bucket is 'Sanitized failure classification bucket.';
comment on column ui_e2e_run_step_result.error_code is 'Stable sanitized step error code.';
comment on column ui_e2e_run_step_result.summary_json is 'Aggregate step summary without raw DOM or script output.';
comment on column ui_e2e_run_step_result.created_by is 'Actor or worker that created the step result.';
comment on column ui_e2e_run_step_result.updated_by is 'Actor or worker that last updated the step result.';
comment on column ui_e2e_run_step_result.created_at is 'Step result creation timestamp.';
comment on column ui_e2e_run_step_result.updated_at is 'Step result update timestamp.';

comment on table ui_e2e_artifact_manifest is 'WP7 aggregate-only artifact manifest.';
comment on column ui_e2e_artifact_manifest.id is 'Artifact manifest ID.';
comment on column ui_e2e_artifact_manifest.run_id is 'Owning run ID.';
comment on column ui_e2e_artifact_manifest.artifact_type is 'Artifact type such as screenshot, trace or log.';
comment on column ui_e2e_artifact_manifest.storage_ref is 'Opaque storage reference without storage credentials.';
comment on column ui_e2e_artifact_manifest.artifact_digest is 'SHA-256 digest of sanitized artifact metadata or object content.';
comment on column ui_e2e_artifact_manifest.size_bytes is 'Artifact object size in bytes when available.';
comment on column ui_e2e_artifact_manifest.redaction_flags_json is 'Redaction and sensitive scan flags.';
comment on column ui_e2e_artifact_manifest.capture_status is 'Artifact capture status.';
comment on column ui_e2e_artifact_manifest.created_by is 'Actor or worker that created the artifact manifest.';
comment on column ui_e2e_artifact_manifest.updated_by is 'Actor or worker that last updated the artifact manifest.';
comment on column ui_e2e_artifact_manifest.created_at is 'Artifact manifest creation timestamp.';
comment on column ui_e2e_artifact_manifest.updated_at is 'Artifact manifest update timestamp.';

comment on table ui_e2e_flaky_mark is 'WP7 flaky governance marker.';
comment on column ui_e2e_flaky_mark.id is 'Flaky mark ID.';
comment on column ui_e2e_flaky_mark.project_id is 'Owning project scope ID.';
comment on column ui_e2e_flaky_mark.scene_id is 'Related scene ID when the mark applies to a scene.';
comment on column ui_e2e_flaky_mark.run_id is 'Related run ID when the mark applies to a specific execution.';
comment on column ui_e2e_flaky_mark.status is 'Flaky governance status.';
comment on column ui_e2e_flaky_mark.reason_code is 'Stable flaky reason code.';
comment on column ui_e2e_flaky_mark.reason_summary is 'Sanitized flaky reason summary.';
comment on column ui_e2e_flaky_mark.created_by is 'Actor that created the flaky mark.';
comment on column ui_e2e_flaky_mark.updated_by is 'Actor that last updated the flaky mark.';
comment on column ui_e2e_flaky_mark.created_at is 'Flaky mark creation timestamp.';
comment on column ui_e2e_flaky_mark.updated_at is 'Flaky mark update timestamp.';
