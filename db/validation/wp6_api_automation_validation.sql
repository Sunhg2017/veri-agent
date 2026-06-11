-- WP6 API automation validation.
-- Every query returns: check_name, status, details.

with expected(table_name) as (
    values
        ('api_automation_spec'),
        ('api_automation_endpoint_snapshot')
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
    'wp6.tables_exist' as check_name,
    case when count(*) = 0 then 'PASS' else 'FAIL' end as status,
    coalesce(string_agg(table_name, ', ' order by table_name), 'all WP6 tables exist') as details
from missing;

with expected(table_name, column_name) as (
    values
        ('api_automation_spec','id'),
        ('api_automation_spec','project_id'),
        ('api_automation_spec','source_type'),
        ('api_automation_spec','spec_digest'),
        ('api_automation_spec','sanitized_spec_json'),
        ('api_automation_spec','parse_summary_json'),
        ('api_automation_spec','status'),
        ('api_automation_spec','endpoint_count'),
        ('api_automation_endpoint_snapshot','id'),
        ('api_automation_endpoint_snapshot','spec_id'),
        ('api_automation_endpoint_snapshot','project_id'),
        ('api_automation_endpoint_snapshot','http_method'),
        ('api_automation_endpoint_snapshot','path'),
        ('api_automation_endpoint_snapshot','schema_digest'),
        ('api_automation_endpoint_snapshot','diff_status'),
        ('api_automation_endpoint_snapshot','asset_api_id'),
        ('api_automation_endpoint_snapshot','diff_summary_json'),
        ('api_automation_endpoint_snapshot','last_diff_at'),
        ('api_automation_endpoint_snapshot','synced_at'),
        ('api_automation_endpoint_snapshot','sync_error_summary')
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
    'wp6.columns_exist' as check_name,
    case when count(*) = 0 then 'PASS' else 'FAIL' end as status,
    coalesce(string_agg(item, ', ' order by item), 'WP6 key columns exist') as details
from missing;

with expected(table_name, constraint_name) as (
    values
        ('api_automation_spec','ck_api_automation_spec_source_type'),
        ('api_automation_spec','ck_api_automation_spec_status'),
        ('api_automation_spec','ck_api_automation_spec_counts'),
        ('api_automation_spec','ck_api_automation_spec_digest'),
        ('api_automation_spec','ck_api_automation_spec_json_object'),
        ('api_automation_endpoint_snapshot','ck_api_automation_endpoint_method'),
        ('api_automation_endpoint_snapshot','ck_api_automation_endpoint_diff_status'),
        ('api_automation_endpoint_snapshot','ck_api_automation_endpoint_counts'),
        ('api_automation_endpoint_snapshot','ck_api_automation_endpoint_schema_digest'),
        ('api_automation_endpoint_snapshot','ck_api_automation_endpoint_diff_summary_object')
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
    'wp6.constraints_exist' as check_name,
    case when count(*) = 0 then 'PASS' else 'FAIL' end as status,
    coalesce(string_agg(item, ', ' order by item), 'WP6 constraints exist') as details
from missing;

with expected(table_name, index_name) as (
    values
        ('api_automation_spec','uk_api_automation_spec_project_digest'),
        ('api_automation_spec','idx_api_automation_spec_project_status'),
        ('api_automation_spec','idx_api_automation_spec_created'),
        ('api_automation_endpoint_snapshot','uk_api_automation_endpoint_spec_method_path'),
        ('api_automation_endpoint_snapshot','idx_api_automation_endpoint_project_method'),
        ('api_automation_endpoint_snapshot','idx_api_automation_endpoint_spec_diff'),
        ('api_automation_endpoint_snapshot','idx_api_automation_endpoint_asset_api'),
        ('api_automation_endpoint_snapshot','idx_api_automation_endpoint_last_diff')
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
    'wp6.indexes_exist' as check_name,
    case when count(*) = 0 then 'PASS' else 'FAIL' end as status,
    coalesce(string_agg(item, ', ' order by item), 'WP6 indexes exist') as details
from missing;

with expected(code) as (
    values
        ('apiAutomation:read'),
        ('apiAutomation:import'),
        ('apiAutomation:generate'),
        ('apiAutomation:review'),
        ('apiAutomation:execute'),
        ('apiAutomation:export')
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
    'wp6.permissions_seeded' as check_name,
    case when count(*) = 0 then 'PASS' else 'FAIL' end as status,
    coalesce(string_agg(code, ', ' order by code), 'WP6 permissions seeded') as details
from missing;

with missing as (
    select item
    from (
        values
            ('SuperAdmin:apiAutomation:execute'),
            ('ProjectOwner:apiAutomation:execute'),
            ('Tester:apiAutomation:execute'),
            ('Auditor:apiAutomation:export')
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
    'wp6.role_permissions_seeded' as check_name,
    case when count(*) = 0 then 'PASS' else 'FAIL' end as status,
    coalesce(string_agg(item, ', ' order by item), 'WP6 role permissions seeded') as details
from missing;
