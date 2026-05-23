-- WP2 durable async invocation job state.
-- Job request/response payloads are stored as JSONB so queued jobs can survive
-- service restarts and finished job status/result can be queried after restart.

create table if not exists ma_invocation_job (
    job_id uuid primary key,
    status varchar(32) not null,
    request_json jsonb not null,
    actor_service varchar(128) not null,
    delegated_user_id varchar(64) not null,
    trace_id varchar(64) not null,
    created_at timestamptz not null default now(),
    started_at timestamptz,
    finished_at timestamptz,
    invocation_id uuid references ma_invocation_log(id),
    error_code varchar(128),
    error_message varchar(512),
    response_json jsonb,
    updated_at timestamptz not null default now(),
    version bigint not null default 0,
    constraint ck_ma_invocation_job_status check (status in ('QUEUED', 'RUNNING', 'SUCCEEDED', 'FAILED', 'CANCELLED')),
    constraint ck_ma_invocation_job_time_order check (
        (started_at is null or started_at >= created_at)
        and (finished_at is null or finished_at >= created_at)
    ),
    constraint ck_ma_invocation_job_terminal_time check (
        (status in ('SUCCEEDED', 'FAILED', 'CANCELLED') and finished_at is not null)
        or (status in ('QUEUED', 'RUNNING'))
    )
);

create index if not exists idx_ma_invocation_job_status_created
    on ma_invocation_job (status, created_at asc);

create index if not exists idx_ma_invocation_job_trace_id
    on ma_invocation_job (trace_id);

create index if not exists idx_ma_invocation_job_invocation_id
    on ma_invocation_job (invocation_id)
    where invocation_id is not null;

comment on table ma_invocation_job is 'WP2 durable async model invocation job ledger. Stores job lifecycle, request payload needed for queued recovery, and finished result/error metadata.';
comment on column ma_invocation_job.request_json is 'Serialized InvokeModelRequest used to resume queued jobs after service restart. Must not contain provider credentials or secret values.';
comment on column ma_invocation_job.response_json is 'Serialized InvokeModelResponse for completed async job query after service restart.';
