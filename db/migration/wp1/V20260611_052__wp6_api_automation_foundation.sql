-- WP6 OpenAPI API automation foundation.
-- Stores sanitized OpenAPI specs and endpoint snapshots for controlled API automation generation.

create table if not exists api_automation_spec (
    id uuid primary key default gen_random_uuid(),
    project_id varchar(64) not null,
    source_type varchar(16) not null,
    source_ref varchar(512),
    name varchar(128) not null,
    version_label varchar(64),
    spec_digest varchar(64) not null,
    content_size_bytes int not null default 0,
    sanitized_spec_json jsonb not null default '{}'::jsonb,
    parse_summary_json jsonb not null default '{}'::jsonb,
    status varchar(32) not null default 'UPLOADED',
    parser_version varchar(32) not null default 'wp6-openapi-parser-v1',
    endpoint_count int not null default 0,
    parse_error_summary text,
    created_by varchar(128),
    updated_by varchar(128),
    parsed_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint ck_api_automation_spec_source_type check (source_type in ('TEXT','UPLOAD','URL')),
    constraint ck_api_automation_spec_status check (
        status in ('UPLOADED','PARSING','PARSED','PARSE_FAILED','ARCHIVED')
    ),
    constraint ck_api_automation_spec_counts check (
        content_size_bytes >= 0 and endpoint_count >= 0
    ),
    constraint ck_api_automation_spec_digest check (spec_digest ~ '^[0-9a-f]{64}$'),
    constraint ck_api_automation_spec_json_object check (
        jsonb_typeof(sanitized_spec_json) = 'object'
        and jsonb_typeof(parse_summary_json) = 'object'
    )
);

create table if not exists api_automation_endpoint_snapshot (
    id uuid primary key default gen_random_uuid(),
    spec_id uuid not null references api_automation_spec(id) on delete cascade,
    project_id varchar(64) not null,
    service_name varchar(128),
    operation_id varchar(256),
    http_method varchar(16) not null,
    path varchar(512) not null,
    summary varchar(512),
    tags text,
    parameter_count int not null default 0,
    request_body_present boolean not null default false,
    response_statuses text,
    schema_digest varchar(64) not null,
    diff_status varchar(32) not null default 'UNKNOWN',
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint ck_api_automation_endpoint_method check (
        http_method in ('GET','POST','PUT','PATCH','DELETE','HEAD','OPTIONS')
    ),
    constraint ck_api_automation_endpoint_diff_status check (
        diff_status in ('UNKNOWN','NEW','CHANGED','MATCHED','CONFLICT','SKIPPED')
    ),
    constraint ck_api_automation_endpoint_counts check (parameter_count >= 0),
    constraint ck_api_automation_endpoint_schema_digest check (schema_digest ~ '^[0-9a-f]{64}$')
);

create unique index if not exists uk_api_automation_spec_project_digest
    on api_automation_spec (project_id, spec_digest)
    where status <> 'ARCHIVED';
create index if not exists idx_api_automation_spec_project_status
    on api_automation_spec (project_id, status, created_at desc);
create index if not exists idx_api_automation_spec_created
    on api_automation_spec (created_at desc);

create unique index if not exists uk_api_automation_endpoint_spec_method_path
    on api_automation_endpoint_snapshot (spec_id, http_method, path);
create index if not exists idx_api_automation_endpoint_project_method
    on api_automation_endpoint_snapshot (project_id, http_method, path);
create index if not exists idx_api_automation_endpoint_spec_diff
    on api_automation_endpoint_snapshot (spec_id, diff_status, path);

insert into rbac_permission (code, resource_type, action, scope_mask, description)
values
    ('apiAutomation:read', 'apiAutomation', 'read', 'PLATFORM,PROJECT,APPLICATION', '查看 WP6 OpenAPI 接口自动化规格、任务和结果'),
    ('apiAutomation:import', 'apiAutomation', 'import', 'PLATFORM,PROJECT,APPLICATION', '导入 OpenAPI 规格并同步 API 资产'),
    ('apiAutomation:generate', 'apiAutomation', 'generate', 'PLATFORM,PROJECT,APPLICATION', '生成接口自动化用例和脚本包'),
    ('apiAutomation:review', 'apiAutomation', 'review', 'PLATFORM,PROJECT,APPLICATION', '评审接口自动化脚本包'),
    ('apiAutomation:execute', 'apiAutomation', 'execute', 'PLATFORM,PROJECT,APPLICATION', '触发受控接口自动化试运行'),
    ('apiAutomation:export', 'apiAutomation', 'export', 'PLATFORM,PROJECT', '导出 WP6 接口自动化摘要')
on conflict (code) do update set
    resource_type = excluded.resource_type,
    action = excluded.action,
    scope_mask = excluded.scope_mask,
    description = excluded.description,
    status = 'ENABLED',
    updated_at = now();

with role_permissions(role_code, permission_code) as (
    values
        ('SuperAdmin', 'apiAutomation:read'),
        ('SuperAdmin', 'apiAutomation:import'),
        ('SuperAdmin', 'apiAutomation:generate'),
        ('SuperAdmin', 'apiAutomation:review'),
        ('SuperAdmin', 'apiAutomation:execute'),
        ('SuperAdmin', 'apiAutomation:export'),
        ('PlatformAdmin', 'apiAutomation:read'),
        ('PlatformAdmin', 'apiAutomation:import'),
        ('PlatformAdmin', 'apiAutomation:generate'),
        ('PlatformAdmin', 'apiAutomation:review'),
        ('PlatformAdmin', 'apiAutomation:execute'),
        ('PlatformAdmin', 'apiAutomation:export'),
        ('ProjectOwner', 'apiAutomation:read'),
        ('ProjectOwner', 'apiAutomation:import'),
        ('ProjectOwner', 'apiAutomation:generate'),
        ('ProjectOwner', 'apiAutomation:review'),
        ('ProjectOwner', 'apiAutomation:execute'),
        ('AppOwner', 'apiAutomation:read'),
        ('AppOwner', 'apiAutomation:import'),
        ('AppOwner', 'apiAutomation:generate'),
        ('AppOwner', 'apiAutomation:review'),
        ('AppOwner', 'apiAutomation:execute'),
        ('Tester', 'apiAutomation:read'),
        ('Tester', 'apiAutomation:import'),
        ('Tester', 'apiAutomation:generate'),
        ('Tester', 'apiAutomation:review'),
        ('Tester', 'apiAutomation:execute'),
        ('Developer', 'apiAutomation:read'),
        ('Auditor', 'apiAutomation:read'),
        ('Auditor', 'apiAutomation:export')
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

comment on table api_automation_spec is 'WP6 sanitized OpenAPI source specs for API automation control plane.';
comment on column api_automation_spec.id is 'Spec ID.';
comment on column api_automation_spec.project_id is 'Owning project scope ID.';
comment on column api_automation_spec.source_type is 'Source type: TEXT, UPLOAD or URL metadata.';
comment on column api_automation_spec.source_ref is 'Bounded external source reference; no secret-bearing URL query should be stored.';
comment on column api_automation_spec.name is 'Human-readable OpenAPI source name.';
comment on column api_automation_spec.version_label is 'User supplied source version label.';
comment on column api_automation_spec.spec_digest is 'SHA-256 digest of submitted source content.';
comment on column api_automation_spec.content_size_bytes is 'Submitted source size in bytes.';
comment on column api_automation_spec.sanitized_spec_json is 'Sanitized and bounded OpenAPI JSON object; secret examples are masked.';
comment on column api_automation_spec.parse_summary_json is 'Aggregate parser summary without raw request or response bodies.';
comment on column api_automation_spec.status is 'Spec parser lifecycle status.';
comment on column api_automation_spec.parser_version is 'Parser policy version used for snapshot generation.';
comment on column api_automation_spec.endpoint_count is 'Number of endpoint snapshots parsed from this spec.';
comment on column api_automation_spec.parse_error_summary is 'Sanitized parser error summary.';
comment on column api_automation_spec.created_by is 'Actor that created the spec.';
comment on column api_automation_spec.updated_by is 'Actor that last updated the spec.';
comment on column api_automation_spec.parsed_at is 'Last successful parse timestamp.';
comment on column api_automation_spec.created_at is 'Spec creation timestamp.';
comment on column api_automation_spec.updated_at is 'Spec update timestamp.';

comment on table api_automation_endpoint_snapshot is 'WP6 parsed OpenAPI endpoint snapshot for diff and generation.';
comment on column api_automation_endpoint_snapshot.id is 'Endpoint snapshot ID.';
comment on column api_automation_endpoint_snapshot.spec_id is 'Owning OpenAPI spec ID.';
comment on column api_automation_endpoint_snapshot.project_id is 'Owning project scope ID.';
comment on column api_automation_endpoint_snapshot.service_name is 'Service name inferred from OpenAPI info or tags.';
comment on column api_automation_endpoint_snapshot.operation_id is 'OpenAPI operationId.';
comment on column api_automation_endpoint_snapshot.http_method is 'HTTP method.';
comment on column api_automation_endpoint_snapshot.path is 'OpenAPI path template.';
comment on column api_automation_endpoint_snapshot.summary is 'Sanitized operation summary.';
comment on column api_automation_endpoint_snapshot.tags is 'Comma-separated bounded tag list.';
comment on column api_automation_endpoint_snapshot.parameter_count is 'Total operation and path parameter count.';
comment on column api_automation_endpoint_snapshot.request_body_present is 'Whether operation defines requestBody.';
comment on column api_automation_endpoint_snapshot.response_statuses is 'Comma-separated response status keys.';
comment on column api_automation_endpoint_snapshot.schema_digest is 'SHA-256 digest of sanitized operation schema summary.';
comment on column api_automation_endpoint_snapshot.diff_status is 'Diff state against WP3 API assets.';
comment on column api_automation_endpoint_snapshot.created_at is 'Endpoint snapshot creation timestamp.';
comment on column api_automation_endpoint_snapshot.updated_at is 'Endpoint snapshot update timestamp.';
