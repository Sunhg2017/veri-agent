-- WP10 M2 report generation/query control-plane audit event dictionary update.

update base_config
set value_json = '[
    "report.generated",
    "report.generate.rejected",
    "report.archived",
    "report.diagnosis.requested",
    "report.diagnosis.completed",
    "report.defect_draft.created",
    "report.exported",
    "report.export.blocked"
]'::jsonb,
    updated_at = now()
where scope_type = 'SYSTEM'
  and scope_id is null
  and config_key = 'reporting.audit_events'
  and deleted_at is null;
