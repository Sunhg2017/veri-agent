-- WP10 reporting and failure diagnosis foundation.
-- Stores aggregate-only report snapshots, evidence manifests, diagnosis summaries, defect drafts and export manifests.

create table if not exists report_execution_report (
    id uuid primary key default gen_random_uuid(),
    project_id varchar(64) not null,
    execution_run_id uuid not null,
    request_key varchar(128),
    status varchar(32) not null default 'QUEUED',
    schema_version varchar(64) not null,
    source_run_digest varchar(64),
    report_summary_json jsonb not null default '{}'::jsonb,
    redaction_policy_json jsonb not null default '{}'::jsonb,
    generated_by varchar(128),
    generated_at timestamptz,
    failed_code varchar(64),
    failure_summary varchar(512),
    trace_id varchar(64),
    archived_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint ck_report_execution_report_status check (
        status in ('QUEUED','GENERATING','READY','FAILED','ARCHIVED')
    ),
    constraint ck_report_execution_report_source_digest check (
        source_run_digest is null
        or source_run_digest ~ '^[0-9a-f]{64}$'
    ),
    constraint ck_report_execution_report_summary_json check (
        jsonb_typeof(report_summary_json) = 'object'
        and jsonb_typeof(redaction_policy_json) = 'object'
    )
);

create table if not exists report_evidence_manifest (
    id uuid primary key default gen_random_uuid(),
    report_id uuid not null references report_execution_report(id) on delete cascade,
    source_wp varchar(16) not null,
    source_type varchar(64) not null,
    source_ref_digest varchar(64),
    schema_version varchar(64) not null,
    summary_keys_json jsonb not null default '[]'::jsonb,
    redaction_flags_json jsonb not null default '{}'::jsonb,
    evidence_summary_json jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default now(),
    constraint ck_report_evidence_manifest_source_wp check (source_wp in ('WP3','WP5','WP8','WP9')),
    constraint ck_report_evidence_manifest_source_digest check (
        source_ref_digest is null
        or source_ref_digest ~ '^[0-9a-f]{64}$'
    ),
    constraint ck_report_evidence_manifest_json check (
        jsonb_typeof(summary_keys_json) = 'array'
        and jsonb_typeof(redaction_flags_json) = 'object'
        and jsonb_typeof(evidence_summary_json) = 'object'
    )
);

create table if not exists report_failure_diagnosis (
    id uuid primary key default gen_random_uuid(),
    report_id uuid not null references report_execution_report(id) on delete cascade,
    status varchar(32) not null default 'NOT_REQUESTED',
    classification_json jsonb not null default '{}'::jsonb,
    model_invocation_digest varchar(64),
    confidence numeric(5,4),
    manual_review_required boolean not null default true,
    diagnosis_summary_json jsonb not null default '{}'::jsonb,
    error_code varchar(64),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint ck_report_failure_diagnosis_status check (
        status in ('NOT_REQUESTED','RULE_READY','AI_RUNNING','AI_READY','AI_FAILED')
    ),
    constraint ck_report_failure_diagnosis_model_digest check (
        model_invocation_digest is null
        or model_invocation_digest ~ '^[0-9a-f]{64}$'
    ),
    constraint ck_report_failure_diagnosis_confidence check (
        confidence is null
        or (confidence >= 0 and confidence <= 1)
    ),
    constraint ck_report_failure_diagnosis_json check (
        jsonb_typeof(classification_json) = 'object'
        and jsonb_typeof(diagnosis_summary_json) = 'object'
    )
);

create table if not exists report_defect_draft (
    id uuid primary key default gen_random_uuid(),
    report_id uuid not null references report_execution_report(id) on delete cascade,
    diagnosis_id uuid references report_failure_diagnosis(id) on delete set null,
    status varchar(32) not null default 'DRAFT',
    title varchar(200) not null,
    reproduction_summary varchar(2000),
    impact_summary varchar(2000),
    priority_suggestion varchar(32),
    evidence_refs_json jsonb not null default '[]'::jsonb,
    payload_preview_json jsonb not null default '{}'::jsonb,
    created_by varchar(128),
    updated_by varchar(128),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint ck_report_defect_draft_status check (status in ('DRAFT','REVIEWED','DISMISSED','EXPORTED')),
    constraint ck_report_defect_draft_priority check (
        priority_suggestion is null
        or priority_suggestion in ('P0','P1','P2','P3','P4','UNKNOWN')
    ),
    constraint ck_report_defect_draft_json check (
        jsonb_typeof(evidence_refs_json) = 'array'
        and jsonb_typeof(payload_preview_json) = 'object'
    )
);

create table if not exists report_export_manifest (
    id uuid primary key default gen_random_uuid(),
    report_id uuid not null references report_execution_report(id) on delete cascade,
    export_type varchar(32) not null,
    status varchar(32) not null default 'CREATED',
    schema_version varchar(64) not null,
    field_set_version varchar(96) not null,
    redaction_policy_json jsonb not null default '{}'::jsonb,
    content_digest varchar(64),
    aggregate_only boolean not null default true,
    exported_by varchar(128),
    exported_at timestamptz,
    block_reason varchar(512),
    created_at timestamptz not null default now(),
    constraint ck_report_export_manifest_type check (export_type in ('JSON','MARKDOWN')),
    constraint ck_report_export_manifest_status check (status in ('CREATED','BLOCKED')),
    constraint ck_report_export_manifest_digest check (
        content_digest is null
        or content_digest ~ '^[0-9a-f]{64}$'
    ),
    constraint ck_report_export_manifest_json check (jsonb_typeof(redaction_policy_json) = 'object'),
    constraint ck_report_export_manifest_aggregate_only check (aggregate_only = true)
);

create unique index if not exists uk_report_execution_report_run_request
    on report_execution_report (project_id, execution_run_id, request_key)
    where request_key is not null;
create index if not exists idx_report_execution_report_project_status
    on report_execution_report (project_id, status, generated_at desc);
create index if not exists idx_report_execution_report_run
    on report_execution_report (execution_run_id);
create index if not exists idx_report_execution_report_trace
    on report_execution_report (trace_id)
    where trace_id is not null;

create index if not exists idx_report_evidence_manifest_report_source
    on report_evidence_manifest (report_id, source_wp, source_type);
create index if not exists idx_report_evidence_manifest_source_digest
    on report_evidence_manifest (source_wp, source_ref_digest)
    where source_ref_digest is not null;

create index if not exists idx_report_failure_diagnosis_report_status
    on report_failure_diagnosis (report_id, status, updated_at desc);
create index if not exists idx_report_failure_diagnosis_model_digest
    on report_failure_diagnosis (model_invocation_digest)
    where model_invocation_digest is not null;

create index if not exists idx_report_defect_draft_report_status
    on report_defect_draft (report_id, status, updated_at desc);
create index if not exists idx_report_defect_draft_diagnosis
    on report_defect_draft (diagnosis_id)
    where diagnosis_id is not null;

create index if not exists idx_report_export_manifest_report_type
    on report_export_manifest (report_id, export_type, created_at desc);
create index if not exists idx_report_export_manifest_digest
    on report_export_manifest (content_digest)
    where content_digest is not null;

insert into rbac_permission (code, resource_type, action, scope_mask, description)
values
    ('report:read', 'report', 'read', 'PLATFORM,PROJECT,APPLICATION,ENVIRONMENT', '查看 WP10 报告、诊断、证据和草稿摘要'),
    ('report:generate', 'report', 'generate', 'PLATFORM,PROJECT,APPLICATION,ENVIRONMENT', '基于执行运行生成或重试 WP10 报告'),
    ('report:diagnose', 'report', 'diagnose', 'PLATFORM,PROJECT,APPLICATION,ENVIRONMENT', '触发 WP10 失败诊断和查看诊断详情'),
    ('report:export', 'report', 'export', 'PLATFORM,PROJECT,APPLICATION', '导出 WP10 脱敏报告摘要和 manifest'),
    ('report:manage', 'report', 'manage', 'PLATFORM,PROJECT', '归档 WP10 报告和审阅缺陷草稿')
on conflict (code) do update set
    resource_type = excluded.resource_type,
    action = excluded.action,
    scope_mask = excluded.scope_mask,
    description = excluded.description,
    status = 'ENABLED',
    updated_at = now();

with role_permissions(role_code, permission_code) as (
    values
        ('SuperAdmin', 'report:read'),
        ('SuperAdmin', 'report:generate'),
        ('SuperAdmin', 'report:diagnose'),
        ('SuperAdmin', 'report:export'),
        ('SuperAdmin', 'report:manage'),
        ('PlatformAdmin', 'report:read'),
        ('PlatformAdmin', 'report:generate'),
        ('PlatformAdmin', 'report:diagnose'),
        ('PlatformAdmin', 'report:export'),
        ('PlatformAdmin', 'report:manage'),
        ('ProjectOwner', 'report:read'),
        ('ProjectOwner', 'report:generate'),
        ('ProjectOwner', 'report:diagnose'),
        ('ProjectOwner', 'report:export'),
        ('ProjectOwner', 'report:manage'),
        ('AppOwner', 'report:read'),
        ('AppOwner', 'report:generate'),
        ('AppOwner', 'report:diagnose'),
        ('Tester', 'report:read'),
        ('Tester', 'report:generate'),
        ('Tester', 'report:diagnose'),
        ('Developer', 'report:read'),
        ('Auditor', 'report:read'),
        ('Auditor', 'report:export')
)
insert into rbac_role_permission (
    role_id,
    permission_id,
    created_at
)
select
    r.id,
    p.id,
    now()
from role_permissions rp
join rbac_role r on r.code = rp.role_code and r.deleted_at is null
join rbac_permission p on p.code = rp.permission_code and p.status = 'ENABLED'
where not exists (
    select 1
    from rbac_role_permission existing
    where existing.role_id = r.id
      and existing.permission_id = p.id
      and existing.deleted_at is null
);

with configs(config_key, value_json) as (
    values
        ('reporting.audit_events', '[
            "report.generated",
            "report.generate.rejected",
            "report.diagnosis.requested",
            "report.diagnosis.completed",
            "report.defect_draft.created",
            "report.exported",
            "report.export.blocked"
        ]'::jsonb),
        ('reporting.enabled', 'true'::jsonb),
        ('reporting.generate_enabled', 'true'::jsonb),
        ('reporting.diagnosis_enabled', 'true'::jsonb),
        ('reporting.defect_draft_enabled', 'true'::jsonb),
        ('reporting.export_enabled', 'true'::jsonb)
)
insert into base_config (scope_type, scope_id, config_key, value_kind, value_json, status)
select 'SYSTEM', null, c.config_key, 'PLAIN', c.value_json, 'ENABLED'
from configs c
where not exists (
    select 1
    from base_config bc
    where bc.scope_type = 'SYSTEM'
      and bc.scope_id is null
      and bc.config_key = c.config_key
      and bc.deleted_at is null
);

do $$
begin
    if to_regrole('wp1_app') is not null then
        grant select, insert, update on
            report_execution_report,
            report_evidence_manifest,
            report_failure_diagnosis,
            report_defect_draft,
            report_export_manifest
        to wp1_app;
    end if;

    if to_regrole('wp1_readonly') is not null then
        grant select on
            report_execution_report,
            report_evidence_manifest,
            report_failure_diagnosis,
            report_defect_draft,
            report_export_manifest
        to wp1_readonly;
    end if;

    if to_regrole('wp1_migration') is not null then
        grant all privileges on
            report_execution_report,
            report_evidence_manifest,
            report_failure_diagnosis,
            report_defect_draft,
            report_export_manifest
        to wp1_migration;
    end if;
end
$$;

comment on table report_execution_report is 'WP10 aggregate execution report snapshot.';
comment on column report_execution_report.id is 'Report ID.';
comment on column report_execution_report.project_id is 'Owning project scope ID.';
comment on column report_execution_report.execution_run_id is 'Source WP9 execution run ID.';
comment on column report_execution_report.request_key is 'Report generation idempotency key.';
comment on column report_execution_report.status is 'Report lifecycle status.';
comment on column report_execution_report.schema_version is 'Report snapshot schema version.';
comment on column report_execution_report.source_run_digest is 'SHA-256 digest of sanitized source run export.';
comment on column report_execution_report.report_summary_json is 'Aggregate report summary without raw evidence.';
comment on column report_execution_report.redaction_policy_json is 'Report redaction policy flags.';
comment on column report_execution_report.generated_by is 'Actor that generated the report.';
comment on column report_execution_report.generated_at is 'Report generation timestamp.';
comment on column report_execution_report.failed_code is 'Stable sanitized generation failure code.';
comment on column report_execution_report.failure_summary is 'Bounded sanitized generation failure summary.';
comment on column report_execution_report.trace_id is 'Request trace ID for audit correlation.';
comment on column report_execution_report.archived_at is 'Archive timestamp.';
comment on column report_execution_report.created_at is 'Report creation timestamp.';
comment on column report_execution_report.updated_at is 'Report update timestamp.';

comment on table report_evidence_manifest is 'WP10 aggregate-only evidence manifest.';
comment on column report_evidence_manifest.id is 'Evidence manifest ID.';
comment on column report_evidence_manifest.report_id is 'Owning report ID.';
comment on column report_evidence_manifest.source_wp is 'Source work package code.';
comment on column report_evidence_manifest.source_type is 'Source evidence type.';
comment on column report_evidence_manifest.source_ref_digest is 'Digest of source reference metadata.';
comment on column report_evidence_manifest.schema_version is 'Source evidence schema version.';
comment on column report_evidence_manifest.summary_keys_json is 'Summary key list exported for this evidence item.';
comment on column report_evidence_manifest.redaction_flags_json is 'Redaction flags for this evidence item.';
comment on column report_evidence_manifest.evidence_summary_json is 'Aggregate evidence summary without raw payload.';
comment on column report_evidence_manifest.created_at is 'Evidence manifest creation timestamp.';

comment on table report_failure_diagnosis is 'WP10 failure classification and AI diagnosis summary.';
comment on column report_failure_diagnosis.id is 'Failure diagnosis ID.';
comment on column report_failure_diagnosis.report_id is 'Owning report ID.';
comment on column report_failure_diagnosis.status is 'Diagnosis lifecycle status.';
comment on column report_failure_diagnosis.classification_json is 'Rule-based failure classification summary.';
comment on column report_failure_diagnosis.model_invocation_digest is 'Digest of WP2 model invocation reference.';
comment on column report_failure_diagnosis.confidence is 'Diagnosis confidence from 0 to 1.';
comment on column report_failure_diagnosis.manual_review_required is 'Whether a human must review the diagnosis.';
comment on column report_failure_diagnosis.diagnosis_summary_json is 'Sanitized diagnosis summary without raw prompt or response.';
comment on column report_failure_diagnosis.error_code is 'Stable sanitized diagnosis error code.';
comment on column report_failure_diagnosis.created_at is 'Diagnosis creation timestamp.';
comment on column report_failure_diagnosis.updated_at is 'Diagnosis update timestamp.';

comment on table report_defect_draft is 'WP10 platform-local defect draft.';
comment on column report_defect_draft.id is 'Defect draft ID.';
comment on column report_defect_draft.report_id is 'Owning report ID.';
comment on column report_defect_draft.diagnosis_id is 'Source diagnosis ID when available.';
comment on column report_defect_draft.status is 'Defect draft review status.';
comment on column report_defect_draft.title is 'Bounded defect title.';
comment on column report_defect_draft.reproduction_summary is 'Sanitized reproduction summary.';
comment on column report_defect_draft.impact_summary is 'Sanitized impact summary.';
comment on column report_defect_draft.priority_suggestion is 'Suggested priority.';
comment on column report_defect_draft.evidence_refs_json is 'Evidence reference list without raw evidence bodies.';
comment on column report_defect_draft.payload_preview_json is 'Masked external defect payload preview.';
comment on column report_defect_draft.created_by is 'Actor that created the draft.';
comment on column report_defect_draft.updated_by is 'Actor that last updated the draft.';
comment on column report_defect_draft.created_at is 'Draft creation timestamp.';
comment on column report_defect_draft.updated_at is 'Draft update timestamp.';

comment on table report_export_manifest is 'WP10 report export manifest and redaction evidence.';
comment on column report_export_manifest.id is 'Export manifest ID.';
comment on column report_export_manifest.report_id is 'Owning report ID.';
comment on column report_export_manifest.export_type is 'Export type JSON or MARKDOWN.';
comment on column report_export_manifest.status is 'Export manifest status.';
comment on column report_export_manifest.schema_version is 'Export schema version.';
comment on column report_export_manifest.field_set_version is 'Export field-set version.';
comment on column report_export_manifest.redaction_policy_json is 'Export redaction policy flags.';
comment on column report_export_manifest.content_digest is 'SHA-256 digest of exported sanitized content.';
comment on column report_export_manifest.aggregate_only is 'Whether export is aggregate-only.';
comment on column report_export_manifest.exported_by is 'Actor that requested export.';
comment on column report_export_manifest.exported_at is 'Export timestamp.';
comment on column report_export_manifest.block_reason is 'Sanitized reason when export is blocked.';
comment on column report_export_manifest.created_at is 'Export manifest creation timestamp.';
