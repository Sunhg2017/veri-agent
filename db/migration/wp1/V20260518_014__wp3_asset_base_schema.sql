-- WP3 test asset model schema for PostgreSQL 15+.
-- Stores requirements, APIs, pages, business flows, test cases, test steps, and traceability links.

create table if not exists asset_requirement (
    id uuid primary key default gen_random_uuid(),
    project_id varchar(64) not null,
    code varchar(32) not null,
    title varchar(256) not null,
    description text,
    source varchar(32) not null,
    source_ref varchar(256),
    source_url text,
    priority varchar(16) not null default 'MEDIUM',
    status varchar(32) not null default 'DRAFT',
    version int not null default 1,
    acceptance_criteria text,
    business_rules text,
    tags text,
    created_by uuid,
    created_at timestamptz not null default now(),
    updated_by uuid,
    updated_at timestamptz not null default now(),
    deleted_by uuid,
    deleted_at timestamptz,
    constraint ck_asset_requirement_source check (source in ('IMPORT','MANUAL')),
    constraint ck_asset_requirement_priority check (priority in ('CRITICAL','HIGH','MEDIUM','LOW')),
    constraint ck_asset_requirement_status check (status in ('DRAFT','REVIEWING','APPROVED','DEPRECATED'))
);

create unique index if not exists uk_asset_requirement_project_code on asset_requirement (project_id, code) where deleted_at is null;
create unique index if not exists uk_asset_requirement_project_import_source_ref
    on asset_requirement (project_id, source, source_ref)
    where deleted_at is null and source = 'IMPORT' and source_ref is not null;
create index if not exists idx_asset_requirement_project_status on asset_requirement (project_id, status) where deleted_at is null;
create index if not exists idx_asset_requirement_project_priority on asset_requirement (project_id, priority) where deleted_at is null;
create index if not exists idx_asset_requirement_title on asset_requirement (title) where deleted_at is null;
create index if not exists idx_asset_requirement_source on asset_requirement (source) where deleted_at is null;
create index if not exists idx_asset_requirement_created_at on asset_requirement (created_at desc) where deleted_at is null;

create table if not exists asset_api (
    id uuid primary key default gen_random_uuid(),
    project_id varchar(64) not null,
    code varchar(32) not null,
    service_name varchar(128),
    path varchar(256) not null,
    http_method varchar(16) not null,
    summary varchar(256),
    description text,
    source varchar(32) not null default 'OPENAPI',
    source_ref varchar(256),
    version varchar(32),
    request_schema jsonb,
    response_schema jsonb,
    status varchar(32) not null default 'ACTIVE',
    tags text,
    created_by uuid,
    created_at timestamptz not null default now(),
    updated_by uuid,
    updated_at timestamptz not null default now(),
    deleted_by uuid,
    deleted_at timestamptz,
    constraint ck_asset_api_http_method check (http_method in ('GET','POST','PUT','PATCH','DELETE','HEAD','OPTIONS')),
    constraint ck_asset_api_status check (status in ('ACTIVE','DEPRECATED','REMOVED'))
);

create unique index if not exists uk_asset_api_project_service_path_method on asset_api (project_id, service_name, path, http_method) where deleted_at is null;
create index if not exists idx_asset_api_project_service on asset_api (project_id, service_name) where deleted_at is null;
create index if not exists idx_asset_api_project_status on asset_api (project_id, status) where deleted_at is null;
create index if not exists idx_asset_api_path on asset_api (path) where deleted_at is null;

create table if not exists asset_page (
    id uuid primary key default gen_random_uuid(),
    project_id varchar(64) not null,
    code varchar(32) not null,
    name varchar(128) not null,
    url_pattern varchar(512),
    source varchar(32) not null default 'MANUAL',
    source_ref varchar(256),
    component_tree jsonb,
    screenshot_url text,
    status varchar(32) not null default 'ACTIVE',
    created_by uuid,
    created_at timestamptz not null default now(),
    updated_by uuid,
    updated_at timestamptz not null default now(),
    deleted_by uuid,
    deleted_at timestamptz,
    version bigint not null default 0,
    constraint ck_asset_page_source check (source in ('FIGMA','LANHU','AXURE','MANUAL')),
    constraint ck_asset_page_status check (status in ('ACTIVE','DEPRECATED'))
);

create unique index if not exists uk_asset_page_project_code on asset_page (project_id, code) where deleted_at is null;
create index if not exists idx_asset_page_project_status on asset_page (project_id, status) where deleted_at is null;

create table if not exists asset_business_flow (
    id uuid primary key default gen_random_uuid(),
    project_id varchar(64) not null,
    code varchar(32) not null,
    name varchar(128) not null,
    description text,
    flow_json jsonb not null default '{}',
    priority varchar(16) not null default 'MEDIUM',
    status varchar(32) not null default 'DRAFT',
    created_by uuid,
    created_at timestamptz not null default now(),
    updated_by uuid,
    updated_at timestamptz not null default now(),
    deleted_by uuid,
    deleted_at timestamptz,
    version bigint not null default 0,
    constraint ck_asset_business_flow_status check (status in ('DRAFT','ACTIVE','ARCHIVED'))
);

create unique index if not exists uk_asset_business_flow_project_code on asset_business_flow (project_id, code) where deleted_at is null;
create index if not exists idx_asset_business_flow_project_status on asset_business_flow (project_id, status) where deleted_at is null;

create table if not exists asset_test_case (
    id uuid primary key default gen_random_uuid(),
    project_id varchar(64) not null,
    code varchar(32) not null,
    title varchar(256) not null,
    description text,
    case_type varchar(32) not null default 'FUNCTIONAL',
    priority varchar(16) not null default 'MEDIUM',
    status varchar(32) not null default 'DRAFT',
    preconditions text,
    test_data_ref varchar(256),
    automation_status varchar(32) not null default 'NONE',
    version int not null default 1,
    source varchar(32) not null default 'MANUAL',
    source_ref varchar(256),
    tags text,
    created_by uuid,
    created_at timestamptz not null default now(),
    updated_by uuid,
    updated_at timestamptz not null default now(),
    deleted_by uuid,
    deleted_at timestamptz,
    constraint ck_asset_test_case_type check (case_type in ('FUNCTIONAL','API','UI','E2E','PERMISSION','REGRESSION','BOUNDARY')),
    constraint ck_asset_test_case_priority check (priority in ('CRITICAL','HIGH','MEDIUM','LOW')),
    constraint ck_asset_test_case_status check (status in ('DRAFT','REVIEWING','APPROVED','DEPRECATED')),
    constraint ck_asset_test_case_automation_status check (automation_status in ('NONE','SCRIPTED','AUTOMATED','FLAKY')),
    constraint ck_asset_test_case_source check (source in ('AI_GENERATED','MANUAL','IMPORTED'))
);

create unique index if not exists uk_asset_test_case_project_code on asset_test_case (project_id, code) where deleted_at is null;
create index if not exists idx_asset_test_case_project_status on asset_test_case (project_id, status) where deleted_at is null;
create index if not exists idx_asset_test_case_project_type on asset_test_case (project_id, case_type) where deleted_at is null;
create index if not exists idx_asset_test_case_project_priority on asset_test_case (project_id, priority) where deleted_at is null;
create index if not exists idx_asset_test_case_project_automation on asset_test_case (project_id, automation_status) where deleted_at is null;

create table if not exists asset_test_step (
    id uuid primary key default gen_random_uuid(),
    case_id uuid not null references asset_test_case(id) on delete restrict,
    step_order int not null,
    action varchar(512) not null,
    target varchar(256),
    input_data text,
    expected_result varchar(512) not null,
    data_ref varchar(256),
    created_by uuid,
    created_at timestamptz not null default now(),
    updated_by uuid,
    updated_at timestamptz not null default now()
);

create unique index if not exists uk_asset_test_step_case_order on asset_test_step (case_id, step_order);
create index if not exists idx_asset_test_step_case on asset_test_step (case_id);

create table if not exists asset_link (
    id uuid primary key default gen_random_uuid(),
    source_type varchar(32) not null,
    source_id uuid not null,
    target_type varchar(32) not null,
    target_id uuid not null,
    link_type varchar(32) not null default 'COVERS',
    created_by uuid,
    created_at timestamptz not null default now(),
    deleted_at timestamptz,
    constraint ck_asset_link_source_type check (source_type in ('REQUIREMENT','API','PAGE','FLOW','CASE','DATA','SCRIPT')),
    constraint ck_asset_link_target_type check (target_type in ('REQUIREMENT','API','PAGE','FLOW','CASE','DATA','SCRIPT')),
    constraint ck_asset_link_link_type check (link_type in ('COVERS','VERIFIES','DEPENDS_ON','RELATED_TO','DUPLICATES'))
);

create unique index if not exists uk_asset_link_source_target_link on asset_link (source_type, source_id, target_type, target_id, link_type) where deleted_at is null;
create index if not exists idx_asset_link_source on asset_link (source_type, source_id) where deleted_at is null;
create index if not exists idx_asset_link_target on asset_link (target_type, target_id) where deleted_at is null;

create extension if not exists pgcrypto;
