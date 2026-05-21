-- WP1 seed validation for single-platform deployment.
-- Every query returns: check_name, status, details.

with expected(code) as (
    values
        ('department:read'), ('department:create'), ('department:edit'), ('department:enable'), ('department:disable'), ('department:member_manage'),
        ('user:read'), ('user:create'), ('user:edit'), ('user:enable'), ('user:disable'), ('user:lock'), ('user:unlock'), ('user:assign_role'), ('user:reset_password'),
        ('role:read'), ('role:create'), ('role:edit'), ('role:bind'), ('role:unbind'),
        ('project:read'), ('project:create'), ('project:edit'), ('project:archive'), ('project:disable'), ('project:member_manage'),
        ('application:read'), ('application:create'), ('application:edit'), ('application:disable'), ('application:owner_manage'),
        ('environment:read'), ('environment:create'), ('environment:edit'), ('environment:disable'), ('environment:use'), ('environment:user_manage'),
        ('config:read'), ('config:edit'),
        ('audit:read'), ('audit:export'), ('audit:write_internal'),
        ('secret:reference'),
        ('context:read'), ('context:switch'), ('context:effective_read'),
        ('asset:read'), ('asset:manage'), ('asset:review'), ('asset:export'),
        ('modelAccess:read'), ('modelAccess:manage'), ('modelAccess:export'),
        ('requirementInput:read'), ('requirementInput:manage'), ('requirementInput:import'),
        ('requirementInput:candidate_review'), ('requirementInput:publish'), ('requirementInput:webhook_replay')
),
missing as (
    select e.code
    from expected e
    left join rbac_permission p on p.code = e.code and p.status = 'ENABLED'
    where p.id is null
)
select
    'seed.p0_permissions_exist' as check_name,
    case when count(*) = 0 then 'PASS' else 'FAIL' end as status,
    coalesce(string_agg(code, ', ' order by code), 'all P0 permission codes exist and are enabled') as details
from missing;

with forbidden as (
    select code
    from rbac_permission
    where code like 'tenant:%'
       or scope_mask like '%TENANT%'
)
select
    'seed.no_tenant_permissions' as check_name,
    case when count(*) = 0 then 'PASS' else 'FAIL' end as status,
    coalesce(string_agg(code, ', ' order by code), 'no tenant permission codes or scope masks remain') as details
from forbidden;

with expected(code, scope_type) as (
    values
        ('SuperAdmin','PLATFORM'),
        ('PlatformAdmin','PLATFORM'),
        ('DepartmentManager','DEPARTMENT'),
        ('ProjectOwner','PROJECT'),
        ('AppOwner','APPLICATION'),
        ('Tester','ENVIRONMENT'),
        ('Developer','APPLICATION'),
        ('Auditor','PLATFORM')
),
missing as (
    select e.code
    from expected e
    left join rbac_role r
        on r.code = e.code
       and r.scope_type = e.scope_type
       and r.is_system = true
       and r.is_builtin = true
       and r.status = 'ENABLED'
       and r.deleted_at is null
    where r.id is null
)
select
    'seed.system_builtin_roles_exist' as check_name,
    case when count(*) = 0 then 'PASS' else 'FAIL' end as status,
    coalesce(string_agg(code, ', ' order by code), '8 builtin role templates exist') as details
from missing;

with dup as (
    select code, count(*) as role_count
    from rbac_role
    where code in ('SuperAdmin','PlatformAdmin','DepartmentManager','ProjectOwner','AppOwner','Tester','Developer','Auditor')
      and deleted_at is null
    group by code
    having count(*) > 1
)
select
    'seed.builtin_roles_idempotent' as check_name,
    case when count(*) = 0 then 'PASS' else 'FAIL' end as status,
    coalesce(string_agg(code || '=' || role_count, ', ' order by code), 'no duplicate builtin roles') as details
from dup;

with providers as (
    select provider_code, provider_type, is_default, status
    from secret_provider
    where provider_code = 'local'
      and provider_type = 'LOCAL_ENCRYPTED'
      and is_default = true
      and status = 'ENABLED'
      and deleted_at is null
)
select
    'seed.default_local_encrypted_provider_exists' as check_name,
    case when count(*) = 1 then 'PASS' else 'FAIL' end as status,
    'count=' || count(*) as details
from providers;

with expected(config_key) as (
    values
        ('integration.dingtalk-bot'),
        ('integration.feishu-bot'),
        ('integration.zentao')
),
missing as (
    select e.config_key
    from expected e
    left join base_config c
        on c.scope_type = 'SYSTEM'
       and c.scope_id is null
       and c.config_key = e.config_key
       and c.status = 'ENABLED'
       and c.deleted_at is null
    where c.id is null
)
select
    'seed.integration_config_defaults_exist' as check_name,
    case when count(*) = 0 then 'PASS' else 'FAIL' end as status,
    coalesce(string_agg(config_key, ', ' order by config_key), 'all integration defaults exist') as details
from missing;

with expected(config_key) as (
    values
        ('audit.retention_days'),
        ('audit.retention_cleanup_enabled'),
        ('audit.retention_min_days'),
        ('audit.retention_cleanup_batch_size')
),
missing as (
    select e.config_key
    from expected e
    left join base_config c
        on c.scope_type = 'SYSTEM'
       and c.scope_id is null
       and c.config_key = e.config_key
       and c.status = 'ENABLED'
       and c.deleted_at is null
    where c.id is null
)
select
    'seed.audit_retention_config_defaults_exist' as check_name,
    case when count(*) = 0 then 'PASS' else 'FAIL' end as status,
    coalesce(string_agg(config_key, ', ' order by config_key), 'audit retention defaults exist') as details
from missing;

with values_by_key as (
    select config_key, value_json
    from base_config
    where scope_type = 'SYSTEM'
      and scope_id is null
      and config_key in (
          'audit.retention_days',
          'audit.retention_cleanup_enabled',
          'audit.retention_min_days',
          'audit.retention_cleanup_batch_size'
      )
      and status = 'ENABLED'
      and deleted_at is null
),
parsed as (
    select
        max((value_json::text)::int) filter (where config_key = 'audit.retention_days') as retention_days,
        max(case when (value_json::text)::boolean then 1 else 0 end) filter (where config_key = 'audit.retention_cleanup_enabled') as cleanup_enabled,
        max((value_json::text)::int) filter (where config_key = 'audit.retention_min_days') as min_days,
        max((value_json::text)::int) filter (where config_key = 'audit.retention_cleanup_batch_size') as batch_size
    from values_by_key
)
select
    'seed.audit_retention_config_values' as check_name,
    case
        when retention_days >= min_days
         and min_days >= 30
         and cleanup_enabled = 0
         and batch_size between 1 and 10000
        then 'PASS'
        else 'FAIL'
    end as status,
    'retentionDays=' || coalesce(retention_days::text, 'missing')
        || ', minDays=' || coalesce(min_days::text, 'missing')
        || ', cleanupEnabled=' || coalesce(cleanup_enabled::text, 'missing')
        || ', batchSize=' || coalesce(batch_size::text, 'missing') as details
from parsed;

with expected(role_code, permission_code) as (
    values
        ('SuperAdmin','role:bind'), ('SuperAdmin','audit:read'), ('SuperAdmin','context:switch'), ('SuperAdmin','user:enable'), ('SuperAdmin','user:disable'), ('SuperAdmin','user:unlock'), ('SuperAdmin','user:reset_password'), ('SuperAdmin','application:owner_manage'), ('SuperAdmin','environment:user_manage'), ('SuperAdmin','asset:manage'), ('SuperAdmin','asset:review'), ('SuperAdmin','asset:export'), ('SuperAdmin','modelAccess:read'), ('SuperAdmin','modelAccess:manage'), ('SuperAdmin','modelAccess:export'), ('SuperAdmin','requirementInput:manage'), ('SuperAdmin','requirementInput:webhook_replay'),
        ('PlatformAdmin','department:create'), ('PlatformAdmin','user:create'), ('PlatformAdmin','user:unlock'), ('PlatformAdmin','user:reset_password'), ('PlatformAdmin','project:create'), ('PlatformAdmin','application:create'), ('PlatformAdmin','environment:create'), ('PlatformAdmin','config:edit'), ('PlatformAdmin','role:bind'), ('PlatformAdmin','audit:read'), ('PlatformAdmin','context:effective_read'), ('PlatformAdmin','application:owner_manage'), ('PlatformAdmin','environment:user_manage'), ('PlatformAdmin','asset:manage'), ('PlatformAdmin','asset:review'), ('PlatformAdmin','asset:export'), ('PlatformAdmin','modelAccess:read'), ('PlatformAdmin','modelAccess:manage'), ('PlatformAdmin','modelAccess:export'), ('PlatformAdmin','requirementInput:manage'), ('PlatformAdmin','requirementInput:webhook_replay'),
        ('DepartmentManager','department:member_manage'), ('DepartmentManager','user:read'), ('DepartmentManager','project:read'), ('DepartmentManager','audit:read'), ('DepartmentManager','context:read'),
        ('ProjectOwner','project:edit'), ('ProjectOwner','project:member_manage'), ('ProjectOwner','application:create'), ('ProjectOwner','environment:create'), ('ProjectOwner','config:edit'), ('ProjectOwner','role:bind'), ('ProjectOwner','secret:reference'), ('ProjectOwner','context:switch'), ('ProjectOwner','application:owner_manage'), ('ProjectOwner','environment:user_manage'), ('ProjectOwner','asset:manage'), ('ProjectOwner','asset:review'), ('ProjectOwner','asset:export'), ('ProjectOwner','requirementInput:publish'),
        ('AppOwner','application:edit'), ('AppOwner','environment:create'), ('AppOwner','environment:edit'), ('AppOwner','config:edit'), ('AppOwner','role:bind'), ('AppOwner','secret:reference'), ('AppOwner','context:read'), ('AppOwner','application:owner_manage'), ('AppOwner','environment:user_manage'), ('AppOwner','asset:manage'), ('AppOwner','asset:review'), ('AppOwner','requirementInput:publish'),
        ('Tester','environment:use'), ('Tester','config:read'), ('Tester','context:effective_read'), ('Tester','asset:manage'), ('Tester','asset:review'), ('Tester','requirementInput:candidate_review'),
        ('Developer','project:read'), ('Developer','application:read'), ('Developer','environment:read'), ('Developer','config:read'), ('Developer','context:switch'), ('Developer','asset:read'), ('Developer','requirementInput:read'),
        ('Auditor','audit:read'), ('Auditor','audit:export'), ('Auditor','context:effective_read'), ('Auditor','asset:read'), ('Auditor','asset:export'), ('Auditor','modelAccess:read'), ('Auditor','modelAccess:export'), ('Auditor','requirementInput:read')
),
missing as (
    select e.role_code || '->' || e.permission_code as binding
    from expected e
    left join rbac_role r
        on r.code = e.role_code
       and r.status = 'ENABLED'
       and r.deleted_at is null
    left join rbac_permission p
        on p.code = e.permission_code
       and p.status = 'ENABLED'
    left join rbac_role_permission rp
        on rp.role_id = r.id
       and rp.permission_id = p.id
       and rp.deleted_at is null
    where rp.id is null
)
select
    'seed.core_role_permission_bindings_exist' as check_name,
    case when count(*) = 0 then 'PASS' else 'FAIL' end as status,
    coalesce(string_agg(binding, ', ' order by binding), 'core role-permission bindings exist') as details
from missing;
