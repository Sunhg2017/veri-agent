-- WP5 test design validation.
-- Every query returns: check_name, status, details.

with expected(table_name) as (
    values
        ('test_design_task'),
        ('test_design_candidate'),
        ('test_design_review_record'),
        ('test_design_publish_record')
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
    'wp5.tables_exist' as check_name,
    case when count(*) = 0 then 'PASS' else 'FAIL' end as status,
    coalesce(string_agg(table_name, ', ' order by table_name), 'all WP5 tables exist') as details
from missing;

with expected(column_name) as (
    values
        ('idempotency_key'),
        ('request_digest')
),
missing as (
    select e.column_name
    from expected e
    left join information_schema.columns c
        on c.table_schema = current_schema()
       and c.table_name = 'test_design_task'
       and c.column_name = e.column_name
    where c.column_name is null
)
select
    'wp5.task_idempotency_columns_exist' as check_name,
    case when count(*) = 0 then 'PASS' else 'FAIL' end as status,
    coalesce(string_agg(column_name, ', ' order by column_name), 'WP5 task idempotency columns exist') as details
from missing;

with expected(index_name) as (
    values
        ('uk_test_design_task_project_idempotency')
),
missing as (
    select e.index_name
    from expected e
    left join pg_indexes i
        on i.schemaname = current_schema()
       and i.tablename = 'test_design_task'
       and i.indexname = e.index_name
    where i.indexname is null
)
select
    'wp5.task_idempotency_indexes_exist' as check_name,
    case when count(*) = 0 then 'PASS' else 'FAIL' end as status,
    coalesce(string_agg(index_name, ', ' order by index_name), 'WP5 task idempotency indexes exist') as details
from missing;

with expected(table_name, constraint_name) as (
    values
        ('test_design_task','ck_test_design_task_status'),
        ('test_design_candidate','ck_test_design_candidate_status'),
        ('test_design_candidate','ck_test_design_candidate_coverage'),
        ('test_design_candidate','ck_test_design_candidate_priority'),
        ('test_design_review_record','ck_test_design_review_action'),
        ('test_design_publish_record','ck_test_design_publish_action'),
        ('test_design_publish_record','ck_test_design_publish_result')
),
missing as (
    select e.table_name || '.' || e.constraint_name as item
    from expected e
    left join pg_constraint c
        on c.conname = e.constraint_name
       and c.conrelid = (current_schema() || '.' || e.table_name)::regclass
    where c.oid is null
)
select
    'wp5.constraints_exist' as check_name,
    case when count(*) = 0 then 'PASS' else 'FAIL' end as status,
    coalesce(string_agg(item, ', ' order by item), 'WP5 status and enum constraints exist') as details
from missing;

with found as (
    select table_name || '.tenant_id' as item
    from information_schema.columns
    where table_schema = current_schema()
      and table_name like 'test_design_%'
      and column_name = 'tenant_id'
)
select
    'wp5.no_tenant_id' as check_name,
    case when count(*) = 0 then 'PASS' else 'FAIL' end as status,
    coalesce(string_agg(item, ', ' order by item), 'WP5 does not reintroduce tenant_id') as details
from found;

with sensitive(column_name) as (
    values ('prompt_text'), ('prompt_plaintext'), ('secret_value'), ('api_key'), ('raw_prompt')
),
found as (
    select c.table_name || '.' || c.column_name as item
    from information_schema.columns c
    join sensitive s on lower(c.column_name) = s.column_name
    where c.table_schema = current_schema()
      and c.table_name like 'test_design_%'
)
select
    'wp5.no_raw_prompt_or_secret_columns' as check_name,
    case when count(*) = 0 then 'PASS' else 'FAIL' end as status,
    coalesce(string_agg(item, ', ' order by item), 'WP5 stores prompt/model references only') as details
from found;

with expected(code) as (
    values
        ('testDesign:read'),
        ('testDesign:generate'),
        ('testDesign:review'),
        ('testDesign:publish'),
        ('testDesign:export')
),
missing as (
    select e.code
    from expected e
    left join rbac_permission p on p.code = e.code
       and p.status = 'ENABLED'
    where p.id is null
)
select
    'wp5.permissions_seeded' as check_name,
    case when count(*) = 0 then 'PASS' else 'FAIL' end as status,
    coalesce(string_agg(code, ', ' order by code), 'WP5 permissions are seeded') as details
from missing;
