-- WP1-D4 Secret reference management permissions.

insert into rbac_permission (code, resource_type, action, scope_mask, description)
values
('secret:read','secret','read','PLATFORM','查看密钥引用摘要'),
('secret:manage','secret','manage','PLATFORM','创建本地加密密钥引用'),
('secret:rotate','secret','rotate','PLATFORM','轮换本地加密密钥引用'),
('secret:disable','secret','disable','PLATFORM','撤销本地加密密钥引用')
on conflict (code) do update set
    resource_type = excluded.resource_type,
    action = excluded.action,
    scope_mask = excluded.scope_mask,
    description = excluded.description,
    status = 'ENABLED',
    updated_at = now();

with role_permissions(role_code, permission_code) as (
    values
        ('SuperAdmin','secret:reference'),
        ('SuperAdmin','secret:read'),
        ('SuperAdmin','secret:manage'),
        ('SuperAdmin','secret:rotate'),
        ('SuperAdmin','secret:disable'),
        ('PlatformAdmin','secret:read'),
        ('PlatformAdmin','secret:manage'),
        ('PlatformAdmin','secret:rotate'),
        ('PlatformAdmin','secret:disable')
)
insert into rbac_role_permission (role_id, permission_id, effect)
select r.id, p.id, 'ALLOW'
from rbac_role r
join role_permissions rp on rp.role_code = r.code
join rbac_permission p on p.code = rp.permission_code
where r.deleted_at is null
  and p.status = 'ENABLED'
  and not exists (
      select 1
      from rbac_role_permission existing
      where existing.role_id = r.id
        and existing.permission_id = p.id
        and existing.deleted_at is null
  );
