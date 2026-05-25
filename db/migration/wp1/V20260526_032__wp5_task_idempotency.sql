-- WP5 task creation idempotency.
-- The idempotency key is scoped by project, while request_digest detects accidental key reuse with different payloads.

alter table test_design_task
    add column if not exists idempotency_key varchar(128),
    add column if not exists request_digest varchar(64);

create unique index if not exists uk_test_design_task_project_idempotency
    on test_design_task (project_id, idempotency_key)
    where idempotency_key is not null;

create index if not exists idx_test_design_task_request_digest
    on test_design_task (request_digest)
    where request_digest is not null;
