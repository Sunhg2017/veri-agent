-- WP9 execution orchestration foundation.
-- Stores orchestration metadata, trigger evidence and queue claims; runner output, secrets and raw request/response bodies are not persisted.

create table if not exists execution_plan (
    id uuid primary key default gen_random_uuid(),
    project_id varchar(64) not null,
    name varchar(128) not null,
    status varchar(32) not null default 'DRAFT',
    environment_key varchar(128) not null,
    trigger_policy_json jsonb not null default '{}'::jsonb,
    dag_digest varchar(64) not null,
    description varchar(512),
    created_by varchar(128),
    updated_by varchar(128),
    archived_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint ck_execution_plan_status check (status in ('DRAFT','READY','DISABLED','ARCHIVED')),
    constraint ck_execution_plan_digest check (dag_digest ~ '^[0-9a-f]{64}$'),
    constraint ck_execution_plan_trigger_policy_json check (jsonb_typeof(trigger_policy_json) = 'object')
);

create table if not exists execution_plan_node (
    id uuid primary key default gen_random_uuid(),
    plan_id uuid not null references execution_plan(id) on delete cascade,
    node_key varchar(128) not null,
    node_type varchar(32) not null,
    dependency_keys text[] not null default '{}'::text[],
    input_summary_json jsonb not null default '{}'::jsonb,
    failure_policy varchar(32) not null default 'FAIL_FAST',
    timeout_seconds int not null default 300,
    retry_policy_json jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint ck_execution_plan_node_type check (
        node_type in ('API_TEST','UI_TEST','SETUP','VERIFY','CLEANUP','REPORT_HANDOFF')
    ),
    constraint ck_execution_plan_node_key check (node_key ~ '^[A-Za-z0-9_-]{1,128}$'),
    constraint ck_execution_plan_node_failure_policy check (
        failure_policy in ('FAIL_FAST','CONTINUE','BLOCK_DOWNSTREAM')
    ),
    constraint ck_execution_plan_node_timeout check (timeout_seconds between 1 and 86400),
    constraint ck_execution_plan_node_json check (
        jsonb_typeof(input_summary_json) = 'object'
        and jsonb_typeof(retry_policy_json) = 'object'
    ),
    constraint uk_execution_plan_node_key unique (plan_id, node_key)
);

create table if not exists execution_run (
    id uuid primary key default gen_random_uuid(),
    plan_id uuid not null references execution_plan(id) on delete restrict,
    project_id varchar(64) not null,
    status varchar(32) not null default 'QUEUED',
    trigger_type varchar(32) not null default 'MANUAL',
    request_key varchar(128),
    source_event_id varchar(256),
    attempt int not null default 1,
    trace_id varchar(64),
    result_summary_json jsonb not null default '{}'::jsonb,
    error_code varchar(64),
    error_summary varchar(512),
    created_by varchar(128),
    started_at timestamptz,
    finished_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint ck_execution_run_status check (
        status in ('QUEUED','RUNNING','SUCCEEDED','PARTIAL_SUCCESS','FAILED','CANCELED','TIMEOUT')
    ),
    constraint ck_execution_run_trigger_type check (trigger_type in ('MANUAL','WEBHOOK','CRON','RETRY','RECOVERY')),
    constraint ck_execution_run_attempt check (attempt >= 1),
    constraint ck_execution_run_result_json check (jsonb_typeof(result_summary_json) = 'object')
);

create table if not exists execution_node_run (
    id uuid primary key default gen_random_uuid(),
    run_id uuid not null references execution_run(id) on delete cascade,
    plan_node_id uuid not null references execution_plan_node(id) on delete restrict,
    status varchar(32) not null default 'PENDING',
    attempt int not null default 1,
    runner_type varchar(32) not null default 'CONTROL',
    external_run_id varchar(128),
    error_code varchar(64),
    error_summary varchar(512),
    result_summary_json jsonb not null default '{}'::jsonb,
    heartbeat_at timestamptz,
    queued_at timestamptz,
    started_at timestamptz,
    finished_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint ck_execution_node_run_status check (
        status in ('PENDING','QUEUED','RUNNING','SUCCEEDED','SKIPPED','FAILED','CANCELED','TIMEOUT','BLOCKED')
    ),
    constraint ck_execution_node_run_runner_type check (runner_type in ('CONTROL','WP6_API','WP7_UI','UTILITY','REPORT')),
    constraint ck_execution_node_run_attempt check (attempt >= 1),
    constraint ck_execution_node_run_result_json check (jsonb_typeof(result_summary_json) = 'object'),
    constraint uk_execution_node_run_attempt unique (run_id, plan_node_id, attempt)
);

create table if not exists execution_trigger (
    id uuid primary key default gen_random_uuid(),
    plan_id uuid not null references execution_plan(id) on delete cascade,
    trigger_type varchar(32) not null,
    status varchar(32) not null default 'DISABLED',
    config_digest varchar(64) not null,
    secret_ref_digest varchar(64),
    next_fire_at timestamptz,
    last_fire_at timestamptz,
    created_by varchar(128),
    updated_by varchar(128),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint ck_execution_trigger_type check (trigger_type in ('WEBHOOK','CRON')),
    constraint ck_execution_trigger_status check (status in ('DISABLED','ENABLED','PAUSED')),
    constraint ck_execution_trigger_config_digest check (config_digest ~ '^[0-9a-f]{64}$'),
    constraint ck_execution_trigger_secret_digest check (
        secret_ref_digest is null
        or secret_ref_digest ~ '^[0-9a-f]{64}$'
    )
);

create table if not exists execution_trigger_event (
    id uuid primary key default gen_random_uuid(),
    trigger_id uuid not null references execution_trigger(id) on delete cascade,
    source_event_id varchar(256) not null,
    request_digest varchar(64) not null,
    status varchar(32) not null default 'RECEIVED',
    run_id uuid references execution_run(id) on delete set null,
    received_at timestamptz not null default now(),
    error_code varchar(64),
    error_summary varchar(512),
    trace_id varchar(64),
    constraint ck_execution_trigger_event_status check (
        status in ('RECEIVED','ACCEPTED','REJECTED','DUPLICATE','FAILED')
    ),
    constraint ck_execution_trigger_event_digest check (request_digest ~ '^[0-9a-f]{64}$'),
    constraint uk_execution_trigger_event_source unique (trigger_id, source_event_id)
);

create table if not exists execution_queue_claim (
    id uuid primary key default gen_random_uuid(),
    node_run_id uuid not null references execution_node_run(id) on delete cascade,
    claim_token varchar(128) not null,
    worker_id varchar(128) not null,
    claimed_at timestamptz not null default now(),
    heartbeat_at timestamptz not null default now(),
    expires_at timestamptz not null,
    status varchar(32) not null default 'CLAIMED',
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint ck_execution_queue_claim_status check (status in ('CLAIMED','RELEASED','EXPIRED','COMPLETED')),
    constraint uk_execution_queue_claim_token unique (claim_token)
);

create index if not exists idx_execution_plan_project_status
    on execution_plan (project_id, status, updated_at desc);
create index if not exists idx_execution_plan_dag_digest
    on execution_plan (dag_digest);

create index if not exists idx_execution_plan_node_plan_type
    on execution_plan_node (plan_id, node_type);

create unique index if not exists uk_execution_run_plan_request_key
    on execution_run (plan_id, request_key)
    where request_key is not null;
create index if not exists idx_execution_run_project_status
    on execution_run (project_id, status, created_at desc);
create index if not exists idx_execution_run_plan_created
    on execution_run (plan_id, created_at desc);
create index if not exists idx_execution_run_trace
    on execution_run (trace_id)
    where trace_id is not null;

create index if not exists idx_execution_node_run_run_status
    on execution_node_run (run_id, status);
create index if not exists idx_execution_node_run_heartbeat
    on execution_node_run (status, heartbeat_at)
    where status in ('QUEUED','RUNNING');
create index if not exists idx_execution_node_run_external
    on execution_node_run (runner_type, external_run_id)
    where external_run_id is not null;

create index if not exists idx_execution_trigger_plan_status
    on execution_trigger (plan_id, status);
create index if not exists idx_execution_trigger_next_fire
    on execution_trigger (trigger_type, status, next_fire_at)
    where next_fire_at is not null;

create index if not exists idx_execution_trigger_event_trigger_status
    on execution_trigger_event (trigger_id, status, received_at desc);
create index if not exists idx_execution_trigger_event_run
    on execution_trigger_event (run_id)
    where run_id is not null;

create unique index if not exists uk_execution_queue_claim_active_node
    on execution_queue_claim (node_run_id)
    where status = 'CLAIMED';
create index if not exists idx_execution_queue_claim_status_expires
    on execution_queue_claim (status, expires_at);

insert into rbac_permission (code, resource_type, action, scope_mask, description)
values
    ('execution:read', 'execution', 'read', 'PLATFORM,PROJECT,APPLICATION,ENVIRONMENT', '查看 WP9 执行计划、运行、节点和触发记录'),
    ('execution:manage', 'execution', 'manage', 'PLATFORM,PROJECT,APPLICATION', '创建、编辑、归档执行计划和触发配置'),
    ('execution:trigger', 'execution', 'trigger', 'PLATFORM,PROJECT,APPLICATION,ENVIRONMENT', '手动触发、取消和重试执行'),
    ('execution:admin', 'execution', 'admin', 'PLATFORM', '启停调度、恢复卡死任务和人工重放'),
    ('execution:export', 'execution', 'export', 'PLATFORM,PROJECT,APPLICATION', '导出脱敏执行摘要和审计聚合')
on conflict (code) do update set
    resource_type = excluded.resource_type,
    action = excluded.action,
    scope_mask = excluded.scope_mask,
    description = excluded.description,
    status = 'ENABLED',
    updated_at = now();

with role_permissions(role_code, permission_code) as (
    values
        ('SuperAdmin', 'execution:read'),
        ('SuperAdmin', 'execution:manage'),
        ('SuperAdmin', 'execution:trigger'),
        ('SuperAdmin', 'execution:admin'),
        ('SuperAdmin', 'execution:export'),
        ('PlatformAdmin', 'execution:read'),
        ('PlatformAdmin', 'execution:manage'),
        ('PlatformAdmin', 'execution:trigger'),
        ('PlatformAdmin', 'execution:admin'),
        ('PlatformAdmin', 'execution:export'),
        ('ProjectOwner', 'execution:read'),
        ('ProjectOwner', 'execution:manage'),
        ('ProjectOwner', 'execution:trigger'),
        ('ProjectOwner', 'execution:export'),
        ('AppOwner', 'execution:read'),
        ('AppOwner', 'execution:manage'),
        ('AppOwner', 'execution:trigger'),
        ('Tester', 'execution:read'),
        ('Tester', 'execution:trigger'),
        ('Auditor', 'execution:read'),
        ('Auditor', 'execution:export')
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
        ('execution.audit_events', '[
            "execution.plan.created",
            "execution.plan.updated",
            "execution.plan.archived",
            "execution.run.created",
            "execution.run.started",
            "execution.run.completed",
            "execution.run.canceled",
            "execution.run.retried",
            "execution.trigger.created",
            "execution.trigger.fired",
            "execution.exported"
        ]'::jsonb),
        ('execution.scheduler_enabled', 'false'::jsonb),
        ('execution.webhook_enabled', 'false'::jsonb),
        ('execution.cron_enabled', 'false'::jsonb)
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
            execution_plan,
            execution_plan_node,
            execution_run,
            execution_node_run,
            execution_trigger,
            execution_trigger_event,
            execution_queue_claim
        to wp1_app;
    end if;

    if to_regrole('wp1_readonly') is not null then
        grant select on
            execution_plan,
            execution_plan_node,
            execution_run,
            execution_node_run,
            execution_trigger,
            execution_trigger_event,
            execution_queue_claim
        to wp1_readonly;
    end if;

    if to_regrole('wp1_migration') is not null then
        grant all privileges on
            execution_plan,
            execution_plan_node,
            execution_run,
            execution_node_run,
            execution_trigger,
            execution_trigger_event,
            execution_queue_claim
        to wp1_migration;
    end if;
end
$$;

comment on table execution_plan is 'WP9 execution orchestration plan metadata.';
comment on column execution_plan.id is 'Execution plan ID.';
comment on column execution_plan.project_id is 'Owning project scope ID.';
comment on column execution_plan.name is 'Human-readable execution plan name.';
comment on column execution_plan.status is 'Plan lifecycle status: DRAFT, READY, DISABLED or ARCHIVED.';
comment on column execution_plan.environment_key is 'Project environment key selected for execution.';
comment on column execution_plan.trigger_policy_json is 'Trigger policy summary; secret values are not stored.';
comment on column execution_plan.dag_digest is 'SHA-256 digest of normalized DAG definition.';
comment on column execution_plan.description is 'Bounded plan description.';
comment on column execution_plan.created_by is 'Actor that created the plan.';
comment on column execution_plan.updated_by is 'Actor that last updated the plan.';
comment on column execution_plan.archived_at is 'Archive timestamp.';
comment on column execution_plan.created_at is 'Plan creation timestamp.';
comment on column execution_plan.updated_at is 'Plan update timestamp.';

comment on table execution_plan_node is 'WP9 execution plan DAG node definition.';
comment on column execution_plan_node.id is 'Plan node ID.';
comment on column execution_plan_node.plan_id is 'Owning execution plan ID.';
comment on column execution_plan_node.node_key is 'Stable DAG node key within a plan.';
comment on column execution_plan_node.node_type is 'Node type such as API_TEST or REPORT_HANDOFF.';
comment on column execution_plan_node.dependency_keys is 'Upstream dependency node keys.';
comment on column execution_plan_node.input_summary_json is 'Aggregate node input summary without secrets or raw runner payload.';
comment on column execution_plan_node.failure_policy is 'Failure handling policy.';
comment on column execution_plan_node.timeout_seconds is 'Node timeout in seconds.';
comment on column execution_plan_node.retry_policy_json is 'Retry policy summary.';
comment on column execution_plan_node.created_at is 'Node creation timestamp.';
comment on column execution_plan_node.updated_at is 'Node update timestamp.';

comment on table execution_run is 'WP9 execution run aggregate metadata.';
comment on column execution_run.id is 'Execution run ID.';
comment on column execution_run.plan_id is 'Source execution plan ID.';
comment on column execution_run.project_id is 'Owning project scope ID.';
comment on column execution_run.status is 'Run lifecycle status.';
comment on column execution_run.trigger_type is 'Run trigger source.';
comment on column execution_run.request_key is 'Manual idempotency key.';
comment on column execution_run.source_event_id is 'External trigger source event ID.';
comment on column execution_run.attempt is 'Run attempt number.';
comment on column execution_run.trace_id is 'Request trace ID for audit correlation.';
comment on column execution_run.result_summary_json is 'Aggregate execution summary without raw evidence.';
comment on column execution_run.error_code is 'Stable sanitized run error code.';
comment on column execution_run.error_summary is 'Bounded sanitized run error summary.';
comment on column execution_run.created_by is 'Actor or trigger that created the run.';
comment on column execution_run.started_at is 'Run start timestamp.';
comment on column execution_run.finished_at is 'Run finish timestamp.';
comment on column execution_run.created_at is 'Run creation timestamp.';
comment on column execution_run.updated_at is 'Run update timestamp.';

comment on table execution_node_run is 'WP9 node-level execution state and sanitized result summary.';
comment on column execution_node_run.id is 'Node run ID.';
comment on column execution_node_run.run_id is 'Owning execution run ID.';
comment on column execution_node_run.plan_node_id is 'Source plan node ID.';
comment on column execution_node_run.status is 'Node run lifecycle status.';
comment on column execution_node_run.attempt is 'Node attempt number.';
comment on column execution_node_run.runner_type is 'Runner integration type.';
comment on column execution_node_run.external_run_id is 'External run reference such as WP6 run ID.';
comment on column execution_node_run.error_code is 'Stable sanitized node error code.';
comment on column execution_node_run.error_summary is 'Bounded sanitized node error summary.';
comment on column execution_node_run.result_summary_json is 'Aggregate node result summary without raw runner output.';
comment on column execution_node_run.heartbeat_at is 'Last runner heartbeat timestamp.';
comment on column execution_node_run.queued_at is 'Node queued timestamp.';
comment on column execution_node_run.started_at is 'Node start timestamp.';
comment on column execution_node_run.finished_at is 'Node finish timestamp.';
comment on column execution_node_run.created_at is 'Node run creation timestamp.';
comment on column execution_node_run.updated_at is 'Node run update timestamp.';

comment on table execution_trigger is 'WP9 webhook and cron trigger configuration summary.';
comment on column execution_trigger.id is 'Trigger ID.';
comment on column execution_trigger.plan_id is 'Owning execution plan ID.';
comment on column execution_trigger.trigger_type is 'Trigger type WEBHOOK or CRON.';
comment on column execution_trigger.status is 'Trigger status.';
comment on column execution_trigger.config_digest is 'SHA-256 digest of normalized trigger config.';
comment on column execution_trigger.secret_ref_digest is 'Digest of secret reference metadata; no secret value is stored.';
comment on column execution_trigger.next_fire_at is 'Next cron fire timestamp when available.';
comment on column execution_trigger.last_fire_at is 'Last trigger fire timestamp.';
comment on column execution_trigger.created_by is 'Actor that created the trigger.';
comment on column execution_trigger.updated_by is 'Actor that last updated the trigger.';
comment on column execution_trigger.created_at is 'Trigger creation timestamp.';
comment on column execution_trigger.updated_at is 'Trigger update timestamp.';

comment on table execution_trigger_event is 'WP9 trigger ingress event and idempotency evidence.';
comment on column execution_trigger_event.id is 'Trigger event ID.';
comment on column execution_trigger_event.trigger_id is 'Owning trigger ID.';
comment on column execution_trigger_event.source_event_id is 'External source event ID used for idempotency.';
comment on column execution_trigger_event.request_digest is 'SHA-256 digest of sanitized trigger request.';
comment on column execution_trigger_event.status is 'Trigger event processing status.';
comment on column execution_trigger_event.run_id is 'Execution run created by this trigger event.';
comment on column execution_trigger_event.received_at is 'Trigger event receive timestamp.';
comment on column execution_trigger_event.error_code is 'Stable sanitized trigger error code.';
comment on column execution_trigger_event.error_summary is 'Bounded sanitized trigger error summary.';
comment on column execution_trigger_event.trace_id is 'Request trace ID for audit correlation.';

comment on table execution_queue_claim is 'WP9 queue claim and heartbeat evidence for recoverable scheduling.';
comment on column execution_queue_claim.id is 'Queue claim ID.';
comment on column execution_queue_claim.node_run_id is 'Claimed node run ID.';
comment on column execution_queue_claim.claim_token is 'Opaque claim token used for conditional worker updates.';
comment on column execution_queue_claim.worker_id is 'Worker instance identifier.';
comment on column execution_queue_claim.claimed_at is 'Claim timestamp.';
comment on column execution_queue_claim.heartbeat_at is 'Last claim heartbeat timestamp.';
comment on column execution_queue_claim.expires_at is 'Claim expiry timestamp.';
comment on column execution_queue_claim.status is 'Claim lifecycle status.';
comment on column execution_queue_claim.created_at is 'Claim creation timestamp.';
comment on column execution_queue_claim.updated_at is 'Claim update timestamp.';
