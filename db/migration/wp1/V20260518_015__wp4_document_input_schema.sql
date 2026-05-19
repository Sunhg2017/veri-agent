-- WP4 document input schema for PostgreSQL 15+.
-- Stores document source metadata, default field mapping, and import records.

create table if not exists document_input_field_mapping (
    id uuid primary key default gen_random_uuid(),
    mapping_code varchar(64) not null,
    name varchar(128) not null,
    item_path varchar(256) not null default 'requirements',
    title_path varchar(256) not null default 'title',
    description_path varchar(256),
    priority_path varchar(256),
    acceptance_criteria_path varchar(256),
    tags_path varchar(256),
    created_by uuid,
    created_at timestamptz not null default now(),
    updated_by uuid,
    updated_at timestamptz not null default now(),
    deleted_by uuid,
    deleted_at timestamptz,
    version bigint not null default 0
);

create unique index if not exists uk_document_input_field_mapping_code
    on document_input_field_mapping (mapping_code)
    where deleted_at is null;

insert into document_input_field_mapping (
    id, mapping_code, name, item_path, title_path, description_path,
    priority_path, acceptance_criteria_path, tags_path
) values (
    '00000000-0000-0000-0000-000000000401',
    'default',
    'Default requirement mapping',
    'requirements',
    'title',
    'description',
    'priority',
    'acceptanceCriteria',
    'tags'
)
on conflict (id) do nothing;

create table if not exists document_input_source (
    id uuid primary key default gen_random_uuid(),
    source_code varchar(64) not null,
    name varchar(128) not null,
    source_type varchar(32) not null,
    status varchar(32) not null default 'DISABLED',
    endpoint_url text,
    default_project_id varchar(64),
    mapping_id uuid references document_input_field_mapping(id) on delete restrict,
    secret_ref varchar(128),
    event_version varchar(32) not null default '1.0',
    mapping_version varchar(64) not null default 'default',
    description text,
    created_by uuid,
    created_at timestamptz not null default now(),
    updated_by uuid,
    updated_at timestamptz not null default now(),
    deleted_by uuid,
    deleted_at timestamptz,
    version bigint not null default 0,
    constraint ck_document_input_source_type check (source_type in (
        'TEXT','MARKDOWN','WORD','PDF','OCR','CONFLUENCE','FEISHU','DINGTALK','YUQUE','CUSTOM_API'
    )),
    constraint ck_document_input_source_status check (status in ('ENABLED','DISABLED','PLANNED')),
    constraint ck_document_input_source_event_version check (event_version in ('1.0'))
);

create unique index if not exists uk_document_input_source_code
    on document_input_source (lower(source_code))
    where deleted_at is null;
create index if not exists idx_document_input_source_type
    on document_input_source (source_type)
    where deleted_at is null;
create index if not exists idx_document_input_source_status
    on document_input_source (status)
    where deleted_at is null;
create index if not exists idx_document_input_source_project
    on document_input_source (default_project_id)
    where deleted_at is null and default_project_id is not null;
create index if not exists idx_document_input_source_secret_ref
    on document_input_source (secret_ref)
    where deleted_at is null and secret_ref is not null;

create table if not exists document_input_import (
    id uuid primary key default gen_random_uuid(),
    project_id varchar(64) not null,
    source_id uuid references document_input_source(id) on delete set null,
    source_code varchar(64),
    source_type varchar(32) not null,
    source_ref varchar(256),
    source_url text,
    title varchar(256),
    status varchar(32) not null,
    total_parsed int not null default 0,
    total_created int not null default 0,
    created_requirement_ids jsonb not null default '[]'::jsonb,
    error_message text,
    raw_digest varchar(128),
    created_by uuid,
    created_at timestamptz not null default now(),
    updated_by uuid,
    updated_at timestamptz not null default now(),
    deleted_by uuid,
    deleted_at timestamptz,
    version bigint not null default 0,
    constraint ck_document_input_import_type check (source_type in (
        'TEXT','MARKDOWN','WORD','PDF','OCR','CONFLUENCE','FEISHU','DINGTALK','YUQUE','CUSTOM_API'
    )),
    constraint ck_document_input_import_status check (status in ('SUCCEEDED','FAILED')),
    constraint ck_document_input_import_total check (total_parsed >= 0 and total_created >= 0)
);

create index if not exists idx_document_input_import_project_created
    on document_input_import (project_id, created_at desc)
    where deleted_at is null;
create index if not exists idx_document_input_import_source
    on document_input_import (source_id, created_at desc)
    where deleted_at is null and source_id is not null;
create index if not exists idx_document_input_import_status
    on document_input_import (status, created_at desc)
    where deleted_at is null;
create index if not exists idx_document_input_import_source_type
    on document_input_import (source_type, created_at desc)
    where deleted_at is null;

create table if not exists document_input_candidate (
    id uuid primary key default gen_random_uuid(),
    import_id uuid not null references document_input_import(id) on delete cascade,
    project_id varchar(64) not null,
    title varchar(256) not null,
    description text,
    priority varchar(32),
    acceptance_criteria text,
    tags text,
    status varchar(32) not null,
    source_ref varchar(256),
    source_fragment text,
    external_requirement_id varchar(256),
    confidence numeric(5, 4) not null default 0,
    parse_source varchar(32) not null default 'RULE',
    model_invocation_id uuid,
    model_provider_name varchar(128),
    model_name varchar(128),
    asset_requirement_id uuid,
    error_message text,
    ignored_reason text,
    confirmed_by varchar(128),
    confirmed_at timestamptz,
    created_by uuid,
    created_at timestamptz not null default now(),
    updated_by uuid,
    updated_at timestamptz not null default now(),
    deleted_by uuid,
    deleted_at timestamptz,
    version bigint not null default 0,
    constraint ck_document_input_candidate_status check (status in (
        'PENDING','CONFIRMED','IGNORED','PUBLISHED','PUBLISH_FAILED'
    )),
    constraint ck_document_input_candidate_parse_source check (parse_source in (
        'RULE','MODEL','WEBHOOK_MAPPING'
    ))
);

create index if not exists idx_document_input_candidate_import
    on document_input_candidate (import_id, created_at)
    where deleted_at is null;
create index if not exists idx_document_input_candidate_project_status
    on document_input_candidate (project_id, status, created_at desc)
    where deleted_at is null;
create index if not exists idx_document_input_candidate_external
    on document_input_candidate (project_id, external_requirement_id)
    where deleted_at is null and external_requirement_id is not null;
create index if not exists idx_document_input_candidate_model_invocation
    on document_input_candidate (model_invocation_id)
    where deleted_at is null and model_invocation_id is not null;

create table if not exists document_input_webhook_event (
    id uuid primary key default gen_random_uuid(),
    source_id uuid references document_input_source(id) on delete set null,
    import_id uuid references document_input_import(id) on delete set null,
    source_code varchar(64) not null,
    event_id varchar(128),
    idempotency_key varchar(128),
    event_type varchar(64),
    event_version varchar(32),
    signature_status varchar(32) not null,
    status varchar(32) not null,
    payload_digest varchar(128) not null,
    raw_payload text,
    error_message text,
    retry_count int not null default 0,
    replay_by varchar(128),
    replay_at timestamptz,
    replay_trace_id varchar(64),
    received_at timestamptz not null default now(),
    processed_at timestamptz,
    created_by uuid,
    created_at timestamptz not null default now(),
    updated_by uuid,
    updated_at timestamptz not null default now(),
    deleted_by uuid,
    deleted_at timestamptz,
    version bigint not null default 0,
    constraint ck_document_input_webhook_signature check (signature_status in (
        'VALID','MISSING','INVALID','EXPIRED'
    )),
    constraint ck_document_input_webhook_status check (status in (
        'ACCEPTED','REJECTED','PROCESSED','FAILED','DEAD_LETTER','REPLAYED'
    )),
    constraint ck_document_input_webhook_retry check (retry_count >= 0)
);

create index if not exists idx_document_input_webhook_source_received
    on document_input_webhook_event (source_code, received_at desc)
    where deleted_at is null;
create index if not exists idx_document_input_webhook_status_received
    on document_input_webhook_event (status, received_at desc)
    where deleted_at is null;
create unique index if not exists uk_document_input_webhook_event_id
    on document_input_webhook_event (source_code, event_id)
    where deleted_at is null and event_id is not null;
create unique index if not exists uk_document_input_webhook_idempotency
    on document_input_webhook_event (source_code, idempotency_key)
    where deleted_at is null and idempotency_key is not null;
