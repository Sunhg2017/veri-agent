-- WP1 account lifecycle permissions.

insert into rbac_permission (code, resource_type, action, scope_mask, description)
values
('user:unlock','user','unlock','PLATFORM','解除用户锁定'),
('user:reset_password','user','reset_password','PLATFORM','重置用户密码')
on conflict (code) do update set
    resource_type = excluded.resource_type,
    action = excluded.action,
    scope_mask = excluded.scope_mask,
    description = excluded.description,
    status = 'ENABLED',
    updated_at = now();

with role_permissions(role_code, permission_code) as (
    values
    ('SuperAdmin','user:enable'),
    ('SuperAdmin','user:disable'),
    ('SuperAdmin','user:lock'),
    ('SuperAdmin','user:unlock'),
    ('SuperAdmin','user:reset_password'),
    ('PlatformAdmin','user:unlock'),
    ('PlatformAdmin','user:reset_password')
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
