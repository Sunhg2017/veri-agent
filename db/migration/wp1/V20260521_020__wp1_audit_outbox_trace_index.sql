create index if not exists idx_audit_outbox_trace_id
    on audit_outbox ((
        coalesce(
            event_payload_json ->> 'traceId',
            event_payload_json ->> 'trace_id',
            event_payload_json #>> '{metadata,traceId}',
            event_payload_json #>> '{metadata,trace_id}',
            event_payload_json #>> '{record,traceId}',
            ''
        )
    ));
