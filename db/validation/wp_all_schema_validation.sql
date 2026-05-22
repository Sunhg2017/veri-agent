-- Consolidated WP1+WP2+WP3+WP4 schema validation for single-platform deployment.
-- Every query returns: check_name, status, details.
-- Validates all tables from WP1 (platform base), WP2 (model access), WP3 (asset service), and WP4 (document input).

with expected(table_name) as (
    values
        -- WP1 platform base tables
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
        ('audit_outbox'),
        -- WP2 model access tables
        ('ma_model_provider'),
        ('ma_prompt_template'),
        ('ma_invocation_log'),
        -- WP3 asset service tables
        ('asset_requirement'),
        ('asset_api'),
        ('asset_page'),
        ('asset_business_flow'),
        ('asset_test_case'),
        ('asset_test_step'),
        ('asset_link'),
        ('asset_version_history'),
        -- WP4 document input tables
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
    'schema.core_tables_exist' as check_name,
    case when count(*) = 0 then 'PASS' else 'FAIL' end as status,
    coalesce(string_agg(table_name, ', ' order by table_name), 'all WP1+WP2+WP3+WP4 core tables exist') as details
from missing;

with expected(table_name, constraint_name) as (
    values
        ('asset_requirement','ck_asset_requirement_lifecycle_status'),
        ('asset_api','ck_asset_api_lifecycle_status'),
        ('asset_page','ck_asset_page_lifecycle_status'),
        ('asset_business_flow','ck_asset_business_flow_lifecycle_status'),
        ('asset_test_case','ck_asset_test_case_lifecycle_status')
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
    'schema.wp3_asset_lifecycle_constraints_exist' as check_name,
    case when count(*) = 0 then 'PASS' else 'FAIL' end as status,
    coalesce(string_agg(item, ', ' order by item), 'WP3 asset lifecycle constraints exist') as details
from missing;

with expected(change_type) as (
    values ('ARCHIVE'), ('SOFT_DELETE'), ('RESTORE')
),
constraint_def as (
    select pg_get_constraintdef(oid) as definition
    from pg_constraint
    where conrelid = 'asset_version_history'::regclass
      and conname = 'ck_asset_version_history_change_type'
),
missing as (
    select change_type
    from expected e
    where not exists (
        select 1
        from constraint_def c
        where c.definition like '%' || e.change_type || '%'
    )
)
select
    'schema.wp3_version_history_lifecycle_change_types' as check_name,
    case when count(*) = 0 then 'PASS' else 'FAIL' end as status,
    coalesce(string_agg(change_type, ', ' order by change_type), 'asset_version_history allows lifecycle change types') as details
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
        -- WP1 key columns
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
        ('audit_outbox','id'), ('audit_outbox','event_payload_json'), ('audit_outbox','status'),
        -- WP2 key columns
        ('ma_model_provider','id'), ('ma_model_provider','name'), ('ma_model_provider','provider_type'), ('ma_model_provider','status'),
        ('ma_prompt_template','id'), ('ma_prompt_template','prompt_key'), ('ma_prompt_template','name'), ('ma_prompt_template','version'), ('ma_prompt_template','content'), ('ma_prompt_template','status'),
        ('ma_invocation_log','id'), ('ma_invocation_log','project_id'), ('ma_invocation_log','model_name'), ('ma_invocation_log','status'), ('ma_invocation_log','created_at'),
        -- WP3 key columns
        ('asset_requirement','id'), ('asset_requirement','project_id'), ('asset_requirement','code'), ('asset_requirement','title'), ('asset_requirement','source'),
        ('asset_requirement','source_ref'), ('asset_requirement','source_url'), ('asset_requirement','acceptance_criteria'), ('asset_requirement','status'),
        ('asset_requirement','version'), ('asset_requirement','lifecycle_status'), ('asset_requirement','archived_at'), ('asset_requirement','deleted_at'),
        ('asset_api','id'), ('asset_api','project_id'), ('asset_api','code'), ('asset_api','path'), ('asset_api','http_method'), ('asset_api','status'),
        ('asset_api','source'), ('asset_api','source_ref'), ('asset_api','version'), ('asset_api','request_schema'), ('asset_api','response_schema'),
        ('asset_api','lifecycle_status'), ('asset_api','archived_at'), ('asset_api','deleted_at'),
        ('asset_page','id'), ('asset_page','project_id'), ('asset_page','code'), ('asset_page','name'), ('asset_page','url_pattern'), ('asset_page','source'),
        ('asset_page','source_ref'), ('asset_page','source_version'), ('asset_page','component_tree'), ('asset_page','screenshot_url'), ('asset_page','status'),
        ('asset_page','lifecycle_status'), ('asset_page','archived_at'), ('asset_page','deleted_at'),
        ('asset_business_flow','id'), ('asset_business_flow','project_id'), ('asset_business_flow','code'), ('asset_business_flow','name'), ('asset_business_flow','status'),
        ('asset_business_flow','lifecycle_status'), ('asset_business_flow','archived_at'), ('asset_business_flow','deleted_at'),
        ('asset_test_case','id'), ('asset_test_case','project_id'), ('asset_test_case','code'), ('asset_test_case','title'), ('asset_test_case','case_type'), ('asset_test_case','status'),
        ('asset_test_case','version'), ('asset_test_case','lifecycle_status'), ('asset_test_case','archived_at'), ('asset_test_case','deleted_at'),
        ('asset_test_step','id'), ('asset_test_step','case_id'), ('asset_test_step','step_order'), ('asset_test_step','action'), ('asset_test_step','expected_result'),
        ('asset_link','id'), ('asset_link','source_type'), ('asset_link','source_id'), ('asset_link','target_type'), ('asset_link','target_id'), ('asset_link','link_type'),
        ('asset_version_history','id'), ('asset_version_history','project_id'), ('asset_version_history','asset_type'), ('asset_version_history','asset_id'),
        ('asset_version_history','version'), ('asset_version_history','change_type'), ('asset_version_history','actor'), ('asset_version_history','changed_fields'),
        ('asset_version_history','diff_json'), ('asset_version_history','snapshot_json'), ('asset_version_history','trace_id'), ('asset_version_history','created_at'),
        -- WP4 key columns
        ('document_input_field_mapping','id'), ('document_input_field_mapping','mapping_code'), ('document_input_field_mapping','title_path'), ('document_input_field_mapping','version'),
        ('document_input_source','id'), ('document_input_source','source_code'), ('document_input_source','source_type'), ('document_input_source','status'), ('document_input_source','mapping_id'),
        ('document_input_source','secret_ref'), ('document_input_source','event_version'), ('document_input_source','mapping_version'),
        ('document_input_import','id'), ('document_input_import','project_id'), ('document_input_import','source_type'), ('document_input_import','status'), ('document_input_import','created_requirement_ids'),
        ('document_input_candidate','id'), ('document_input_candidate','import_id'), ('document_input_candidate','project_id'), ('document_input_candidate','status'),
        ('document_input_candidate','parse_source'), ('document_input_candidate','model_invocation_id'), ('document_input_candidate','model_provider_name'),
        ('document_input_candidate','model_name'), ('document_input_candidate','version'),
        ('document_input_webhook_event','id'), ('document_input_webhook_event','source_code'), ('document_input_webhook_event','event_id'), ('document_input_webhook_event','idempotency_key'),
        ('document_input_webhook_event','event_version'), ('document_input_webhook_event','signature_status'), ('document_input_webhook_event','status'), ('document_input_webhook_event','payload_digest'),
        ('document_input_webhook_event','replay_by'), ('document_input_webhook_event','replay_at'), ('document_input_webhook_event','replay_trace_id'),
        ('document_input_retention_archive','id'), ('document_input_retention_archive','record_type'), ('document_input_retention_archive','record_id'),
        ('document_input_retention_archive','snapshot_json'), ('document_input_retention_archive','archived_at')
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
        -- WP1 key indexes
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
        ('audit_outbox','idx_audit_outbox_trace_id'),
        -- WP2 key indexes
        ('ma_model_provider','uk_ma_model_provider_name'),
        ('ma_prompt_template','uk_ma_prompt_template_key_version'),
        ('ma_prompt_template','uk_ma_prompt_template_one_active'),
        ('ma_invocation_log','idx_ma_invocation_scope_time'),
        -- WP3 key indexes
        ('asset_requirement','uk_asset_requirement_project_code'),
        ('asset_requirement','uk_asset_requirement_project_import_source_ref'),
        ('asset_requirement','idx_asset_requirement_project_lifecycle'),
        ('asset_api','uk_asset_api_project_service_path_method'),
        ('asset_api','idx_asset_api_project_lifecycle'),
        ('asset_page','uk_asset_page_project_code'),
        ('asset_page','idx_asset_page_project_lifecycle'),
        ('asset_business_flow','uk_asset_business_flow_project_code'),
        ('asset_business_flow','idx_asset_business_flow_project_lifecycle'),
        ('asset_test_case','uk_asset_test_case_project_code'),
        ('asset_test_case','idx_asset_test_case_project_lifecycle'),
        ('asset_test_step','uk_asset_test_step_case_order'),
        ('asset_link','uk_asset_link_source_target_link'),
        ('asset_version_history','uk_asset_version_history_asset_version'),
        ('asset_version_history','idx_asset_version_history_asset_created'),
        ('asset_version_history','idx_asset_version_history_project_created'),
        -- WP4 key indexes
        ('document_input_field_mapping','uk_document_input_field_mapping_code'),
        ('document_input_source','uk_document_input_source_code'),
        ('document_input_source','idx_document_input_source_secret_ref'),
        ('document_input_import','idx_document_input_import_project_created'),
        ('document_input_candidate','idx_document_input_candidate_import'),
        ('document_input_candidate','idx_document_input_candidate_external'),
        ('document_input_candidate','idx_document_input_candidate_model_invocation'),
        ('document_input_webhook_event','uk_document_input_webhook_event_id'),
        ('document_input_webhook_event','uk_document_input_webhook_idempotency'),
        ('document_input_retention_archive','uk_document_input_retention_archive_record'),
        ('document_input_retention_archive','idx_document_input_retention_archive_type_time')
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
