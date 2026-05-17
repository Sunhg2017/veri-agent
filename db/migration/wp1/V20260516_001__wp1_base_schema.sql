-- WP1 base schema for PostgreSQL 15+.
-- Department tree cycle checks and scope_id type validation remain service-layer responsibilities in MVP P0.

create extension if not exists pgcrypto;


create table if not exists base_department (
    id uuid primary key default gen_random_uuid(),
    parent_id uuid references base_department(id) on delete restrict,
    code varchar(32) not null,
    name varchar(64) not null,
    path varchar(1024) not null,
    level int not null default 1,
    sort_order int not null default 0,
    status varchar(32) not null default 'ENABLED',
    created_by uuid,
    created_at timestamptz not null default now(),
    updated_by uuid,
    updated_at timestamptz not null default now(),
    deleted_by uuid,
    deleted_at timestamptz,
    version bigint not null default 0,
    constraint ck_base_department_status check (status in ('ENABLED','DISABLED')),
    constraint ck_base_department_level check (level >= 1),
    constraint ck_base_department_parent_self check (parent_id is null or parent_id <> id)
);

create unique index if not exists uk_base_department_code on base_department (code) where deleted_at is null;
create unique index if not exists uk_base_department_parent_name on base_department (coalesce(parent_id, '00000000-0000-0000-0000-000000000000'::uuid), name) where deleted_at is null;
create index if not exists idx_base_department_parent on base_department (parent_id, sort_order) where deleted_at is null;
create index if not exists idx_base_department_path on base_department (path) where deleted_at is null;
create index if not exists idx_base_department_status on base_department (status) where deleted_at is null;

create table if not exists iam_user (
    id uuid primary key default gen_random_uuid(),
    username varchar(64) not null,
    password_hash varchar(255),
    display_name varchar(64) not null,
    email varchar(128),
    mobile varchar(32),
    status varchar(32) not null default 'PENDING_ACTIVATION',
    external_id varchar(128),
    auth_version bigint not null default 1,
    must_change_password boolean not null default false,
    last_login_at timestamptz,
    created_by uuid,
    created_at timestamptz not null default now(),
    updated_by uuid,
    updated_at timestamptz not null default now(),
    deleted_by uuid,
    deleted_at timestamptz,
    version bigint not null default 0,
    constraint ck_iam_user_status check (status in ('PENDING_ACTIVATION','ENABLED','DISABLED','LOCKED')),
    constraint ck_iam_user_auth_version check (auth_version >= 1)
);

create unique index if not exists uk_iam_user_username on iam_user (username) where deleted_at is null;
create unique index if not exists uk_iam_user_email on iam_user (email) where email is not null and deleted_at is null;
create unique index if not exists uk_iam_user_external on iam_user (external_id) where external_id is not null and deleted_at is null;
create index if not exists idx_iam_user_status on iam_user (status) where deleted_at is null;
create index if not exists idx_iam_user_display_name on iam_user (display_name) where deleted_at is null;
create index if not exists idx_iam_user_last_login_at on iam_user (last_login_at desc) where deleted_at is null;

create table if not exists base_department_manager (
    id uuid primary key default gen_random_uuid(),
    dept_id uuid not null references base_department(id) on delete restrict,
    user_id uuid not null references iam_user(id) on delete restrict,
    status varchar(32) not null default 'ENABLED',
    created_by uuid,
    created_at timestamptz not null default now(),
    updated_by uuid,
    updated_at timestamptz not null default now(),
    deleted_by uuid,
    deleted_at timestamptz,
    version bigint not null default 0,
    constraint ck_base_department_manager_status check (status in ('ENABLED','DISABLED'))
);

create unique index if not exists uk_base_department_manager_dept_user on base_department_manager (dept_id, user_id) where deleted_at is null;
create index if not exists idx_base_department_manager_dept on base_department_manager (dept_id) where deleted_at is null;
create index if not exists idx_base_department_manager_user on base_department_manager (user_id) where deleted_at is null;

create table if not exists base_department_member (
    id uuid primary key default gen_random_uuid(),
    dept_id uuid not null references base_department(id) on delete restrict,
    user_id uuid not null references iam_user(id) on delete restrict,
    is_primary boolean not null default false,
    position varchar(64),
    status varchar(32) not null default 'ENABLED',
    created_by uuid,
    created_at timestamptz not null default now(),
    updated_by uuid,
    updated_at timestamptz not null default now(),
    deleted_by uuid,
    deleted_at timestamptz,
    version bigint not null default 0,
    constraint ck_base_department_member_status check (status in ('ENABLED','DISABLED'))
);

create unique index if not exists uk_base_department_member_dept_user on base_department_member (dept_id, user_id) where deleted_at is null;
create unique index if not exists uk_base_department_member_primary_user on base_department_member (user_id) where is_primary = true and status = 'ENABLED' and deleted_at is null;
create index if not exists idx_base_department_member_user on base_department_member (user_id) where deleted_at is null;
create index if not exists idx_base_department_member_dept_status on base_department_member (dept_id, status) where deleted_at is null;

create table if not exists iam_session (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references iam_user(id) on delete restrict,
    session_token_hash varchar(128) not null,
    refresh_token_hash varchar(128) not null,
    auth_version bigint not null,
    client_ip inet,
    user_agent text,
    expires_at timestamptz not null,
    refreshed_at timestamptz,
    revoked_at timestamptz,
    revoked_by uuid,
    revoke_reason text,
    created_at timestamptz not null default now(),
    constraint ck_iam_session_auth_version check (auth_version >= 1)
);

create unique index if not exists uk_iam_session_token on iam_session (session_token_hash);
create unique index if not exists uk_iam_session_refresh on iam_session (refresh_token_hash);
create index if not exists idx_iam_session_user_active on iam_session (user_id, revoked_at, expires_at);
create index if not exists idx_iam_session_expires_at on iam_session (expires_at);
create index if not exists idx_iam_session_created_at on iam_session (created_at desc);

create table if not exists base_project (
    id uuid primary key default gen_random_uuid(),
    code varchar(32) not null,
    name varchar(64) not null,
    status varchar(32) not null default 'PREPARING',
    sensitivity_level varchar(32) not null default 'INTERNAL',
    allow_public_model boolean not null default false,
    default_resource_pool varchar(64),
    description text,
    created_by uuid,
    created_at timestamptz not null default now(),
    updated_by uuid,
    updated_at timestamptz not null default now(),
    deleted_by uuid,
    deleted_at timestamptz,
    version bigint not null default 0,
    constraint ck_base_project_status check (status in ('PREPARING','ACTIVE','ARCHIVED','DISABLED')),
    constraint ck_base_project_sensitivity check (sensitivity_level in ('PUBLIC','INTERNAL','CONFIDENTIAL','STRICT'))
);

create unique index if not exists uk_base_project_code on base_project (code) where deleted_at is null;
create index if not exists idx_base_project_status on base_project (status) where deleted_at is null;
create index if not exists idx_base_project_name on base_project (name) where deleted_at is null;
create index if not exists idx_base_project_sensitivity on base_project (sensitivity_level) where deleted_at is null;

create table if not exists base_project_department (
    id uuid primary key default gen_random_uuid(),
    project_id uuid not null references base_project(id) on delete restrict,
    dept_id uuid not null references base_department(id) on delete restrict,
    relation_type varchar(32) not null default 'PARTICIPANT',
    is_primary boolean not null default false,
    status varchar(32) not null default 'ENABLED',
    created_by uuid,
    created_at timestamptz not null default now(),
    updated_by uuid,
    updated_at timestamptz not null default now(),
    deleted_by uuid,
    deleted_at timestamptz,
    version bigint not null default 0,
    constraint ck_base_project_department_relation_type check (relation_type in ('OWNER','PARTICIPANT','OBSERVER')),
    constraint ck_base_project_department_status check (status in ('ENABLED','DISABLED'))
);

create unique index if not exists uk_base_project_department_project_dept on base_project_department (project_id, dept_id) where deleted_at is null;
create unique index if not exists uk_base_project_department_primary on base_project_department (project_id) where is_primary = true and status = 'ENABLED' and deleted_at is null;
create index if not exists idx_base_project_department_project on base_project_department (project_id) where deleted_at is null;
create index if not exists idx_base_project_department_dept on base_project_department (dept_id) where deleted_at is null;

create table if not exists base_project_member (
    id uuid primary key default gen_random_uuid(),
    project_id uuid not null references base_project(id) on delete restrict,
    user_id uuid not null references iam_user(id) on delete restrict,
    member_type varchar(32) not null default 'MEMBER',
    status varchar(32) not null default 'ENABLED',
    joined_at timestamptz not null default now(),
    created_by uuid,
    created_at timestamptz not null default now(),
    updated_by uuid,
    updated_at timestamptz not null default now(),
    deleted_by uuid,
    deleted_at timestamptz,
    version bigint not null default 0,
    constraint ck_base_project_member_type check (member_type in ('OWNER','MEMBER','OBSERVER')),
    constraint ck_base_project_member_status check (status in ('ENABLED','DISABLED'))
);

create unique index if not exists uk_base_project_member_project_user on base_project_member (project_id, user_id) where deleted_at is null;
create index if not exists idx_base_project_member_project_status on base_project_member (project_id, status) where deleted_at is null;
create index if not exists idx_base_project_member_user_status on base_project_member (user_id, status) where deleted_at is null;
create index if not exists idx_base_project_member_type on base_project_member (member_type) where deleted_at is null;

create table if not exists base_application (
    id uuid primary key default gen_random_uuid(),
    project_id uuid not null references base_project(id) on delete restrict,
    code varchar(32) not null,
    name varchar(64) not null,
    app_type varchar(32) not null,
    default_web_url text,
    default_api_base_url text,
    repo_url text,
    service_identifier varchar(128),
    sensitivity_level varchar(32) not null default 'INTERNAL',
    allow_public_model boolean not null default false,
    status varchar(32) not null default 'ENABLED',
    description text,
    created_by uuid,
    created_at timestamptz not null default now(),
    updated_by uuid,
    updated_at timestamptz not null default now(),
    deleted_by uuid,
    deleted_at timestamptz,
    version bigint not null default 0,
    constraint ck_base_application_status check (status in ('ENABLED','DISABLED')),
    constraint ck_base_application_sensitivity check (sensitivity_level in ('PUBLIC','INTERNAL','CONFIDENTIAL','STRICT'))
);

create unique index if not exists uk_base_application_project_code on base_application (project_id, code) where deleted_at is null;
create index if not exists idx_base_application_project_status on base_application (project_id, status) where deleted_at is null;
create index if not exists idx_base_application_type on base_application (app_type) where deleted_at is null;

create table if not exists base_environment (
    id uuid primary key default gen_random_uuid(),
    project_id uuid not null references base_project(id) on delete restrict,
    app_id uuid references base_application(id) on delete restrict,
    scope_type varchar(32) not null,
    code varchar(32) not null,
    name varchar(64) not null,
    env_type varchar(32) not null,
    web_url text,
    api_base_url text,
    execution_policy_json jsonb not null default '{}'::jsonb,
    health_check_json jsonb not null default '{}'::jsonb,
    auth_secret_ref text,
    status varchar(32) not null default 'ENABLED',
    created_by uuid,
    created_at timestamptz not null default now(),
    updated_by uuid,
    updated_at timestamptz not null default now(),
    deleted_by uuid,
    deleted_at timestamptz,
    version bigint not null default 0,
    constraint ck_base_environment_scope check (
        (scope_type = 'PROJECT' and app_id is null) or
        (scope_type = 'APPLICATION' and app_id is not null)
    ),
    constraint ck_base_environment_env_type check (env_type in ('DEV','TEST','STAGING','PREPROD','PROD')),
    constraint ck_base_environment_status check (status in ('ENABLED','DISABLED'))
);

create unique index if not exists uk_base_environment_project_code on base_environment (project_id, code) where scope_type = 'PROJECT' and deleted_at is null;
create unique index if not exists uk_base_environment_app_code on base_environment (app_id, code) where scope_type = 'APPLICATION' and deleted_at is null;
create index if not exists idx_base_environment_project_status on base_environment (project_id, scope_type, status) where deleted_at is null;
create index if not exists idx_base_environment_app_status on base_environment (app_id, status) where deleted_at is null;
create index if not exists idx_base_environment_env_type on base_environment (env_type) where deleted_at is null;

create table if not exists base_environment_variable (
    id uuid primary key default gen_random_uuid(),
    env_id uuid not null references base_environment(id) on delete restrict,
    key varchar(128) not null,
    value_kind varchar(32) not null,
    plain_value text,
    secret_ref text,
    secret_provider varchar(64),
    secret_version varchar(64),
    masked_value varchar(255),
    description text,
    status varchar(32) not null default 'ENABLED',
    created_by uuid,
    created_at timestamptz not null default now(),
    updated_by uuid,
    updated_at timestamptz not null default now(),
    deleted_by uuid,
    deleted_at timestamptz,
    version bigint not null default 0,
    constraint ck_base_environment_variable_kind check (value_kind in ('PLAIN','SECRET','SECRET_REF')),
    constraint ck_base_environment_variable_status check (status in ('ENABLED','DISABLED')),
    constraint ck_base_environment_variable_secret check (
        (value_kind = 'PLAIN' and plain_value is not null and secret_ref is null and secret_provider is null) or
        (value_kind in ('SECRET','SECRET_REF') and plain_value is null and secret_ref is not null and secret_provider is not null)
    )
);

create unique index if not exists uk_base_environment_variable_key on base_environment_variable (env_id, key) where deleted_at is null;
create index if not exists idx_base_environment_variable_env on base_environment_variable (env_id) where deleted_at is null;
create index if not exists idx_base_environment_variable_secret_provider on base_environment_variable (secret_provider) where deleted_at is null;
create index if not exists idx_base_environment_variable_key on base_environment_variable (key) where deleted_at is null;

create table if not exists base_config (
    id uuid primary key default gen_random_uuid(),
    scope_type varchar(32) not null,
    scope_id uuid,
    config_key varchar(128) not null,
    value_kind varchar(32) not null default 'PLAIN',
    value_json jsonb,
    secret_ref text,
    masked_value varchar(255),
    status varchar(32) not null default 'ENABLED',
    created_by uuid,
    created_at timestamptz not null default now(),
    updated_by uuid,
    updated_at timestamptz not null default now(),
    deleted_by uuid,
    deleted_at timestamptz,
    version bigint not null default 0,
    constraint ck_base_config_scope check (
        (scope_type = 'SYSTEM' and scope_id is null) or
        (scope_type in ('PROJECT','APPLICATION','ENVIRONMENT') and scope_id is not null)
    ),
    constraint ck_base_config_kind check (value_kind in ('PLAIN','SECRET_REF')),
    constraint ck_base_config_secret check (
        (value_kind = 'PLAIN' and value_json is not null and secret_ref is null) or
        (value_kind = 'SECRET_REF' and value_json is null and secret_ref is not null)
    ),
    constraint ck_base_config_status check (status in ('ENABLED','DISABLED'))
);

create unique index if not exists uk_base_config_scope_key on base_config (scope_type, coalesce(scope_id, '00000000-0000-0000-0000-000000000000'::uuid), config_key) where deleted_at is null;
create index if not exists idx_base_config_scope on base_config (scope_type, scope_id) where deleted_at is null;
create index if not exists idx_base_config_key on base_config (config_key) where deleted_at is null;

create table if not exists rbac_permission (
    id uuid primary key default gen_random_uuid(),
    code varchar(128) not null,
    resource_type varchar(64) not null,
    action varchar(64) not null,
    scope_mask varchar(255) not null default '',
    description text,
    status varchar(32) not null default 'ENABLED',
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint ck_rbac_permission_status check (status in ('ENABLED','DISABLED'))
);

create unique index if not exists uk_rbac_permission_code on rbac_permission (code);
create index if not exists idx_rbac_permission_resource_action on rbac_permission (resource_type, action);
create index if not exists idx_rbac_permission_status on rbac_permission (status);

create table if not exists rbac_role (
    id uuid primary key default gen_random_uuid(),
    code varchar(64) not null,
    name varchar(64) not null,
    scope_type varchar(32) not null,
    is_system boolean not null default false,
    is_builtin boolean not null default true,
    status varchar(32) not null default 'ENABLED',
    description text,
    created_by uuid,
    created_at timestamptz not null default now(),
    updated_by uuid,
    updated_at timestamptz not null default now(),
    deleted_by uuid,
    deleted_at timestamptz,
    version bigint not null default 0,
    constraint ck_rbac_role_scope check (scope_type in ('PLATFORM','DEPARTMENT','PROJECT','APPLICATION','ENVIRONMENT')),
    constraint ck_rbac_role_status check (status in ('ENABLED','DISABLED'))
);

create unique index if not exists uk_rbac_role_code on rbac_role (code) where deleted_at is null;
create index if not exists idx_rbac_role_scope on rbac_role (scope_type, status) where deleted_at is null;
create index if not exists idx_rbac_role_builtin on rbac_role (is_builtin) where deleted_at is null;

create table if not exists rbac_role_permission (
    id uuid primary key default gen_random_uuid(),
    role_id uuid not null references rbac_role(id) on delete restrict,
    permission_id uuid not null references rbac_permission(id) on delete restrict,
    effect varchar(16) not null default 'ALLOW',
    created_by uuid,
    created_at timestamptz not null default now(),
    deleted_by uuid,
    deleted_at timestamptz,
    version bigint not null default 0,
    constraint ck_rbac_role_permission_effect check (effect in ('ALLOW','DENY'))
);

create unique index if not exists uk_rbac_role_permission on rbac_role_permission (role_id, permission_id) where deleted_at is null;
create index if not exists idx_rbac_role_permission_role on rbac_role_permission (role_id) where deleted_at is null;
create index if not exists idx_rbac_role_permission_permission on rbac_role_permission (permission_id) where deleted_at is null;

create table if not exists rbac_role_binding (
    id uuid primary key default gen_random_uuid(),
    subject_type varchar(32) not null,
    subject_id uuid not null,
    role_id uuid not null references rbac_role(id) on delete restrict,
    role_code varchar(64) not null,
    scope_type varchar(32) not null,
    scope_id uuid,
    condition_json jsonb not null default '{}'::jsonb,
    expires_at timestamptz,
    status varchar(32) not null default 'ENABLED',
    created_by uuid,
    created_at timestamptz not null default now(),
    updated_by uuid,
    updated_at timestamptz not null default now(),
    deleted_by uuid,
    deleted_at timestamptz,
    version bigint not null default 0,
    constraint ck_rbac_role_binding_subject check (subject_type in ('USER','DEPARTMENT')),
    constraint ck_rbac_role_binding_scope check (
        (scope_type = 'PLATFORM' and scope_id is null) or
        (scope_type in ('DEPARTMENT','PROJECT','APPLICATION','ENVIRONMENT') and scope_id is not null)
    ),
    constraint ck_rbac_role_binding_status check (status in ('ENABLED','DISABLED'))
);

create unique index if not exists uk_rbac_role_binding_unique on rbac_role_binding (subject_type, subject_id, role_id, scope_type, coalesce(scope_id, '00000000-0000-0000-0000-000000000000'::uuid)) where deleted_at is null;
create index if not exists idx_rbac_role_binding_subject on rbac_role_binding (subject_type, subject_id, status) where deleted_at is null;
create index if not exists idx_rbac_role_binding_scope on rbac_role_binding (scope_type, scope_id, status) where deleted_at is null;
create index if not exists idx_rbac_role_binding_role_code on rbac_role_binding (role_code) where deleted_at is null;

create table if not exists secret_provider (
    id uuid primary key default gen_random_uuid(),
    provider_code varchar(64) not null,
    provider_type varchar(32) not null,
    config_json jsonb not null default '{}'::jsonb,
    is_default boolean not null default false,
    status varchar(32) not null default 'ENABLED',
    created_by uuid,
    created_at timestamptz not null default now(),
    updated_by uuid,
    updated_at timestamptz not null default now(),
    deleted_by uuid,
    deleted_at timestamptz,
    version bigint not null default 0,
    constraint ck_secret_provider_type check (provider_type in ('LOCAL_ENCRYPTED','VAULT','KMS')),
    constraint ck_secret_provider_status check (status in ('ENABLED','DISABLED'))
);

create unique index if not exists uk_secret_provider_code on secret_provider (provider_code) where deleted_at is null;
create unique index if not exists uk_secret_provider_default on secret_provider (is_default) where is_default = true and status = 'ENABLED' and deleted_at is null;
create index if not exists idx_secret_provider_type_status on secret_provider (provider_type, status) where deleted_at is null;

create table if not exists secret_reference (
    id uuid primary key default gen_random_uuid(),
    provider_id uuid not null references secret_provider(id) on delete restrict,
    secret_ref text not null,
    scope_type varchar(32) not null,
    scope_id uuid not null,
    purpose varchar(64) not null,
    masked_value varchar(255),
    secret_version varchar(64),
    status varchar(32) not null default 'ACTIVE',
    rotated_at timestamptz,
    expires_at timestamptz,
    created_by uuid,
    created_at timestamptz not null default now(),
    updated_by uuid,
    updated_at timestamptz not null default now(),
    deleted_by uuid,
    deleted_at timestamptz,
    version bigint not null default 0,
    constraint ck_secret_reference_scope check (scope_type in ('PROJECT','APPLICATION','ENVIRONMENT','CONFIG')),
    constraint ck_secret_reference_status check (status in ('ACTIVE','DEPRECATED','REVOKED'))
);

create unique index if not exists uk_secret_reference_ref on secret_reference (secret_ref);
create index if not exists idx_secret_reference_scope on secret_reference (scope_type, scope_id);
create index if not exists idx_secret_reference_provider_status on secret_reference (provider_id, status);
create index if not exists idx_secret_reference_purpose on secret_reference (purpose);

create table if not exists secret_local_store (
    id uuid primary key default gen_random_uuid(),
    secret_ref_id uuid not null references secret_reference(id) on delete restrict,
    cipher_text text not null,
    iv varchar(128) not null,
    auth_tag varchar(128) not null,
    algorithm varchar(64) not null default 'AES-256-GCM',
    master_key_version varchar(64) not null,
    status varchar(32) not null default 'ACTIVE',
    created_by uuid,
    created_at timestamptz not null default now(),
    updated_by uuid,
    updated_at timestamptz not null default now(),
    deleted_by uuid,
    deleted_at timestamptz,
    version bigint not null default 0,
    constraint ck_secret_local_store_status check (status in ('ACTIVE','ROTATED','REVOKED')),
    constraint uk_secret_local_store_ref unique (secret_ref_id)
);

create index if not exists idx_secret_local_store_ref on secret_local_store (secret_ref_id);
create index if not exists idx_secret_local_store_status on secret_local_store (status);

create table if not exists audit_log (
    id uuid primary key default gen_random_uuid(),
    trace_id varchar(128),
    idempotency_key varchar(128),
    actor_type varchar(32) not null,
    actor_user_id uuid,
    actor_service varchar(128),
    actor_ip inet,
    user_agent text,
    action varchar(128) not null,
    resource_type varchar(64) not null,
    resource_id varchar(128),
    scope_type varchar(32) not null,
    scope_id uuid,
    result varchar(32) not null,
    before_json jsonb,
    after_json jsonb,
    diff_json jsonb,
    reason text,
    created_at timestamptz not null default now(),
    constraint ck_audit_log_actor_type check (actor_type in ('USER','SERVICE','SYSTEM')),
    constraint ck_audit_log_result check (result in ('SUCCESS','FAILED','DENIED'))
);

create unique index if not exists uk_audit_log_idempotency on audit_log (idempotency_key) where idempotency_key is not null;
create index if not exists idx_audit_log_time on audit_log (created_at desc);
create index if not exists idx_audit_log_action_time on audit_log (action, created_at desc);
create index if not exists idx_audit_log_resource on audit_log (resource_type, resource_id);
create index if not exists idx_audit_log_actor_time on audit_log (actor_user_id, created_at desc);
create index if not exists idx_audit_log_trace on audit_log (trace_id);

create table if not exists audit_outbox (
    id uuid primary key default gen_random_uuid(),
    idempotency_key varchar(128),
    event_payload_json jsonb not null,
    status varchar(32) not null default 'PENDING',
    retry_count int not null default 0,
    next_retry_at timestamptz not null default now(),
    last_error text,
    locked_at timestamptz,
    locked_by varchar(128),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint ck_audit_outbox_status check (status in ('PENDING','PROCESSING','DONE','FAILED','DEAD')),
    constraint ck_audit_outbox_retry_count check (retry_count >= 0)
);

create unique index if not exists uk_audit_outbox_idempotency on audit_outbox (idempotency_key) where idempotency_key is not null;
create index if not exists idx_audit_outbox_pending on audit_outbox (status, next_retry_at);
create index if not exists idx_audit_outbox_created_at on audit_outbox (created_at);
