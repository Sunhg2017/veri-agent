-- WP1 SuperAdmin seed validation.
-- Every query returns: check_name, status, details.

with seeded as (
    select u.id, u.username, u.password_hash, u.must_change_password
      from iam_user u
      join rbac_role_binding b on b.subject_type = 'USER'
       and b.subject_id = u.id
       and b.role_code = 'SuperAdmin'
       and b.scope_type = 'PLATFORM'
       and b.scope_id is null
       and b.status = 'ENABLED'
       and b.deleted_at is null
     where u.username = 'admin'
       and u.status = 'ENABLED'
       and u.deleted_at is null
)
select
    'super_admin_seed.user_and_role_binding_exist' as check_name,
    case when count(*) = 1 then 'PASS' else 'FAIL' end as status,
    'count=' || count(*) as details
from seeded;

with seeded as (
    select u.password_hash, u.must_change_password
      from iam_user u
      join rbac_role_binding b on b.subject_type = 'USER'
       and b.subject_id = u.id
       and b.role_code = 'SuperAdmin'
       and b.scope_type = 'PLATFORM'
       and b.scope_id is null
       and b.status = 'ENABLED'
       and b.deleted_at is null
     where u.username = 'admin'
       and u.status = 'ENABLED'
       and u.deleted_at is null
)
select
    'super_admin_seed.password_hash_and_force_change' as check_name,
    case
        when count(*) = 1
         and bool_and(must_change_password)
         and bool_and(password_hash is not null and password_hash <> 'AdminPass12345!' and password_hash like '$2%')
        then 'PASS'
        else 'FAIL'
    end as status,
    'count=' || count(*) || ', must_change_password=' || coalesce(bool_and(must_change_password)::text, 'null') as details
from seeded;

with seeded_audit as (
    select action
      from audit_log
     where actor_type = 'SYSTEM'
       and result = 'SUCCESS'
       and after_json @> '{"seed_script": true}'::jsonb
       and action in ('USER_CREATE', 'SUPER_ADMIN_INIT')
)
select
    'super_admin_seed.audit_written' as check_name,
    case when count(distinct action) = 2 then 'PASS' else 'FAIL' end as status,
    coalesce(string_agg(distinct action, ', ' order by action), 'missing seed audit') as details
from seeded_audit;
