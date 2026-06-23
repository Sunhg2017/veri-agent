-- WP9 execution orchestration validation.
-- Every query returns: check_name, status, details.

with expected(table_name) as (
    values
        ('execution_plan'),
        ('execution_plan_node'),
        ('execution_run'),
        ('execution_node_run'),
        ('execution_run_log'),
        ('execution_trigger'),
        ('execution_trigger_event'),
        ('execution_queue_claim')
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
    'wp9.tables_exist' as check_name,
    case when count(*) = 0 then 'PASS' else 'FAIL' end as status,
    coalesce(string_agg(table_name, ', ' order by table_name), 'WP9 execution tables exist') as details
from missing;

with expected(table_name, column_name) as (
    values
        ('execution_plan','id'),
        ('execution_plan','project_id'),
        ('execution_plan','name'),
        ('execution_plan','status'),
        ('execution_plan','environment_key'),
        ('execution_plan','trigger_policy_json'),
        ('execution_plan','dag_digest'),
        ('execution_plan','description'),
        ('execution_plan','archived_at'),
        ('execution_plan_node','id'),
        ('execution_plan_node','plan_id'),
        ('execution_plan_node','node_key'),
        ('execution_plan_node','node_type'),
        ('execution_plan_node','dependency_keys'),
        ('execution_plan_node','input_summary_json'),
        ('execution_plan_node','failure_policy'),
        ('execution_plan_node','timeout_seconds'),
        ('execution_plan_node','retry_policy_json'),
        ('execution_run','id'),
        ('execution_run','plan_id'),
        ('execution_run','project_id'),
        ('execution_run','status'),
        ('execution_run','trigger_type'),
        ('execution_run','request_key'),
        ('execution_run','source_event_id'),
        ('execution_run','attempt'),
        ('execution_run','trace_id'),
        ('execution_run','result_summary_json'),
        ('execution_run','error_code'),
        ('execution_run','error_summary'),
        ('execution_node_run','id'),
        ('execution_node_run','run_id'),
        ('execution_node_run','plan_node_id'),
        ('execution_node_run','status'),
        ('execution_node_run','attempt'),
        ('execution_node_run','runner_type'),
        ('execution_node_run','external_run_id'),
        ('execution_node_run','result_summary_json'),
        ('execution_node_run','heartbeat_at'),
        ('execution_run_log','id'),
        ('execution_run_log','run_id'),
        ('execution_run_log','node_run_id'),
        ('execution_run_log','level'),
        ('execution_run_log','stage'),
        ('execution_run_log','message'),
        ('execution_run_log','metadata_json'),
        ('execution_run_log','event_at'),
        ('execution_trigger','id'),
        ('execution_trigger','plan_id'),
        ('execution_trigger','trigger_type'),
        ('execution_trigger','status'),
        ('execution_trigger','config_digest'),
        ('execution_trigger','config_summary_json'),
        ('execution_trigger','secret_ref'),
        ('execution_trigger','secret_ref_digest'),
        ('execution_trigger','next_fire_at'),
        ('execution_trigger','last_fire_at'),
        ('execution_trigger_event','id'),
        ('execution_trigger_event','trigger_id'),
        ('execution_trigger_event','source_event_id'),
        ('execution_trigger_event','request_digest'),
        ('execution_trigger_event','status'),
        ('execution_trigger_event','run_id'),
        ('execution_trigger_event','received_at'),
        ('execution_trigger_event','trace_id'),
        ('execution_queue_claim','id'),
        ('execution_queue_claim','node_run_id'),
        ('execution_queue_claim','claim_token'),
        ('execution_queue_claim','worker_id'),
        ('execution_queue_claim','heartbeat_at'),
        ('execution_queue_claim','expires_at'),
        ('execution_queue_claim','status')
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
    'wp9.columns_exist' as check_name,
    case when count(*) = 0 then 'PASS' else 'FAIL' end as status,
    coalesce(string_agg(item, ', ' order by item), 'WP9 key columns exist') as details
from missing;

with expected(table_name, constraint_name) as (
    values
        ('execution_plan','ck_execution_plan_status'),
        ('execution_plan','ck_execution_plan_digest'),
        ('execution_plan','ck_execution_plan_trigger_policy_json'),
        ('execution_plan_node','ck_execution_plan_node_type'),
        ('execution_plan_node','ck_execution_plan_node_key'),
        ('execution_plan_node','ck_execution_plan_node_failure_policy'),
        ('execution_plan_node','ck_execution_plan_node_timeout'),
        ('execution_plan_node','ck_execution_plan_node_json'),
        ('execution_plan_node','uk_execution_plan_node_key'),
        ('execution_run','ck_execution_run_status'),
        ('execution_run','ck_execution_run_trigger_type'),
        ('execution_run','ck_execution_run_attempt'),
        ('execution_run','ck_execution_run_result_json'),
        ('execution_node_run','ck_execution_node_run_status'),
        ('execution_node_run','ck_execution_node_run_runner_type'),
        ('execution_node_run','ck_execution_node_run_attempt'),
        ('execution_node_run','ck_execution_node_run_result_json'),
        ('execution_node_run','uk_execution_node_run_attempt'),
        ('execution_run_log','ck_execution_run_log_level'),
        ('execution_run_log','ck_execution_run_log_stage'),
        ('execution_run_log','ck_execution_run_log_message'),
        ('execution_run_log','ck_execution_run_log_metadata_json'),
        ('execution_trigger','ck_execution_trigger_type'),
        ('execution_trigger','ck_execution_trigger_status'),
        ('execution_trigger','ck_execution_trigger_config_digest'),
        ('execution_trigger','ck_execution_trigger_config_summary_json'),
        ('execution_trigger','ck_execution_trigger_secret_ref'),
        ('execution_trigger','ck_execution_trigger_secret_digest'),
        ('execution_trigger_event','ck_execution_trigger_event_status'),
        ('execution_trigger_event','ck_execution_trigger_event_digest'),
        ('execution_trigger_event','uk_execution_trigger_event_source'),
        ('execution_queue_claim','ck_execution_queue_claim_status'),
        ('execution_queue_claim','uk_execution_queue_claim_token')
),
missing as (
    select e.table_name || '.' || e.constraint_name as item
    from expected e
    left join information_schema.table_constraints tc
        on tc.table_schema = current_schema()
       and tc.table_name = e.table_name
       and tc.constraint_name = e.constraint_name
    where tc.constraint_name is null
)
select
    'wp9.constraints_exist' as check_name,
    case when count(*) = 0 then 'PASS' else 'FAIL' end as status,
    coalesce(string_agg(item, ', ' order by item), 'WP9 constraints exist') as details
from missing;

with expected(table_name, index_name) as (
    values
        ('execution_plan','idx_execution_plan_project_status'),
        ('execution_plan','idx_execution_plan_dag_digest'),
        ('execution_plan_node','idx_execution_plan_node_plan_type'),
        ('execution_run','uk_execution_run_plan_request_key'),
        ('execution_run','idx_execution_run_project_status'),
        ('execution_run','idx_execution_run_plan_created'),
        ('execution_run','idx_execution_run_trace'),
        ('execution_node_run','idx_execution_node_run_run_status'),
        ('execution_node_run','idx_execution_node_run_heartbeat'),
        ('execution_node_run','idx_execution_node_run_external'),
        ('execution_node_run','idx_execution_node_run_plan_node'),
        ('execution_run_log','idx_execution_run_log_run_event'),
        ('execution_run_log','idx_execution_run_log_node_event'),
        ('execution_trigger','idx_execution_trigger_plan_status'),
        ('execution_trigger','idx_execution_trigger_next_fire'),
        ('execution_trigger','idx_execution_trigger_secret_digest'),
        ('execution_trigger_event','idx_execution_trigger_event_trigger_status'),
        ('execution_trigger_event','idx_execution_trigger_event_run'),
        ('execution_queue_claim','uk_execution_queue_claim_active_node'),
        ('execution_queue_claim','idx_execution_queue_claim_status_expires')
),
missing as (
    select e.table_name || '.' || e.index_name as item
    from expected e
    left join pg_indexes i
        on i.schemaname = current_schema()
       and i.tablename = e.table_name
       and i.indexname = e.index_name
    where i.indexname is null
)
select
    'wp9.indexes_exist' as check_name,
    case when count(*) = 0 then 'PASS' else 'FAIL' end as status,
    coalesce(string_agg(item, ', ' order by item), 'WP9 indexes exist') as details
from missing;

with expected(code) as (
    values
        ('execution:read'),
        ('execution:manage'),
        ('execution:trigger'),
        ('execution:admin'),
        ('execution:export')
),
missing as (
    select e.code
    from expected e
    left join rbac_permission p
        on p.code = e.code
       and p.status = 'ENABLED'
    where p.code is null
)
select
    'wp9.permissions_seeded' as check_name,
    case when count(*) = 0 then 'PASS' else 'FAIL' end as status,
    coalesce(string_agg(code, ', ' order by code), 'WP9 permissions seeded') as details
from missing;

with missing as (
    select item
    from (
        values
            ('SuperAdmin:execution:admin'),
            ('PlatformAdmin:execution:admin'),
            ('ProjectOwner:execution:manage'),
            ('ProjectOwner:execution:export'),
            ('AppOwner:execution:manage'),
            ('Tester:execution:trigger'),
            ('Auditor:execution:export')
    ) as expected(item)
    where not exists (
        select 1
        from rbac_role_permission rp
        join rbac_role r on r.id = rp.role_id and r.deleted_at is null
        join rbac_permission p on p.id = rp.permission_id and p.status = 'ENABLED'
        where (r.code || ':' || p.code) = expected.item
          and rp.deleted_at is null
    )
)
select
    'wp9.role_permissions_seeded' as check_name,
    case when count(*) = 0 then 'PASS' else 'FAIL' end as status,
    coalesce(string_agg(item, ', ' order by item), 'WP9 role permissions seeded') as details
from missing;

with expected(config_key) as (
    values
        ('execution.audit_events'),
        ('execution.scheduler_enabled'),
        ('execution.webhook_enabled'),
        ('execution.cron_enabled')
),
missing as (
    select e.config_key
    from expected e
    left join base_config c
        on c.config_key = e.config_key
       and c.scope_type = 'SYSTEM'
       and c.scope_id is null
       and c.deleted_at is null
    where c.config_key is null
)
select
    'wp9.base_config_seeded' as check_name,
    case when count(*) = 0 then 'PASS' else 'FAIL' end as status,
    coalesce(string_agg(config_key, ', ' order by config_key), 'WP9 base config seeded') as details
from missing;
