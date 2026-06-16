-- WP10 reporting foundation validation.
-- Every query returns: check_name, status, details.

with expected(table_name) as (
    values
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
    'wp10.tables_exist' as check_name,
    case when count(*) = 0 then 'PASS' else 'FAIL' end as status,
    coalesce(string_agg(table_name, ', ' order by table_name), 'WP10 reporting tables exist') as details
from missing;

with expected(table_name, column_name) as (
    values
        ('report_execution_report','id'),
        ('report_execution_report','project_id'),
        ('report_execution_report','execution_run_id'),
        ('report_execution_report','request_key'),
        ('report_execution_report','status'),
        ('report_execution_report','schema_version'),
        ('report_execution_report','source_run_digest'),
        ('report_execution_report','report_summary_json'),
        ('report_execution_report','redaction_policy_json'),
        ('report_execution_report','generated_by'),
        ('report_execution_report','generated_at'),
        ('report_execution_report','failed_code'),
        ('report_execution_report','failure_summary'),
        ('report_execution_report','trace_id'),
        ('report_execution_report','archived_at'),
        ('report_execution_report','created_at'),
        ('report_execution_report','updated_at'),
        ('report_evidence_manifest','id'),
        ('report_evidence_manifest','report_id'),
        ('report_evidence_manifest','source_wp'),
        ('report_evidence_manifest','source_type'),
        ('report_evidence_manifest','source_ref_digest'),
        ('report_evidence_manifest','schema_version'),
        ('report_evidence_manifest','summary_keys_json'),
        ('report_evidence_manifest','redaction_flags_json'),
        ('report_evidence_manifest','evidence_summary_json'),
        ('report_evidence_manifest','created_at'),
        ('report_failure_diagnosis','id'),
        ('report_failure_diagnosis','report_id'),
        ('report_failure_diagnosis','status'),
        ('report_failure_diagnosis','classification_json'),
        ('report_failure_diagnosis','model_invocation_digest'),
        ('report_failure_diagnosis','confidence'),
        ('report_failure_diagnosis','manual_review_required'),
        ('report_failure_diagnosis','diagnosis_summary_json'),
        ('report_failure_diagnosis','error_code'),
        ('report_failure_diagnosis','created_at'),
        ('report_failure_diagnosis','updated_at'),
        ('report_defect_draft','id'),
        ('report_defect_draft','report_id'),
        ('report_defect_draft','diagnosis_id'),
        ('report_defect_draft','status'),
        ('report_defect_draft','title'),
        ('report_defect_draft','reproduction_summary'),
        ('report_defect_draft','impact_summary'),
        ('report_defect_draft','priority_suggestion'),
        ('report_defect_draft','evidence_refs_json'),
        ('report_defect_draft','payload_preview_json'),
        ('report_defect_draft','created_by'),
        ('report_defect_draft','updated_by'),
        ('report_defect_draft','created_at'),
        ('report_defect_draft','updated_at'),
        ('report_export_manifest','id'),
        ('report_export_manifest','report_id'),
        ('report_export_manifest','export_type'),
        ('report_export_manifest','status'),
        ('report_export_manifest','schema_version'),
        ('report_export_manifest','field_set_version'),
        ('report_export_manifest','redaction_policy_json'),
        ('report_export_manifest','content_digest'),
        ('report_export_manifest','aggregate_only'),
        ('report_export_manifest','exported_by'),
        ('report_export_manifest','exported_at'),
        ('report_export_manifest','block_reason'),
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
    'wp10.columns_exist' as check_name,
    case when count(*) = 0 then 'PASS' else 'FAIL' end as status,
    coalesce(string_agg(item, ', ' order by item), 'WP10 key columns exist') as details
from missing;

with expected(table_name, constraint_name) as (
    values
        ('report_execution_report','ck_report_execution_report_status'),
        ('report_execution_report','ck_report_execution_report_source_digest'),
        ('report_execution_report','ck_report_execution_report_summary_json'),
        ('report_evidence_manifest','ck_report_evidence_manifest_source_wp'),
        ('report_evidence_manifest','ck_report_evidence_manifest_source_digest'),
        ('report_evidence_manifest','ck_report_evidence_manifest_json'),
        ('report_failure_diagnosis','ck_report_failure_diagnosis_status'),
        ('report_failure_diagnosis','ck_report_failure_diagnosis_model_digest'),
        ('report_failure_diagnosis','ck_report_failure_diagnosis_confidence'),
        ('report_failure_diagnosis','ck_report_failure_diagnosis_json'),
        ('report_defect_draft','ck_report_defect_draft_status'),
        ('report_defect_draft','ck_report_defect_draft_priority'),
        ('report_defect_draft','ck_report_defect_draft_json'),
        ('report_export_manifest','ck_report_export_manifest_type'),
        ('report_export_manifest','ck_report_export_manifest_status'),
        ('report_export_manifest','ck_report_export_manifest_digest'),
        ('report_export_manifest','ck_report_export_manifest_json'),
        ('report_export_manifest','ck_report_export_manifest_aggregate_only')
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
    'wp10.constraints_exist' as check_name,
    case when count(*) = 0 then 'PASS' else 'FAIL' end as status,
    coalesce(string_agg(item, ', ' order by item), 'WP10 constraints exist') as details
from missing;

with expected(table_name, index_name) as (
    values
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
    'wp10.indexes_exist' as check_name,
    case when count(*) = 0 then 'PASS' else 'FAIL' end as status,
    coalesce(string_agg(item, ', ' order by item), 'WP10 indexes exist') as details
from missing;

with expected(code) as (
    values
        ('report:read'),
        ('report:generate'),
        ('report:diagnose'),
        ('report:export'),
        ('report:manage')
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
    'wp10.permissions_seeded' as check_name,
    case when count(*) = 0 then 'PASS' else 'FAIL' end as status,
    coalesce(string_agg(code, ', ' order by code), 'WP10 permissions seeded') as details
from missing;

with missing as (
    select item
    from (
        values
            ('SuperAdmin:report:manage'),
            ('PlatformAdmin:report:manage'),
            ('ProjectOwner:report:manage'),
            ('AppOwner:report:diagnose'),
            ('Tester:report:generate'),
            ('Developer:report:read'),
            ('Auditor:report:export')
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
    'wp10.role_permissions_seeded' as check_name,
    case when count(*) = 0 then 'PASS' else 'FAIL' end as status,
    coalesce(string_agg(item, ', ' order by item), 'WP10 role permissions seeded') as details
from missing;

with expected(config_key) as (
    values
        ('reporting.audit_events'),
        ('reporting.enabled'),
        ('reporting.generate_enabled'),
        ('reporting.diagnosis_enabled'),
        ('reporting.defect_draft_enabled'),
        ('reporting.export_enabled')
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
    'wp10.base_config_seeded' as check_name,
    case when count(*) = 0 then 'PASS' else 'FAIL' end as status,
    coalesce(string_agg(config_key, ', ' order by config_key), 'WP10 base config seeded') as details
from missing;

with events as (
    select jsonb_array_elements_text(value_json) as event_name
    from base_config
    where scope_type = 'SYSTEM'
      and scope_id is null
      and config_key = 'reporting.audit_events'
      and deleted_at is null
),
missing as (
    select expected.event_name
    from (
        values
            ('report.generated'),
            ('report.generate.rejected'),
            ('report.archived'),
            ('report.diagnosis.requested'),
            ('report.diagnosis.completed'),
            ('report.defect_draft.created'),
            ('report.defect_draft.reviewed'),
            ('report.exported'),
            ('report.export.blocked')
    ) as expected(event_name)
    left join events e on e.event_name = expected.event_name
    where e.event_name is null
)
select
    'wp10.audit_events_seeded' as check_name,
    case when count(*) = 0 then 'PASS' else 'FAIL' end as status,
    coalesce(string_agg(event_name, ', ' order by event_name), 'WP10 audit events seeded') as details
from missing;

with bad as (
    select table_name || '.tenant_id' as item
    from information_schema.columns
    where table_schema = current_schema()
      and table_name like 'report_%'
      and column_name = 'tenant_id'
)
select
    'wp10.no_tenant_id_columns' as check_name,
    case when count(*) = 0 then 'PASS' else 'FAIL' end as status,
    coalesce(string_agg(item, ', ' order by item), 'WP10 reporting has no tenant_id columns') as details
from bad;
