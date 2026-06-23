-- WP7 UI/E2E control-plane foundation validation.
-- Every query returns: check_name, status, details.

with expected(table_name) as (
    values
        ('ui_e2e_scene'),
        ('ui_e2e_scene_step'),
        ('ui_e2e_bundle'),
        ('ui_e2e_bundle_review'),
        ('ui_e2e_run'),
        ('ui_e2e_run_step_result'),
        ('ui_e2e_artifact_manifest'),
        ('ui_e2e_flaky_mark')
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
    'wp7.tables_exist' as check_name,
    case when count(*) = 0 then 'PASS' else 'FAIL' end as status,
    coalesce(string_agg(table_name, ', ' order by table_name), 'WP7 UI/E2E tables exist') as details
from missing;

with expected(table_name, column_name) as (
    values
        ('ui_e2e_scene','id'),
        ('ui_e2e_scene','project_id'),
        ('ui_e2e_scene','application_id'),
        ('ui_e2e_scene','environment_id'),
        ('ui_e2e_scene','code'),
        ('ui_e2e_scene','name'),
        ('ui_e2e_scene','status'),
        ('ui_e2e_scene','risk_level'),
        ('ui_e2e_scene','source_summary_json'),
        ('ui_e2e_scene','tags_json'),
        ('ui_e2e_scene','archived_at'),
        ('ui_e2e_scene_step','id'),
        ('ui_e2e_scene_step','scene_id'),
        ('ui_e2e_scene_step','project_id'),
        ('ui_e2e_scene_step','step_order'),
        ('ui_e2e_scene_step','step_type'),
        ('ui_e2e_scene_step','action_summary_json'),
        ('ui_e2e_scene_step','locator_strategy_json'),
        ('ui_e2e_scene_step','assertion_summary_json'),
        ('ui_e2e_scene_step','wait_policy_json'),
        ('ui_e2e_scene_step','data_binding_json'),
        ('ui_e2e_bundle','id'),
        ('ui_e2e_bundle','scene_id'),
        ('ui_e2e_bundle','project_id'),
        ('ui_e2e_bundle','status'),
        ('ui_e2e_bundle','bundle_digest'),
        ('ui_e2e_bundle','spec_summary_json'),
        ('ui_e2e_bundle','fixture_summary_json'),
        ('ui_e2e_bundle','static_check_summary_json'),
        ('ui_e2e_bundle_review','id'),
        ('ui_e2e_bundle_review','bundle_id'),
        ('ui_e2e_bundle_review','project_id'),
        ('ui_e2e_bundle_review','review_status'),
        ('ui_e2e_bundle_review','review_comment'),
        ('ui_e2e_bundle_review','reviewed_by'),
        ('ui_e2e_bundle_review','reviewed_at'),
        ('ui_e2e_run','id'),
        ('ui_e2e_run','scene_id'),
        ('ui_e2e_run','bundle_id'),
        ('ui_e2e_run','project_id'),
        ('ui_e2e_run','status'),
        ('ui_e2e_run','request_key'),
        ('ui_e2e_run','runner_mode'),
        ('ui_e2e_run','base_url_digest'),
        ('ui_e2e_run','account_lease_ref'),
        ('ui_e2e_run','account_summary_json'),
        ('ui_e2e_run','execution_summary_json'),
        ('ui_e2e_run','failure_code'),
        ('ui_e2e_run','failure_summary'),
        ('ui_e2e_run','trace_id'),
        ('ui_e2e_run','started_at'),
        ('ui_e2e_run','finished_at'),
        ('ui_e2e_run_step_result','id'),
        ('ui_e2e_run_step_result','run_id'),
        ('ui_e2e_run_step_result','scene_step_id'),
        ('ui_e2e_run_step_result','step_order'),
        ('ui_e2e_run_step_result','status'),
        ('ui_e2e_run_step_result','duration_ms'),
        ('ui_e2e_run_step_result','failure_bucket'),
        ('ui_e2e_run_step_result','error_code'),
        ('ui_e2e_run_step_result','summary_json'),
        ('ui_e2e_artifact_manifest','id'),
        ('ui_e2e_artifact_manifest','run_id'),
        ('ui_e2e_artifact_manifest','artifact_type'),
        ('ui_e2e_artifact_manifest','storage_ref'),
        ('ui_e2e_artifact_manifest','artifact_digest'),
        ('ui_e2e_artifact_manifest','size_bytes'),
        ('ui_e2e_artifact_manifest','redaction_flags_json'),
        ('ui_e2e_artifact_manifest','capture_status'),
        ('ui_e2e_flaky_mark','id'),
        ('ui_e2e_flaky_mark','project_id'),
        ('ui_e2e_flaky_mark','scene_id'),
        ('ui_e2e_flaky_mark','run_id'),
        ('ui_e2e_flaky_mark','status'),
        ('ui_e2e_flaky_mark','reason_code'),
        ('ui_e2e_flaky_mark','reason_summary')
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
    'wp7.columns_exist' as check_name,
    case when count(*) = 0 then 'PASS' else 'FAIL' end as status,
    coalesce(string_agg(item, ', ' order by item), 'WP7 key columns exist') as details
from missing;

with expected(table_name, constraint_name) as (
    values
        ('ui_e2e_scene','ck_ui_e2e_scene_status'),
        ('ui_e2e_scene','ck_ui_e2e_scene_risk_level'),
        ('ui_e2e_scene','ck_ui_e2e_scene_code'),
        ('ui_e2e_scene','ck_ui_e2e_scene_source_summary_json'),
        ('ui_e2e_scene','ck_ui_e2e_scene_tags_json'),
        ('ui_e2e_scene','uk_ui_e2e_scene_project_code'),
        ('ui_e2e_scene_step','ck_ui_e2e_scene_step_order'),
        ('ui_e2e_scene_step','ck_ui_e2e_scene_step_type'),
        ('ui_e2e_scene_step','ck_ui_e2e_scene_step_json'),
        ('ui_e2e_scene_step','uk_ui_e2e_scene_step_scene_order'),
        ('ui_e2e_bundle','ck_ui_e2e_bundle_status'),
        ('ui_e2e_bundle','ck_ui_e2e_bundle_digest'),
        ('ui_e2e_bundle','ck_ui_e2e_bundle_json'),
        ('ui_e2e_bundle_review','ck_ui_e2e_bundle_review_status'),
        ('ui_e2e_run','ck_ui_e2e_run_status'),
        ('ui_e2e_run','ck_ui_e2e_run_request_key'),
        ('ui_e2e_run','ck_ui_e2e_run_runner_mode'),
        ('ui_e2e_run','ck_ui_e2e_run_base_url_digest'),
        ('ui_e2e_run','ck_ui_e2e_run_account_lease_ref'),
        ('ui_e2e_run','ck_ui_e2e_run_account_summary_json'),
        ('ui_e2e_run','ck_ui_e2e_run_execution_summary_json'),
        ('ui_e2e_run_step_result','ck_ui_e2e_run_step_result_status'),
        ('ui_e2e_run_step_result','ck_ui_e2e_run_step_result_order'),
        ('ui_e2e_run_step_result','ck_ui_e2e_run_step_result_duration'),
        ('ui_e2e_run_step_result','ck_ui_e2e_run_step_result_failure_bucket'),
        ('ui_e2e_run_step_result','ck_ui_e2e_run_step_result_summary_json'),
        ('ui_e2e_run_step_result','uk_ui_e2e_run_step_result_run_order'),
        ('ui_e2e_artifact_manifest','ck_ui_e2e_artifact_manifest_type'),
        ('ui_e2e_artifact_manifest','ck_ui_e2e_artifact_manifest_digest'),
        ('ui_e2e_artifact_manifest','ck_ui_e2e_artifact_manifest_size_bytes'),
        ('ui_e2e_artifact_manifest','ck_ui_e2e_artifact_manifest_redaction_flags_json'),
        ('ui_e2e_artifact_manifest','ck_ui_e2e_artifact_manifest_capture_status'),
        ('ui_e2e_artifact_manifest','ck_ui_e2e_artifact_manifest_storage'),
        ('ui_e2e_flaky_mark','ck_ui_e2e_flaky_mark_status'),
        ('ui_e2e_flaky_mark','ck_ui_e2e_flaky_mark_ref'),
        ('ui_e2e_flaky_mark','ck_ui_e2e_flaky_mark_reason_code')
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
    'wp7.constraints_exist' as check_name,
    case when count(*) = 0 then 'PASS' else 'FAIL' end as status,
    coalesce(string_agg(item, ', ' order by item), 'WP7 constraints exist') as details
from missing;

with expected(table_name, index_name) as (
    values
        ('ui_e2e_scene','idx_ui_e2e_scene_project_status'),
        ('ui_e2e_scene','idx_ui_e2e_scene_project_app_env'),
        ('ui_e2e_scene_step','idx_ui_e2e_scene_step_scene_order'),
        ('ui_e2e_scene_step','idx_ui_e2e_scene_step_project_type'),
        ('ui_e2e_bundle','uk_ui_e2e_bundle_scene_digest'),
        ('ui_e2e_bundle','idx_ui_e2e_bundle_scene_status'),
        ('ui_e2e_bundle','idx_ui_e2e_bundle_digest'),
        ('ui_e2e_bundle_review','idx_ui_e2e_bundle_review_bundle_status'),
        ('ui_e2e_bundle_review','idx_ui_e2e_bundle_review_project_reviewed'),
        ('ui_e2e_run','uk_ui_e2e_run_project_scene_request_key'),
        ('ui_e2e_run','idx_ui_e2e_run_project_status'),
        ('ui_e2e_run','idx_ui_e2e_run_scene_created'),
        ('ui_e2e_run','idx_ui_e2e_run_bundle_created'),
        ('ui_e2e_run','idx_ui_e2e_run_trace'),
        ('ui_e2e_run_step_result','idx_ui_e2e_run_step_result_run_status'),
        ('ui_e2e_run_step_result','idx_ui_e2e_run_step_result_scene_step'),
        ('ui_e2e_artifact_manifest','idx_ui_e2e_artifact_manifest_run_type'),
        ('ui_e2e_artifact_manifest','idx_ui_e2e_artifact_manifest_digest'),
        ('ui_e2e_flaky_mark','idx_ui_e2e_flaky_mark_project_status'),
        ('ui_e2e_flaky_mark','idx_ui_e2e_flaky_mark_scene_status'),
        ('ui_e2e_flaky_mark','idx_ui_e2e_flaky_mark_run_status')
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
    'wp7.indexes_exist' as check_name,
    case when count(*) = 0 then 'PASS' else 'FAIL' end as status,
    coalesce(string_agg(item, ', ' order by item), 'WP7 indexes exist') as details
from missing;

with expected(code) as (
    values
        ('uiE2e:read'),
        ('uiE2e:manage'),
        ('uiE2e:review'),
        ('uiE2e:execute'),
        ('uiE2e:export'),
        ('uiE2e:flaky')
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
    'wp7.permissions_seeded' as check_name,
    case when count(*) = 0 then 'PASS' else 'FAIL' end as status,
    coalesce(string_agg(code, ', ' order by code), 'WP7 permissions seeded') as details
from missing;

with missing as (
    select item
    from (
        values
            ('SuperAdmin:uiE2e:flaky'),
            ('PlatformAdmin:uiE2e:flaky'),
            ('ProjectOwner:uiE2e:review'),
            ('ProjectOwner:uiE2e:export'),
            ('AppOwner:uiE2e:review'),
            ('Tester:uiE2e:execute'),
            ('Developer:uiE2e:read'),
            ('Auditor:uiE2e:export')
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
    'wp7.role_permissions_seeded' as check_name,
    case when count(*) = 0 then 'PASS' else 'FAIL' end as status,
    coalesce(string_agg(item, ', ' order by item), 'WP7 role permissions seeded') as details
from missing;

with expected(config_key) as (
    values
        ('ui_e2e.audit_events'),
        ('ui_e2e.enabled'),
        ('ui_e2e.runner_enabled'),
        ('ui_e2e.export_enabled')
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
    'wp7.base_config_seeded' as check_name,
    case when count(*) = 0 then 'PASS' else 'FAIL' end as status,
    coalesce(string_agg(config_key, ', ' order by config_key), 'WP7 base config seeded') as details
from missing;

with events as (
    select jsonb_array_elements_text(value_json) as event_name
    from base_config
    where scope_type = 'SYSTEM'
      and scope_id is null
      and config_key = 'ui_e2e.audit_events'
      and deleted_at is null
),
missing as (
    select expected.event_name
    from (
        values
            ('ui_e2e.scene.created'),
            ('ui_e2e.scene.updated'),
            ('ui_e2e.scene.archived'),
            ('ui_e2e.bundle.created'),
            ('ui_e2e.bundle.reviewed'),
            ('ui_e2e.run.created'),
            ('ui_e2e.run.started'),
            ('ui_e2e.run.completed'),
            ('ui_e2e.run.canceled'),
            ('ui_e2e.run.exported'),
            ('ui_e2e.flaky.marked')
    ) as expected(event_name)
    left join events e on e.event_name = expected.event_name
    where e.event_name is null
)
select
    'wp7.audit_events_seeded' as check_name,
    case when count(*) = 0 then 'PASS' else 'FAIL' end as status,
    coalesce(string_agg(event_name, ', ' order by event_name), 'WP7 audit events seeded') as details
from missing;

with bad as (
    select table_name || '.tenant_id' as item
    from information_schema.columns
    where table_schema = current_schema()
      and table_name like 'ui_e2e_%'
      and column_name = 'tenant_id'
)
select
    'wp7.no_tenant_id_columns' as check_name,
    case when count(*) = 0 then 'PASS' else 'FAIL' end as status,
    coalesce(string_agg(item, ', ' order by item), 'WP7 UI/E2E has no tenant_id columns') as details
from bad;
