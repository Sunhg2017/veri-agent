-- WP9 execution run log persistence and historical replay support.
-- Stores sanitized control-plane log lines for history lookup and SSE reconnect backfill.

create table if not exists execution_run_log (
    id uuid primary key default gen_random_uuid(),
    run_id uuid not null references execution_run(id) on delete cascade,
    node_run_id uuid references execution_node_run(id) on delete set null,
    level varchar(16) not null,
    stage varchar(64),
    message varchar(512) not null,
    metadata_json jsonb not null default '{}'::jsonb,
    event_at timestamptz not null,
    created_at timestamptz not null default now(),
    constraint ck_execution_run_log_level check (level in ('INFO','WARN','ERROR','SUCCESS')),
    constraint ck_execution_run_log_stage check (
        stage is null
        or stage ~ '^[A-Za-z0-9._:-]{1,64}$'
    ),
    constraint ck_execution_run_log_message check (btrim(message) <> ''),
    constraint ck_execution_run_log_metadata_json check (jsonb_typeof(metadata_json) = 'object')
);

create index if not exists idx_execution_run_log_run_event
    on execution_run_log (run_id, event_at desc, created_at desc);

create index if not exists idx_execution_run_log_node_event
    on execution_run_log (node_run_id, event_at desc, created_at desc)
    where node_run_id is not null;

do $$
begin
    if to_regrole('wp1_app') is not null then
        grant select, insert, update on execution_run_log to wp1_app;
    end if;

    if to_regrole('wp1_readonly') is not null then
        grant select on execution_run_log to wp1_readonly;
    end if;

    if to_regrole('wp1_migration') is not null then
        grant all privileges on execution_run_log to wp1_migration;
    end if;
end
$$;

comment on table execution_run_log is 'WP9 sanitized execution run control-plane log lines for history replay.';
comment on column execution_run_log.id is 'Execution run log entry ID.';
comment on column execution_run_log.run_id is 'Owning execution run ID.';
comment on column execution_run_log.node_run_id is 'Optional node run ID associated with this log line.';
comment on column execution_run_log.level is 'Sanitized log level.';
comment on column execution_run_log.stage is 'Bounded control-plane stage key.';
comment on column execution_run_log.message is 'Sanitized bounded log message without raw runner payloads.';
comment on column execution_run_log.metadata_json is 'Sanitized metadata map with sensitive keys removed.';
comment on column execution_run_log.event_at is 'Event time used for ordering and replay.';
comment on column execution_run_log.created_at is 'Persistence timestamp.';
