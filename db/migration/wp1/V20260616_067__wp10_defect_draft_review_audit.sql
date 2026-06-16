-- WP10 M5B defect draft review audit event dictionary update.

update base_config
set value_json = (
        select coalesce(jsonb_agg(distinct event_name order by event_name), '[]'::jsonb)
        from (
            select jsonb_array_elements_text(value_json) as event_name
            union all
            select 'report.defect_draft.reviewed'
        ) events
    ),
    updated_at = now()
where scope_type = 'SYSTEM'
  and scope_id is null
  and config_key = 'reporting.audit_events'
  and deleted_at is null;
