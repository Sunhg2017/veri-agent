-- WP2 model-access schema validation.
-- Every query returns: check_name, status, details.

with expected(table_name) as (
    values
        ('ma_model_provider'),
        ('ma_prompt_template'),
        ('ma_invocation_log'),
        ('ma_invocation_job'),
        ('ma_model_policy_override')
),
missing as (
    select e.table_name
    from expected e
    left join information_schema.tables t
        on t.table_schema = current_schema()
       and t.table_name = e.table_name
       and t.table_type = 'BASE TABLE'
    where t.table_name is null
)
select
    'wp2.schema.core_tables_exist' as check_name,
    case when count(*) = 0 then 'PASS' else 'FAIL' end as status,
    coalesce(string_agg(table_name, ', ' order by table_name), 'all WP2 core tables exist') as details
from missing;

with expected(table_name, column_name) as (
    values
        ('ma_model_provider','id'), ('ma_model_provider','provider_type'), ('ma_model_provider','api_key_ref'), ('ma_model_provider','priority'),
        ('ma_model_provider','routing_group'), ('ma_model_provider','capabilities'),
        ('ma_model_provider','version'), ('ma_model_provider','created_by'), ('ma_model_provider','deleted_at'),
        ('ma_prompt_template','prompt_key'), ('ma_prompt_template','version'), ('ma_prompt_template','content'), ('ma_prompt_template','status'),
        ('ma_prompt_template','high_risk'), ('ma_prompt_template','approval_status'), ('ma_prompt_template','approved_by'), ('ma_prompt_template','approved_at'), ('ma_prompt_template','approval_note'),
        ('ma_prompt_template','created_by'), ('ma_prompt_template','deleted_at'), ('ma_prompt_template','version'),
        ('ma_invocation_log','project_id'), ('ma_invocation_log','sensitivity_level'), ('ma_invocation_log','prompt_digest'), ('ma_invocation_log','request_preview'),
        ('ma_invocation_log','routing_rule_name'), ('ma_invocation_log','routing_group'), ('ma_invocation_log','model_capability'),
        ('ma_invocation_log','input_tokens'), ('ma_invocation_log','output_tokens'), ('ma_invocation_log','total_cost'), ('ma_invocation_log','actor_service'),
        ('ma_invocation_log','role_scope'), ('ma_invocation_log','created_by'), ('ma_invocation_log','version'),
        ('ma_invocation_job','job_id'), ('ma_invocation_job','status'), ('ma_invocation_job','request_json'), ('ma_invocation_job','actor_service'),
        ('ma_invocation_job','delegated_user_id'), ('ma_invocation_job','principal_roles'), ('ma_invocation_job','trace_id'), ('ma_invocation_job','invocation_id'), ('ma_invocation_job','response_json'),
        ('ma_invocation_job','created_at'), ('ma_invocation_job','started_at'), ('ma_invocation_job','finished_at'), ('ma_invocation_job','version')
        ,('ma_model_policy_override','id'), ('ma_model_policy_override','scope_type'), ('ma_model_policy_override','scope_key'),
        ('ma_model_policy_override','enabled'), ('ma_model_policy_override','model_invocation_enabled'), ('ma_model_policy_override','public_model_allowed'),
        ('ma_model_policy_override','daily_budget_limit'), ('ma_model_policy_override','cost_alert_warning_ratio'),
        ('ma_model_policy_override','budget_overrun_action'), ('ma_model_policy_override','routing_group'),
        ('ma_model_policy_override','reason'), ('ma_model_policy_override','updated_by'), ('ma_model_policy_override','version')
),
missing as (
    select e.table_name || '.' || e.column_name as item
    from expected e
    left join information_schema.columns c
        on c.table_schema = current_schema()
       and c.table_name = e.table_name
       and c.column_name = e.column_name
    where c.column_name is null
)
select
    'wp2.schema.key_columns_exist' as check_name,
    case when count(*) = 0 then 'PASS' else 'FAIL' end as status,
    coalesce(string_agg(item, ', ' order by item), 'all WP2 key columns exist') as details
from missing;

with expected(index_name) as (
    values
        ('uk_ma_model_provider_name'),
        ('idx_ma_model_provider_enabled_priority'),
        ('uk_ma_prompt_template_key_version'),
        ('uk_ma_prompt_template_one_active'),
        ('idx_ma_invocation_scope_time'),
        ('idx_ma_invocation_provider_time'),
        ('idx_ma_invocation_status_time'),
        ('idx_ma_invocation_sensitivity_time'),
        ('idx_ma_model_provider_deleted'),
        ('idx_ma_prompt_template_deleted'),
        ('idx_ma_prompt_template_approval_status'),
        ('idx_ma_model_provider_routing_group'),
        ('idx_ma_invocation_routing_time'),
        ('idx_ma_invocation_job_status_created'),
        ('idx_ma_invocation_job_trace_id'),
        ('idx_ma_invocation_job_invocation_id'),
        ('uk_ma_model_policy_override_scope'),
        ('idx_ma_model_policy_override_enabled_scope'),
        ('idx_ma_invocation_role_scope_time')
),
missing as (
    select e.index_name as item
    from expected e
    left join pg_indexes i
        on i.schemaname = current_schema()
       and i.indexname = e.index_name
    where i.indexname is null
)
select
    'wp2.schema.key_indexes_exist' as check_name,
    case when count(*) = 0 then 'PASS' else 'FAIL' end as status,
    coalesce(string_agg(item, ', ' order by item), 'all WP2 key indexes exist') as details
from missing;

with plaintext_risk as (
    select table_name || '.' || column_name as item
    from information_schema.columns
    where table_schema = current_schema()
      and table_name like 'ma_%'
      and column_name in ('api_key', 'secret_value', 'prompt_plaintext', 'request_body')
)
select
    'wp2.security.no_secret_plaintext_columns' as check_name,
    case when count(*) = 0 then 'PASS' else 'FAIL' end as status,
    coalesce(string_agg(item, ', ' order by item), 'no plaintext secret/prompt body columns found') as details
from plaintext_risk;

select
    'wp2.schema.sensitivity_constraint_accepts_restricted' as check_name,
    case when count(*) = 1 then 'PASS' else 'FAIL' end as status,
    coalesce(max(pg_get_constraintdef(oid)), 'ck_ma_invocation_sensitivity missing RESTRICTED') as details
from pg_constraint
where conname = 'ck_ma_invocation_sensitivity'
  and conrelid = 'ma_invocation_log'::regclass
  and pg_get_constraintdef(oid) like '%RESTRICTED%';

select
    'wp2.schema.prompt_approval_constraint_accepts_statuses' as check_name,
    case when count(*) = 1 then 'PASS' else 'FAIL' end as status,
    coalesce(max(pg_get_constraintdef(oid)), 'ck_ma_prompt_template_approval_status missing expected statuses') as details
from pg_constraint
where conname = 'ck_ma_prompt_template_approval_status'
  and conrelid = 'ma_prompt_template'::regclass
  and pg_get_constraintdef(oid) like '%NOT_REQUIRED%'
  and pg_get_constraintdef(oid) like '%PENDING%'
  and pg_get_constraintdef(oid) like '%APPROVED%'
  and pg_get_constraintdef(oid) like '%REJECTED%';

select
    'wp2.schema.async_job_status_constraint_accepts_statuses' as check_name,
    case when count(*) = 1 then 'PASS' else 'FAIL' end as status,
    coalesce(max(pg_get_constraintdef(oid)), 'ck_ma_invocation_job_status missing expected statuses') as details
from pg_constraint
where conname = 'ck_ma_invocation_job_status'
  and conrelid = 'ma_invocation_job'::regclass
  and pg_get_constraintdef(oid) like '%QUEUED%'
  and pg_get_constraintdef(oid) like '%RUNNING%'
  and pg_get_constraintdef(oid) like '%SUCCEEDED%'
  and pg_get_constraintdef(oid) like '%FAILED%'
  and pg_get_constraintdef(oid) like '%CANCELLED%';

select
    'wp2.schema.policy_scope_constraint_accepts_expected_scopes' as check_name,
    case when count(*) = 1 then 'PASS' else 'FAIL' end as status,
    coalesce(max(pg_get_constraintdef(oid)), 'ck_ma_model_policy_override_scope missing expected scopes') as details
from pg_constraint
where conname = 'ck_ma_model_policy_override_scope'
  and conrelid = 'ma_model_policy_override'::regclass
  and pg_get_constraintdef(oid) like '%PLATFORM%'
  and pg_get_constraintdef(oid) like '%ROLE%'
  and pg_get_constraintdef(oid) like '%PROJECT%'
  and pg_get_constraintdef(oid) like '%ENVIRONMENT%';

with found as (
    select table_name || '.tenant_id' as item
    from information_schema.columns
    where table_schema = current_schema()
      and table_name like 'ma_%'
      and column_name = 'tenant_id'
)
select
    'wp2.schema.no_tenant_id_columns' as check_name,
    case when count(*) = 0 then 'PASS' else 'FAIL' end as status,
    coalesce(string_agg(item, ', ' order by item), 'no tenant_id columns remain in WP2 model-access tables') as details
from found;

select
    'wp2.seed.default_local_provider_exists' as check_name,
    case when count(*) = 1 then 'PASS' else 'FAIL' end as status,
    coalesce(max(name || ':' || status || ':' || priority), 'default local provider missing') as details
from ma_model_provider
where id = '00000000-0000-0000-0000-000000000201'
  and name = 'local-echo-primary'
  and provider_type = 'LOCAL_ECHO'
  and api_key_ref = 'local://echo'
  and routing_group = 'default'
  and capabilities like '%CHAT%'
  and status = 'ENABLED'
  and priority = 10;

select
    'wp2.seed.default_active_prompt_exists' as check_name,
    case when count(*) = 1 then 'PASS' else 'FAIL' end as status,
    coalesce(max(prompt_key || ':v' || version || ':' || status), 'default active prompt missing') as details
from ma_prompt_template
where id = '00000000-0000-0000-0000-000000000301'
  and prompt_key = 'test-case-design'
  and version = 1
  and status = 'ACTIVE';
