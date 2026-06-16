-- Consolidated WP1+ cross-WP schema validation for single-platform deployment.
-- Every query returns: check_name, status, details.
-- Validates shared platform tables and current work-package foundation schemas.

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
        ('ma_invocation_job'),
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
        ('document_input_parse_feedback_sample'),
        ('document_input_webhook_event'),
        ('document_input_retention_archive'),
        -- WP5 AI test design tables
        ('test_design_template'),
        ('test_design_task'),
        ('test_design_candidate'),
        ('test_design_review_record'),
        ('test_design_publish_record'),
        ('test_design_report_manifest'),
        ('test_design_report_archive'),
        ('test_design_report_archive_line_integrity'),
        ('test_design_report_archive_approval'),
        ('test_design_report_archive_note'),
        ('test_design_context_policy_override'),
        ('test_design_context_policy_note'),
        ('test_design_release_readiness_approval'),
        ('test_design_release_readiness_note'),
        ('test_design_evaluation_sample'),
        ('test_design_calibration_run'),
        -- WP6 OpenAPI API automation tables
        ('api_automation_spec'),
        ('api_automation_endpoint_snapshot'),
        ('api_automation_generation_task'),
        ('api_automation_case'),
        ('api_automation_script_bundle'),
        ('api_automation_run'),
        ('api_automation_run_result'),
        -- WP9 execution orchestration tables
        ('execution_plan'),
        ('execution_plan_node'),
        ('execution_run'),
        ('execution_node_run'),
        ('execution_trigger'),
        ('execution_trigger_event'),
        ('execution_queue_claim'),
        -- WP10 reporting foundation tables
        ('report_execution_report'),
        ('report_evidence_manifest'),
        ('report_failure_diagnosis'),
        ('report_defect_draft'),
        ('report_export_manifest')
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
    coalesce(string_agg(table_name, ', ' order by table_name), 'all core work-package tables exist') as details
from missing;

with missing as (
    select t.table_name
    from information_schema.tables t
    join pg_class c on c.relname = t.table_name
    join pg_namespace n on n.oid = c.relnamespace
        and n.nspname = t.table_schema
    where t.table_schema = current_schema()
      and t.table_type = 'BASE TABLE'
      and (
          obj_description(c.oid, 'pg_class') is null
          or btrim(obj_description(c.oid, 'pg_class')) = ''
      )
)
select
    'schema.table_comments_exist' as check_name,
    case when count(*) = 0 then 'PASS' else 'FAIL' end as status,
    coalesce(string_agg(table_name, ', ' order by table_name), 'all platform table comments exist') as details
from missing;

with missing as (
    select c.table_name || '.' || c.column_name as item
    from information_schema.columns c
    join pg_class pc on pc.relname = c.table_name
    join pg_namespace pn on pn.oid = pc.relnamespace
        and pn.nspname = c.table_schema
    join pg_attribute pa on pa.attrelid = pc.oid
        and pa.attname = c.column_name
        and pa.attnum > 0
        and not pa.attisdropped
    where c.table_schema = current_schema()
      and exists (
          select 1
          from information_schema.tables t
          where t.table_schema = c.table_schema
            and t.table_name = c.table_name
            and t.table_type = 'BASE TABLE'
      )
      and (
          col_description(pc.oid, pa.attnum) is null
          or btrim(col_description(pc.oid, pa.attnum)) = ''
      )
)
select
    'schema.column_comments_exist' as check_name,
    case when count(*) = 0 then 'PASS' else 'FAIL' end as status,
    coalesce(string_agg(item, ', ' order by item), 'all platform column comments exist') as details
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
        ('ma_prompt_template','high_risk'), ('ma_prompt_template','approval_status'), ('ma_prompt_template','approved_by'), ('ma_prompt_template','approved_at'), ('ma_prompt_template','approval_note'),
        ('ma_invocation_log','id'), ('ma_invocation_log','project_id'), ('ma_invocation_log','model_name'), ('ma_invocation_log','status'), ('ma_invocation_log','created_at'),
        ('ma_invocation_job','job_id'), ('ma_invocation_job','status'), ('ma_invocation_job','request_json'), ('ma_invocation_job','trace_id'), ('ma_invocation_job','created_at'),
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
        ('document_input_parse_feedback_sample','id'), ('document_input_parse_feedback_sample','candidate_id'), ('document_input_parse_feedback_sample','import_id'),
        ('document_input_parse_feedback_sample','project_id'), ('document_input_parse_feedback_sample','parse_source'), ('document_input_parse_feedback_sample','changed_fields'),
        ('document_input_parse_feedback_sample','before_snapshot_json'), ('document_input_parse_feedback_sample','after_snapshot_json'),
        ('document_input_parse_feedback_sample','curation_status'), ('document_input_parse_feedback_sample','version'),
        ('document_input_webhook_event','id'), ('document_input_webhook_event','source_code'), ('document_input_webhook_event','event_id'), ('document_input_webhook_event','idempotency_key'),
        ('document_input_webhook_event','event_version'), ('document_input_webhook_event','signature_status'), ('document_input_webhook_event','status'), ('document_input_webhook_event','payload_digest'),
        ('document_input_webhook_event','replay_by'), ('document_input_webhook_event','replay_at'), ('document_input_webhook_event','replay_trace_id'),
        ('document_input_retention_archive','id'), ('document_input_retention_archive','record_type'), ('document_input_retention_archive','record_id'),
        ('document_input_retention_archive','snapshot_json'), ('document_input_retention_archive','archived_at'),
        -- WP5 key columns
        ('test_design_template','id'), ('test_design_template','project_id'), ('test_design_template','name'),
        ('test_design_template','prompt_key'), ('test_design_template','prompt_version'),
        ('test_design_template','coverage_types'), ('test_design_template','generation_strategy'),
        ('test_design_template','coverage_strategy'), ('test_design_template','case_count_per_requirement'),
        ('test_design_template','context_defaults_json'), ('test_design_template','enabled'),
        ('test_design_task','id'), ('test_design_task','project_id'), ('test_design_task','title'), ('test_design_task','status'),
        ('test_design_task','requirement_ids'), ('test_design_task','coverage_types'), ('test_design_task','prompt_key'),
        ('test_design_task','prompt_version'), ('test_design_task','model_invocation_id'), ('test_design_task','model_provider_name'),
        ('test_design_task','model_name'), ('test_design_task','generated_count'), ('test_design_task','confirmed_count'),
        ('test_design_task','published_count'), ('test_design_task','idempotency_key'), ('test_design_task','request_digest'),
        ('test_design_task','input_digest'), ('test_design_task','context_summary_json'),
        ('test_design_candidate','id'), ('test_design_candidate','task_id'),
        ('test_design_candidate','project_id'), ('test_design_candidate','requirement_id'), ('test_design_candidate','api_id'),
        ('test_design_candidate','title'), ('test_design_candidate','coverage_type'), ('test_design_candidate','priority'),
        ('test_design_candidate','status'), ('test_design_candidate','steps_json'), ('test_design_candidate','duplicate_key'),
        ('test_design_candidate','confidence'), ('test_design_candidate','asset_case_id'), ('test_design_candidate','version'),
        ('test_design_review_record','id'), ('test_design_review_record','candidate_id'), ('test_design_review_record','task_id'),
        ('test_design_review_record','project_id'), ('test_design_review_record','action'), ('test_design_review_record','diff_json'),
        ('test_design_publish_record','id'), ('test_design_publish_record','task_id'), ('test_design_publish_record','candidate_id'),
        ('test_design_publish_record','project_id'), ('test_design_publish_record','requirement_id'), ('test_design_publish_record','asset_case_id'),
        ('test_design_publish_record','dry_run'), ('test_design_publish_record','action'), ('test_design_publish_record','result'),
        ('test_design_report_manifest','id'), ('test_design_report_manifest','task_id'), ('test_design_report_manifest','project_id'),
        ('test_design_report_manifest','schema_version'), ('test_design_report_manifest','field_set_version'),
        ('test_design_report_manifest','manifest_mode'), ('test_design_report_manifest','row_count_before_manifest'),
        ('test_design_report_manifest','report_row_count'), ('test_design_report_manifest','aggregate_only'),
        ('test_design_report_manifest','detail_rows_exported'), ('test_design_report_manifest','manifest_status'),
        ('test_design_report_manifest','content_digest'), ('test_design_report_manifest','generated_at'),
        ('test_design_report_manifest','created_at'),
        ('test_design_report_archive','id'), ('test_design_report_archive','manifest_id'),
        ('test_design_report_archive','task_id'), ('test_design_report_archive','project_id'),
        ('test_design_report_archive','storage_backend'), ('test_design_report_archive','storage_key'),
        ('test_design_report_archive','content_digest'), ('test_design_report_archive','content_size_bytes'),
        ('test_design_report_archive','report_row_count'), ('test_design_report_archive','line_integrity_count'),
        ('test_design_report_archive','status'), ('test_design_report_archive','archive_approval_status'),
        ('test_design_report_archive','external_approval_status'), ('test_design_report_archive','retention_until'),
        ('test_design_report_archive','content_bytes'), ('test_design_report_archive','created_by'),
        ('test_design_report_archive','created_at'), ('test_design_report_archive','updated_at'),
        ('test_design_report_archive_line_integrity','archive_id'),
        ('test_design_report_archive_line_integrity','row_number'),
        ('test_design_report_archive_line_integrity','row_digest'),
        ('test_design_report_archive_line_integrity','previous_row_digest'),
        ('test_design_report_archive_line_integrity','chain_digest'),
        ('test_design_report_archive_line_integrity','record_type'),
        ('test_design_report_archive_line_integrity','section'),
        ('test_design_report_archive_line_integrity','metric'),
        ('test_design_report_archive_line_integrity','created_at'),
        ('test_design_report_archive_approval','id'), ('test_design_report_archive_approval','archive_id'),
        ('test_design_report_archive_approval','task_id'), ('test_design_report_archive_approval','project_id'),
        ('test_design_report_archive_approval','approval_type'), ('test_design_report_archive_approval','status'),
        ('test_design_report_archive_approval','reason_code'),
        ('test_design_report_archive_approval','approval_reason_code'),
        ('test_design_report_archive_approval','work_order_key'),
        ('test_design_report_archive_approval','work_order_title'),
        ('test_design_report_archive_approval','work_order_url'),
        ('test_design_report_archive_approval','work_order_status'),
        ('test_design_report_archive_approval','request_summary'),
        ('test_design_report_archive_approval','request_summary_digest'),
        ('test_design_report_archive_approval','request_note'),
        ('test_design_report_archive_approval','review_note'),
        ('test_design_report_archive_approval','requested_by'),
        ('test_design_report_archive_approval','approved_by'),
        ('test_design_report_archive_approval','reviewed_at'),
        ('test_design_report_archive_approval','created_at'),
        ('test_design_report_archive_approval','updated_at'),
        ('test_design_report_archive_note','id'), ('test_design_report_archive_note','approval_id'),
        ('test_design_report_archive_note','note_type'), ('test_design_report_archive_note','note_text'),
        ('test_design_report_archive_note','created_by'), ('test_design_report_archive_note','created_at'),
        ('test_design_context_policy_override','id'), ('test_design_context_policy_override','scope_type'),
        ('test_design_context_policy_override','project_id'), ('test_design_context_policy_override','environment_key'),
        ('test_design_context_policy_override','status'),
        ('test_design_context_policy_override','context_linked_assets_per_requirement'),
        ('test_design_context_policy_override','context_explicit_assets_per_type'),
        ('test_design_context_policy_override','context_existing_cases_per_requirement'),
        ('test_design_context_policy_override','context_requirement_description_chars'),
        ('test_design_context_policy_override','context_acceptance_criteria_chars'),
        ('test_design_context_policy_override','context_asset_schema_chars'),
        ('test_design_context_policy_override','change_reason_code'),
        ('test_design_context_policy_override','approval_reason_code'),
        ('test_design_context_policy_override','work_order_key'),
        ('test_design_context_policy_override','work_order_title'),
        ('test_design_context_policy_override','work_order_url'),
        ('test_design_context_policy_override','work_order_status'),
        ('test_design_context_policy_override','policy_body'),
        ('test_design_context_policy_override','policy_body_digest'),
        ('test_design_context_policy_override','policy_body_version'),
        ('test_design_context_policy_override','policy_diff_summary'),
        ('test_design_context_policy_override','request_note'),
        ('test_design_context_policy_override','review_note'),
        ('test_design_context_policy_override','requested_by'), ('test_design_context_policy_override','approved_by'),
        ('test_design_context_policy_override','reviewed_at'),
        ('test_design_context_policy_override','created_at'), ('test_design_context_policy_override','updated_at'),
        ('test_design_context_policy_note','id'), ('test_design_context_policy_note','override_id'),
        ('test_design_context_policy_note','note_type'), ('test_design_context_policy_note','note_text'),
        ('test_design_context_policy_note','created_by'), ('test_design_context_policy_note','created_at'),
        ('test_design_release_readiness_approval','id'), ('test_design_release_readiness_approval','task_id'),
        ('test_design_release_readiness_approval','project_id'), ('test_design_release_readiness_approval','status'),
        ('test_design_release_readiness_approval','quality_gate_status'),
        ('test_design_release_readiness_approval','blocking_count'),
        ('test_design_release_readiness_approval','warning_count'),
        ('test_design_release_readiness_approval','readiness_digest'),
        ('test_design_release_readiness_approval','exception_reason_code'),
        ('test_design_release_readiness_approval','approval_reason_code'),
        ('test_design_release_readiness_approval','work_order_key'),
        ('test_design_release_readiness_approval','work_order_title'),
        ('test_design_release_readiness_approval','work_order_url'),
        ('test_design_release_readiness_approval','work_order_status'),
        ('test_design_release_readiness_approval','exception_summary'),
        ('test_design_release_readiness_approval','exception_summary_digest'),
        ('test_design_release_readiness_approval','risk_mitigation'),
        ('test_design_release_readiness_approval','request_note'),
        ('test_design_release_readiness_approval','review_note'),
        ('test_design_release_readiness_approval','requested_by'),
        ('test_design_release_readiness_approval','approved_by'),
        ('test_design_release_readiness_approval','reviewed_at'),
        ('test_design_release_readiness_approval','created_at'),
        ('test_design_release_readiness_approval','updated_at'),
        ('test_design_release_readiness_note','id'), ('test_design_release_readiness_note','approval_id'),
        ('test_design_release_readiness_note','note_type'), ('test_design_release_readiness_note','note_text'),
        ('test_design_release_readiness_note','created_by'), ('test_design_release_readiness_note','created_at'),
        ('test_design_evaluation_sample','id'), ('test_design_evaluation_sample','project_id'),
        ('test_design_evaluation_sample','sample_key'), ('test_design_evaluation_sample','title'),
        ('test_design_evaluation_sample','source_type'), ('test_design_evaluation_sample','source_task_id'),
        ('test_design_evaluation_sample','source_candidate_id'), ('test_design_evaluation_sample','prompt_key'),
        ('test_design_evaluation_sample','prompt_version'), ('test_design_evaluation_sample','coverage_type'),
        ('test_design_evaluation_sample','priority'), ('test_design_evaluation_sample','status'),
        ('test_design_evaluation_sample','baseline_version'), ('test_design_evaluation_sample','requirement_summary'),
        ('test_design_evaluation_sample','expected_case_outline'), ('test_design_evaluation_sample','assertion_notes'),
        ('test_design_evaluation_sample','tags'), ('test_design_evaluation_sample','maintenance_note'),
        ('test_design_evaluation_sample','sample_digest'), ('test_design_evaluation_sample','sensitive_scan_status'),
        ('test_design_evaluation_sample','created_by'), ('test_design_evaluation_sample','updated_by'),
        ('test_design_evaluation_sample','created_at'), ('test_design_evaluation_sample','updated_at'),
        ('test_design_calibration_run','id'), ('test_design_calibration_run','project_id'),
        ('test_design_calibration_run','prompt_key'), ('test_design_calibration_run','prompt_version'),
        ('test_design_calibration_run','baseline_version'), ('test_design_calibration_run','run_mode'),
        ('test_design_calibration_run','status'), ('test_design_calibration_run','sample_count'),
        ('test_design_calibration_run','golden_sample_count'), ('test_design_calibration_run','task_count'),
        ('test_design_calibration_run','candidate_count'), ('test_design_calibration_run','step_complete_percent'),
        ('test_design_calibration_run','expected_complete_percent'), ('test_design_calibration_run','low_confidence_percent'),
        ('test_design_calibration_run','error_percent'), ('test_design_calibration_run','duplicate_key_collision_count'),
        ('test_design_calibration_run','feedback_signal_count'), ('test_design_calibration_run','readiness_status'),
        ('test_design_calibration_run','readiness_blocking_count'), ('test_design_calibration_run','readiness_warning_count'),
        ('test_design_calibration_run','regression_count'), ('test_design_calibration_run','baseline_digest'),
        ('test_design_calibration_run','result_digest'), ('test_design_calibration_run','notes'),
        ('test_design_calibration_run','run_by'), ('test_design_calibration_run','created_at'),
        -- WP6 key columns
        ('api_automation_spec','id'), ('api_automation_spec','project_id'),
        ('api_automation_spec','source_type'), ('api_automation_spec','name'),
        ('api_automation_spec','spec_digest'), ('api_automation_spec','content_size_bytes'),
        ('api_automation_spec','sanitized_spec_json'), ('api_automation_spec','parse_summary_json'),
        ('api_automation_spec','status'), ('api_automation_spec','endpoint_count'),
        ('api_automation_endpoint_snapshot','id'), ('api_automation_endpoint_snapshot','spec_id'),
        ('api_automation_endpoint_snapshot','project_id'), ('api_automation_endpoint_snapshot','http_method'),
        ('api_automation_endpoint_snapshot','path'), ('api_automation_endpoint_snapshot','operation_id'),
        ('api_automation_endpoint_snapshot','schema_digest'), ('api_automation_endpoint_snapshot','diff_status'),
        ('api_automation_endpoint_snapshot','asset_api_id'), ('api_automation_endpoint_snapshot','diff_summary_json'),
        ('api_automation_endpoint_snapshot','last_diff_at'), ('api_automation_endpoint_snapshot','synced_at'),
        ('api_automation_endpoint_snapshot','sync_error_summary'),
        ('api_automation_generation_task','id'), ('api_automation_generation_task','project_id'),
        ('api_automation_generation_task','spec_id'), ('api_automation_generation_task','request_digest'),
        ('api_automation_generation_task','generation_mode'), ('api_automation_generation_task','coverage_types_json'),
        ('api_automation_generation_task','status'), ('api_automation_generation_task','fallback_used'),
        ('api_automation_generation_task','api_count'), ('api_automation_generation_task','case_count'),
        ('api_automation_generation_task','input_summary_json'),
        ('api_automation_case','id'), ('api_automation_case','task_id'),
        ('api_automation_case','project_id'), ('api_automation_case','spec_id'),
        ('api_automation_case','endpoint_snapshot_id'), ('api_automation_case','asset_api_id'),
        ('api_automation_case','title'), ('api_automation_case','http_method'),
        ('api_automation_case','path'), ('api_automation_case','coverage_type'),
        ('api_automation_case','expected_status'), ('api_automation_case','assertion_summary_json'),
        ('api_automation_case','request_template_json'), ('api_automation_case','source'),
        ('api_automation_case','status'),
        ('api_automation_script_bundle','id'), ('api_automation_script_bundle','project_id'),
        ('api_automation_script_bundle','task_id'), ('api_automation_script_bundle','status'),
        ('api_automation_script_bundle','bundle_digest'), ('api_automation_script_bundle','file_count'),
        ('api_automation_script_bundle','file_tree_summary_json'),
        ('api_automation_script_bundle','dependency_summary_json'),
        ('api_automation_script_bundle','static_check_status'),
        ('api_automation_script_bundle','static_check_summary_json'),
        ('api_automation_script_bundle','review_note'), ('api_automation_script_bundle','submitted_by'),
        ('api_automation_script_bundle','approved_by'), ('api_automation_script_bundle','submitted_at'),
        ('api_automation_script_bundle','approved_at'), ('api_automation_script_bundle','rejected_at'),
        -- WP10 key columns
        ('report_execution_report','id'), ('report_execution_report','project_id'),
        ('report_execution_report','execution_run_id'), ('report_execution_report','request_key'),
        ('report_execution_report','status'), ('report_execution_report','schema_version'),
        ('report_execution_report','source_run_digest'), ('report_execution_report','report_summary_json'),
        ('report_execution_report','redaction_policy_json'), ('report_execution_report','generated_by'),
        ('report_execution_report','generated_at'), ('report_execution_report','failed_code'),
        ('report_execution_report','failure_summary'), ('report_execution_report','trace_id'),
        ('report_execution_report','archived_at'), ('report_execution_report','created_at'),
        ('report_execution_report','updated_at'),
        ('report_evidence_manifest','id'), ('report_evidence_manifest','report_id'),
        ('report_evidence_manifest','source_wp'), ('report_evidence_manifest','source_type'),
        ('report_evidence_manifest','source_ref_digest'), ('report_evidence_manifest','schema_version'),
        ('report_evidence_manifest','summary_keys_json'), ('report_evidence_manifest','redaction_flags_json'),
        ('report_evidence_manifest','evidence_summary_json'), ('report_evidence_manifest','created_at'),
        ('report_failure_diagnosis','id'), ('report_failure_diagnosis','report_id'),
        ('report_failure_diagnosis','status'), ('report_failure_diagnosis','classification_json'),
        ('report_failure_diagnosis','model_invocation_digest'), ('report_failure_diagnosis','confidence'),
        ('report_failure_diagnosis','manual_review_required'), ('report_failure_diagnosis','diagnosis_summary_json'),
        ('report_failure_diagnosis','error_code'), ('report_failure_diagnosis','created_at'),
        ('report_failure_diagnosis','updated_at'),
        ('report_defect_draft','id'), ('report_defect_draft','report_id'),
        ('report_defect_draft','diagnosis_id'), ('report_defect_draft','status'),
        ('report_defect_draft','title'), ('report_defect_draft','reproduction_summary'),
        ('report_defect_draft','impact_summary'), ('report_defect_draft','priority_suggestion'),
        ('report_defect_draft','evidence_refs_json'), ('report_defect_draft','payload_preview_json'),
        ('report_defect_draft','created_by'), ('report_defect_draft','updated_by'),
        ('report_defect_draft','created_at'), ('report_defect_draft','updated_at'),
        ('report_export_manifest','id'), ('report_export_manifest','report_id'),
        ('report_export_manifest','export_type'), ('report_export_manifest','status'),
        ('report_export_manifest','schema_version'), ('report_export_manifest','field_set_version'),
        ('report_export_manifest','redaction_policy_json'), ('report_export_manifest','content_digest'),
        ('report_export_manifest','aggregate_only'), ('report_export_manifest','exported_by'),
        ('report_export_manifest','exported_at'), ('report_export_manifest','block_reason'),
        ('report_export_manifest','created_at')
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
        ('iam_session','idx_iam_session_expires_at'),
        ('iam_session','idx_iam_session_revoked_at'),
        ('iam_session','idx_iam_session_cleanup'),
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
        ('ma_prompt_template','idx_ma_prompt_template_approval_status'),
        ('ma_invocation_log','idx_ma_invocation_scope_time'),
        ('ma_invocation_job','idx_ma_invocation_job_status_created'),
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
        ('asset_test_case','uk_asset_test_case_project_ai_source_ref'),
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
        ('document_input_parse_feedback_sample','idx_document_input_parse_feedback_project_status'),
        ('document_input_parse_feedback_sample','idx_document_input_parse_feedback_candidate'),
        ('document_input_webhook_event','uk_document_input_webhook_event_id'),
        ('document_input_webhook_event','uk_document_input_webhook_idempotency'),
        ('document_input_retention_archive','uk_document_input_retention_archive_record'),
        ('document_input_retention_archive','idx_document_input_retention_archive_type_time'),
        -- WP5 key indexes
        ('test_design_template','uk_test_design_template_global_name'),
        ('test_design_template','uk_test_design_template_project_name'),
        ('test_design_template','idx_test_design_template_project_enabled'),
        ('test_design_template','idx_test_design_template_enabled_updated'),
        ('test_design_template','idx_test_design_template_strategy'),
        ('test_design_task','idx_test_design_task_project_status'),
        ('test_design_task','uk_test_design_task_project_idempotency'),
        ('test_design_task','idx_test_design_task_input_digest'),
        ('test_design_candidate','uk_test_design_candidate_task_duplicate'),
        ('test_design_candidate','idx_test_design_candidate_task_status'),
        ('test_design_candidate','idx_test_design_candidate_project_status'),
        ('test_design_candidate','idx_test_design_candidate_requirement'),
        ('test_design_review_record','idx_test_design_review_candidate_created'),
        ('test_design_publish_record','idx_test_design_publish_task_created'),
        ('test_design_report_manifest','uk_test_design_report_manifest_content_digest'),
        ('test_design_report_manifest','idx_test_design_report_manifest_task_created'),
        ('test_design_report_manifest','idx_test_design_report_manifest_project_created'),
        ('test_design_report_archive','uk_test_design_report_archive_manifest'),
        ('test_design_report_archive','uk_test_design_report_archive_storage_key'),
        ('test_design_report_archive','idx_test_design_report_archive_task_created'),
        ('test_design_report_archive','idx_test_design_report_archive_project_status'),
        ('test_design_report_archive','idx_test_design_report_archive_retention'),
        ('test_design_report_archive_line_integrity','idx_test_design_report_archive_line_chain'),
        ('test_design_report_archive_line_integrity','idx_test_design_report_archive_line_section_metric'),
        -- WP6 key indexes
        ('api_automation_spec','uk_api_automation_spec_project_digest'),
        ('api_automation_spec','idx_api_automation_spec_project_status'),
        ('api_automation_spec','idx_api_automation_spec_created'),
        ('api_automation_endpoint_snapshot','uk_api_automation_endpoint_spec_method_path'),
        ('api_automation_endpoint_snapshot','idx_api_automation_endpoint_project_method'),
        ('api_automation_endpoint_snapshot','idx_api_automation_endpoint_spec_diff'),
        ('api_automation_endpoint_snapshot','idx_api_automation_endpoint_asset_api'),
        ('api_automation_endpoint_snapshot','idx_api_automation_endpoint_last_diff'),
        ('api_automation_generation_task','uk_api_automation_generation_project_digest'),
        ('api_automation_generation_task','uk_api_automation_generation_project_key'),
        ('api_automation_generation_task','idx_api_automation_generation_spec_created'),
        ('api_automation_generation_task','idx_api_automation_generation_project_status'),
        ('api_automation_case','idx_api_automation_case_task'),
        ('api_automation_case','idx_api_automation_case_asset_api'),
        ('api_automation_case','idx_api_automation_case_endpoint'),
        ('api_automation_script_bundle','uk_api_automation_script_bundle_task_active'),
        ('api_automation_script_bundle','idx_api_automation_script_bundle_project_status'),
        ('api_automation_script_bundle','idx_api_automation_script_bundle_digest'),
        ('api_automation_script_bundle','idx_api_automation_script_bundle_static_check'),
        ('test_design_report_archive_approval','idx_test_design_report_archive_approval_archive_created'),
        ('test_design_report_archive_approval','idx_test_design_report_archive_approval_project_type_status'),
        ('test_design_report_archive_approval','idx_test_design_report_archive_approval_work_order'),
        ('test_design_report_archive_note','idx_test_design_report_archive_note_approval_created'),
        ('test_design_context_policy_override','idx_test_design_context_policy_override_project_created'),
        ('test_design_context_policy_override','idx_test_design_context_policy_override_project_status'),
        ('test_design_context_policy_override','idx_test_design_context_policy_override_environment_status'),
        ('test_design_context_policy_override','idx_test_design_context_policy_override_work_order'),
        ('test_design_context_policy_note','idx_test_design_context_policy_note_override_created'),
        ('test_design_release_readiness_approval','idx_test_design_rr_approval_task_created'),
        ('test_design_release_readiness_approval','idx_test_design_rr_approval_task_status_digest'),
        ('test_design_release_readiness_approval','idx_test_design_rr_approval_project_created'),
        ('test_design_release_readiness_approval','idx_test_design_rr_approval_work_order'),
        ('test_design_release_readiness_note','idx_test_design_rr_note_approval_created'),
        ('test_design_evaluation_sample','uk_test_design_eval_sample_project_key'),
        ('test_design_evaluation_sample','idx_test_design_eval_sample_project_status'),
        ('test_design_evaluation_sample','idx_test_design_eval_sample_prompt_baseline'),
        ('test_design_evaluation_sample','idx_test_design_eval_sample_source_candidate'),
        ('test_design_calibration_run','idx_test_design_calibration_run_project_prompt_created'),
        ('test_design_calibration_run','idx_test_design_calibration_run_baseline_created'),
        ('test_design_calibration_run','idx_test_design_calibration_run_status_created'),
        -- WP10 key indexes
        ('report_execution_report','uk_report_execution_report_run_request'),
        ('report_execution_report','idx_report_execution_report_project_status'),
        ('report_execution_report','idx_report_execution_report_run'),
        ('report_execution_report','idx_report_execution_report_trace'),
        ('report_evidence_manifest','idx_report_evidence_manifest_report_source'),
        ('report_evidence_manifest','idx_report_evidence_manifest_source_digest'),
        ('report_failure_diagnosis','idx_report_failure_diagnosis_report_status'),
        ('report_failure_diagnosis','idx_report_failure_diagnosis_model_digest'),
        ('report_defect_draft','idx_report_defect_draft_report_status'),
        ('report_defect_draft','idx_report_defect_draft_diagnosis'),
        ('report_export_manifest','idx_report_export_manifest_report_type'),
        ('report_export_manifest','idx_report_export_manifest_digest')
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
