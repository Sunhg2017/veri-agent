-- WP2-A model access management console permissions and DB compatibility fixes.

insert into rbac_permission (code, resource_type, action, scope_mask, description)
values
    ('modelAccess:read', 'modelAccess', 'read', 'PLATFORM,PROJECT,APPLICATION', '查看模型接入配置、Prompt、调用日志和成本'),
    ('modelAccess:manage', 'modelAccess', 'manage', 'PLATFORM', '管理模型供应商、Prompt 版本、就绪检查和熔断恢复'),
    ('modelAccess:export', 'modelAccess', 'export', 'PLATFORM,PROJECT,APPLICATION', '导出模型调用日志')
on conflict (code) do update set
    resource_type = excluded.resource_type,
    action = excluded.action,
    scope_mask = excluded.scope_mask,
    description = excluded.description,
    status = 'ENABLED',
    updated_at = now();

with role_permissions(role_code, permission_code) as (
    values
        ('SuperAdmin', 'modelAccess:read'),
        ('SuperAdmin', 'modelAccess:manage'),
        ('SuperAdmin', 'modelAccess:export'),
        ('PlatformAdmin', 'modelAccess:read'),
        ('PlatformAdmin', 'modelAccess:manage'),
        ('PlatformAdmin', 'modelAccess:export'),
        ('Auditor', 'modelAccess:read'),
        ('Auditor', 'modelAccess:export')
)
insert into rbac_role_permission (
    role_id,
    permission_id,
    effect,
    created_by
)
select
    r.id,
    p.id,
    'ALLOW',
    null
from role_permissions rp
join rbac_role r on r.code = rp.role_code
    and r.deleted_at is null
join rbac_permission p on p.code = rp.permission_code
    and p.status = 'ENABLED'
on conflict do nothing;

alter table ma_invocation_log
    drop constraint if exists ck_ma_invocation_sensitivity;

alter table ma_invocation_log
    add constraint ck_ma_invocation_sensitivity
    check (sensitivity_level in ('PUBLIC', 'INTERNAL', 'CONFIDENTIAL', 'STRICT', 'RESTRICTED'));
