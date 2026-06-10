-- WP2 P2 runtime policy operations.
-- Non-engineering operators can manage bounded model-access guardrails without deployment changes.

create table if not exists ma_model_policy_override (
    id uuid primary key default gen_random_uuid(),
    scope_type varchar(32) not null,
    scope_key varchar(128) not null,
    enabled boolean not null default true,
    model_invocation_enabled boolean,
    public_model_allowed boolean,
    daily_budget_limit numeric(18, 8),
    cost_alert_warning_ratio numeric(6, 4),
    budget_overrun_action varchar(16),
    routing_group varchar(64),
    reason varchar(300),
    updated_by varchar(128) not null default 'system',
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    version bigint not null default 0,
    constraint uk_ma_model_policy_override_scope unique (scope_type, scope_key),
    constraint ck_ma_model_policy_override_scope check (scope_type in ('PLATFORM', 'ROLE', 'PROJECT', 'ENVIRONMENT')),
    constraint ck_ma_model_policy_override_platform_key check (scope_type <> 'PLATFORM' or scope_key = 'GLOBAL'),
    constraint ck_ma_model_policy_override_budget check (daily_budget_limit is null or daily_budget_limit >= 0),
    constraint ck_ma_model_policy_override_ratio check (
        cost_alert_warning_ratio is null
        or (cost_alert_warning_ratio > 0 and cost_alert_warning_ratio <= 1)
    ),
    constraint ck_ma_model_policy_override_action check (
        budget_overrun_action is null
        or budget_overrun_action in ('BLOCK', 'FALLBACK')
    )
);

create index if not exists idx_ma_model_policy_override_enabled_scope
    on ma_model_policy_override (enabled, scope_type, scope_key);

alter table ma_invocation_log
    add column if not exists role_scope varchar(128);

create index if not exists idx_ma_invocation_role_scope_time
    on ma_invocation_log (role_scope, created_at desc)
    where role_scope is not null;

alter table ma_invocation_job
    add column if not exists principal_roles varchar(512);

comment on table ma_model_policy_override is 'WP2 runtime model-access policy overrides maintained by operators. Stores bounded switches, budgets and route groups only.';
comment on column ma_model_policy_override.id is 'Primary key for one runtime model-access policy override.';
comment on column ma_model_policy_override.scope_type is 'Policy scope: PLATFORM, ROLE, PROJECT or ENVIRONMENT. Effective precedence is environment > project > role > platform.';
comment on column ma_model_policy_override.scope_key is 'Scope key. PLATFORM uses GLOBAL; ROLE uses role code; PROJECT/ENVIRONMENT use logical WP1 resource IDs.';
comment on column ma_model_policy_override.enabled is 'Whether this override participates in effective policy resolution.';
comment on column ma_model_policy_override.model_invocation_enabled is 'Nullable model invocation switch. Null inherits the lower-precedence or default policy.';
comment on column ma_model_policy_override.public_model_allowed is 'Nullable public-model access switch. Null inherits the lower-precedence or default policy.';
comment on column ma_model_policy_override.daily_budget_limit is 'Nullable daily budget cap for the scope, stored in billing cost units.';
comment on column ma_model_policy_override.cost_alert_warning_ratio is 'Nullable warning threshold ratio for budget alerts, greater than 0 and no more than 1.';
comment on column ma_model_policy_override.budget_overrun_action is 'Nullable budget overrun action. BLOCK rejects calls; FALLBACK uses configured fallback routing.';
comment on column ma_model_policy_override.routing_group is 'Nullable logical routing group selected by operators for this scope.';
comment on column ma_model_policy_override.reason is 'Operator change reason after sensitive-content masking. Must not contain secrets or prompt/request payloads.';
comment on column ma_model_policy_override.updated_by is 'Operator or service account that last changed the override.';
comment on column ma_model_policy_override.created_at is 'Creation timestamp for audit and retention checks.';
comment on column ma_model_policy_override.updated_at is 'Last update timestamp for audit and optimistic concurrency checks.';
comment on column ma_model_policy_override.version is 'Optimistic concurrency version incremented by update operations.';
comment on column ma_invocation_log.role_scope is 'Matched role policy scope used for role-level budget aggregation. Service-token invocations keep this null.';
comment on column ma_invocation_job.principal_roles is 'Comma-separated role snapshot captured at async job submission for runtime policy resolution in workers.';
