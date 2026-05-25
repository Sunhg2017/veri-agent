-- WP5 task context pack summary for model-ready generation.
-- Stores only redacted source summaries and a digest; raw prompts or full source documents stay out of WP5 tables.

alter table test_design_task
    add column if not exists input_digest varchar(96);

alter table test_design_task
    add column if not exists context_summary_json jsonb not null default '{}'::jsonb;

create index if not exists idx_test_design_task_input_digest
    on test_design_task (project_id, input_digest)
    where input_digest is not null;
