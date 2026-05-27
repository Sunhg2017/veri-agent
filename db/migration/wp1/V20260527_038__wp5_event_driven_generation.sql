-- WP5 event-driven test design generation state.
-- Existing environments created before QUEUED existed need the task status check widened before async task creation.

alter table test_design_task
    drop constraint if exists ck_test_design_task_status;

alter table test_design_task
    add constraint ck_test_design_task_status check (status in (
        'DRAFT','QUEUED','RUNNING','SUCCEEDED','PARTIAL_SUCCESS','FAILED','CANCELLED','PUBLISHING','PUBLISHED'
    ));
