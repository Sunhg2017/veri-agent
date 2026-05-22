-- WP4 document input validation for PostgreSQL 15+.
-- Every query returns: check_name, status, details.

with expected(table_name) as (
    values
        ('document_input_field_mapping'),
        ('document_input_source'),
        ('document_input_import'),
        ('document_input_candidate'),
        ('document_input_webhook_event'),
        ('document_input_retention_archive')
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
    'wp4.tables_exist' as check_name,
    case when count(*) = 0 then 'PASS' else 'FAIL' end as status,
    coalesce(string_agg(table_name, ', ' order by table_name), 'all WP4 document input tables exist') as details
from missing;

with expected(table_name, column_name) as (
    values
        ('document_input_field_mapping','id'), ('document_input_field_mapping','mapping_code'), ('document_input_field_mapping','name'),
        ('document_input_field_mapping','item_path'), ('document_input_field_mapping','title_path'), ('document_input_field_mapping','description_path'),
        ('document_input_field_mapping','created_at'), ('document_input_field_mapping','updated_at'), ('document_input_field_mapping','deleted_at'), ('document_input_field_mapping','version'),
        ('document_input_source','id'), ('document_input_source','source_code'), ('document_input_source','name'), ('document_input_source','source_type'),
        ('document_input_source','status'), ('document_input_source','endpoint_url'), ('document_input_source','default_project_id'),
        ('document_input_source','mapping_id'), ('document_input_source','secret_ref'), ('document_input_source','event_version'),
        ('document_input_source','mapping_version'), ('document_input_source','description'), ('document_input_source','deleted_at'), ('document_input_source','version'),
        ('document_input_import','id'), ('document_input_import','project_id'), ('document_input_import','source_id'), ('document_input_import','source_code'),
        ('document_input_import','source_type'), ('document_input_import','source_ref'), ('document_input_import','source_url'), ('document_input_import','title'),
        ('document_input_import','status'), ('document_input_import','total_parsed'), ('document_input_import','total_created'),
        ('document_input_import','created_requirement_ids'), ('document_input_import','raw_digest'), ('document_input_import','deleted_at'), ('document_input_import','version'),
        ('document_input_candidate','id'), ('document_input_candidate','import_id'), ('document_input_candidate','project_id'), ('document_input_candidate','title'),
        ('document_input_candidate','description'), ('document_input_candidate','priority'), ('document_input_candidate','acceptance_criteria'),
        ('document_input_candidate','tags'), ('document_input_candidate','status'), ('document_input_candidate','source_ref'),
        ('document_input_candidate','source_fragment'), ('document_input_candidate','external_requirement_id'), ('document_input_candidate','confidence'),
        ('document_input_candidate','parse_source'), ('document_input_candidate','model_invocation_id'), ('document_input_candidate','model_provider_name'),
        ('document_input_candidate','model_name'),
        ('document_input_candidate','asset_requirement_id'), ('document_input_candidate','error_message'), ('document_input_candidate','ignored_reason'),
        ('document_input_candidate','confirmed_by'), ('document_input_candidate','confirmed_at'), ('document_input_candidate','deleted_at'), ('document_input_candidate','version'),
        ('document_input_webhook_event','id'), ('document_input_webhook_event','source_id'), ('document_input_webhook_event','import_id'),
        ('document_input_webhook_event','source_code'), ('document_input_webhook_event','event_id'), ('document_input_webhook_event','idempotency_key'),
        ('document_input_webhook_event','event_type'), ('document_input_webhook_event','event_version'), ('document_input_webhook_event','signature_status'),
        ('document_input_webhook_event','status'), ('document_input_webhook_event','payload_digest'), ('document_input_webhook_event','raw_payload'),
        ('document_input_webhook_event','error_message'), ('document_input_webhook_event','retry_count'), ('document_input_webhook_event','replay_by'),
        ('document_input_webhook_event','replay_at'), ('document_input_webhook_event','replay_trace_id'), ('document_input_webhook_event','received_at'),
        ('document_input_webhook_event','processed_at'), ('document_input_webhook_event','deleted_at'), ('document_input_webhook_event','version'),
        ('document_input_retention_archive','id'), ('document_input_retention_archive','record_type'), ('document_input_retention_archive','record_id'),
        ('document_input_retention_archive','project_id'), ('document_input_retention_archive','source_code'), ('document_input_retention_archive','payload_digest'),
        ('document_input_retention_archive','original_created_at'), ('document_input_retention_archive','snapshot_json'), ('document_input_retention_archive','archived_at')
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
    'wp4.key_columns_exist' as check_name,
    case when count(*) = 0 then 'PASS' else 'FAIL' end as status,
    coalesce(string_agg(item, ', ' order by item), 'all WP4 key columns exist') as details
from missing;

with expected(table_name, index_name) as (
    values
        ('document_input_field_mapping','uk_document_input_field_mapping_code'),
        ('document_input_source','uk_document_input_source_code'),
        ('document_input_source','idx_document_input_source_type'),
        ('document_input_source','idx_document_input_source_status'),
        ('document_input_source','idx_document_input_source_project'),
        ('document_input_source','idx_document_input_source_secret_ref'),
        ('document_input_import','idx_document_input_import_project_created'),
        ('document_input_import','idx_document_input_import_source'),
        ('document_input_import','idx_document_input_import_status'),
        ('document_input_import','idx_document_input_import_source_type'),
        ('document_input_candidate','idx_document_input_candidate_import'),
        ('document_input_candidate','idx_document_input_candidate_project_status'),
        ('document_input_candidate','idx_document_input_candidate_external'),
        ('document_input_candidate','idx_document_input_candidate_model_invocation'),
        ('document_input_webhook_event','idx_document_input_webhook_source_received'),
        ('document_input_webhook_event','idx_document_input_webhook_status_received'),
        ('document_input_webhook_event','uk_document_input_webhook_event_id'),
        ('document_input_webhook_event','uk_document_input_webhook_idempotency'),
        ('document_input_retention_archive','uk_document_input_retention_archive_record'),
        ('document_input_retention_archive','idx_document_input_retention_archive_type_time'),
        ('document_input_retention_archive','idx_document_input_retention_archive_project'),
        ('document_input_retention_archive','idx_document_input_retention_archive_source')
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
    'wp4.key_indexes_exist' as check_name,
    case when count(*) = 0 then 'PASS' else 'FAIL' end as status,
    coalesce(string_agg(item, ', ' order by item), 'all WP4 key indexes exist') as details
from missing;

with expected(constraint_name) as (
    values
        ('ck_document_input_source_type'),
        ('ck_document_input_source_status'),
        ('ck_document_input_source_event_version'),
        ('ck_document_input_import_type'),
        ('ck_document_input_import_status'),
        ('ck_document_input_import_total'),
        ('ck_document_input_candidate_status'),
        ('ck_document_input_candidate_parse_source'),
        ('ck_document_input_webhook_signature'),
        ('ck_document_input_webhook_status'),
        ('ck_document_input_webhook_retry'),
        ('ck_document_input_retention_archive_type')
),
missing as (
    select e.constraint_name
    from expected e
    left join pg_constraint c
        on c.connamespace = current_schema()::regnamespace
       and c.conname = e.constraint_name
    where c.conname is null
)
select
    'wp4.check_constraints_exist' as check_name,
    case when count(*) = 0 then 'PASS' else 'FAIL' end as status,
    coalesce(string_agg(constraint_name, ', ' order by constraint_name), 'all WP4 check constraints exist') as details
from missing;

with constraint_defs as (
    select c.conname, pg_get_constraintdef(c.oid) as definition
    from pg_constraint c
    where c.connamespace = current_schema()::regnamespace
      and c.conname in ('ck_document_input_source_type', 'ck_document_input_import_type')
)
select
    'wp4.binary_source_types_allowed' as check_name,
    case when count(*) filter (where definition like '%WORD%' and definition like '%PDF%' and definition like '%OCR%') = 2 then 'PASS' else 'FAIL' end as status,
    coalesce(string_agg(conname || '=' || definition, '; ' order by conname), 'source/import type constraints missing') as details
from constraint_defs;

with constraint_def as (
    select pg_get_constraintdef(c.oid) as definition
    from pg_constraint c
    where c.connamespace = current_schema()::regnamespace
      and c.conname = 'ck_document_input_webhook_status'
)
select
    'wp4.webhook_status_dead_letter_allowed' as check_name,
    case when exists (select 1 from constraint_def where definition like '%DEAD_LETTER%') then 'PASS' else 'FAIL' end as status,
    coalesce((select definition from constraint_def), 'ck_document_input_webhook_status missing') as details;

with found as (
    select table_name || '.tenant_id' as item
    from information_schema.columns
    where table_schema = current_schema()
      and table_name like 'document_input_%'
      and column_name = 'tenant_id'
)
select
    'wp4.no_tenant_id_columns' as check_name,
    case when count(*) = 0 then 'PASS' else 'FAIL' end as status,
    coalesce(string_agg(item, ', ' order by item), 'WP4 document input tables have no tenant_id columns') as details
from found;

with default_mapping as (
    select id
    from document_input_field_mapping
    where mapping_code = 'default'
      and deleted_at is null
)
select
    'wp4.default_mapping_seeded' as check_name,
    case when count(*) = 1 then 'PASS' else 'FAIL' end as status,
    'default mapping rows=' || count(*)::text as details
from default_mapping;
