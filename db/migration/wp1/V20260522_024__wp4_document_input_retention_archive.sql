-- WP4 document input retention archive.
-- Keeps cleanup recoverable without granting runtime code direct hard-delete privileges.

create table if not exists document_input_retention_archive (
    id uuid primary key default gen_random_uuid(),
    record_type varchar(32) not null,
    record_id uuid not null,
    project_id varchar(64),
    source_code varchar(64),
    payload_digest varchar(128),
    original_created_at timestamptz,
    snapshot_json jsonb not null,
    archived_at timestamptz not null default now(),
    constraint ck_document_input_retention_archive_type check (record_type in (
        'IMPORT','CANDIDATE','WEBHOOK_EVENT'
    ))
);

create unique index if not exists uk_document_input_retention_archive_record
    on document_input_retention_archive (record_type, record_id);
create index if not exists idx_document_input_retention_archive_type_time
    on document_input_retention_archive (record_type, original_created_at desc);
create index if not exists idx_document_input_retention_archive_project
    on document_input_retention_archive (project_id, original_created_at desc)
    where project_id is not null;
create index if not exists idx_document_input_retention_archive_source
    on document_input_retention_archive (source_code, original_created_at desc)
    where source_code is not null;
