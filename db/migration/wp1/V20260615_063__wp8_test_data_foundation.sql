-- WP8 test data and account pool foundation.
-- The control plane stores sanitized summaries and secretRef digests only; credentials and raw data payloads are not persisted.

create table if not exists test_data_set (
    id uuid primary key default gen_random_uuid(),
    project_id varchar(64) not null,
    application_id varchar(64),
    environment_id varchar(64),
    code varchar(128) not null,
    name varchar(128) not null,
    status varchar(32) not null default 'DRAFT',
    schema_json jsonb not null default '{}'::jsonb,
    sensitivity_level varchar(32) not null default 'INTERNAL',
    cleanup_policy_json jsonb not null default '{}'::jsonb,
    source_type varchar(32) not null default 'MANUAL',
    source_ref_digest varchar(64),
    created_by varchar(128),
    updated_by varchar(128),
    archived_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint ck_test_data_set_status check (status in ('DRAFT','READY','DISABLED','ARCHIVED')),
    constraint ck_test_data_set_code check (code ~ '^[A-Za-z0-9_-]{1,128}$'),
    constraint ck_test_data_set_schema_json check (jsonb_typeof(schema_json) = 'object'),
    constraint ck_test_data_set_cleanup_policy_json check (jsonb_typeof(cleanup_policy_json) = 'object'),
    constraint ck_test_data_set_sensitivity check (sensitivity_level in ('PUBLIC','INTERNAL','CONFIDENTIAL','RESTRICTED')),
    constraint ck_test_data_set_source_type check (source_type in ('MANUAL','GENERATED','EXTERNAL_REF')),
    constraint ck_test_data_set_source_ref_digest check (
        source_ref_digest is null
        or source_ref_digest ~ '^[0-9a-f]{64}$'
    ),
    constraint uk_test_data_set_project_code unique (project_id, code)
);

create table if not exists test_data_record (
    id uuid primary key default gen_random_uuid(),
    data_set_id uuid not null references test_data_set(id) on delete cascade,
    project_id varchar(64) not null,
    record_key varchar(128) not null,
    status varchar(32) not null default 'ACTIVE',
    record_digest varchar(64) not null,
    masked_summary_json jsonb not null default '{}'::jsonb,
    external_ref_digest varchar(64),
    tags_json jsonb not null default '[]'::jsonb,
    created_by varchar(128),
    updated_by varchar(128),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint ck_test_data_record_status check (status in ('ACTIVE','MASKED','INVALID','ARCHIVED')),
    constraint ck_test_data_record_key check (record_key ~ '^[A-Za-z0-9_.:-]{1,128}$'),
    constraint ck_test_data_record_digest check (record_digest ~ '^[0-9a-f]{64}$'),
    constraint ck_test_data_record_external_ref_digest check (
        external_ref_digest is null
        or external_ref_digest ~ '^[0-9a-f]{64}$'
    ),
    constraint ck_test_data_record_json check (
        jsonb_typeof(masked_summary_json) = 'object'
        and jsonb_typeof(tags_json) = 'array'
    ),
    constraint uk_test_data_record_key unique (data_set_id, record_key)
);

create table if not exists test_data_task (
    id uuid primary key default gen_random_uuid(),
    project_id varchar(64) not null,
    data_set_id uuid references test_data_set(id) on delete set null,
    task_type varchar(32) not null,
    status varchar(32) not null default 'PENDING',
    request_key varchar(128),
    target_ref varchar(256),
    attempt int not null default 1,
    result_summary_json jsonb not null default '{}'::jsonb,
    error_code varchar(64),
    error_summary varchar(512),
    trace_id varchar(64),
    created_by varchar(128),
    started_at timestamptz,
    finished_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint ck_test_data_task_type check (task_type in ('PREPARE','REFRESH','CLEANUP','ROLLBACK')),
    constraint ck_test_data_task_status check (status in ('PENDING','RUNNING','SUCCEEDED','FAILED','CANCELED')),
    constraint ck_test_data_task_attempt check (attempt >= 1),
    constraint ck_test_data_task_result_json check (jsonb_typeof(result_summary_json) = 'object')
);

create table if not exists test_account_pool (
    id uuid primary key default gen_random_uuid(),
    project_id varchar(64) not null,
    application_id varchar(64),
    environment_id varchar(64),
    code varchar(128) not null,
    name varchar(128) not null,
    status varchar(32) not null default 'DRAFT',
    lease_policy_json jsonb not null default '{}'::jsonb,
    default_ttl_seconds int not null default 1800,
    created_by varchar(128),
    updated_by varchar(128),
    archived_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint ck_test_account_pool_status check (status in ('DRAFT','READY','DISABLED','ARCHIVED')),
    constraint ck_test_account_pool_code check (code ~ '^[A-Za-z0-9_-]{1,128}$'),
    constraint ck_test_account_pool_lease_policy_json check (jsonb_typeof(lease_policy_json) = 'object'),
    constraint ck_test_account_pool_ttl check (default_ttl_seconds between 1 and 86400),
    constraint uk_test_account_pool_project_code unique (project_id, code)
);

create table if not exists test_pooled_account (
    id uuid primary key default gen_random_uuid(),
    pool_id uuid not null references test_account_pool(id) on delete cascade,
    project_id varchar(64) not null,
    account_key varchar(128) not null,
    display_name varchar(128),
    status varchar(32) not null default 'AVAILABLE',
    role_tags_json jsonb not null default '[]'::jsonb,
    scope_summary_json jsonb not null default '{}'::jsonb,
    secret_ref_digest varchar(64) not null,
    secret_ref_cipher text,
    last_health_status varchar(32),
    last_health_summary varchar(512),
    created_by varchar(128),
    updated_by varchar(128),
    archived_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint ck_test_pooled_account_status check (
        status in ('AVAILABLE','LEASED','LOCKED','EXPIRED','DISABLED','ARCHIVED')
    ),
    constraint ck_test_pooled_account_key check (account_key ~ '^[A-Za-z0-9_.@:-]{1,128}$'),
    constraint ck_test_pooled_account_secret_digest check (secret_ref_digest ~ '^[0-9a-f]{64}$'),
    constraint ck_test_pooled_account_json check (
        jsonb_typeof(role_tags_json) = 'array'
        and jsonb_typeof(scope_summary_json) = 'object'
    ),
    constraint ck_test_pooled_account_health check (
        last_health_status is null
        or last_health_status in ('UNKNOWN','HEALTHY','UNHEALTHY','LOCKED')
    ),
    constraint uk_test_pooled_account_key unique (pool_id, account_key)
);

create table if not exists test_account_lease (
    id uuid primary key default gen_random_uuid(),
    pool_id uuid not null references test_account_pool(id) on delete restrict,
    account_id uuid not null references test_pooled_account(id) on delete restrict,
    project_id varchar(64) not null,
    status varchar(32) not null default 'ACTIVE',
    holder_type varchar(32) not null,
    holder_ref varchar(128) not null,
    request_key varchar(128),
    lease_token_digest varchar(64) not null,
    expires_at timestamptz not null,
    released_at timestamptz,
    release_reason varchar(256),
    created_by varchar(128),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint ck_test_account_lease_status check (status in ('ACTIVE','RELEASED','EXPIRED','REVOKED')),
    constraint ck_test_account_lease_holder_type check (holder_type in ('MANUAL','EXECUTION_RUN','UI_E2E_RUN','API_AUTOMATION_RUN')),
    constraint ck_test_account_lease_token_digest check (lease_token_digest ~ '^[0-9a-f]{64}$')
);

create table if not exists test_account_role_matrix (
    id uuid primary key default gen_random_uuid(),
    project_id varchar(64) not null,
    pool_id uuid not null references test_account_pool(id) on delete cascade,
    role_code varchar(64) not null,
    resource_scope_json jsonb not null default '{}'::jsonb,
    menu_scope_json jsonb not null default '{}'::jsonb,
    scenario_tags_json jsonb not null default '[]'::jsonb,
    created_by varchar(128),
    updated_by varchar(128),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint ck_test_account_role_matrix_json check (
        jsonb_typeof(resource_scope_json) = 'object'
        and jsonb_typeof(menu_scope_json) = 'object'
        and jsonb_typeof(scenario_tags_json) = 'array'
    ),
    constraint uk_test_account_role_matrix_role unique (pool_id, role_code)
);

create index if not exists idx_test_data_set_project_status
    on test_data_set (project_id, status, updated_at desc);
create index if not exists idx_test_data_set_app_env
    on test_data_set (project_id, application_id, environment_id)
    where application_id is not null or environment_id is not null;

create index if not exists idx_test_data_record_set_status
    on test_data_record (data_set_id, status, updated_at desc);
create index if not exists idx_test_data_record_digest
    on test_data_record (record_digest);

create unique index if not exists uk_test_data_task_project_request_key
    on test_data_task (project_id, request_key)
    where request_key is not null;
create index if not exists idx_test_data_task_project_status
    on test_data_task (project_id, status, created_at desc);
create index if not exists idx_test_data_task_data_set
    on test_data_task (data_set_id)
    where data_set_id is not null;

create index if not exists idx_test_account_pool_project_status
    on test_account_pool (project_id, status, updated_at desc);
create index if not exists idx_test_account_pool_app_env
    on test_account_pool (project_id, application_id, environment_id)
    where application_id is not null or environment_id is not null;

create index if not exists idx_test_pooled_account_pool_status
    on test_pooled_account (pool_id, status, updated_at desc);
create index if not exists idx_test_pooled_account_secret_digest
    on test_pooled_account (secret_ref_digest);

create unique index if not exists uk_test_account_lease_active_account
    on test_account_lease (account_id)
    where status = 'ACTIVE';
create unique index if not exists uk_test_account_lease_project_request_key
    on test_account_lease (project_id, request_key)
    where request_key is not null;
create index if not exists idx_test_account_lease_project_status
    on test_account_lease (project_id, status, created_at desc);
create index if not exists idx_test_account_lease_expires
    on test_account_lease (status, expires_at)
    where status = 'ACTIVE';

create index if not exists idx_test_account_role_matrix_pool
    on test_account_role_matrix (pool_id, role_code);

comment on table test_data_set is 'WP8 sanitized test data set control-plane metadata.';
comment on column test_data_set.id is 'Test data set ID.';
comment on column test_data_set.project_id is 'Owning project scope ID.';
comment on column test_data_set.application_id is 'Optional application scope ID.';
comment on column test_data_set.environment_id is 'Optional environment scope ID.';
comment on column test_data_set.code is 'Stable data set code within a project.';
comment on column test_data_set.name is 'Human-readable data set name.';
comment on column test_data_set.status is 'Data set lifecycle status.';
comment on column test_data_set.schema_json is 'Sanitized record schema summary.';
comment on column test_data_set.sensitivity_level is 'Declared data sensitivity level.';
comment on column test_data_set.cleanup_policy_json is 'Cleanup policy summary without destructive adapter secrets.';
comment on column test_data_set.source_type is 'Data source type.';
comment on column test_data_set.source_ref_digest is 'SHA-256 digest of external source reference.';
comment on column test_data_set.created_by is 'Actor that created the data set.';
comment on column test_data_set.updated_by is 'Actor that last updated the data set.';
comment on column test_data_set.archived_at is 'Archive timestamp.';
comment on column test_data_set.created_at is 'Data set creation timestamp.';
comment on column test_data_set.updated_at is 'Data set update timestamp.';

comment on table test_data_record is 'WP8 sanitized test data record summary and digest.';
comment on column test_data_record.id is 'Test data record ID.';
comment on column test_data_record.data_set_id is 'Owning data set ID.';
comment on column test_data_record.project_id is 'Owning project scope ID.';
comment on column test_data_record.record_key is 'Stable record key within a data set.';
comment on column test_data_record.status is 'Record lifecycle status.';
comment on column test_data_record.record_digest is 'SHA-256 digest of normalized sanitized record content.';
comment on column test_data_record.masked_summary_json is 'Masked record summary; raw payload is not stored.';
comment on column test_data_record.external_ref_digest is 'SHA-256 digest of external record reference.';
comment on column test_data_record.tags_json is 'Record tags for filtering and scenario matching.';
comment on column test_data_record.created_by is 'Actor that created the record.';
comment on column test_data_record.updated_by is 'Actor that last updated the record.';
comment on column test_data_record.created_at is 'Record creation timestamp.';
comment on column test_data_record.updated_at is 'Record update timestamp.';

comment on table test_data_task is 'WP8 test data prepare, refresh, cleanup and rollback task metadata.';
comment on column test_data_task.id is 'Test data task ID.';
comment on column test_data_task.project_id is 'Owning project scope ID.';
comment on column test_data_task.data_set_id is 'Optional target data set ID.';
comment on column test_data_task.task_type is 'Task type such as PREPARE, REFRESH, CLEANUP or ROLLBACK.';
comment on column test_data_task.status is 'Task lifecycle status.';
comment on column test_data_task.request_key is 'Idempotency key for task creation.';
comment on column test_data_task.target_ref is 'Bounded target reference for adapters or downstream jobs.';
comment on column test_data_task.attempt is 'Task attempt number.';
comment on column test_data_task.result_summary_json is 'Sanitized task result summary.';
comment on column test_data_task.error_code is 'Stable sanitized task error code.';
comment on column test_data_task.error_summary is 'Bounded sanitized task error summary.';
comment on column test_data_task.trace_id is 'Request trace ID for audit correlation.';
comment on column test_data_task.created_by is 'Actor that created the task.';
comment on column test_data_task.started_at is 'Task start timestamp.';
comment on column test_data_task.finished_at is 'Task finish timestamp.';
comment on column test_data_task.created_at is 'Task creation timestamp.';
comment on column test_data_task.updated_at is 'Task update timestamp.';

comment on table test_account_pool is 'WP8 reusable test account pool metadata.';
comment on column test_account_pool.id is 'Test account pool ID.';
comment on column test_account_pool.project_id is 'Owning project scope ID.';
comment on column test_account_pool.application_id is 'Optional application scope ID.';
comment on column test_account_pool.environment_id is 'Optional environment scope ID.';
comment on column test_account_pool.code is 'Stable account pool code within a project.';
comment on column test_account_pool.name is 'Human-readable account pool name.';
comment on column test_account_pool.status is 'Account pool lifecycle status.';
comment on column test_account_pool.lease_policy_json is 'Lease policy summary without credentials.';
comment on column test_account_pool.default_ttl_seconds is 'Default lease TTL in seconds.';
comment on column test_account_pool.created_by is 'Actor that created the pool.';
comment on column test_account_pool.updated_by is 'Actor that last updated the pool.';
comment on column test_account_pool.archived_at is 'Archive timestamp.';
comment on column test_account_pool.created_at is 'Pool creation timestamp.';
comment on column test_account_pool.updated_at is 'Pool update timestamp.';

comment on table test_pooled_account is 'WP8 pooled test account metadata and protected secret reference.';
comment on column test_pooled_account.id is 'Pooled account ID.';
comment on column test_pooled_account.pool_id is 'Owning account pool ID.';
comment on column test_pooled_account.project_id is 'Owning project scope ID.';
comment on column test_pooled_account.account_key is 'Stable account key within a pool.';
comment on column test_pooled_account.display_name is 'Bounded display name for operators.';
comment on column test_pooled_account.status is 'Pooled account lifecycle status.';
comment on column test_pooled_account.role_tags_json is 'Role tags used for scenario matching.';
comment on column test_pooled_account.scope_summary_json is 'Sanitized account scope summary.';
comment on column test_pooled_account.secret_ref_digest is 'SHA-256 digest of the secret reference.';
comment on column test_pooled_account.secret_ref_cipher is 'Encrypted secret reference payload; readonly role must not read this column.';
comment on column test_pooled_account.last_health_status is 'Last account health status.';
comment on column test_pooled_account.last_health_summary is 'Bounded sanitized health summary.';
comment on column test_pooled_account.created_by is 'Actor that created the account metadata.';
comment on column test_pooled_account.updated_by is 'Actor that last updated the account metadata.';
comment on column test_pooled_account.archived_at is 'Archive timestamp.';
comment on column test_pooled_account.created_at is 'Account metadata creation timestamp.';
comment on column test_pooled_account.updated_at is 'Account metadata update timestamp.';

comment on table test_account_lease is 'WP8 account lease state and idempotency evidence.';
comment on column test_account_lease.id is 'Account lease ID.';
comment on column test_account_lease.pool_id is 'Source account pool ID.';
comment on column test_account_lease.account_id is 'Leased pooled account ID.';
comment on column test_account_lease.project_id is 'Owning project scope ID.';
comment on column test_account_lease.status is 'Lease lifecycle status.';
comment on column test_account_lease.holder_type is 'Lease holder type.';
comment on column test_account_lease.holder_ref is 'Lease holder reference.';
comment on column test_account_lease.request_key is 'Idempotency key for lease acquisition.';
comment on column test_account_lease.lease_token_digest is 'SHA-256 digest of lease token.';
comment on column test_account_lease.expires_at is 'Lease expiry timestamp.';
comment on column test_account_lease.released_at is 'Lease release timestamp.';
comment on column test_account_lease.release_reason is 'Bounded sanitized release reason.';
comment on column test_account_lease.created_by is 'Actor or job that created the lease.';
comment on column test_account_lease.created_at is 'Lease creation timestamp.';
comment on column test_account_lease.updated_at is 'Lease update timestamp.';

comment on table test_account_role_matrix is 'WP8 account role, resource, menu and scenario coverage matrix.';
comment on column test_account_role_matrix.id is 'Account role matrix ID.';
comment on column test_account_role_matrix.project_id is 'Owning project scope ID.';
comment on column test_account_role_matrix.pool_id is 'Owning account pool ID.';
comment on column test_account_role_matrix.role_code is 'Business or platform role code represented by the pool.';
comment on column test_account_role_matrix.resource_scope_json is 'Sanitized resource scope coverage summary.';
comment on column test_account_role_matrix.menu_scope_json is 'Sanitized menu scope coverage summary.';
comment on column test_account_role_matrix.scenario_tags_json is 'Scenario tags covered by this role mapping.';
comment on column test_account_role_matrix.created_by is 'Actor that created the role matrix entry.';
comment on column test_account_role_matrix.updated_by is 'Actor that last updated the role matrix entry.';
comment on column test_account_role_matrix.created_at is 'Role matrix creation timestamp.';
comment on column test_account_role_matrix.updated_at is 'Role matrix update timestamp.';

insert into rbac_permission (code, resource_type, action, scope_mask, description)
values
    ('testData:read', 'test_data', 'read', 'PLATFORM,PROJECT,APPLICATION,ENVIRONMENT', '查看 WP8 数据集、账号池、租借和清理任务摘要'),
    ('testData:manage', 'test_data', 'manage', 'PLATFORM,PROJECT,APPLICATION', '维护 WP8 数据集、账号池和账号摘要'),
    ('testData:lease', 'test_data', 'lease', 'PLATFORM,PROJECT,APPLICATION,ENVIRONMENT', '申请、续租和释放测试账号租借'),
    ('testData:cleanup', 'test_data', 'cleanup', 'PLATFORM,PROJECT,APPLICATION,ENVIRONMENT', '创建、重试和确认测试数据清理任务'),
    ('testData:export', 'test_data', 'export', 'PLATFORM,PROJECT,APPLICATION', '导出 WP8 脱敏数据、租借和清理审计摘要')
on conflict (code) do update set
    resource_type = excluded.resource_type,
    action = excluded.action,
    scope_mask = excluded.scope_mask,
    description = excluded.description,
    status = 'ENABLED',
    updated_at = now();

with role_permissions(role_code, permission_code) as (
    values
        ('SuperAdmin', 'testData:read'),
        ('SuperAdmin', 'testData:manage'),
        ('SuperAdmin', 'testData:lease'),
        ('SuperAdmin', 'testData:cleanup'),
        ('SuperAdmin', 'testData:export'),
        ('PlatformAdmin', 'testData:read'),
        ('PlatformAdmin', 'testData:manage'),
        ('PlatformAdmin', 'testData:lease'),
        ('PlatformAdmin', 'testData:cleanup'),
        ('PlatformAdmin', 'testData:export'),
        ('ProjectOwner', 'testData:read'),
        ('ProjectOwner', 'testData:manage'),
        ('ProjectOwner', 'testData:lease'),
        ('ProjectOwner', 'testData:cleanup'),
        ('ProjectOwner', 'testData:export'),
        ('AppOwner', 'testData:read'),
        ('AppOwner', 'testData:manage'),
        ('AppOwner', 'testData:lease'),
        ('AppOwner', 'testData:cleanup'),
        ('Tester', 'testData:read'),
        ('Tester', 'testData:lease'),
        ('Developer', 'testData:read'),
        ('Auditor', 'testData:read'),
        ('Auditor', 'testData:export')
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
        ('test_data.audit_events', '[
            "test_data.data_set.created",
            "test_data.data_set.updated",
            "test_data.data_set.archived",
            "test_data.record.imported",
            "test_data.task.created",
            "test_data.task.completed",
            "test_data.account_pool.created",
            "test_data.account_pool.updated",
            "test_data.account_pool.archived",
            "test_data.account.created",
            "test_data.account.updated",
            "test_data.account.secret_ref_replaced",
            "test_data.lease.acquired",
            "test_data.lease.renewed",
            "test_data.lease.released",
            "test_data.lease.expired",
            "test_data.cleanup.requested",
            "test_data.cleanup.retried",
            "test_data.exported"
        ]'::jsonb),
        ('test_data.enabled', 'true'::jsonb),
        ('test_data.cleanup_enabled', 'false'::jsonb),
        ('test_data.export_enabled', 'true'::jsonb)
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
            test_data_set,
            test_data_record,
            test_data_task,
            test_account_pool,
            test_pooled_account,
            test_account_lease,
            test_account_role_matrix
        to wp1_app;
    end if;

    if to_regrole('wp1_readonly') is not null then
        revoke all on test_pooled_account from wp1_readonly;
        grant select on
            test_data_set,
            test_data_record,
            test_data_task,
            test_account_pool,
            test_account_lease,
            test_account_role_matrix
        to wp1_readonly;
        grant select (
            id,
            pool_id,
            project_id,
            account_key,
            display_name,
            status,
            role_tags_json,
            scope_summary_json,
            secret_ref_digest,
            last_health_status,
            last_health_summary,
            created_by,
            updated_by,
            archived_at,
            created_at,
            updated_at
        ) on test_pooled_account to wp1_readonly;
    end if;

    if to_regrole('wp1_migration') is not null then
        grant all privileges on
            test_data_set,
            test_data_record,
            test_data_task,
            test_account_pool,
            test_pooled_account,
            test_account_lease,
            test_account_role_matrix
        to wp1_migration;
    end if;
end
$$;
