-- WP5 publish compensation backend records a restricted automatic repair action.
alter table test_design_publish_record
    drop constraint if exists ck_test_design_publish_action;

alter table test_design_publish_record
    add constraint ck_test_design_publish_action check (action in (
        'CREATE',
        'SKIP_PUBLISHED',
        'SKIP_UNCONFIRMED',
        'LINK_EXISTING',
        'RETRY_LINK_EXISTING',
        'DUPLICATE_REVIEW_REQUIRED',
        'MANUAL_LINK_EXISTING',
        'AUTO_COMPENSATE_LINK_EXISTING'
    ));

alter table test_design_publish_record
    drop constraint if exists ck_test_design_publish_result;

alter table test_design_publish_record
    add constraint ck_test_design_publish_result check (result in (
        'PLANNED',
        'SUCCEEDED',
        'SKIPPED',
        'FAILED',
        'CONFLICT'
    ));

create index if not exists idx_test_design_candidate_publish_compensation
    on test_design_candidate (updated_at asc)
    where status = 'FAILED'
      and asset_case_id is not null;

create index if not exists idx_test_design_publish_compensation_candidate_action
    on test_design_publish_record (candidate_id, action, result, created_at desc)
    where action = 'AUTO_COMPENSATE_LINK_EXISTING'
       or result = 'SUCCEEDED';

create unique index if not exists uk_test_design_publish_auto_comp_candidate
    on test_design_publish_record (candidate_id)
    where action = 'AUTO_COMPENSATE_LINK_EXISTING';
