-- WP1 schema validation for single-platform deployment.
-- Every query returns: check_name, status, details.

with expected(table_name) as (
    values
        ('base_department'),
        ('base_department_manager'),
        ('base_department_member'),
        ('iam_user'),
        ('iam_session'),
        ('base_project'),
        ('base_project_department'),
        ('base_project_member'),
        ('base_application'),
        ('base_environment'),
        ('base_environment_variable'),
        ('base_config'),
        ('secret_provider'),
        ('secret_reference'),
        ('secret_local_store'),
        ('rbac_permission'),
        ('rbac_role'),
        ('rbac_role_permission'),
        ('rbac_role_binding'),
        ('audit_log'),
        ('audit_log_archive'),
        ('audit_outbox')
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
    'schema.core_tables_exist' as check_name,
    case when count(*) = 0 then 'PASS' else 'FAIL' end as status,
    coalesce(string_agg(table_name, ', ' order by table_name), 'all WP1 core tables exist') as details
from missing;

with forbidden(table_name) as (
    values
        ('base_tenant'),
        ('sys_tenant'),
        ('sys_department'),
        ('sys_user'),
        ('sys_role'),
        ('sys_permission'),
        ('auth_user'),
        ('auth_role'),
        ('auth_permission')
),
found as (
    select f.table_name
    from forbidden f
    join information_schema.tables t
        on t.table_schema = current_schema()
       and t.table_name = f.table_name
       and t.table_type = 'BASE TABLE'
)
select
    'schema.no_tenant_or_legacy_tables' as check_name,
    case when count(*) = 0 then 'PASS' else 'FAIL' end as status,
    coalesce(string_agg(table_name, ', ' order by table_name), 'no tenant or legacy core table names found') as details
from found;

with found as (
    select table_name || '.tenant_id' as item
    from information_schema.columns
    where table_schema = current_schema()
      and column_name = 'tenant_id'
)
select
    'schema.no_tenant_id_columns' as check_name,
    case when count(*) = 0 then 'PASS' else 'FAIL' end as status,
    coalesce(string_agg(item, ', ' order by item), 'no tenant_id columns remain') as details
from found;

with expected(table_name, column_name) as (
    values
        ('base_department','id'), ('base_department','parent_id'), ('base_department','code'), ('base_department','name'), ('base_department','path'), ('base_department','status'),
        ('iam_user','id'), ('iam_user','username'), ('iam_user','password_hash'), ('iam_user','display_name'), ('iam_user','status'), ('iam_user','auth_version'),
        ('iam_session','id'), ('iam_session','user_id'), ('iam_session','session_token_hash'), ('iam_session','refresh_token_hash'), ('iam_session','auth_version'), ('iam_session','expires_at'),
        ('base_project','id'), ('base_project','code'), ('base_project','name'), ('base_project','status'), ('base_project','allow_public_model'),
        ('base_application','id'), ('base_application','project_id'), ('base_application','code'), ('base_application','name'), ('base_application','app_type'), ('base_application','status'),
        ('base_environment','id'), ('base_environment','project_id'), ('base_environment','app_id'), ('base_environment','scope_type'), ('base_environment','code'), ('base_environment','env_type'), ('base_environment','health_check_json'), ('base_environment','status'),
        ('base_config','id'), ('base_config','scope_type'), ('base_config','scope_id'), ('base_config','config_key'), ('base_config','value_kind'),
        ('rbac_permission','id'), ('rbac_permission','code'), ('rbac_permission','resource_type'), ('rbac_permission','action'), ('rbac_permission','scope_mask'),
        ('rbac_role','id'), ('rbac_role','code'), ('rbac_role','name'), ('rbac_role','scope_type'), ('rbac_role','is_system'), ('rbac_role','is_builtin'),
        ('rbac_role_permission','id'), ('rbac_role_permission','role_id'), ('rbac_role_permission','permission_id'), ('rbac_role_permission','effect'),
        ('rbac_role_binding','id'), ('rbac_role_binding','subject_type'), ('rbac_role_binding','subject_id'), ('rbac_role_binding','role_id'), ('rbac_role_binding','role_code'), ('rbac_role_binding','scope_type'), ('rbac_role_binding','scope_id'),
        ('audit_log','id'), ('audit_log','trace_id'), ('audit_log','actor_type'), ('audit_log','action'), ('audit_log','resource_type'), ('audit_log','scope_type'), ('audit_log','result'),
        ('audit_log_archive','id'), ('audit_log_archive','trace_id'), ('audit_log_archive','action'), ('audit_log_archive','resource_type'), ('audit_log_archive','result'), ('audit_log_archive','archived_at'),
        ('audit_outbox','id'), ('audit_outbox','event_payload_json'), ('audit_outbox','status')
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
    'schema.key_columns_exist' as check_name,
    case when count(*) = 0 then 'PASS' else 'FAIL' end as status,
    coalesce(string_agg(item, ', ' order by item), 'all key columns exist') as details
from missing;

with expected(table_name, index_name) as (
    values
        ('base_department','uk_base_department_code'),
        ('iam_user','uk_iam_user_username'),
        ('base_project','uk_base_project_code'),
        ('base_application','uk_base_application_project_code'),
        ('base_environment','uk_base_environment_project_code'),
        ('base_environment','uk_base_environment_app_code'),
        ('rbac_permission','uk_rbac_permission_code'),
        ('rbac_role','uk_rbac_role_code'),
        ('rbac_role_permission','uk_rbac_role_permission'),
        ('rbac_role_binding','uk_rbac_role_binding_unique'),
        ('audit_log','idx_audit_log_time'),
        ('audit_log_archive','idx_audit_log_archive_created_at'),
        ('audit_outbox','idx_audit_outbox_pending'),
        ('audit_outbox','idx_audit_outbox_trace_id')
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
    'schema.key_indexes_exist' as check_name,
    case when count(*) = 0 then 'PASS' else 'FAIL' end as status,
    coalesce(string_agg(item, ', ' order by item), 'all key indexes/unique indexes exist') as details
from missing;
