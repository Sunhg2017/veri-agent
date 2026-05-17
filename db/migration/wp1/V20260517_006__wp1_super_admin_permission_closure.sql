with permission_closure(permission_code) as (
    values
        ('department:enable'),
        ('department:disable'),
        ('department:member_manage'),
        ('user:enable'),
        ('user:disable'),
        ('user:lock'),
        ('user:unlock'),
        ('project:archive'),
        ('project:disable'),
        ('project:member_manage'),
        ('application:disable'),
        ('environment:disable'),
        ('environment:use'),
        ('secret:reference')
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
from permission_closure closure
join rbac_role r on r.code = 'SuperAdmin'
    and r.deleted_at is null
join rbac_permission p on p.code = closure.permission_code
    and p.status = 'ENABLED'
on conflict do nothing;
