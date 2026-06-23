-- WP6 API automation runs need an external execution handle so asynchronous runners can receive cancel callbacks.

alter table api_automation_run
    add column if not exists external_run_id varchar(128);

create index if not exists idx_api_automation_run_external
    on api_automation_run (runner_mode, external_run_id)
    where external_run_id is not null;

comment on column api_automation_run.external_run_id is
    'Opaque external runner execution handle used for best-effort cancellation callbacks; not exported to users.';
