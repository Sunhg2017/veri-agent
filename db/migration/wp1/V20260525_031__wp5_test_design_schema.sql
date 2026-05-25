-- WP5 AI test case generation and review schema.
-- Stores deterministic/model candidate metadata while publishing final cases through WP3 assets.

create table if not exists test_design_task (
    id uuid primary key default gen_random_uuid(),
    project_id varchar(64) not null,
    title varchar(256) not null,
    status varchar(32) not null default 'DRAFT',
    requirement_ids text not null,
    coverage_types text not null,
    prompt_key varchar(128),
    prompt_version varchar(64),
    model_invocation_id uuid,
    model_provider_name varchar(128),
    model_name varchar(128),
    total_requirements int not null default 0,
    generated_count int not null default 0,
    confirmed_count int not null default 0,
    published_count int not null default 0,
    error_message text,
    requested_by varchar(128),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint ck_test_design_task_status check (status in (
        'DRAFT','RUNNING','SUCCEEDED','PARTIAL_SUCCESS','FAILED','CANCELLED','PUBLISHING','PUBLISHED'
    )),
    constraint ck_test_design_task_counts check (
        total_requirements >= 0 and generated_count >= 0 and confirmed_count >= 0 and published_count >= 0
    )
);

create index if not exists idx_test_design_task_project_status
    on test_design_task (project_id, status, created_at desc);
create index if not exists idx_test_design_task_model_invocation
    on test_design_task (model_invocation_id)
    where model_invocation_id is not null;

create table if not exists test_design_candidate (
    id uuid primary key default gen_random_uuid(),
    task_id uuid not null references test_design_task(id) on delete cascade,
    project_id varchar(64) not null,
    requirement_id uuid not null references asset_requirement(id) on delete restrict,
    api_id uuid references asset_api(id) on delete restrict,
    title varchar(256) not null,
    description text,
    coverage_type varchar(32) not null,
    priority varchar(16) not null default 'MEDIUM',
    status varchar(32) not null default 'GENERATED',
    preconditions text,
    steps_json jsonb not null default '[]'::jsonb,
    expected_result text,
    tags text,
    duplicate_key varchar(512) not null,
    confidence numeric(5,4) not null default 0,
    prompt_key varchar(128),
    prompt_version varchar(64),
    model_invocation_id uuid,
    model_provider_name varchar(128),
    model_name varchar(128),
    asset_case_id uuid references asset_test_case(id) on delete restrict,
    review_comment text,
    rejected_reason text,
    ignored_reason text,
    error_message text,
    confirmed_by varchar(128),
    confirmed_at timestamptz,
    version bigint not null default 0,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint ck_test_design_candidate_status check (status in (
        'GENERATED','EDITED','CONFIRMED','REJECTED','IGNORED','PUBLISHED','FAILED'
    )),
    constraint ck_test_design_candidate_coverage check (coverage_type in (
        'SMOKE','FUNCTIONAL','EXCEPTION','BOUNDARY','PERMISSION','REGRESSION'
    )),
    constraint ck_test_design_candidate_priority check (priority in ('CRITICAL','HIGH','MEDIUM','LOW')),
    constraint ck_test_design_candidate_confidence check (confidence >= 0 and confidence <= 1),
    constraint ck_test_design_candidate_version check (version >= 0)
);

create unique index if not exists uk_test_design_candidate_task_duplicate
    on test_design_candidate (task_id, duplicate_key);
create index if not exists idx_test_design_candidate_task_status
    on test_design_candidate (task_id, status, created_at desc);
create index if not exists idx_test_design_candidate_project_status
    on test_design_candidate (project_id, status, created_at desc);
create index if not exists idx_test_design_candidate_requirement
    on test_design_candidate (requirement_id, coverage_type);
create index if not exists idx_test_design_candidate_model_invocation
    on test_design_candidate (model_invocation_id)
    where model_invocation_id is not null;
create index if not exists idx_test_design_candidate_asset_case
    on test_design_candidate (asset_case_id)
    where asset_case_id is not null;

create table if not exists test_design_review_record (
    id uuid primary key default gen_random_uuid(),
    candidate_id uuid not null references test_design_candidate(id) on delete cascade,
    task_id uuid not null references test_design_task(id) on delete cascade,
    project_id varchar(64) not null,
    action varchar(32) not null,
    before_status varchar(32),
    after_status varchar(32),
    reviewer varchar(128),
    comment text,
    diff_json jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default now(),
    constraint ck_test_design_review_action check (action in ('UPDATE','CONFIRMED','REJECTED','IGNORED'))
);

create index if not exists idx_test_design_review_candidate_created
    on test_design_review_record (candidate_id, created_at desc);
create index if not exists idx_test_design_review_task_created
    on test_design_review_record (task_id, created_at desc);

create table if not exists test_design_publish_record (
    id uuid primary key default gen_random_uuid(),
    task_id uuid not null references test_design_task(id) on delete cascade,
    candidate_id uuid not null references test_design_candidate(id) on delete cascade,
    project_id varchar(64) not null,
    requirement_id uuid not null references asset_requirement(id) on delete restrict,
    asset_case_id uuid references asset_test_case(id) on delete restrict,
    dry_run boolean not null default false,
    action varchar(32) not null,
    result varchar(32) not null,
    error_message text,
    published_by varchar(128),
    created_at timestamptz not null default now(),
    constraint ck_test_design_publish_action check (action in (
        'CREATE','SKIP_PUBLISHED','SKIP_UNCONFIRMED'
    )),
    constraint ck_test_design_publish_result check (result in (
        'PLANNED','SUCCEEDED','SKIPPED','FAILED'
    ))
);

create index if not exists idx_test_design_publish_task_created
    on test_design_publish_record (task_id, created_at desc);
create index if not exists idx_test_design_publish_candidate_created
    on test_design_publish_record (candidate_id, created_at desc);
create index if not exists idx_test_design_publish_asset_case
    on test_design_publish_record (asset_case_id)
    where asset_case_id is not null;

insert into rbac_permission (code, resource_type, action, scope_mask, description)
values
    ('testDesign:read', 'testDesign', 'read', 'PLATFORM,PROJECT,APPLICATION', '查看 WP5 用例生成任务和候选用例'),
    ('testDesign:generate', 'testDesign', 'generate', 'PLATFORM,PROJECT,APPLICATION', '从需求生成候选测试用例'),
    ('testDesign:review', 'testDesign', 'review', 'PLATFORM,PROJECT,APPLICATION', '评审、编辑、确认或忽略候选测试用例'),
    ('testDesign:publish', 'testDesign', 'publish', 'PLATFORM,PROJECT,APPLICATION', '发布候选测试用例到 WP3 资产库'),
    ('testDesign:export', 'testDesign', 'export', 'PLATFORM,PROJECT,APPLICATION', '导出 WP5 生成和评审记录')
on conflict (code) do update set
    resource_type = excluded.resource_type,
    action = excluded.action,
    scope_mask = excluded.scope_mask,
    description = excluded.description,
    status = 'ENABLED',
    updated_at = now();

with role_permissions(role_code, permission_code) as (
    values
        ('SuperAdmin', 'testDesign:read'),
        ('SuperAdmin', 'testDesign:generate'),
        ('SuperAdmin', 'testDesign:review'),
        ('SuperAdmin', 'testDesign:publish'),
        ('SuperAdmin', 'testDesign:export'),
        ('PlatformAdmin', 'testDesign:read'),
        ('PlatformAdmin', 'testDesign:generate'),
        ('PlatformAdmin', 'testDesign:review'),
        ('PlatformAdmin', 'testDesign:publish'),
        ('PlatformAdmin', 'testDesign:export'),
        ('ProjectOwner', 'testDesign:read'),
        ('ProjectOwner', 'testDesign:generate'),
        ('ProjectOwner', 'testDesign:review'),
        ('ProjectOwner', 'testDesign:publish'),
        ('AppOwner', 'testDesign:read'),
        ('AppOwner', 'testDesign:generate'),
        ('AppOwner', 'testDesign:review'),
        ('AppOwner', 'testDesign:publish'),
        ('Tester', 'testDesign:read'),
        ('Tester', 'testDesign:generate'),
        ('Tester', 'testDesign:review'),
        ('Tester', 'testDesign:publish'),
        ('Developer', 'testDesign:read'),
        ('Auditor', 'testDesign:read'),
        ('Auditor', 'testDesign:export')
)
insert into rbac_role_permission (
    role_id,
    permission_id,
    effect,
    created_by
)
select
    r.id,
    p.id,
    'ALLOW',
    null
from role_permissions rp
join rbac_role r on r.code = rp.role_code
    and r.deleted_at is null
join rbac_permission p on p.code = rp.permission_code
    and p.status = 'ENABLED'
on conflict do nothing;

comment on table test_design_task is 'WP5 test case generation task metadata. Prompt/model identifiers are references only; raw prompts are not stored.';
comment on table test_design_candidate is 'WP5 generated test case candidates before human review and WP3 asset publishing.';
comment on table test_design_review_record is 'WP5 candidate review audit trail.';
comment on table test_design_publish_record is 'WP5 candidate dry-run and publish results.';
