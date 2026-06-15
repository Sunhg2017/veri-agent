-- WP8 test data and account pool validation.
-- Every query returns: check_name, status, details.

with expected(table_name) as (
    values
        ('test_data_set'),
        ('test_data_record'),
        ('test_data_task'),
        ('test_account_pool'),
        ('test_pooled_account'),
        ('test_account_lease'),
        ('test_account_role_matrix')
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
    'wp8.tables_exist' as check_name,
    case when count(*) = 0 then 'PASS' else 'FAIL' end as status,
    coalesce(string_agg(table_name, ', ' order by table_name), 'WP8 test data tables exist') as details
from missing;

with expected(table_name, column_name) as (
    values
        ('test_data_set','id'),
        ('test_data_set','project_id'),
        ('test_data_set','application_id'),
        ('test_data_set','environment_id'),
        ('test_data_set','code'),
        ('test_data_set','status'),
        ('test_data_set','schema_json'),
        ('test_data_set','cleanup_policy_json'),
        ('test_data_set','source_ref_digest'),
        ('test_data_record','id'),
        ('test_data_record','data_set_id'),
        ('test_data_record','project_id'),
        ('test_data_record','record_key'),
        ('test_data_record','record_digest'),
        ('test_data_record','masked_summary_json'),
        ('test_data_task','id'),
        ('test_data_task','project_id'),
        ('test_data_task','data_set_id'),
        ('test_data_task','task_type'),
        ('test_data_task','status'),
        ('test_data_task','request_key'),
        ('test_account_pool','id'),
        ('test_account_pool','project_id'),
        ('test_account_pool','application_id'),
        ('test_account_pool','environment_id'),
        ('test_account_pool','code'),
        ('test_account_pool','status'),
        ('test_account_pool','lease_policy_json'),
        ('test_account_pool','default_ttl_seconds'),
        ('test_pooled_account','id'),
        ('test_pooled_account','pool_id'),
        ('test_pooled_account','project_id'),
        ('test_pooled_account','account_key'),
        ('test_pooled_account','status'),
        ('test_pooled_account','role_tags_json'),
        ('test_pooled_account','scope_summary_json'),
        ('test_pooled_account','secret_ref_digest'),
        ('test_pooled_account','secret_ref_cipher'),
        ('test_account_lease','id'),
        ('test_account_lease','pool_id'),
        ('test_account_lease','account_id'),
        ('test_account_lease','project_id'),
        ('test_account_lease','status'),
        ('test_account_lease','holder_type'),
        ('test_account_lease','holder_ref'),
        ('test_account_lease','request_key'),
        ('test_account_lease','request_digest'),
        ('test_account_lease','lease_token_digest'),
        ('test_account_lease','expires_at'),
        ('test_account_role_matrix','id'),
        ('test_account_role_matrix','project_id'),
        ('test_account_role_matrix','pool_id'),
        ('test_account_role_matrix','role_code'),
        ('test_account_role_matrix','resource_scope_json'),
        ('test_account_role_matrix','menu_scope_json'),
        ('test_account_role_matrix','scenario_tags_json')
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
    'wp8.columns_exist' as check_name,
    case when count(*) = 0 then 'PASS' else 'FAIL' end as status,
    coalesce(string_agg(item, ', ' order by item), 'WP8 key columns exist') as details
from missing;

with expected(table_name, constraint_name) as (
    values
        ('test_data_set','ck_test_data_set_status'),
        ('test_data_set','ck_test_data_set_code'),
        ('test_data_set','ck_test_data_set_schema_json'),
        ('test_data_set','ck_test_data_set_cleanup_policy_json'),
        ('test_data_set','ck_test_data_set_sensitivity'),
        ('test_data_set','ck_test_data_set_source_type'),
        ('test_data_set','ck_test_data_set_source_ref_digest'),
        ('test_data_set','uk_test_data_set_project_code'),
        ('test_data_record','ck_test_data_record_status'),
        ('test_data_record','ck_test_data_record_key'),
        ('test_data_record','ck_test_data_record_digest'),
        ('test_data_record','ck_test_data_record_external_ref_digest'),
        ('test_data_record','ck_test_data_record_json'),
        ('test_data_record','uk_test_data_record_key'),
        ('test_data_task','ck_test_data_task_type'),
        ('test_data_task','ck_test_data_task_status'),
        ('test_data_task','ck_test_data_task_attempt'),
        ('test_data_task','ck_test_data_task_result_json'),
        ('test_account_pool','ck_test_account_pool_status'),
        ('test_account_pool','ck_test_account_pool_code'),
        ('test_account_pool','ck_test_account_pool_lease_policy_json'),
        ('test_account_pool','ck_test_account_pool_ttl'),
        ('test_account_pool','uk_test_account_pool_project_code'),
        ('test_pooled_account','ck_test_pooled_account_status'),
        ('test_pooled_account','ck_test_pooled_account_key'),
        ('test_pooled_account','ck_test_pooled_account_secret_digest'),
        ('test_pooled_account','ck_test_pooled_account_json'),
        ('test_pooled_account','ck_test_pooled_account_health'),
        ('test_pooled_account','uk_test_pooled_account_key'),
        ('test_account_lease','ck_test_account_lease_status'),
        ('test_account_lease','ck_test_account_lease_holder_type'),
        ('test_account_lease','ck_test_account_lease_request_digest'),
        ('test_account_lease','ck_test_account_lease_token_digest'),
        ('test_account_role_matrix','ck_test_account_role_matrix_json'),
        ('test_account_role_matrix','uk_test_account_role_matrix_role')
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
    'wp8.constraints_exist' as check_name,
    case when count(*) = 0 then 'PASS' else 'FAIL' end as status,
    coalesce(string_agg(item, ', ' order by item), 'WP8 constraints exist') as details
from missing;

with expected(table_name, index_name) as (
    values
        ('test_data_set','idx_test_data_set_project_status'),
        ('test_data_set','idx_test_data_set_app_env'),
        ('test_data_record','idx_test_data_record_set_status'),
        ('test_data_record','idx_test_data_record_digest'),
        ('test_data_task','uk_test_data_task_project_request_key'),
        ('test_data_task','idx_test_data_task_project_status'),
        ('test_data_task','idx_test_data_task_data_set'),
        ('test_account_pool','idx_test_account_pool_project_status'),
        ('test_account_pool','idx_test_account_pool_app_env'),
        ('test_pooled_account','idx_test_pooled_account_pool_status'),
        ('test_pooled_account','idx_test_pooled_account_secret_digest'),
        ('test_account_lease','uk_test_account_lease_active_account'),
        ('test_account_lease','uk_test_account_lease_project_request_key'),
        ('test_account_lease','idx_test_account_lease_project_status'),
        ('test_account_lease','idx_test_account_lease_expires'),
        ('test_account_role_matrix','idx_test_account_role_matrix_pool')
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
    'wp8.indexes_exist' as check_name,
    case when count(*) = 0 then 'PASS' else 'FAIL' end as status,
    coalesce(string_agg(item, ', ' order by item), 'WP8 indexes exist') as details
from missing;

with expected(code) as (
    values
        ('testData:read'),
        ('testData:manage'),
        ('testData:lease'),
        ('testData:cleanup'),
        ('testData:export')
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
    'wp8.permissions_seeded' as check_name,
    case when count(*) = 0 then 'PASS' else 'FAIL' end as status,
    coalesce(string_agg(code, ', ' order by code), 'WP8 permissions seeded') as details
from missing;

with missing as (
    select item
    from (
        values
            ('SuperAdmin:testData:cleanup'),
            ('PlatformAdmin:testData:cleanup'),
            ('ProjectOwner:testData:manage'),
            ('ProjectOwner:testData:export'),
            ('AppOwner:testData:cleanup'),
            ('Tester:testData:lease'),
            ('Developer:testData:read'),
            ('Auditor:testData:export')
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
    'wp8.role_permissions_seeded' as check_name,
    case when count(*) = 0 then 'PASS' else 'FAIL' end as status,
    coalesce(string_agg(item, ', ' order by item), 'WP8 role permissions seeded') as details
from missing;

with expected(config_key) as (
    values
        ('test_data.audit_events'),
        ('test_data.enabled'),
        ('test_data.cleanup_enabled'),
        ('test_data.export_enabled')
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
    'wp8.config_seeded' as check_name,
    case when count(*) = 0 then 'PASS' else 'FAIL' end as status,
    coalesce(string_agg(config_key, ', ' order by config_key), 'WP8 system config seeded') as details
from missing;

with expected(action) as (
    values
        ('test_data.data_set.created'),
        ('test_data.data_set.updated'),
        ('test_data.data_set.archived'),
        ('test_data.record.imported'),
        ('test_data.task.created'),
        ('test_data.task.completed'),
        ('test_data.account_pool.created'),
        ('test_data.account_pool.updated'),
        ('test_data.account_pool.archived'),
        ('test_data.account.created'),
        ('test_data.account.updated'),
        ('test_data.account.secret_ref_replaced'),
        ('test_data.lease.acquired'),
        ('test_data.lease.renewed'),
        ('test_data.lease.released'),
        ('test_data.lease.expired'),
        ('test_data.cleanup.requested'),
        ('test_data.cleanup.retried'),
        ('test_data.exported')
),
configured as (
    select jsonb_array_elements_text(c.value_json) as action
    from base_config c
    where c.config_key = 'test_data.audit_events'
      and c.scope_type = 'SYSTEM'
      and c.scope_id is null
      and c.deleted_at is null
),
missing as (
    select e.action
    from expected e
    left join configured c on c.action = e.action
    where c.action is null
)
select
    'wp8.audit_event_dictionary_seeded' as check_name,
    case when count(*) = 0 then 'PASS' else 'FAIL' end as status,
    coalesce(string_agg(action, ', ' order by action), 'WP8 audit event dictionary seeded') as details
from missing;

with expected(role_name, table_name, privilege) as (
    values
        ('wp1_app', 'test_data_set', 'SELECT'),
        ('wp1_app', 'test_data_set', 'INSERT'),
        ('wp1_app', 'test_data_set', 'UPDATE'),
        ('wp1_app', 'test_pooled_account', 'SELECT'),
        ('wp1_app', 'test_pooled_account', 'INSERT'),
        ('wp1_app', 'test_pooled_account', 'UPDATE'),
        ('wp1_migration', 'test_data_set', 'SELECT'),
        ('wp1_migration', 'test_pooled_account', 'UPDATE'),
        ('wp1_readonly', 'test_data_set', 'SELECT'),
        ('wp1_readonly', 'test_account_lease', 'SELECT')
),
missing as (
    select role_name || ':' || table_name || ':' || privilege as item
    from expected e
    where not has_table_privilege(e.role_name, e.table_name, e.privilege)
)
select
    'wp8.runtime_role_privileges' as check_name,
    case when count(*) = 0 then 'PASS' else 'FAIL' end as status,
    coalesce(string_agg(item, ', ' order by item), 'WP8 runtime roles have required privileges') as details
from missing;

with forbidden as (
    select 'test_pooled_account.secret_ref_cipher is selected by readonly role' as item
    where has_table_privilege('wp1_readonly', 'test_pooled_account', 'SELECT')
      and has_column_privilege('wp1_readonly', 'test_pooled_account', 'secret_ref_cipher', 'SELECT')
)
select
    'wp8.secret_cipher_readonly_restricted' as check_name,
    case when count(*) = 0 then 'PASS' else 'FAIL' end as status,
    coalesce(string_agg(item, ', ' order by item), 'readonly role cannot read secret_ref_cipher') as details
from forbidden;
