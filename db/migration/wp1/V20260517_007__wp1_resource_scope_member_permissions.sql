-- WP1 resource-scope collaboration permissions for applications and environments.

insert into rbac_permission (code, resource_type, action, scope_mask, description)
values
    ('application:owner_manage', 'application', 'owner_manage', 'PLATFORM,PROJECT,APPLICATION', '管理应用负责人'),
    ('environment:user_manage', 'environment', 'user_manage', 'PLATFORM,PROJECT,APPLICATION,ENVIRONMENT', '管理环境授权用户')
on conflict (code) do update set
    resource_type = excluded.resource_type,
    action = excluded.action,
    scope_mask = excluded.scope_mask,
    description = excluded.description,
    status = 'ENABLED',
    updated_at = now();

with role_permissions(role_code, permission_code) as (
    values
        ('SuperAdmin', 'application:owner_manage'),
        ('SuperAdmin', 'environment:user_manage'),
        ('PlatformAdmin', 'application:owner_manage'),
        ('PlatformAdmin', 'environment:user_manage'),
        ('ProjectOwner', 'application:owner_manage'),
        ('ProjectOwner', 'environment:user_manage'),
        ('AppOwner', 'application:owner_manage'),
        ('AppOwner', 'environment:user_manage')
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
