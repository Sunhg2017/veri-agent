-- WP5 release-readiness approval workflow and quality-gate exceptions.

create table if not exists test_design_release_readiness_approval (
    id uuid primary key default gen_random_uuid(),
    task_id uuid not null references test_design_task(id),
    project_id varchar(64) not null,
    status varchar(32) not null,
    quality_gate_status varchar(32) not null,
    blocking_count bigint not null default 0,
    warning_count bigint not null default 0,
    readiness_digest varchar(64) not null,
    exception_reason_code varchar(64) not null,
    approval_reason_code varchar(64),
    work_order_key varchar(128) not null,
    work_order_title varchar(256),
    work_order_url varchar(512),
    work_order_status varchar(32) not null,
    exception_summary text not null,
    exception_summary_digest varchar(64) not null,
    risk_mitigation text not null,
    request_note text,
    review_note text,
    requested_by varchar(128),
    approved_by varchar(128),
    reviewed_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint ck_test_design_rr_approval_status check (
        status in ('PENDING', 'APPROVED', 'REJECTED')
    ),
    constraint ck_test_design_rr_approval_quality_status check (
        quality_gate_status in ('PASSED', 'WARNING', 'BLOCKED')
    ),
    constraint ck_test_design_rr_approval_counts check (
        blocking_count >= 0 and warning_count >= 0
    ),
    constraint ck_test_design_rr_approval_digest check (
        readiness_digest ~ '^[0-9a-f]{64}$'
        and exception_summary_digest ~ '^[0-9a-f]{64}$'
    ),
    constraint ck_test_design_rr_approval_reason check (
        exception_reason_code in (
            'BUSINESS_CRITICAL_RELEASE',
            'FALSE_POSITIVE_QUALITY_GATE',
            'LOW_RISK_ACCEPTANCE',
            'TIME_BOXED_EXCEPTION',
            'SMOKE_VALIDATION'
        )
        and (
            approval_reason_code is null
            or approval_reason_code in (
                'BUSINESS_CRITICAL_RELEASE',
                'FALSE_POSITIVE_QUALITY_GATE',
                'LOW_RISK_ACCEPTANCE',
                'TIME_BOXED_EXCEPTION',
                'SMOKE_VALIDATION'
            )
        )
    ),
    constraint ck_test_design_rr_approval_work_status check (
        work_order_status in ('OPEN', 'IN_REVIEW', 'APPROVED', 'REJECTED', 'CANCELLED')
    ),
    constraint ck_test_design_rr_approval_work_lengths check (
        char_length(work_order_key) between 1 and 128
        and (work_order_title is null or char_length(work_order_title) between 1 and 256)
        and (work_order_url is null or char_length(work_order_url) between 1 and 512)
    ),
    constraint ck_test_design_rr_approval_text_lengths check (
        char_length(exception_summary) between 1 and 1000
        and char_length(risk_mitigation) between 1 and 1000
        and (request_note is null or char_length(request_note) between 1 and 1000)
        and (review_note is null or char_length(review_note) between 1 and 1000)
    )
);

create index if not exists idx_test_design_rr_approval_task_created
    on test_design_release_readiness_approval (task_id, created_at desc);

create index if not exists idx_test_design_rr_approval_task_status_digest
    on test_design_release_readiness_approval (task_id, status, readiness_digest, updated_at desc);

create index if not exists idx_test_design_rr_approval_project_created
    on test_design_release_readiness_approval (project_id, created_at desc);

create index if not exists idx_test_design_rr_approval_work_order
    on test_design_release_readiness_approval (work_order_key);

create table if not exists test_design_release_readiness_note (
    id uuid primary key default gen_random_uuid(),
    approval_id uuid not null references test_design_release_readiness_approval(id),
    note_type varchar(32) not null,
    note_text text not null,
    created_by varchar(128),
    created_at timestamptz not null default now(),
    constraint ck_test_design_rr_note_type check (
        note_type in ('REQUEST', 'REVIEW', 'COMMENT', 'WORK_ORDER')
    ),
    constraint ck_test_design_rr_note_text check (
        char_length(note_text) between 1 and 1000
    )
);

create index if not exists idx_test_design_rr_note_approval_created
    on test_design_release_readiness_note (approval_id, created_at asc);

comment on table test_design_release_readiness_approval is 'WP5 task-scoped release-readiness approval and quality-gate exception metadata.';
comment on column test_design_release_readiness_approval.id is 'Primary key for a release-readiness approval request.';
comment on column test_design_release_readiness_approval.task_id is 'Owning WP5 test design task.';
comment on column test_design_release_readiness_approval.project_id is 'Owning project scope ID copied from the task.';
comment on column test_design_release_readiness_approval.status is 'Approval state: PENDING, APPROVED or REJECTED.';
comment on column test_design_release_readiness_approval.quality_gate_status is 'Aggregate readiness status captured for this exception.';
comment on column test_design_release_readiness_approval.blocking_count is 'Aggregate blocking readiness check count captured for this exception.';
comment on column test_design_release_readiness_approval.warning_count is 'Aggregate warning readiness check count captured for this exception.';
comment on column test_design_release_readiness_approval.readiness_digest is 'SHA-256 digest of aggregate readiness checks; prevents stale exception reuse.';
comment on column test_design_release_readiness_approval.exception_reason_code is 'Enum-like exception reason code.';
comment on column test_design_release_readiness_approval.approval_reason_code is 'Enum-like approval reason code.';
comment on column test_design_release_readiness_approval.work_order_key is 'Release-readiness approval work order key.';
comment on column test_design_release_readiness_approval.work_order_title is 'Bounded approval work order title.';
comment on column test_design_release_readiness_approval.work_order_url is 'Optional approval work order URL; never exported to task diagnostics or reports.';
comment on column test_design_release_readiness_approval.work_order_status is 'Approval work order status.';
comment on column test_design_release_readiness_approval.exception_summary is 'Bounded exception summary for operators; candidate evidence and sensitive payloads are prohibited.';
comment on column test_design_release_readiness_approval.exception_summary_digest is 'SHA-256 digest of the bounded exception summary.';
comment on column test_design_release_readiness_approval.risk_mitigation is 'Bounded risk mitigation text for operators.';
comment on column test_design_release_readiness_approval.request_note is 'Bounded requester note.';
comment on column test_design_release_readiness_approval.review_note is 'Bounded reviewer note.';
comment on column test_design_release_readiness_approval.requested_by is 'Requester identity snapshot for audit attribution.';
comment on column test_design_release_readiness_approval.approved_by is 'Approver identity snapshot for audit attribution.';
comment on column test_design_release_readiness_approval.reviewed_at is 'Approval or rejection timestamp.';
comment on column test_design_release_readiness_approval.created_at is 'Creation timestamp for approval request ordering.';
comment on column test_design_release_readiness_approval.updated_at is 'Last status or metadata update timestamp.';
comment on table test_design_release_readiness_note is 'WP5 release-readiness approval work order note timeline.';
comment on column test_design_release_readiness_note.id is 'Primary key of a release-readiness approval note.';
comment on column test_design_release_readiness_note.approval_id is 'Release-readiness approval owning this note.';
comment on column test_design_release_readiness_note.note_type is 'Approval note type: REQUEST, REVIEW, COMMENT or WORK_ORDER.';
comment on column test_design_release_readiness_note.note_text is 'Bounded approval note text; candidate evidence, prompt and secrets are prohibited.';
comment on column test_design_release_readiness_note.created_by is 'Operator or service actor who appended the note.';
comment on column test_design_release_readiness_note.created_at is 'Time when the approval note was appended.';
