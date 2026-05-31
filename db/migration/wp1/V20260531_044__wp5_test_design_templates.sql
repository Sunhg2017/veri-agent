-- WP5 generation template management.
-- Stores reusable generation configuration only. Prompt bodies, source context bodies and model payloads are excluded.

create table if not exists test_design_template (
    id uuid primary key default gen_random_uuid(),
    project_id varchar(64),
    name varchar(128) not null,
    description varchar(512),
    prompt_key varchar(128) not null,
    prompt_version varchar(64) not null,
    coverage_types varchar(256) not null,
    case_count_per_requirement int not null,
    context_defaults_json jsonb not null default '{}'::jsonb,
    enabled boolean not null default true,
    created_by varchar(128),
    updated_by varchar(128),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint ck_test_design_template_case_count check (case_count_per_requirement between 1 and 20),
    constraint ck_test_design_template_context_object check (jsonb_typeof(context_defaults_json) = 'object'),
    constraint ck_test_design_template_context_keys check (
        context_defaults_json ?| array['environmentKey', 'contextApiIds', 'contextPageIds', 'contextFlowIds']
        or context_defaults_json = '{}'::jsonb
    )
);

create unique index if not exists uk_test_design_template_global_name
    on test_design_template (lower(name))
    where project_id is null;

create unique index if not exists uk_test_design_template_project_name
    on test_design_template (project_id, lower(name))
    where project_id is not null;

create index if not exists idx_test_design_template_project_enabled
    on test_design_template (project_id, enabled, updated_at desc);

create index if not exists idx_test_design_template_enabled_updated
    on test_design_template (enabled, updated_at desc);

insert into test_design_template (
    project_id,
    name,
    description,
    prompt_key,
    prompt_version,
    coverage_types,
    case_count_per_requirement,
    context_defaults_json,
    enabled,
    created_by,
    updated_by
)
select
    null,
    'WP5 默认生成模板',
    '平台默认模板：冒烟、功能和异常覆盖。',
    'wp5-test-design-v1',
    '1.0.0',
    'SMOKE,FUNCTIONAL,EXCEPTION',
    3,
    '{}'::jsonb,
    true,
    null,
    null
where not exists (
    select 1
    from test_design_template
    where project_id is null
      and lower(name) = lower('WP5 默认生成模板')
);

comment on table test_design_template is 'WP5 generation template metadata; stores reusable prompt references, coverage defaults and safe context identifiers only.';
comment on column test_design_template.id is 'Primary key for a generation template.';
comment on column test_design_template.project_id is 'Owning project scope ID; null means platform global template.';
comment on column test_design_template.name is 'Template display name unique within project/global scope.';
comment on column test_design_template.description is 'Short template description; must not contain secrets or Prompt bodies.';
comment on column test_design_template.prompt_key is 'Prompt template key reference; Prompt content is not stored here.';
comment on column test_design_template.prompt_version is 'Prompt template version reference.';
comment on column test_design_template.coverage_types is 'Comma-separated coverage defaults such as SMOKE,FUNCTIONAL,EXCEPTION.';
comment on column test_design_template.case_count_per_requirement is 'Default case count per selected requirement.';
comment on column test_design_template.context_defaults_json is 'Safe context defaults: environmentKey and explicit asset ID arrays only.';
comment on column test_design_template.enabled is 'Whether new task creation may use this template.';
comment on column test_design_template.created_by is 'Creator identity snapshot.';
comment on column test_design_template.updated_by is 'Last updater identity snapshot.';
comment on column test_design_template.created_at is 'Creation timestamp.';
comment on column test_design_template.updated_at is 'Last update timestamp.';
