-- WP4 model parse correction feedback samples.
-- Captures sanitized manual edits to MODEL candidates for later golden corpus curation.

create table if not exists document_input_parse_feedback_sample (
    id uuid primary key default gen_random_uuid(),
    candidate_id uuid not null references document_input_candidate(id) on delete cascade,
    import_id uuid not null references document_input_import(id) on delete cascade,
    project_id varchar(64) not null,
    source_type varchar(32) not null,
    input_digest varchar(128),
    source_ref_digest varchar(128),
    source_fragment_digest varchar(128),
    parse_source varchar(32) not null,
    model_invocation_id uuid,
    model_provider_name varchar(128),
    model_name varchar(128),
    correction_type varchar(32) not null default 'MANUAL_EDIT',
    changed_fields text not null,
    before_snapshot_json jsonb not null default '{}'::jsonb,
    after_snapshot_json jsonb not null default '{}'::jsonb,
    curation_status varchar(32) not null default 'READY_FOR_CORPUS',
    created_by varchar(128),
    created_at timestamptz not null default now(),
    updated_by uuid,
    updated_at timestamptz not null default now(),
    deleted_by uuid,
    deleted_at timestamptz,
    version bigint not null default 0,
    constraint ck_document_input_parse_feedback_source_type check (source_type in (
        'TEXT','MARKDOWN','WORD','PDF','OCR','CONFLUENCE','FEISHU','DINGTALK','YUQUE','CUSTOM_API'
    )),
    constraint ck_document_input_parse_feedback_parse_source check (parse_source in (
        'MODEL'
    )),
    constraint ck_document_input_parse_feedback_correction_type check (correction_type in (
        'MANUAL_EDIT'
    )),
    constraint ck_document_input_parse_feedback_curation_status check (curation_status in (
        'READY_FOR_CORPUS','CURATED','REJECTED'
    )),
    constraint ck_document_input_parse_feedback_changed_fields check (length(trim(changed_fields)) > 0)
);

create index if not exists idx_document_input_parse_feedback_project_status
    on document_input_parse_feedback_sample (project_id, curation_status, created_at desc)
    where deleted_at is null;
create index if not exists idx_document_input_parse_feedback_candidate
    on document_input_parse_feedback_sample (candidate_id, created_at desc)
    where deleted_at is null;
create index if not exists idx_document_input_parse_feedback_import
    on document_input_parse_feedback_sample (import_id, created_at desc)
    where deleted_at is null;
create index if not exists idx_document_input_parse_feedback_invocation
    on document_input_parse_feedback_sample (model_invocation_id)
    where deleted_at is null and model_invocation_id is not null;
