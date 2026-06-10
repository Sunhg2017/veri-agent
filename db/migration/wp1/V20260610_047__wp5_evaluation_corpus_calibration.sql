-- WP5 real evaluation sample maintenance and long-term prompt calibration.

create table if not exists test_design_evaluation_sample (
    id uuid primary key default gen_random_uuid(),
    project_id varchar(64) not null,
    sample_key varchar(128) not null,
    title varchar(256) not null,
    source_type varchar(32) not null default 'MANUAL',
    source_task_id uuid references test_design_task(id) on delete set null,
    source_candidate_id uuid references test_design_candidate(id) on delete set null,
    prompt_key varchar(128),
    prompt_version varchar(64),
    coverage_type varchar(32) not null default 'FUNCTIONAL',
    priority varchar(16) not null default 'MEDIUM',
    status varchar(32) not null default 'CANDIDATE',
    baseline_version varchar(128),
    requirement_summary text not null,
    expected_case_outline text not null,
    assertion_notes text,
    tags text,
    maintenance_note text,
    sample_digest varchar(64) not null,
    sensitive_scan_status varchar(32) not null default 'PASSED',
    created_by varchar(128),
    updated_by varchar(128),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint ck_test_design_eval_sample_key check (sample_key ~ '^[A-Za-z0-9_.:-]{1,128}$'),
    constraint ck_test_design_eval_sample_source check (source_type in (
        'MANUAL','REVIEW_FEEDBACK','PUBLISHED_CASE','IMPORTED'
    )),
    constraint ck_test_design_eval_sample_status check (status in (
        'CANDIDATE','GOLDEN','FROZEN','DEPRECATED'
    )),
    constraint ck_test_design_eval_sample_coverage check (coverage_type in (
        'SMOKE','FUNCTIONAL','EXCEPTION','BOUNDARY','PERMISSION','REGRESSION'
    )),
    constraint ck_test_design_eval_sample_priority check (priority in ('CRITICAL','HIGH','MEDIUM','LOW')),
    constraint ck_test_design_eval_sample_baseline check (
        status not in ('GOLDEN','FROZEN') or baseline_version is not null
    ),
    constraint ck_test_design_eval_sample_digest check (sample_digest ~ '^[0-9a-f]{64}$'),
    constraint ck_test_design_eval_sample_scan_status check (sensitive_scan_status in ('PASSED')),
    constraint ck_test_design_eval_sample_text_lengths check (
        char_length(requirement_summary) <= 2000
        and char_length(expected_case_outline) <= 2000
        and coalesce(char_length(assertion_notes), 0) <= 1000
        and coalesce(char_length(tags), 0) <= 512
        and coalesce(char_length(maintenance_note), 0) <= 1000
    )
);

create unique index if not exists uk_test_design_eval_sample_project_key
    on test_design_evaluation_sample (project_id, lower(sample_key));
create index if not exists idx_test_design_eval_sample_project_status
    on test_design_evaluation_sample (project_id, status, updated_at desc);
create index if not exists idx_test_design_eval_sample_prompt_baseline
    on test_design_evaluation_sample (project_id, prompt_key, baseline_version, status);
create index if not exists idx_test_design_eval_sample_source_candidate
    on test_design_evaluation_sample (source_candidate_id)
    where source_candidate_id is not null;

create table if not exists test_design_calibration_run (
    id uuid primary key default gen_random_uuid(),
    project_id varchar(64) not null,
    prompt_key varchar(128),
    prompt_version varchar(64),
    baseline_version varchar(128),
    run_mode varchar(32) not null default 'MANUAL',
    status varchar(32) not null,
    sample_count bigint not null default 0,
    golden_sample_count bigint not null default 0,
    task_count bigint not null default 0,
    candidate_count bigint not null default 0,
    step_complete_percent numeric(6,2) not null default 0,
    expected_complete_percent numeric(6,2) not null default 0,
    low_confidence_percent numeric(6,2) not null default 0,
    error_percent numeric(6,2) not null default 0,
    duplicate_key_collision_count bigint not null default 0,
    feedback_signal_count bigint not null default 0,
    readiness_status varchar(32) not null default 'UNKNOWN',
    readiness_blocking_count bigint not null default 0,
    readiness_warning_count bigint not null default 0,
    regression_count bigint not null default 0,
    baseline_digest varchar(64),
    result_digest varchar(64) not null,
    notes text,
    run_by varchar(128),
    created_at timestamptz not null default now(),
    constraint ck_test_design_calibration_run_mode check (run_mode in (
        'MANUAL','PROMPT_CHANGE','SCHEDULED','BASELINE_FREEZE'
    )),
    constraint ck_test_design_calibration_run_status check (status in ('PASSED','WARNING','BLOCKED')),
    constraint ck_test_design_calibration_run_counts check (
        sample_count >= 0
        and golden_sample_count >= 0
        and task_count >= 0
        and candidate_count >= 0
        and duplicate_key_collision_count >= 0
        and feedback_signal_count >= 0
        and readiness_blocking_count >= 0
        and readiness_warning_count >= 0
        and regression_count >= 0
    ),
    constraint ck_test_design_calibration_run_percents check (
        step_complete_percent >= 0 and step_complete_percent <= 100
        and expected_complete_percent >= 0 and expected_complete_percent <= 100
        and low_confidence_percent >= 0 and low_confidence_percent <= 100
        and error_percent >= 0 and error_percent <= 100
    ),
    constraint ck_test_design_calibration_run_digest check (
        (baseline_digest is null or baseline_digest ~ '^[0-9a-f]{64}$')
        and result_digest ~ '^[0-9a-f]{64}$'
    ),
    constraint ck_test_design_calibration_run_notes check (coalesce(char_length(notes), 0) <= 1000)
);

create index if not exists idx_test_design_calibration_run_project_prompt_created
    on test_design_calibration_run (project_id, prompt_key, prompt_version, created_at desc);
create index if not exists idx_test_design_calibration_run_baseline_created
    on test_design_calibration_run (project_id, baseline_version, created_at desc);
create index if not exists idx_test_design_calibration_run_status_created
    on test_design_calibration_run (project_id, status, created_at desc);

comment on table test_design_evaluation_sample is 'WP5 curated evaluation samples for prompt calibration; stores bounded sanitized summaries instead of raw prompt payloads.';
comment on column test_design_evaluation_sample.id is 'Evaluation sample ID.';
comment on column test_design_evaluation_sample.project_id is 'Owning project scope ID.';
comment on column test_design_evaluation_sample.sample_key is 'Project-unique sample key for stable calibration identity.';
comment on column test_design_evaluation_sample.title is 'Bounded sample title.';
comment on column test_design_evaluation_sample.source_type is 'Sample source category.';
comment on column test_design_evaluation_sample.source_task_id is 'Optional source WP5 task reference.';
comment on column test_design_evaluation_sample.source_candidate_id is 'Optional source WP5 candidate reference.';
comment on column test_design_evaluation_sample.prompt_key is 'Prompt template key this sample calibrates.';
comment on column test_design_evaluation_sample.prompt_version is 'Prompt version associated with this sample.';
comment on column test_design_evaluation_sample.coverage_type is 'Coverage type bucket.';
comment on column test_design_evaluation_sample.priority is 'Sample priority.';
comment on column test_design_evaluation_sample.status is 'Sample lifecycle state.';
comment on column test_design_evaluation_sample.baseline_version is 'Golden-set baseline version when the sample is active in calibration.';
comment on column test_design_evaluation_sample.requirement_summary is 'Bounded sanitized requirement summary maintained for calibration.';
comment on column test_design_evaluation_sample.expected_case_outline is 'Bounded expected test-case outline for calibration.';
comment on column test_design_evaluation_sample.assertion_notes is 'Bounded assertion or calibration note.';
comment on column test_design_evaluation_sample.tags is 'Bounded comma-separated sample tags.';
comment on column test_design_evaluation_sample.maintenance_note is 'Bounded maintenance note for operators.';
comment on column test_design_evaluation_sample.sample_digest is 'SHA-256 digest of maintained sample content and lifecycle metadata.';
comment on column test_design_evaluation_sample.sensitive_scan_status is 'Sensitive text scan status for maintained sample text.';
comment on column test_design_evaluation_sample.created_by is 'Actor that created the sample.';
comment on column test_design_evaluation_sample.updated_by is 'Actor that last updated the sample.';
comment on column test_design_evaluation_sample.created_at is 'Sample creation timestamp.';
comment on column test_design_evaluation_sample.updated_at is 'Sample update timestamp.';

comment on table test_design_calibration_run is 'WP5 prompt calibration run history bound to maintained sample baselines and aggregate quality metrics.';
comment on column test_design_calibration_run.id is 'Calibration run ID.';
comment on column test_design_calibration_run.project_id is 'Owning project scope ID.';
comment on column test_design_calibration_run.prompt_key is 'Prompt template key evaluated by the run.';
comment on column test_design_calibration_run.prompt_version is 'Prompt version evaluated by the run.';
comment on column test_design_calibration_run.baseline_version is 'Sample baseline version evaluated by the run.';
comment on column test_design_calibration_run.run_mode is 'Calibration trigger mode.';
comment on column test_design_calibration_run.status is 'Calibration result status.';
comment on column test_design_calibration_run.sample_count is 'Maintained sample count included in the baseline query.';
comment on column test_design_calibration_run.golden_sample_count is 'Golden or frozen sample count available for calibration.';
comment on column test_design_calibration_run.task_count is 'Completed WP5 task count in the evaluated prompt window.';
comment on column test_design_calibration_run.candidate_count is 'Candidate count in the evaluated prompt window.';
comment on column test_design_calibration_run.step_complete_percent is 'Aggregate step completeness percentage.';
comment on column test_design_calibration_run.expected_complete_percent is 'Aggregate final expected-result completeness percentage.';
comment on column test_design_calibration_run.low_confidence_percent is 'Aggregate low-confidence percentage.';
comment on column test_design_calibration_run.error_percent is 'Aggregate error-candidate percentage.';
comment on column test_design_calibration_run.duplicate_key_collision_count is 'Aggregate duplicate-key collision count.';
comment on column test_design_calibration_run.feedback_signal_count is 'Aggregate human feedback signal count.';
comment on column test_design_calibration_run.readiness_status is 'Readiness status calculated from aggregate quality checks.';
comment on column test_design_calibration_run.readiness_blocking_count is 'Blocking readiness check count.';
comment on column test_design_calibration_run.readiness_warning_count is 'Warning readiness check count.';
comment on column test_design_calibration_run.regression_count is 'Number of metrics that regressed compared with the previous run for the same prompt version.';
comment on column test_design_calibration_run.baseline_digest is 'SHA-256 digest of maintained baseline sample identities.';
comment on column test_design_calibration_run.result_digest is 'SHA-256 digest of the aggregate calibration result.';
comment on column test_design_calibration_run.notes is 'Bounded operator note for the calibration run.';
comment on column test_design_calibration_run.run_by is 'Actor that created the calibration run.';
comment on column test_design_calibration_run.created_at is 'Calibration run creation timestamp.';
