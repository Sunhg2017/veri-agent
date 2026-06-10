-- WP5 test design validation.
-- Every query returns: check_name, status, details.

with expected(table_name) as (
    values
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
        ('test_design_queue_alert_subscription')
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
        ('generation_strategy'),
        ('coverage_strategy')
),
missing as (
    select e.column_name
    from expected e
    left join information_schema.columns c
        on c.table_schema = current_schema()
       and c.table_name = 'test_design_template'
       and c.column_name = e.column_name
    where c.column_name is null
)
select
    'wp5.template_strategy_columns_exist' as check_name,
    case when count(*) = 0 then 'PASS' else 'FAIL' end as status,
    coalesce(string_agg(column_name, ', ' order by column_name), 'WP5 template generation/coverage strategy columns exist') as details
from missing;

with expected(column_name) as (
    values
        ('idempotency_key'),
        ('request_digest'),
        ('input_digest'),
        ('context_summary_json')
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
    coalesce(string_agg(column_name, ', ' order by column_name), 'WP5 task idempotency and context columns exist') as details
from missing;

with expected(index_name) as (
    values
        ('uk_test_design_task_project_idempotency'),
        ('idx_test_design_task_input_digest')
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
    coalesce(string_agg(index_name, ', ' order by index_name), 'WP5 task idempotency and context indexes exist') as details
from missing;

with expected(table_name, constraint_name) as (
    values
        ('test_design_task','ck_test_design_task_status'),
        ('test_design_template','ck_test_design_template_case_count'),
        ('test_design_template','ck_test_design_template_context_object'),
        ('test_design_template','ck_test_design_template_context_keys'),
        ('test_design_template','ck_test_design_template_generation_strategy'),
        ('test_design_template','ck_test_design_template_coverage_strategy'),
        ('test_design_candidate','ck_test_design_candidate_status'),
        ('test_design_candidate','ck_test_design_candidate_coverage'),
        ('test_design_candidate','ck_test_design_candidate_priority'),
        ('test_design_review_record','ck_test_design_review_action'),
        ('test_design_publish_record','ck_test_design_publish_action'),
        ('test_design_publish_record','ck_test_design_publish_result'),
        ('test_design_report_manifest','ck_test_design_report_manifest_row_counts'),
        ('test_design_report_manifest','ck_test_design_report_manifest_mode'),
        ('test_design_report_manifest','ck_test_design_report_manifest_status'),
        ('test_design_report_manifest','ck_test_design_report_manifest_aggregate_only'),
        ('test_design_report_manifest','ck_test_design_report_manifest_digest'),
        ('test_design_report_archive','ck_test_design_report_archive_storage_backend'),
        ('test_design_report_archive','ck_test_design_report_archive_digest'),
        ('test_design_report_archive','ck_test_design_report_archive_counts'),
        ('test_design_report_archive','ck_test_design_report_archive_status'),
        ('test_design_report_archive','ck_test_design_report_archive_approval_state'),
        ('test_design_report_archive','ck_test_design_report_archive_storage_key'),
        ('test_design_report_archive_line_integrity','ck_test_design_report_archive_line_number'),
        ('test_design_report_archive_line_integrity','ck_test_design_report_archive_line_digest'),
        ('test_design_report_archive_line_integrity','ck_test_design_report_archive_line_metadata'),
        ('test_design_report_archive_approval','ck_test_design_report_archive_approval_type'),
        ('test_design_report_archive_approval','ck_test_design_report_archive_approval_status'),
        ('test_design_report_archive_approval','ck_test_design_report_archive_approval_reason'),
        ('test_design_report_archive_approval','ck_test_design_report_archive_approval_work_status'),
        ('test_design_report_archive_approval','ck_test_design_report_archive_approval_digest'),
        ('test_design_report_archive_approval','ck_test_design_report_archive_approval_lengths'),
        ('test_design_report_archive_note','ck_test_design_report_archive_note_type'),
        ('test_design_report_archive_note','ck_test_design_report_archive_note_text'),
        ('test_design_context_policy_override','ck_test_design_context_policy_override_scope'),
        ('test_design_context_policy_override','ck_test_design_context_policy_override_status'),
        ('test_design_context_policy_override','ck_test_design_context_policy_override_environment'),
        ('test_design_context_policy_override','ck_test_design_context_policy_override_any_limit'),
        ('test_design_context_policy_override','ck_test_design_context_policy_override_item_limits'),
        ('test_design_context_policy_override','ck_test_design_context_policy_override_char_limits'),
        ('test_design_context_policy_override','ck_test_design_context_policy_override_work_order_status'),
        ('test_design_context_policy_override','ck_test_design_context_policy_override_policy_body_version'),
        ('test_design_context_policy_override','ck_test_design_context_policy_override_body_digest'),
        ('test_design_context_policy_override','ck_test_design_context_policy_override_work_order_lengths'),
        ('test_design_context_policy_override','ck_test_design_context_policy_override_text_lengths'),
        ('test_design_context_policy_note','ck_test_design_context_policy_note_type'),
        ('test_design_context_policy_note','ck_test_design_context_policy_note_text'),
        ('test_design_release_readiness_approval','ck_test_design_rr_approval_status'),
        ('test_design_release_readiness_approval','ck_test_design_rr_approval_quality_status'),
        ('test_design_release_readiness_approval','ck_test_design_rr_approval_counts'),
        ('test_design_release_readiness_approval','ck_test_design_rr_approval_digest'),
        ('test_design_release_readiness_approval','ck_test_design_rr_approval_reason'),
        ('test_design_release_readiness_approval','ck_test_design_rr_approval_work_status'),
        ('test_design_release_readiness_approval','ck_test_design_rr_approval_work_lengths'),
        ('test_design_release_readiness_approval','ck_test_design_rr_approval_text_lengths'),
        ('test_design_release_readiness_note','ck_test_design_rr_note_type'),
        ('test_design_release_readiness_note','ck_test_design_rr_note_text'),
        ('test_design_evaluation_sample','ck_test_design_eval_sample_key'),
        ('test_design_evaluation_sample','ck_test_design_eval_sample_source'),
        ('test_design_evaluation_sample','ck_test_design_eval_sample_status'),
        ('test_design_evaluation_sample','ck_test_design_eval_sample_coverage'),
        ('test_design_evaluation_sample','ck_test_design_eval_sample_priority'),
        ('test_design_evaluation_sample','ck_test_design_eval_sample_baseline'),
        ('test_design_evaluation_sample','ck_test_design_eval_sample_digest'),
        ('test_design_evaluation_sample','ck_test_design_eval_sample_scan_status'),
        ('test_design_evaluation_sample','ck_test_design_eval_sample_text_lengths'),
        ('test_design_calibration_run','ck_test_design_calibration_run_mode'),
        ('test_design_calibration_run','ck_test_design_calibration_run_status'),
        ('test_design_calibration_run','ck_test_design_calibration_run_counts'),
        ('test_design_calibration_run','ck_test_design_calibration_run_percents'),
        ('test_design_calibration_run','ck_test_design_calibration_run_digest'),
        ('test_design_calibration_run','ck_test_design_calibration_run_notes'),
        ('test_design_queue_alert_subscription','ck_test_design_queue_alert_subscription_alert_type'),
        ('test_design_queue_alert_subscription','ck_test_design_queue_alert_subscription_channel'),
        ('test_design_queue_alert_subscription','ck_test_design_queue_alert_subscription_threshold'),
        ('test_design_queue_alert_subscription','ck_test_design_queue_alert_subscription_target_ref'),
        ('test_design_queue_alert_subscription','ck_test_design_queue_alert_subscription_prompt_key')
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

with task_status_constraint as (
    select pg_get_constraintdef(c.oid) as definition
    from pg_constraint c
    where c.conname = 'ck_test_design_task_status'
      and c.conrelid = (current_schema() || '.test_design_task')::regclass
)
select
    'wp5.task_status_allows_queued' as check_name,
    case when exists (
        select 1
        from task_status_constraint
        where definition like '%QUEUED%'
          and definition like '%PUBLISH_QUEUED%'
          and definition like '%PUBLISHING%'
    ) then 'PASS' else 'FAIL' end as status,
    coalesce((select definition from task_status_constraint limit 1), 'test_design_task status constraint must allow QUEUED, PUBLISH_QUEUED and PUBLISHING') as details;

with candidate_status_constraint as (
    select pg_get_constraintdef(c.oid) as definition
    from pg_constraint c
    where c.conname = 'ck_test_design_candidate_status'
      and c.conrelid = (current_schema() || '.test_design_candidate')::regclass
)
select
    'wp5.candidate_status_allows_async_publish' as check_name,
    case when exists (
        select 1
        from candidate_status_constraint
        where definition like '%PUBLISH_QUEUED%'
          and definition like '%PUBLISHING%'
    ) then 'PASS' else 'FAIL' end as status,
    coalesce((select definition from candidate_status_constraint limit 1), 'test_design_candidate status constraint must allow PUBLISH_QUEUED and PUBLISHING') as details;

with publish_result_constraint as (
    select pg_get_constraintdef(c.oid) as definition
    from pg_constraint c
    where c.conname = 'ck_test_design_publish_result'
      and c.conrelid = (current_schema() || '.test_design_publish_record')::regclass
)
select
    'wp5.publish_result_allows_async_queue' as check_name,
    case when exists (
        select 1
        from publish_result_constraint
        where definition like '%QUEUED%'
          and definition like '%RUNNING%'
          and definition like '%CONFLICT%'
    ) then 'PASS' else 'FAIL' end as status,
    coalesce((select definition from publish_result_constraint limit 1), 'test_design_publish_record result constraint must allow QUEUED/RUNNING/CONFLICT') as details;

with expected(table_name, index_name) as (
    values
        ('asset_test_case', 'uk_asset_test_case_project_ai_source_ref'),
        ('test_design_template', 'uk_test_design_template_global_name'),
        ('test_design_template', 'uk_test_design_template_project_name'),
        ('test_design_template', 'idx_test_design_template_project_enabled'),
        ('test_design_template', 'idx_test_design_template_enabled_updated'),
        ('test_design_template', 'idx_test_design_template_strategy'),
        ('test_design_candidate', 'idx_test_design_candidate_publish_queue'),
        ('test_design_candidate', 'idx_test_design_candidate_publish_running'),
        ('test_design_publish_record', 'uk_test_design_publish_auto_comp_candidate'),
        ('test_design_report_manifest', 'uk_test_design_report_manifest_content_digest'),
        ('test_design_report_manifest', 'idx_test_design_report_manifest_task_created'),
        ('test_design_report_manifest', 'idx_test_design_report_manifest_project_created'),
        ('test_design_report_archive', 'uk_test_design_report_archive_manifest'),
        ('test_design_report_archive', 'uk_test_design_report_archive_storage_key'),
        ('test_design_report_archive', 'idx_test_design_report_archive_task_created'),
        ('test_design_report_archive', 'idx_test_design_report_archive_project_status'),
        ('test_design_report_archive', 'idx_test_design_report_archive_retention'),
        ('test_design_report_archive_line_integrity', 'idx_test_design_report_archive_line_chain'),
        ('test_design_report_archive_line_integrity', 'idx_test_design_report_archive_line_section_metric'),
        ('test_design_report_archive_approval', 'idx_test_design_report_archive_approval_archive_created'),
        ('test_design_report_archive_approval', 'idx_test_design_report_archive_approval_project_type_status'),
        ('test_design_report_archive_approval', 'idx_test_design_report_archive_approval_work_order'),
        ('test_design_report_archive_note', 'idx_test_design_report_archive_note_approval_created'),
        ('test_design_context_policy_override', 'idx_test_design_context_policy_override_project_created'),
        ('test_design_context_policy_override', 'idx_test_design_context_policy_override_project_status'),
        ('test_design_context_policy_override', 'idx_test_design_context_policy_override_environment_status'),
        ('test_design_context_policy_override', 'idx_test_design_context_policy_override_work_order'),
        ('test_design_context_policy_note', 'idx_test_design_context_policy_note_override_created'),
        ('test_design_release_readiness_approval', 'idx_test_design_rr_approval_task_created'),
        ('test_design_release_readiness_approval', 'idx_test_design_rr_approval_task_status_digest'),
        ('test_design_release_readiness_approval', 'idx_test_design_rr_approval_project_created'),
        ('test_design_release_readiness_approval', 'idx_test_design_rr_approval_work_order'),
        ('test_design_release_readiness_note', 'idx_test_design_rr_note_approval_created'),
        ('test_design_evaluation_sample', 'uk_test_design_eval_sample_project_key'),
        ('test_design_evaluation_sample', 'idx_test_design_eval_sample_project_status'),
        ('test_design_evaluation_sample', 'idx_test_design_eval_sample_prompt_baseline'),
        ('test_design_evaluation_sample', 'idx_test_design_eval_sample_source_candidate'),
        ('test_design_calibration_run', 'idx_test_design_calibration_run_project_prompt_created'),
        ('test_design_calibration_run', 'idx_test_design_calibration_run_baseline_created'),
        ('test_design_calibration_run', 'idx_test_design_calibration_run_status_created'),
        ('test_design_queue_alert_subscription', 'uk_test_design_queue_alert_subscription_scope'),
        ('test_design_queue_alert_subscription', 'idx_test_design_queue_alert_subscription_project_enabled'),
        ('test_design_queue_alert_subscription', 'idx_test_design_queue_alert_subscription_prompt_alert')
),
missing as (
    select e.table_name || '.' || e.index_name as item
    from expected e
    left join pg_indexes i on i.schemaname = current_schema()
       and i.tablename = e.table_name
       and i.indexname = e.index_name
    where i.indexname is null
)
select
    'wp5.publish_idempotency_indexes_exist' as check_name,
    case when count(*) = 0 then 'PASS' else 'FAIL' end as status,
    coalesce(string_agg(item, ', ' order by item), 'WP5 publish and report manifest idempotency indexes exist') as details
from missing;

with expected(column_name) as (
    values
        ('id'),
        ('task_id'),
        ('project_id'),
        ('status'),
        ('quality_gate_status'),
        ('blocking_count'),
        ('warning_count'),
        ('readiness_digest'),
        ('exception_reason_code'),
        ('approval_reason_code'),
        ('work_order_key'),
        ('work_order_title'),
        ('work_order_url'),
        ('work_order_status'),
        ('exception_summary'),
        ('exception_summary_digest'),
        ('risk_mitigation'),
        ('request_note'),
        ('review_note'),
        ('requested_by'),
        ('approved_by'),
        ('reviewed_at'),
        ('created_at'),
        ('updated_at')
),
missing as (
    select e.column_name
    from expected e
    left join information_schema.columns c
        on c.table_schema = current_schema()
       and c.table_name = 'test_design_release_readiness_approval'
       and c.column_name = e.column_name
    where c.column_name is null
)
select
    'wp5.release_readiness_approval_columns_exist' as check_name,
    case when count(*) = 0 then 'PASS' else 'FAIL' end as status,
    coalesce(string_agg(column_name, ', ' order by column_name), 'WP5 release readiness approval stores bounded exception, work order and metadata columns') as details
from missing;

with expected(column_name) as (
    values
        ('id'),
        ('approval_id'),
        ('note_type'),
        ('note_text'),
        ('created_by'),
        ('created_at')
),
missing as (
    select e.column_name
    from expected e
    left join information_schema.columns c
        on c.table_schema = current_schema()
       and c.table_name = 'test_design_release_readiness_note'
       and c.column_name = e.column_name
    where c.column_name is null
)
select
    'wp5.release_readiness_note_columns_exist' as check_name,
    case when count(*) = 0 then 'PASS' else 'FAIL' end as status,
    coalesce(string_agg(column_name, ', ' order by column_name), 'WP5 release readiness approval note timeline columns exist') as details
from missing;

with expected(column_name) as (
    values
        ('id'),
        ('project_id'),
        ('sample_key'),
        ('title'),
        ('source_type'),
        ('source_task_id'),
        ('source_candidate_id'),
        ('prompt_key'),
        ('prompt_version'),
        ('coverage_type'),
        ('priority'),
        ('status'),
        ('baseline_version'),
        ('requirement_summary'),
        ('expected_case_outline'),
        ('assertion_notes'),
        ('tags'),
        ('maintenance_note'),
        ('sample_digest'),
        ('sensitive_scan_status'),
        ('created_by'),
        ('updated_by'),
        ('created_at'),
        ('updated_at')
),
missing as (
    select e.column_name
    from expected e
    left join information_schema.columns c
        on c.table_schema = current_schema()
       and c.table_name = 'test_design_evaluation_sample'
       and c.column_name = e.column_name
    where c.column_name is null
)
select
    'wp5.evaluation_sample_columns_exist' as check_name,
    case when count(*) = 0 then 'PASS' else 'FAIL' end as status,
    coalesce(string_agg(column_name, ', ' order by column_name), 'WP5 evaluation sample maintenance columns exist') as details
from missing;

with expected(column_name) as (
    values
        ('id'),
        ('project_id'),
        ('prompt_key'),
        ('prompt_version'),
        ('baseline_version'),
        ('run_mode'),
        ('status'),
        ('sample_count'),
        ('golden_sample_count'),
        ('task_count'),
        ('candidate_count'),
        ('step_complete_percent'),
        ('expected_complete_percent'),
        ('low_confidence_percent'),
        ('error_percent'),
        ('duplicate_key_collision_count'),
        ('feedback_signal_count'),
        ('readiness_status'),
        ('readiness_blocking_count'),
        ('readiness_warning_count'),
        ('regression_count'),
        ('baseline_digest'),
        ('result_digest'),
        ('notes'),
        ('run_by'),
        ('created_at')
),
missing as (
    select e.column_name
    from expected e
    left join information_schema.columns c
        on c.table_schema = current_schema()
       and c.table_name = 'test_design_calibration_run'
       and c.column_name = e.column_name
    where c.column_name is null
)
select
    'wp5.calibration_run_columns_exist' as check_name,
    case when count(*) = 0 then 'PASS' else 'FAIL' end as status,
    coalesce(string_agg(column_name, ', ' order by column_name), 'WP5 long-term calibration run columns exist') as details
from missing;

with expected(column_name) as (
    values
        ('id'),
        ('project_id'),
        ('prompt_key'),
        ('alert_type'),
        ('channel'),
        ('target_ref'),
        ('threshold_seconds'),
        ('enabled'),
        ('created_by'),
        ('updated_by'),
        ('created_at'),
        ('updated_at')
),
missing as (
    select e.column_name
    from expected e
    left join information_schema.columns c
        on c.table_schema = current_schema()
       and c.table_name = 'test_design_queue_alert_subscription'
       and c.column_name = e.column_name
    where c.column_name is null
)
select
    'wp5.queue_alert_subscription_columns_exist' as check_name,
    case when count(*) = 0 then 'PASS' else 'FAIL' end as status,
    coalesce(string_agg(column_name, ', ' order by column_name), 'WP5 queue alert subscription stores bounded non-secret routing references') as details
from missing;

with forbidden(column_name) as (
    values
        ('task_id'),
        ('task_ids'),
        ('candidate_id'),
        ('candidate_ids'),
        ('trace_id'),
        ('trace_ids'),
        ('event_payload_json'),
        ('payload_json'),
        ('webhook_url'),
        ('webhook_token'),
        ('secret_value'),
        ('api_key')
),
found as (
    select column_name
    from information_schema.columns c
    join forbidden f using (column_name)
    where c.table_schema = current_schema()
      and c.table_name = 'test_design_queue_alert_subscription'
)
select
    'wp5.queue_alert_subscription_no_payload_or_identifier_columns' as check_name,
    case when count(*) = 0 then 'PASS' else 'FAIL' end as status,
    coalesce(string_agg(column_name, ', ' order by column_name), 'WP5 queue alert subscriptions store no queue payload, trace, task, candidate or secret columns') as details
from found;

with expected(column_name) as (
    values
        ('id'),
        ('scope_type'),
        ('project_id'),
        ('environment_key'),
        ('status'),
        ('context_linked_assets_per_requirement'),
        ('context_explicit_assets_per_type'),
        ('context_existing_cases_per_requirement'),
        ('context_requirement_description_chars'),
        ('context_acceptance_criteria_chars'),
        ('context_asset_schema_chars'),
        ('change_reason_code'),
        ('approval_reason_code'),
        ('work_order_key'),
        ('work_order_title'),
        ('work_order_url'),
        ('work_order_status'),
        ('policy_body'),
        ('policy_body_digest'),
        ('policy_body_version'),
        ('policy_diff_summary'),
        ('request_note'),
        ('review_note'),
        ('requested_by'),
        ('approved_by'),
        ('reviewed_at'),
        ('created_at'),
        ('updated_at')
),
missing as (
    select e.column_name
    from expected e
    left join information_schema.columns c
        on c.table_schema = current_schema()
       and c.table_name = 'test_design_context_policy_override'
       and c.column_name = e.column_name
    where c.column_name is null
)
select
    'wp5.context_policy_override_columns_exist' as check_name,
    case when count(*) = 0 then 'PASS' else 'FAIL' end as status,
    coalesce(string_agg(column_name, ', ' order by column_name), 'WP5 context policy override stores bounded policy, work order and metadata columns') as details
from missing;

with forbidden(column_name) as (
    values
        ('policy_text'),
        ('policy_json'),
        ('policy_document'),
        ('policy_diff'),
        ('diff_json'),
        ('approval_note'),
        ('ticket_url'),
        ('context_body'),
        ('context_json'),
        ('context_summary_json'),
        ('raw_context'),
        ('raw_prompt'),
        ('prompt_text'),
        ('prompt_payload'),
        ('request_body'),
        ('response_body')
),
found as (
    select column_name
    from information_schema.columns c
    join forbidden f using (column_name)
    where c.table_schema = current_schema()
      and c.table_name = 'test_design_context_policy_override'
)
select
    'wp5.context_policy_override_no_raw_context_or_prompt_columns' as check_name,
    case when count(*) = 0 then 'PASS' else 'FAIL' end as status,
    coalesce(string_agg(column_name, ', ' order by column_name), 'WP5 context policy override stores no raw context, prompt payload, provider request or response body columns') as details
from found;

with expected(column_name) as (
    values
        ('id'),
        ('override_id'),
        ('note_type'),
        ('note_text'),
        ('created_by'),
        ('created_at')
),
missing as (
    select e.column_name
    from expected e
    left join information_schema.columns c
        on c.table_schema = current_schema()
       and c.table_name = 'test_design_context_policy_note'
       and c.column_name = e.column_name
    where c.column_name is null
)
select
    'wp5.context_policy_note_columns_exist' as check_name,
    case when count(*) = 0 then 'PASS' else 'FAIL' end as status,
    coalesce(string_agg(column_name, ', ' order by column_name), 'WP5 context policy approval note timeline columns exist') as details
from missing;

with expected(column_name) as (
    values
        ('id'),
        ('task_id'),
        ('project_id'),
        ('schema_version'),
        ('field_set_version'),
        ('manifest_mode'),
        ('row_count_before_manifest'),
        ('report_row_count'),
        ('aggregate_only'),
        ('detail_rows_exported'),
        ('manifest_status'),
        ('content_digest'),
        ('generated_at'),
        ('created_at')
),
missing as (
    select e.column_name
    from expected e
    left join information_schema.columns c
        on c.table_schema = current_schema()
       and c.table_name = 'test_design_report_manifest'
       and c.column_name = e.column_name
    where c.column_name is null
)
select
    'wp5.report_manifest_columns_exist' as check_name,
    case when count(*) = 0 then 'PASS' else 'FAIL' end as status,
    coalesce(string_agg(column_name, ', ' order by column_name), 'WP5 report manifest aggregate-only columns exist') as details
from missing;

with forbidden(column_name) as (
    values
        ('row_digest'),
        ('row_summary'),
        ('row_content_summary'),
        ('candidate_id'),
        ('candidate_ids'),
        ('trace_id'),
        ('trace_ids'),
        ('audit_id'),
        ('audit_ids'),
        ('audit_log_id'),
        ('audit_log_ids'),
        ('report_content'),
        ('csv_content'),
        ('raw_report')
),
found as (
    select column_name
    from information_schema.columns c
    join forbidden f using (column_name)
    where c.table_schema = current_schema()
      and c.table_name = 'test_design_report_manifest'
)
select
    'wp5.report_manifest_no_detail_identifier_columns' as check_name,
    case when count(*) = 0 then 'PASS' else 'FAIL' end as status,
    coalesce(string_agg(column_name, ', ' order by column_name), 'WP5 report manifest stores no row/candidate/trace/audit detail columns') as details
from found;

with aggregate_only_constraint as (
    select pg_get_constraintdef(c.oid) as definition
    from pg_constraint c
    where c.conname = 'ck_test_design_report_manifest_aggregate_only'
      and c.conrelid = (current_schema() || '.test_design_report_manifest')::regclass
)
select
    'wp5.report_manifest_aggregate_only_enforced' as check_name,
    case when exists (
        select 1
        from aggregate_only_constraint
        where definition like '%aggregate_only%'
          and definition like '%detail_rows_exported%'
    ) then 'PASS' else 'FAIL' end as status,
    coalesce((select definition from aggregate_only_constraint limit 1), 'report manifest aggregate-only constraint missing') as details;

with expected(column_name) as (
    values
        ('id'),
        ('manifest_id'),
        ('task_id'),
        ('project_id'),
        ('storage_backend'),
        ('storage_key'),
        ('content_digest'),
        ('content_size_bytes'),
        ('report_row_count'),
        ('line_integrity_count'),
        ('status'),
        ('archive_approval_status'),
        ('external_approval_status'),
        ('retention_until'),
        ('content_bytes'),
        ('created_by'),
        ('created_at'),
        ('updated_at')
),
missing as (
    select e.column_name
    from expected e
    left join information_schema.columns c
        on c.table_schema = current_schema()
       and c.table_name = 'test_design_report_archive'
       and c.column_name = e.column_name
    where c.column_name is null
)
select
    'wp5.report_archive_columns_exist' as check_name,
    case when count(*) = 0 then 'PASS' else 'FAIL' end as status,
    coalesce(string_agg(column_name, ', ' order by column_name), 'WP5 report archive stores managed content, digest, retention and approval state') as details
from missing;

with expected(column_name) as (
    values
        ('archive_id'),
        ('row_number'),
        ('row_digest'),
        ('previous_row_digest'),
        ('chain_digest'),
        ('record_type'),
        ('section'),
        ('metric'),
        ('created_at')
),
missing as (
    select e.column_name
    from expected e
    left join information_schema.columns c
        on c.table_schema = current_schema()
       and c.table_name = 'test_design_report_archive_line_integrity'
       and c.column_name = e.column_name
    where c.column_name is null
)
select
    'wp5.report_archive_line_integrity_columns_exist' as check_name,
    case when count(*) = 0 then 'PASS' else 'FAIL' end as status,
    coalesce(string_agg(column_name, ', ' order by column_name), 'WP5 report archive line-integrity index columns exist') as details
from missing;

with expected(column_name) as (
    values
        ('id'),
        ('archive_id'),
        ('task_id'),
        ('project_id'),
        ('approval_type'),
        ('status'),
        ('reason_code'),
        ('approval_reason_code'),
        ('work_order_key'),
        ('work_order_title'),
        ('work_order_url'),
        ('work_order_status'),
        ('request_summary'),
        ('request_summary_digest'),
        ('request_note'),
        ('review_note'),
        ('requested_by'),
        ('approved_by'),
        ('reviewed_at'),
        ('created_at'),
        ('updated_at')
),
missing as (
    select e.column_name
    from expected e
    left join information_schema.columns c
        on c.table_schema = current_schema()
       and c.table_name = 'test_design_report_archive_approval'
       and c.column_name = e.column_name
    where c.column_name is null
)
select
    'wp5.report_archive_approval_columns_exist' as check_name,
    case when count(*) = 0 then 'PASS' else 'FAIL' end as status,
    coalesce(string_agg(column_name, ', ' order by column_name), 'WP5 report archive approval and external-share work-order columns exist') as details
from missing;

with expected(column_name) as (
    values
        ('id'),
        ('approval_id'),
        ('note_type'),
        ('note_text'),
        ('created_by'),
        ('created_at')
),
missing as (
    select e.column_name
    from expected e
    left join information_schema.columns c
        on c.table_schema = current_schema()
       and c.table_name = 'test_design_report_archive_note'
       and c.column_name = e.column_name
    where c.column_name is null
)
select
    'wp5.report_archive_note_columns_exist' as check_name,
    case when count(*) = 0 then 'PASS' else 'FAIL' end as status,
    coalesce(string_agg(column_name, ', ' order by column_name), 'WP5 report archive work-order note timeline columns exist') as details
from missing;

with forbidden(column_name) as (
    values
        ('archive_path'),
        ('download_url'),
        ('candidate_id'),
        ('candidate_ids'),
        ('trace_id'),
        ('trace_ids'),
        ('audit_id'),
        ('audit_ids'),
        ('audit_log_id'),
        ('audit_log_ids'),
        ('raw_prompt'),
        ('prompt_payload'),
        ('model_payload'),
        ('request_body'),
        ('response_body')
),
found as (
    select column_name
    from information_schema.columns c
    join forbidden f using (column_name)
    where c.table_schema = current_schema()
      and c.table_name = 'test_design_report_archive'
)
select
    'wp5.report_archive_no_external_path_or_detail_identifier_columns' as check_name,
    case when count(*) = 0 then 'PASS' else 'FAIL' end as status,
    coalesce(string_agg(column_name, ', ' order by column_name), 'WP5 report archive stores no external path, candidate, trace, audit or model payload columns') as details
from found;

with forbidden(column_name) as (
    values
        ('row_content'),
        ('row_summary'),
        ('row_content_summary'),
        ('candidate_id'),
        ('candidate_ids'),
        ('trace_id'),
        ('trace_ids'),
        ('audit_id'),
        ('audit_ids'),
        ('audit_log_id'),
        ('audit_log_ids')
),
found as (
    select column_name
    from information_schema.columns c
    join forbidden f using (column_name)
    where c.table_schema = current_schema()
      and c.table_name = 'test_design_report_archive_line_integrity'
)
select
    'wp5.report_archive_line_integrity_no_row_content_or_identifier_columns' as check_name,
    case when count(*) = 0 then 'PASS' else 'FAIL' end as status,
    coalesce(string_agg(column_name, ', ' order by column_name), 'WP5 line-integrity index stores no row content, row summary, candidate, trace or audit identifiers') as details
from found;

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

with wp5_tables(table_name) as (
    values
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
        ('test_design_queue_alert_subscription')
),
missing as (
    select t.table_name
    from wp5_tables t
    join pg_class c on c.relname = t.table_name
    join pg_namespace n on n.oid = c.relnamespace
        and n.nspname = current_schema()
    where obj_description(c.oid, 'pg_class') is null
       or btrim(obj_description(c.oid, 'pg_class')) = ''
)
select
    'wp5.table_comments_exist' as check_name,
    case when count(*) = 0 then 'PASS' else 'FAIL' end as status,
    coalesce(string_agg(table_name, ', ' order by table_name), 'WP5 table comments exist') as details
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
      and c.table_name in (
          'test_design_template',
          'test_design_task',
          'test_design_candidate',
          'test_design_review_record',
          'test_design_publish_record',
          'test_design_report_manifest',
          'test_design_report_archive',
          'test_design_report_archive_line_integrity',
          'test_design_report_archive_approval',
          'test_design_report_archive_note',
          'test_design_context_policy_override',
          'test_design_context_policy_note',
          'test_design_release_readiness_approval',
          'test_design_release_readiness_note',
          'test_design_evaluation_sample',
          'test_design_calibration_run'
      )
      and (
          col_description(pc.oid, pa.attnum) is null
          or btrim(col_description(pc.oid, pa.attnum)) = ''
      )
)
select
    'wp5.column_comments_exist' as check_name,
    case when count(*) = 0 then 'PASS' else 'FAIL' end as status,
    coalesce(string_agg(item, ', ' order by item), 'WP5 column comments exist') as details
from missing;

with expected(code) as (
    values
        ('testDesign:read'),
        ('testDesign:generate'),
        ('testDesign:review'),
        ('testDesign:publish'),
        ('testDesign:export'),
        ('testDesign:policy_manage')
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

select
    'wp5.model_prompt_seeded' as check_name,
    case when count(*) = 1 then 'PASS' else 'FAIL' end as status,
    coalesce(max(prompt_key || ':v' || version || ':' || status), 'WP5 model prompt missing') as details
from ma_prompt_template
where prompt_key = 'wp5-test-design-v1'
  and version = 1
  and status = 'ACTIVE'
  and approval_status = 'NOT_REQUIRED';
