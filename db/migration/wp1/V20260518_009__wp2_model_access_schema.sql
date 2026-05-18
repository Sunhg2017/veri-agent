-- WP2 model access schema for PostgreSQL 15+.
-- WP2 stores its own model providers, prompt versions, and invocation logs.
-- WP1 resource IDs are stored only as logical references; validation must go through WP1 APIs.

create table if not exists ma_model_provider (
    id uuid primary key,
    name varchar(128) not null,
    provider_type varchar(64) not null,
    base_url varchar(512),
    api_key_ref varchar(512),
    status varchar(32) not null default 'ENABLED',
    priority integer not null default 100,
    timeout_ms integer not null default 10000,
    input_cost_per_1k_tokens numeric(18, 8) not null default 0,
    output_cost_per_1k_tokens numeric(18, 8) not null default 0,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint ck_ma_model_provider_type check (provider_type in ('LOCAL_ECHO', 'OPENAI_COMPATIBLE', 'MOCK_FAILURE')),
    constraint ck_ma_model_provider_status check (status in ('ENABLED', 'DISABLED')),
    constraint ck_ma_model_provider_priority check (priority >= 0),
    constraint ck_ma_model_provider_timeout check (timeout_ms >= 100)
);

create unique index if not exists uk_ma_model_provider_name on ma_model_provider (lower(name));
create index if not exists idx_ma_model_provider_enabled_priority
    on ma_model_provider (status, priority, created_at desc);

create table if not exists ma_prompt_template (
    id uuid primary key,
    prompt_key varchar(128) not null,
    name varchar(128) not null,
    version integer not null,
    content text not null,
    status varchar(32) not null default 'DRAFT',
    change_note varchar(512),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint uk_ma_prompt_template_key_version unique (prompt_key, version),
    constraint ck_ma_prompt_template_status check (status in ('DRAFT', 'ACTIVE', 'ARCHIVED')),
    constraint ck_ma_prompt_template_version check (version > 0),
    constraint ck_ma_prompt_template_content check (length(content) between 1 and 12000)
);

create unique index if not exists uk_ma_prompt_template_one_active
    on ma_prompt_template (prompt_key)
    where status = 'ACTIVE';

create index if not exists idx_ma_prompt_template_key_created
    on ma_prompt_template (prompt_key, created_at desc);

create table if not exists ma_invocation_log (
    id uuid primary key,
    project_id varchar(64) not null,
    application_id varchar(64),
    environment_id varchar(64),
    prompt_key varchar(128),
    prompt_version integer,
    provider_id uuid references ma_model_provider(id),
    provider_name varchar(128),
    model_name varchar(128) not null,
    status varchar(32) not null,
    fallback_used boolean not null default false,
    prompt_digest varchar(64) not null,
    request_preview text,
    response_preview text,
    input_tokens integer not null default 0,
    output_tokens integer not null default 0,
    total_cost numeric(18, 8) not null default 0,
    error_code varchar(128),
    error_message varchar(512),
    latency_ms bigint not null default 0,
    actor_service varchar(128) not null,
    delegated_user_id varchar(64) not null,
    created_at timestamptz not null default now(),
    constraint ck_ma_invocation_status check (status in ('SUCCEEDED', 'FAILED', 'BLOCKED')),
    constraint ck_ma_invocation_tokens check (input_tokens >= 0 and output_tokens >= 0),
    constraint ck_ma_invocation_cost check (total_cost >= 0),
    constraint ck_ma_invocation_no_prompt_plaintext check (request_preview is null or length(request_preview) <= 700)
);

create index if not exists idx_ma_invocation_scope_time
    on ma_invocation_log (project_id, application_id, created_at desc);

create index if not exists idx_ma_invocation_provider_time
    on ma_invocation_log (provider_id, created_at desc);

create index if not exists idx_ma_invocation_status_time
    on ma_invocation_log (status, created_at desc);
