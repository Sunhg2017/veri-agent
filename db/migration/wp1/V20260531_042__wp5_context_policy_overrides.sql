-- WP5 context policy override metadata.
-- Stores bounded clipping values and approval state only; policy bodies, diffs, notes, ticket URLs and context payloads
-- are intentionally excluded from this table.

create table if not exists test_design_context_policy_override (
    id uuid primary key default gen_random_uuid(),
    scope_type varchar(32) not null,
    project_id varchar(64) not null,
    environment_key varchar(64),
    status varchar(32) not null,
    context_linked_assets_per_requirement int,
    context_explicit_assets_per_type int,
    context_existing_cases_per_requirement int,
    context_requirement_description_chars int,
    context_acceptance_criteria_chars int,
    context_asset_schema_chars int,
    change_reason_code varchar(64) not null,
    approval_reason_code varchar(64),
    requested_by varchar(128),
    approved_by varchar(128),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint ck_test_design_context_policy_override_scope check (
        scope_type in ('PROJECT', 'ENVIRONMENT')
    ),
    constraint ck_test_design_context_policy_override_status check (
        status in ('PENDING', 'APPROVED', 'REJECTED')
    ),
    constraint ck_test_design_context_policy_override_environment check (
        (scope_type = 'PROJECT' and environment_key is null)
        or (scope_type = 'ENVIRONMENT' and environment_key is not null)
    ),
    constraint ck_test_design_context_policy_override_any_limit check (
        context_linked_assets_per_requirement is not null
        or context_explicit_assets_per_type is not null
        or context_existing_cases_per_requirement is not null
        or context_requirement_description_chars is not null
        or context_acceptance_criteria_chars is not null
        or context_asset_schema_chars is not null
    ),
    constraint ck_test_design_context_policy_override_item_limits check (
        (context_linked_assets_per_requirement is null or context_linked_assets_per_requirement between 1 and 50)
        and (context_explicit_assets_per_type is null or context_explicit_assets_per_type between 1 and 50)
        and (context_existing_cases_per_requirement is null or context_existing_cases_per_requirement between 1 and 50)
    ),
    constraint ck_test_design_context_policy_override_char_limits check (
        (context_requirement_description_chars is null or context_requirement_description_chars between 1 and 2000)
        and (context_acceptance_criteria_chars is null or context_acceptance_criteria_chars between 1 and 2000)
        and (context_asset_schema_chars is null or context_asset_schema_chars between 1 and 2000)
    )
);

create index if not exists idx_test_design_context_policy_override_project_created
    on test_design_context_policy_override (project_id, created_at desc);

create index if not exists idx_test_design_context_policy_override_project_status
    on test_design_context_policy_override (project_id, status, updated_at desc);

create index if not exists idx_test_design_context_policy_override_environment_status
    on test_design_context_policy_override (project_id, environment_key, status, updated_at desc)
    where environment_key is not null;

insert into rbac_permission (code, resource_type, action, scope_mask, description)
values
    ('testDesign:policy_manage', 'testDesign', 'policy_manage', 'PLATFORM,PROJECT',
     '维护 WP5 上下文策略覆盖请求和审批状态')
on conflict (code) do update set
    resource_type = excluded.resource_type,
    action = excluded.action,
    scope_mask = excluded.scope_mask,
    description = excluded.description,
    status = 'ENABLED',
    updated_at = now();

with role_permissions(role_code, permission_code) as (
    values
        ('SuperAdmin', 'testDesign:policy_manage'),
        ('PlatformAdmin', 'testDesign:policy_manage'),
        ('ProjectOwner', 'testDesign:policy_manage')
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

comment on table test_design_context_policy_override is 'WP5 project/environment context policy override metadata; stores bounded clipping values and approval state only.';
comment on column test_design_context_policy_override.id is 'Primary key for a context policy override request.';
comment on column test_design_context_policy_override.scope_type is 'Override scope type: PROJECT or ENVIRONMENT.';
comment on column test_design_context_policy_override.project_id is 'Owning project scope ID.';
comment on column test_design_context_policy_override.environment_key is 'Environment key for environment-level overrides.';
comment on column test_design_context_policy_override.status is 'Approval state: PENDING, APPROVED or REJECTED.';
comment on column test_design_context_policy_override.context_linked_assets_per_requirement is 'Bounded linked asset count per requirement; stores numeric limit only.';
comment on column test_design_context_policy_override.context_explicit_assets_per_type is 'Bounded explicit asset count per asset type; stores numeric limit only.';
comment on column test_design_context_policy_override.context_existing_cases_per_requirement is 'Bounded existing case count per requirement; stores numeric limit only.';
comment on column test_design_context_policy_override.context_requirement_description_chars is 'Bounded requirement description character limit; stores numeric limit only.';
comment on column test_design_context_policy_override.context_acceptance_criteria_chars is 'Bounded acceptance criteria character limit; stores numeric limit only.';
comment on column test_design_context_policy_override.context_asset_schema_chars is 'Bounded asset schema character limit; stores numeric limit only.';
comment on column test_design_context_policy_override.change_reason_code is 'Enum-like change reason code; no free-form notes are stored.';
comment on column test_design_context_policy_override.approval_reason_code is 'Enum-like approval reason code; no approval notes are stored.';
comment on column test_design_context_policy_override.requested_by is 'Requester identity snapshot for audit attribution.';
comment on column test_design_context_policy_override.approved_by is 'Approver identity snapshot for audit attribution.';
comment on column test_design_context_policy_override.created_at is 'Creation timestamp for override request ordering.';
comment on column test_design_context_policy_override.updated_at is 'Last status or metadata update timestamp.';
