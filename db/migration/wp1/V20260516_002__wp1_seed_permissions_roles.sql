-- WP1 seed data for single-platform deployment.
-- WP1 uses a single-platform boundary. Isolation starts from department,
-- project, application, and environment scopes.

insert into rbac_permission (code, resource_type, action, scope_mask, description)
values
('department:read','department','read','PLATFORM,DEPARTMENT,PROJECT','查看部门'),
('department:create','department','create','PLATFORM','创建部门'),
('department:edit','department','edit','PLATFORM,DEPARTMENT','编辑部门'),
('department:enable','department','enable','PLATFORM,DEPARTMENT','启用部门'),
('department:disable','department','disable','PLATFORM,DEPARTMENT','停用部门'),
('department:member_manage','department','member_manage','PLATFORM,DEPARTMENT','管理部门成员'),
('user:read','user','read','PLATFORM,DEPARTMENT,PROJECT,APPLICATION','查看用户'),
('user:create','user','create','PLATFORM','创建用户'),
('user:edit','user','edit','PLATFORM,DEPARTMENT','编辑用户'),
('user:enable','user','enable','PLATFORM','启用用户'),
('user:disable','user','disable','PLATFORM','停用用户'),
('user:lock','user','lock','PLATFORM','锁定用户'),
('user:assign_role','user','assign_role','PLATFORM,PROJECT,APPLICATION','分配角色'),
('role:read','role','read','PLATFORM,PROJECT,APPLICATION','查看角色'),
('role:create','role','create','PLATFORM','创建角色'),
('role:edit','role','edit','PLATFORM','编辑角色'),
('role:bind','role','bind','PLATFORM,PROJECT,APPLICATION,ENVIRONMENT','绑定角色'),
('role:unbind','role','unbind','PLATFORM,PROJECT,APPLICATION,ENVIRONMENT','解绑角色'),
('project:read','project','read','PLATFORM,DEPARTMENT,PROJECT','查看项目'),
('project:create','project','create','PLATFORM,DEPARTMENT','创建项目'),
('project:edit','project','edit','PLATFORM,PROJECT','编辑项目'),
('project:archive','project','archive','PLATFORM,PROJECT','归档项目'),
('project:disable','project','disable','PLATFORM,PROJECT','停用项目'),
('project:member_manage','project','member_manage','PLATFORM,PROJECT','管理项目成员'),
('application:read','application','read','PLATFORM,PROJECT,APPLICATION','查看应用'),
('application:create','application','create','PLATFORM,PROJECT','创建应用'),
('application:edit','application','edit','PLATFORM,PROJECT,APPLICATION','编辑应用'),
('application:disable','application','disable','PLATFORM,PROJECT,APPLICATION','停用应用'),
('environment:read','environment','read','PLATFORM,PROJECT,APPLICATION,ENVIRONMENT','查看环境'),
('environment:create','environment','create','PLATFORM,PROJECT,APPLICATION','创建环境'),
('environment:edit','environment','edit','PLATFORM,PROJECT,APPLICATION,ENVIRONMENT','编辑环境'),
('environment:disable','environment','disable','PLATFORM,PROJECT,APPLICATION,ENVIRONMENT','停用环境'),
('environment:use','environment','use','PROJECT,APPLICATION,ENVIRONMENT','使用环境'),
('config:read','config','read','PLATFORM,PROJECT,APPLICATION,ENVIRONMENT','查看配置'),
('config:edit','config','edit','PLATFORM,PROJECT,APPLICATION,ENVIRONMENT','编辑配置'),
('audit:read','audit','read','PLATFORM,DEPARTMENT,PROJECT,APPLICATION,ENVIRONMENT','查看审计'),
('audit:export','audit','export','PLATFORM,PROJECT,APPLICATION,ENVIRONMENT','导出审计'),
('audit:write_internal','audit','write_internal','SERVICE','内部写审计'),
('secret:reference','secret','reference','PLATFORM,PROJECT,APPLICATION,ENVIRONMENT','引用密钥'),
('context:read','context','read','PLATFORM,DEPARTMENT,PROJECT,APPLICATION,ENVIRONMENT','查看上下文'),
('context:switch','context','switch','PLATFORM,DEPARTMENT,PROJECT,APPLICATION,ENVIRONMENT','切换上下文'),
('context:effective_read','context','effective_read','PLATFORM,PROJECT,APPLICATION,ENVIRONMENT','读取有效上下文'),
('asset:read','asset','read','PLATFORM,PROJECT,APPLICATION','查看测试资产'),
('asset:manage','asset','manage','PLATFORM,PROJECT,APPLICATION','管理测试资产'),
('asset:review','asset','review','PLATFORM,PROJECT,APPLICATION','评审测试资产状态'),
('asset:export','asset','export','PLATFORM,PROJECT','导出测试资产'),
('requirementInput:read','requirementInput','read','PLATFORM,PROJECT,APPLICATION','查看需求输入'),
('requirementInput:manage','requirementInput','manage','PLATFORM,PROJECT','管理需求输入源和字段映射'),
('requirementInput:import','requirementInput','import','PLATFORM,PROJECT,APPLICATION','导入需求文档'),
('requirementInput:candidate_review','requirementInput','candidate_review','PLATFORM,PROJECT,APPLICATION','评审需求候选项'),
('requirementInput:publish','requirementInput','publish','PLATFORM,PROJECT,APPLICATION','发布需求候选项'),
('requirementInput:webhook_replay','requirementInput','webhook_replay','PLATFORM,PROJECT','重放需求输入 webhook')
on conflict (code) do update set
    resource_type = excluded.resource_type,
    action = excluded.action,
    scope_mask = excluded.scope_mask,
    description = excluded.description,
    status = 'ENABLED',
    updated_at = now();

with roles(code, name, scope_type, description) as (
    values
    ('SuperAdmin','超级管理员','PLATFORM','平台初始化、组织治理、平台审计'),
    ('PlatformAdmin','平台管理员','PLATFORM','组织、用户、项目、应用、环境、权限、审计管理'),
    ('DepartmentManager','部门负责人','DEPARTMENT','部门成员和部门关联项目协同'),
    ('ProjectOwner','项目负责人','PROJECT','项目成员、应用、项目公共环境、项目配置管理'),
    ('AppOwner','应用负责人','APPLICATION','应用信息和应用专属环境管理'),
    ('Tester','测试工程师','ENVIRONMENT','授权范围只读和启用环境使用'),
    ('Developer','研发工程师','APPLICATION','授权范围脱敏查看'),
    ('Auditor','审计员','PLATFORM','授权范围审计只读和导出')
)
insert into rbac_role (code, name, scope_type, is_system, is_builtin, status, description)
select r.code, r.name, r.scope_type, true, true, 'ENABLED', r.description
from roles r
where not exists (
    select 1
    from rbac_role existing
    where existing.code = r.code
      and existing.deleted_at is null
);

with role_permissions(role_code, permission_code) as (
    values
    ('SuperAdmin','role:read'),('SuperAdmin','role:create'),('SuperAdmin','role:edit'),('SuperAdmin','role:bind'),('SuperAdmin','role:unbind'),
    ('SuperAdmin','audit:read'),('SuperAdmin','audit:export'),('SuperAdmin','audit:write_internal'),
    ('SuperAdmin','context:read'),('SuperAdmin','context:switch'),('SuperAdmin','context:effective_read'),
    ('SuperAdmin','department:read'),('SuperAdmin','department:create'),('SuperAdmin','department:edit'),
    ('SuperAdmin','user:read'),('SuperAdmin','user:create'),('SuperAdmin','user:edit'),('SuperAdmin','user:assign_role'),
    ('SuperAdmin','project:read'),('SuperAdmin','project:create'),('SuperAdmin','project:edit'),
    ('SuperAdmin','application:read'),('SuperAdmin','application:create'),('SuperAdmin','application:edit'),
    ('SuperAdmin','environment:read'),('SuperAdmin','environment:create'),('SuperAdmin','environment:edit'),('SuperAdmin','config:read'),('SuperAdmin','config:edit'),
    ('SuperAdmin','asset:read'),('SuperAdmin','asset:manage'),('SuperAdmin','asset:review'),('SuperAdmin','asset:export'),
    ('SuperAdmin','requirementInput:read'),('SuperAdmin','requirementInput:manage'),('SuperAdmin','requirementInput:import'),
    ('SuperAdmin','requirementInput:candidate_review'),('SuperAdmin','requirementInput:publish'),('SuperAdmin','requirementInput:webhook_replay'),

    ('PlatformAdmin','department:read'),('PlatformAdmin','department:create'),('PlatformAdmin','department:edit'),('PlatformAdmin','department:enable'),('PlatformAdmin','department:disable'),('PlatformAdmin','department:member_manage'),
    ('PlatformAdmin','user:read'),('PlatformAdmin','user:create'),('PlatformAdmin','user:edit'),('PlatformAdmin','user:enable'),('PlatformAdmin','user:disable'),('PlatformAdmin','user:lock'),('PlatformAdmin','user:assign_role'),
    ('PlatformAdmin','role:read'),('PlatformAdmin','role:bind'),('PlatformAdmin','role:unbind'),
    ('PlatformAdmin','project:read'),('PlatformAdmin','project:create'),('PlatformAdmin','project:edit'),('PlatformAdmin','project:archive'),('PlatformAdmin','project:disable'),('PlatformAdmin','project:member_manage'),
    ('PlatformAdmin','application:read'),('PlatformAdmin','application:create'),('PlatformAdmin','application:edit'),('PlatformAdmin','application:disable'),
    ('PlatformAdmin','environment:read'),('PlatformAdmin','environment:create'),('PlatformAdmin','environment:edit'),('PlatformAdmin','environment:disable'),('PlatformAdmin','environment:use'),
    ('PlatformAdmin','config:read'),('PlatformAdmin','config:edit'),('PlatformAdmin','audit:read'),('PlatformAdmin','audit:export'),('PlatformAdmin','secret:reference'),
    ('PlatformAdmin','context:read'),('PlatformAdmin','context:switch'),('PlatformAdmin','context:effective_read'),
    ('PlatformAdmin','asset:read'),('PlatformAdmin','asset:manage'),('PlatformAdmin','asset:review'),('PlatformAdmin','asset:export'),
    ('PlatformAdmin','requirementInput:read'),('PlatformAdmin','requirementInput:manage'),('PlatformAdmin','requirementInput:import'),
    ('PlatformAdmin','requirementInput:candidate_review'),('PlatformAdmin','requirementInput:publish'),('PlatformAdmin','requirementInput:webhook_replay'),

    ('DepartmentManager','department:read'),('DepartmentManager','department:edit'),('DepartmentManager','department:enable'),('DepartmentManager','department:disable'),('DepartmentManager','department:member_manage'),
    ('DepartmentManager','user:read'),('DepartmentManager','user:edit'),('DepartmentManager','project:read'),('DepartmentManager','application:read'),('DepartmentManager','environment:read'),('DepartmentManager','config:read'),('DepartmentManager','audit:read'),
    ('DepartmentManager','context:read'),('DepartmentManager','context:switch'),('DepartmentManager','context:effective_read'),

    ('ProjectOwner','project:read'),('ProjectOwner','project:edit'),('ProjectOwner','project:archive'),('ProjectOwner','project:disable'),('ProjectOwner','project:member_manage'),
    ('ProjectOwner','application:read'),('ProjectOwner','application:create'),('ProjectOwner','application:edit'),('ProjectOwner','application:disable'),
    ('ProjectOwner','environment:read'),('ProjectOwner','environment:create'),('ProjectOwner','environment:edit'),('ProjectOwner','environment:disable'),('ProjectOwner','environment:use'),
    ('ProjectOwner','config:read'),('ProjectOwner','config:edit'),('ProjectOwner','role:read'),('ProjectOwner','role:bind'),('ProjectOwner','role:unbind'),('ProjectOwner','audit:read'),('ProjectOwner','secret:reference'),
    ('ProjectOwner','context:read'),('ProjectOwner','context:switch'),('ProjectOwner','context:effective_read'),
    ('ProjectOwner','asset:read'),('ProjectOwner','asset:manage'),('ProjectOwner','asset:review'),('ProjectOwner','asset:export'),
    ('ProjectOwner','requirementInput:read'),('ProjectOwner','requirementInput:import'),('ProjectOwner','requirementInput:candidate_review'),('ProjectOwner','requirementInput:publish'),

    ('AppOwner','project:read'),('AppOwner','application:read'),('AppOwner','application:edit'),('AppOwner','application:disable'),
    ('AppOwner','environment:read'),('AppOwner','environment:create'),('AppOwner','environment:edit'),('AppOwner','environment:disable'),
    ('AppOwner','config:read'),('AppOwner','config:edit'),('AppOwner','role:read'),('AppOwner','role:bind'),('AppOwner','role:unbind'),('AppOwner','audit:read'),('AppOwner','secret:reference'),
    ('AppOwner','context:read'),('AppOwner','context:switch'),('AppOwner','context:effective_read'),
    ('AppOwner','asset:read'),('AppOwner','asset:manage'),('AppOwner','asset:review'),
    ('AppOwner','requirementInput:read'),('AppOwner','requirementInput:import'),('AppOwner','requirementInput:candidate_review'),('AppOwner','requirementInput:publish'),

    ('Tester','project:read'),('Tester','application:read'),('Tester','environment:read'),('Tester','environment:use'),('Tester','config:read'),
    ('Tester','context:read'),('Tester','context:switch'),('Tester','context:effective_read'),
    ('Tester','asset:read'),('Tester','asset:manage'),('Tester','asset:review'),
    ('Tester','requirementInput:read'),('Tester','requirementInput:import'),('Tester','requirementInput:candidate_review'),

    ('Developer','project:read'),('Developer','application:read'),('Developer','environment:read'),('Developer','config:read'),
    ('Developer','context:read'),('Developer','context:switch'),('Developer','context:effective_read'),
    ('Developer','asset:read'),
    ('Developer','requirementInput:read'),

    ('Auditor','department:read'),('Auditor','user:read'),('Auditor','project:read'),('Auditor','application:read'),('Auditor','environment:read'),('Auditor','config:read'),('Auditor','role:read'),('Auditor','audit:read'),('Auditor','audit:export'),
    ('Auditor','context:read'),('Auditor','context:effective_read'),('Auditor','asset:read'),('Auditor','asset:export'),('Auditor','requirementInput:read')
)
insert into rbac_role_permission (role_id, permission_id, effect)
select r.id, p.id, 'ALLOW'
from rbac_role r
join role_permissions rp on rp.role_code = r.code
join rbac_permission p on p.code = rp.permission_code
where not exists (
    select 1
    from rbac_role_permission existing
    where existing.role_id = r.id
      and existing.permission_id = p.id
      and existing.deleted_at is null
);

insert into secret_provider (provider_code, provider_type, config_json, is_default, status)
select
    'local',
    'LOCAL_ENCRYPTED',
    '{"key_source":"env","key_env":"WP1_LOCAL_SECRET_MASTER_KEY"}'::jsonb,
    true,
    'ENABLED'
where not exists (
    select 1
    from secret_provider sp
    where sp.provider_code = 'local'
      and sp.deleted_at is null
);

with configs(config_key, value_json) as (
    values
    ('allow_public_model','false'::jsonb),
    ('sensitivity_level','"INTERNAL"'::jsonb),
    ('default_resource_pool','"default"'::jsonb),
    ('execution.api_enabled','true'::jsonb),
    ('execution.ui_enabled','true'::jsonb),
    ('execution.e2e_enabled','true'::jsonb),
    ('execution.prod_default_enabled','false'::jsonb),
    ('notification.default_channel','null'::jsonb),
    ('integration.dingtalk-bot','{"name":"钉钉机器人","category":"通知/审批/文档同步","scope":"平台级"}'::jsonb),
    ('integration.feishu-bot','{"name":"飞书机器人","category":"通知/审批/文档同步","scope":"平台级"}'::jsonb),
    ('integration.zentao','{"name":"禅道","category":"缺陷系统","scope":"项目级"}'::jsonb),
    ('audit.retention_days','365'::jsonb),
    ('session.access_token_ttl_minutes','30'::jsonb),
    ('secret.default_provider','"local"'::jsonb)
)
insert into base_config (scope_type, scope_id, config_key, value_kind, value_json, status)
select 'SYSTEM', null, c.config_key, 'PLAIN', c.value_json, 'ENABLED'
from configs c
where not exists (
    select 1
    from base_config bc
    where bc.scope_type = 'SYSTEM'
      and bc.scope_id is null
      and bc.config_key = c.config_key
      and bc.deleted_at is null
);
