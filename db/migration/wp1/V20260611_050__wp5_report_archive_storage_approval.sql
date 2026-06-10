-- WP5 report archive storage, approval workflow and line-integrity index.

create table if not exists test_design_report_archive (
    id uuid primary key default gen_random_uuid(),
    manifest_id uuid not null references test_design_report_manifest(id) on delete cascade,
    task_id uuid not null references test_design_task(id) on delete cascade,
    project_id varchar(64) not null,
    storage_backend varchar(32) not null,
    storage_key varchar(160) not null,
    content_digest varchar(64) not null,
    content_size_bytes bigint not null,
    report_row_count bigint not null,
    line_integrity_count bigint not null,
    status varchar(32) not null,
    archive_approval_status varchar(32) not null,
    external_approval_status varchar(32) not null,
    retention_until timestamptz not null,
    content_bytes bytea not null,
    created_by varchar(128),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint ck_test_design_report_archive_storage_backend check (storage_backend in ('DATABASE')),
    constraint ck_test_design_report_archive_digest check (content_digest ~ '^[0-9a-f]{64}$'),
    constraint ck_test_design_report_archive_counts check (
        content_size_bytes > 0
        and report_row_count >= 0
        and line_integrity_count = report_row_count
    ),
    constraint ck_test_design_report_archive_status check (
        status in ('PENDING_APPROVAL', 'ARCHIVED', 'REJECTED')
    ),
    constraint ck_test_design_report_archive_approval_state check (
        archive_approval_status in ('NOT_REQUIRED', 'PENDING', 'APPROVED', 'REJECTED')
        and external_approval_status in ('NOT_REQUESTED', 'PENDING', 'APPROVED', 'REJECTED')
    ),
    constraint ck_test_design_report_archive_storage_key check (
        storage_key ~ '^wp5-report-archive/[0-9a-f-]{36}/[0-9a-f]{64}\.csv$'
    )
);

create unique index if not exists uk_test_design_report_archive_manifest
    on test_design_report_archive (manifest_id);

create unique index if not exists uk_test_design_report_archive_storage_key
    on test_design_report_archive (storage_key);

create index if not exists idx_test_design_report_archive_task_created
    on test_design_report_archive (task_id, created_at desc);

create index if not exists idx_test_design_report_archive_project_status
    on test_design_report_archive (project_id, status, created_at desc);

create index if not exists idx_test_design_report_archive_retention
    on test_design_report_archive (retention_until);

create table if not exists test_design_report_archive_line_integrity (
    archive_id uuid not null references test_design_report_archive(id) on delete cascade,
    row_number int not null,
    row_digest varchar(64) not null,
    previous_row_digest varchar(64),
    chain_digest varchar(64) not null,
    record_type varchar(32),
    section varchar(64),
    metric varchar(128),
    created_at timestamptz not null default now(),
    primary key (archive_id, row_number),
    constraint ck_test_design_report_archive_line_number check (row_number > 0),
    constraint ck_test_design_report_archive_line_digest check (
        row_digest ~ '^[0-9a-f]{64}$'
        and (previous_row_digest is null or previous_row_digest ~ '^[0-9a-f]{64}$')
        and chain_digest ~ '^[0-9a-f]{64}$'
    ),
    constraint ck_test_design_report_archive_line_metadata check (
        (record_type is null or record_type ~ '^[A-Za-z0-9_.:-]{1,32}$')
        and (section is null or section ~ '^[A-Za-z0-9_.:-]{1,64}$')
        and (metric is null or metric ~ '^[A-Za-z0-9_.:-]{1,128}$')
    )
);

create index if not exists idx_test_design_report_archive_line_chain
    on test_design_report_archive_line_integrity (archive_id, chain_digest);

create index if not exists idx_test_design_report_archive_line_section_metric
    on test_design_report_archive_line_integrity (archive_id, section, metric);

create table if not exists test_design_report_archive_approval (
    id uuid primary key default gen_random_uuid(),
    archive_id uuid not null references test_design_report_archive(id) on delete cascade,
    task_id uuid not null references test_design_task(id) on delete cascade,
    project_id varchar(64) not null,
    approval_type varchar(32) not null,
    status varchar(32) not null,
    reason_code varchar(64) not null,
    approval_reason_code varchar(64),
    work_order_key varchar(128) not null,
    work_order_title varchar(256),
    work_order_url varchar(512),
    work_order_status varchar(32) not null,
    request_summary text not null,
    request_summary_digest varchar(64) not null,
    request_note text,
    review_note text,
    requested_by varchar(128),
    approved_by varchar(128),
    reviewed_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint ck_test_design_report_archive_approval_type check (
        approval_type in ('ARCHIVE', 'EXTERNAL_SHARE')
    ),
    constraint ck_test_design_report_archive_approval_status check (
        status in ('PENDING', 'APPROVED', 'REJECTED')
    ),
    constraint ck_test_design_report_archive_approval_reason check (
        reason_code in (
            'RETENTION_POLICY',
            'COMPLIANCE_AUDIT',
            'CUSTOMER_REQUEST',
            'REGULATED_EXPORT',
            'SMOKE_VALIDATION'
        )
        and (
            approval_reason_code is null
            or approval_reason_code in (
                'RETENTION_POLICY',
                'COMPLIANCE_AUDIT',
                'CUSTOMER_REQUEST',
                'REGULATED_EXPORT',
                'SMOKE_VALIDATION'
            )
        )
    ),
    constraint ck_test_design_report_archive_approval_work_status check (
        work_order_status in ('OPEN', 'IN_REVIEW', 'APPROVED', 'REJECTED', 'CANCELLED')
    ),
    constraint ck_test_design_report_archive_approval_digest check (
        request_summary_digest ~ '^[0-9a-f]{64}$'
    ),
    constraint ck_test_design_report_archive_approval_lengths check (
        char_length(work_order_key) between 1 and 128
        and (work_order_title is null or char_length(work_order_title) between 1 and 256)
        and (work_order_url is null or char_length(work_order_url) between 1 and 512)
        and char_length(request_summary) between 1 and 1000
        and (request_note is null or char_length(request_note) between 1 and 1000)
        and (review_note is null or char_length(review_note) between 1 and 1000)
    )
);

create index if not exists idx_test_design_report_archive_approval_archive_created
    on test_design_report_archive_approval (archive_id, created_at desc);

create index if not exists idx_test_design_report_archive_approval_project_type_status
    on test_design_report_archive_approval (project_id, approval_type, status, updated_at desc);

create index if not exists idx_test_design_report_archive_approval_work_order
    on test_design_report_archive_approval (work_order_key);

create table if not exists test_design_report_archive_note (
    id uuid primary key default gen_random_uuid(),
    approval_id uuid not null references test_design_report_archive_approval(id) on delete cascade,
    note_type varchar(32) not null,
    note_text text not null,
    created_by varchar(128),
    created_at timestamptz not null default now(),
    constraint ck_test_design_report_archive_note_type check (
        note_type in ('REQUEST', 'REVIEW', 'COMMENT', 'WORK_ORDER')
    ),
    constraint ck_test_design_report_archive_note_text check (
        char_length(note_text) between 1 and 1000
    )
);

create index if not exists idx_test_design_report_archive_note_approval_created
    on test_design_report_archive_note (approval_id, created_at asc);

comment on table test_design_report_archive is 'WP5 stored aggregate task-report archives. Content is server-side only and never returned by operations metadata APIs.';
comment on column test_design_report_archive.id is 'Report archive identifier.';
comment on column test_design_report_archive.manifest_id is 'Stored report manifest associated with this archive.';
comment on column test_design_report_archive.task_id is 'WP5 task associated with this archive.';
comment on column test_design_report_archive.project_id is 'Project scope for permission and retention operations.';
comment on column test_design_report_archive.storage_backend is 'Managed archive backend. DATABASE means content_bytes stores the safety-scanned aggregate CSV.';
comment on column test_design_report_archive.storage_key is 'Internal storage key; never returned by public metadata APIs.';
comment on column test_design_report_archive.content_digest is 'SHA-256 digest of archived aggregate CSV content.';
comment on column test_design_report_archive.content_size_bytes is 'Archived CSV byte size.';
comment on column test_design_report_archive.report_row_count is 'Aggregate report data row count excluding the CSV header.';
comment on column test_design_report_archive.line_integrity_count is 'Stored line-integrity index row count.';
comment on column test_design_report_archive.status is 'Archive lifecycle status.';
comment on column test_design_report_archive.archive_approval_status is 'Archive finalization approval status.';
comment on column test_design_report_archive.external_approval_status is 'External-share approval status.';
comment on column test_design_report_archive.retention_until is 'Retention deadline calculated from report generation time.';
comment on column test_design_report_archive.content_bytes is 'Safety-scanned aggregate CSV bytes for true platform-managed archive storage.';
comment on column test_design_report_archive.created_by is 'Operator or service principal that created the archive.';
comment on column test_design_report_archive.created_at is 'Archive creation timestamp.';
comment on column test_design_report_archive.updated_at is 'Archive last update timestamp.';

comment on table test_design_report_archive_line_integrity is 'Line-level integrity index for WP5 aggregate report archives; stores only line numbers and digests.';
comment on column test_design_report_archive_line_integrity.archive_id is 'Report archive owning this integrity row.';
comment on column test_design_report_archive_line_integrity.row_number is 'One-based report data row number excluding the CSV header.';
comment on column test_design_report_archive_line_integrity.row_digest is 'SHA-256 digest of the report row, retained server-side only.';
comment on column test_design_report_archive_line_integrity.previous_row_digest is 'Previous row SHA-256 digest used to chain integrity rows.';
comment on column test_design_report_archive_line_integrity.chain_digest is 'SHA-256 digest chaining previous and current row digest.';
comment on column test_design_report_archive_line_integrity.record_type is 'Safe fixed metadata extracted from report recordType.';
comment on column test_design_report_archive_line_integrity.section is 'Safe fixed metadata extracted from report section.';
comment on column test_design_report_archive_line_integrity.metric is 'Safe fixed metadata extracted from report metric.';
comment on column test_design_report_archive_line_integrity.created_at is 'Integrity row creation timestamp.';

comment on table test_design_report_archive_approval is 'WP5 report archive finalization and external-share approval work orders.';
comment on column test_design_report_archive_approval.id is 'Report archive approval identifier.';
comment on column test_design_report_archive_approval.archive_id is 'Report archive being reviewed.';
comment on column test_design_report_archive_approval.task_id is 'WP5 task associated with the archive.';
comment on column test_design_report_archive_approval.project_id is 'Project scope for approval permissions.';
comment on column test_design_report_archive_approval.approval_type is 'Approval type: ARCHIVE or EXTERNAL_SHARE.';
comment on column test_design_report_archive_approval.status is 'Approval status.';
comment on column test_design_report_archive_approval.reason_code is 'Bounded request reason code.';
comment on column test_design_report_archive_approval.approval_reason_code is 'Bounded review reason code.';
comment on column test_design_report_archive_approval.work_order_key is 'Bounded work-order key.';
comment on column test_design_report_archive_approval.work_order_title is 'Bounded work-order title.';
comment on column test_design_report_archive_approval.work_order_url is 'Bounded optional work-order URL.';
comment on column test_design_report_archive_approval.work_order_status is 'External or internal work-order status.';
comment on column test_design_report_archive_approval.request_summary is 'Bounded request summary without archive content or sensitive payloads.';
comment on column test_design_report_archive_approval.request_summary_digest is 'SHA-256 digest of the request summary.';
comment on column test_design_report_archive_approval.request_note is 'Bounded request note without archive content or sensitive payloads.';
comment on column test_design_report_archive_approval.review_note is 'Bounded review note without archive content or sensitive payloads.';
comment on column test_design_report_archive_approval.requested_by is 'Operator or service principal that requested approval.';
comment on column test_design_report_archive_approval.approved_by is 'Operator or service principal that approved the work order.';
comment on column test_design_report_archive_approval.reviewed_at is 'Approval review timestamp.';
comment on column test_design_report_archive_approval.created_at is 'Approval creation timestamp.';
comment on column test_design_report_archive_approval.updated_at is 'Approval last update timestamp.';

comment on table test_design_report_archive_note is 'WP5 report archive approval work order note timeline.';
comment on column test_design_report_archive_note.id is 'Report archive approval note identifier.';
comment on column test_design_report_archive_note.approval_id is 'Approval work order owning this note.';
comment on column test_design_report_archive_note.note_type is 'Note type: REQUEST, REVIEW, COMMENT or WORK_ORDER.';
comment on column test_design_report_archive_note.note_text is 'Bounded note text without archive content or sensitive payloads.';
comment on column test_design_report_archive_note.created_by is 'Operator or service principal that created the note.';
comment on column test_design_report_archive_note.created_at is 'Note creation timestamp.';
