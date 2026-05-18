-- WP2 single-platform scope cleanup.
-- Removes the legacy tenant_id column from model invocation logs.

drop index if exists idx_ma_invocation_scope_time;

alter table ma_invocation_log
    drop column if exists tenant_id;

create index if not exists idx_ma_invocation_scope_time
    on ma_invocation_log (project_id, application_id, created_at desc);
