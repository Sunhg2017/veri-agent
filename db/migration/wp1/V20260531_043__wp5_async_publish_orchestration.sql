-- WP5 async cross-WP publish orchestration state.
-- Formal publish first persists local queue state, then an event consumer writes WP3 assets idempotently.

alter table test_design_task
    drop constraint if exists ck_test_design_task_status;

alter table test_design_task
    add constraint ck_test_design_task_status check (status in (
        'DRAFT',
        'QUEUED',
        'RUNNING',
        'SUCCEEDED',
        'PARTIAL_SUCCESS',
        'FAILED',
        'CANCELLED',
        'PUBLISH_QUEUED',
        'PUBLISHING',
        'PUBLISHED'
    ));

alter table test_design_candidate
    drop constraint if exists ck_test_design_candidate_status;

alter table test_design_candidate
    add constraint ck_test_design_candidate_status check (status in (
        'GENERATED',
        'EDITED',
        'CONFIRMED',
        'REJECTED',
        'IGNORED',
        'PUBLISH_QUEUED',
        'PUBLISHING',
        'PUBLISHED',
        'FAILED'
    ));

alter table test_design_publish_record
    drop constraint if exists ck_test_design_publish_result;

alter table test_design_publish_record
    add constraint ck_test_design_publish_result check (result in (
        'PLANNED',
        'QUEUED',
        'RUNNING',
        'SUCCEEDED',
        'SKIPPED',
        'FAILED',
        'CONFLICT'
    ));

create index if not exists idx_test_design_candidate_publish_queue
    on test_design_candidate (updated_at asc)
    where status = 'PUBLISH_QUEUED';

create index if not exists idx_test_design_candidate_publish_running
    on test_design_candidate (updated_at asc)
    where status = 'PUBLISHING';
