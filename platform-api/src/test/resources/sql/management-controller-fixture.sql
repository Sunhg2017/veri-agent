delete from secret_local_store;
delete from secret_reference;
delete from audit_outbox;
select set_config('veri_agent.audit_retention_maintenance', 'on', true);
delete from audit_log;
select set_config('veri_agent.audit_retention_maintenance', 'off', true);
delete from base_environment_variable;
delete from base_environment;
delete from base_application;
delete from base_project_member;
delete from base_project_department;
delete from base_department_manager;
delete from base_department_member;
delete from iam_session;
delete from rbac_role_binding;
delete from iam_user;
delete from rbac_role_permission rp
using rbac_role r
where rp.role_id = r.id
  and r.is_builtin = false;
delete from rbac_role
where is_builtin = false;
delete from base_project;
delete from base_department;
delete from base_config;

insert into iam_user (
    id, username, password_hash, display_name, email, status, must_change_password, created_at, updated_at
)
values
    ('00000000-0000-0000-0000-000000000101', 'admin_user', '{noop}unused', '平台管理员', 'admin@example.com', 'ENABLED', false, '2026-05-16T09:00:00Z', '2026-05-16T09:00:00Z'),
    ('00000000-0000-0000-0000-000000000102', 'shao.min', '{noop}unused', '邵敏', 'shao.min@example.com', 'ENABLED', false, '2026-05-16T09:01:00Z', '2026-05-16T09:01:00Z'),
    ('00000000-0000-0000-0000-000000000103', 'he.xu', '{noop}unused', '何序', 'he.xu@example.com', 'ENABLED', false, '2026-05-16T09:02:00Z', '2026-05-16T09:02:00Z'),
    ('00000000-0000-0000-0000-000000000104', 'zhao.wen', '{noop}unused', '赵文', 'zhao.wen@example.com', 'ENABLED', false, '2026-05-16T09:03:00Z', '2026-05-16T09:03:00Z'),
    ('00000000-0000-0000-0000-000000000105', 'dev_user', '{noop}unused', '研发用户', 'dev@example.com', 'ENABLED', false, '2026-05-16T09:04:00Z', '2026-05-16T09:04:00Z');

insert into base_department (
    id, parent_id, code, name, path, level, sort_order, status, created_by, updated_by, created_at, updated_at
)
values
    ('00000000-0000-0000-0000-000000000201', null, 'quality', '质量工程中心', '/质量工程中心', 1, 10, 'ENABLED', '00000000-0000-0000-0000-000000000101', '00000000-0000-0000-0000-000000000101', '2026-05-16T09:30:00Z', '2026-05-16T09:30:00Z'),
    ('00000000-0000-0000-0000-000000000202', '00000000-0000-0000-0000-000000000201', 'automation', '自动化平台组', '/质量工程中心/自动化平台组', 2, 20, 'ENABLED', '00000000-0000-0000-0000-000000000101', '00000000-0000-0000-0000-000000000101', '2026-05-16T09:20:00Z', '2026-05-16T09:20:00Z'),
    ('00000000-0000-0000-0000-000000000203', '00000000-0000-0000-0000-000000000201', 'business-acceptance', '业务验收组', '/质量工程中心/业务验收组', 2, 30, 'ENABLED', '00000000-0000-0000-0000-000000000101', '00000000-0000-0000-0000-000000000101', '2026-05-16T09:10:00Z', '2026-05-16T09:10:00Z');

insert into base_department_manager (dept_id, user_id, status, created_by, updated_by)
values
    ('00000000-0000-0000-0000-000000000201', '00000000-0000-0000-0000-000000000102', 'ENABLED', '00000000-0000-0000-0000-000000000101', '00000000-0000-0000-0000-000000000101');

insert into base_department_member (dept_id, user_id, is_primary, position, status, created_by, updated_by)
values
    ('00000000-0000-0000-0000-000000000201', '00000000-0000-0000-0000-000000000101', true, '管理员', 'ENABLED', '00000000-0000-0000-0000-000000000101', '00000000-0000-0000-0000-000000000101'),
    ('00000000-0000-0000-0000-000000000201', '00000000-0000-0000-0000-000000000102', true, '负责人', 'ENABLED', '00000000-0000-0000-0000-000000000101', '00000000-0000-0000-0000-000000000101'),
    ('00000000-0000-0000-0000-000000000202', '00000000-0000-0000-0000-000000000103', true, '工程师', 'ENABLED', '00000000-0000-0000-0000-000000000101', '00000000-0000-0000-0000-000000000101'),
    ('00000000-0000-0000-0000-000000000203', '00000000-0000-0000-0000-000000000104', true, '验收员', 'ENABLED', '00000000-0000-0000-0000-000000000101', '00000000-0000-0000-0000-000000000101');

insert into rbac_role_binding (
    subject_type, subject_id, role_id, role_code, scope_type, scope_id, status, created_by, updated_by
)
select 'USER', '00000000-0000-0000-0000-000000000101'::uuid, r.id, 'SuperAdmin', 'PLATFORM', null, 'ENABLED',
       '00000000-0000-0000-0000-000000000101', '00000000-0000-0000-0000-000000000101'
from rbac_role r
where r.code = 'SuperAdmin'
  and r.deleted_at is null;

insert into rbac_role_binding (
    subject_type, subject_id, role_id, role_code, scope_type, scope_id, status, created_by, updated_by
)
select 'USER', '00000000-0000-0000-0000-000000000102'::uuid, r.id, 'PlatformAdmin', 'PLATFORM', null, 'ENABLED',
       '00000000-0000-0000-0000-000000000101', '00000000-0000-0000-0000-000000000101'
from rbac_role r
where r.code = 'PlatformAdmin'
  and r.deleted_at is null;

insert into base_project (
    id, code, name, status, sensitivity_level, allow_public_model, created_by, updated_by, created_at, updated_at
)
values
    ('00000000-0000-0000-0000-000000000301', 'default-project', '默认项目', 'ACTIVE', 'INTERNAL', false, '00000000-0000-0000-0000-000000000101', '00000000-0000-0000-0000-000000000101', '2026-05-16T10:00:00Z', '2026-05-16T10:00:00Z'),
    ('00000000-0000-0000-0000-000000000302', 'api-stability', 'API Stability', 'ACTIVE', 'INTERNAL', false, '00000000-0000-0000-0000-000000000101', '00000000-0000-0000-0000-000000000101', '2026-05-16T10:10:00Z', '2026-05-16T10:10:00Z'),
    ('00000000-0000-0000-0000-000000000303', 'ui-regression', 'UI Regression', 'ACTIVE', 'INTERNAL', false, '00000000-0000-0000-0000-000000000101', '00000000-0000-0000-0000-000000000101', '2026-05-16T10:20:00Z', '2026-05-16T10:20:00Z');

insert into base_project_department (project_id, dept_id, relation_type, is_primary, status, created_by, updated_by)
values
    ('00000000-0000-0000-0000-000000000301', '00000000-0000-0000-0000-000000000201', 'OWNER', true, 'ENABLED', '00000000-0000-0000-0000-000000000101', '00000000-0000-0000-0000-000000000101'),
    ('00000000-0000-0000-0000-000000000302', '00000000-0000-0000-0000-000000000201', 'OWNER', true, 'ENABLED', '00000000-0000-0000-0000-000000000101', '00000000-0000-0000-0000-000000000101'),
    ('00000000-0000-0000-0000-000000000303', '00000000-0000-0000-0000-000000000201', 'OWNER', true, 'ENABLED', '00000000-0000-0000-0000-000000000101', '00000000-0000-0000-0000-000000000101');

insert into base_project_member (project_id, user_id, member_type, status, created_by, updated_by, created_at, updated_at)
values
    ('00000000-0000-0000-0000-000000000301', '00000000-0000-0000-0000-000000000101', 'OWNER', 'ENABLED', '00000000-0000-0000-0000-000000000101', '00000000-0000-0000-0000-000000000101', '2026-05-16T10:00:00Z', '2026-05-16T10:00:00Z'),
    ('00000000-0000-0000-0000-000000000302', '00000000-0000-0000-0000-000000000102', 'OWNER', 'ENABLED', '00000000-0000-0000-0000-000000000101', '00000000-0000-0000-0000-000000000101', '2026-05-16T10:10:00Z', '2026-05-16T10:10:00Z');

insert into base_config (scope_type, scope_id, config_key, value_kind, value_json, status, created_by, updated_by, created_at, updated_at)
values
    ('SYSTEM', null, 'password.min_length', 'PLAIN', '{"_display_name":"密码最小长度","_value":"10 位"}'::jsonb, 'ENABLED',
     '00000000-0000-0000-0000-000000000101', '00000000-0000-0000-0000-000000000101', '2026-05-16T10:30:00Z', '2026-05-16T10:30:00Z');

insert into audit_outbox (
    id, idempotency_key, event_payload_json, status, retry_count, next_retry_at, last_error, created_at, updated_at
)
values
    (
        '00000000-0000-0000-0000-000000000401',
        'management-fixture-pending',
        '{"traceId":"trc_outbox_pending","action":"创建部门","resourceType":"department","resourceId":"dept-qa","result":"SUCCESS"}'::jsonb,
        'PENDING',
        0,
        '2026-05-16T11:00:00Z',
        null,
        '2026-05-16T11:00:00Z',
        '2026-05-16T11:00:00Z'
    ),
    (
        '00000000-0000-0000-0000-000000000402',
        'management-fixture-failed',
        '{"traceId":"trc_outbox_failed","action":"写入审计","resourceType":"audit_log","resourceId":"audit-1","result":"FAILED"}'::jsonb,
        'FAILED',
        3,
        '2026-05-16T11:05:00Z',
        'insert audit_log timeout',
        '2026-05-16T11:05:00Z',
        '2026-05-16T11:05:00Z'
    );
