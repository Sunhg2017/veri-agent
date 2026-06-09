-- Validate the real preprod/production application database role before release.
-- Usage:
--   psql "$WP1_RELEASE_DATABASE_URL" \
--     -v WP1_RELEASE_SCHEMA=public \
--     -v WP1_RELEASE_APP_ROLE=wp1_app \
--     -v WP1_RELEASE_READONLY_ROLE=wp1_readonly \
--     -v WP1_RELEASE_MIGRATION_ROLE=wp1_migration \
--     -f db/validation/wp1_release_role_validation.sql

drop table if exists pg_temp.wp1_release_role_checks;

create temp table wp1_release_role_checks as
with settings as (
    select
        :'WP1_RELEASE_SCHEMA'::name as schema_name,
        :'WP1_RELEASE_APP_ROLE'::name as app_role,
        :'WP1_RELEASE_READONLY_ROLE'::name as readonly_role,
        :'WP1_RELEASE_MIGRATION_ROLE'::name as migration_role
),
schema_exists as (
    select n.oid, n.nspname
    from pg_namespace n
    join settings s on s.schema_name = n.nspname
),
roles as (
    select p.rolname, p.rolsuper, p.rolcreatedb, p.rolcreaterole, p.rolreplication, p.rolbypassrls
    from pg_roles p
    join settings s on p.rolname in (s.app_role, s.readonly_role, s.migration_role)
),
tables as (
    select c.oid, c.relname, c.relkind
    from pg_class c
    join schema_exists s on s.oid = c.relnamespace
    where c.relkind in ('r', 'p', 'v', 'm')
),
owned_by_app_or_readonly as (
    select c.relname
    from pg_class c
    join schema_exists s on s.oid = c.relnamespace
    join settings i on pg_get_userbyid(c.relowner) in (i.app_role::text, i.readonly_role::text)
    where c.relkind in ('r', 'p', 'i', 'S', 'v', 'm')
),
app_role_exists as (
    select 1
    from roles r
    join settings s on s.app_role = r.rolname
),
readonly_role_exists as (
    select 1
    from roles r
    join settings s on s.readonly_role = r.rolname
),
migration_role_exists as (
    select 1
    from roles r
    join settings s on s.migration_role = r.rolname
),
app_role as (
    select r.*
    from roles r
    join settings s on s.app_role = r.rolname
),
readonly_role as (
    select r.*
    from roles r
    join settings s on s.readonly_role = r.rolname
),
migration_role as (
    select r.*
    from roles r
    join settings s on s.migration_role = r.rolname
),
checks as (
    select
        'release.schema.exists' as check_name,
        case when exists (select 1 from schema_exists) then 'PASS' else 'FAIL' end as status,
        (select schema_name::text from settings) as details
    union all
    select
        'release.roles.distinct' as check_name,
        case
            when (select count(distinct role_name) from (
                select app_role::text as role_name from settings
                union all select readonly_role::text from settings
                union all select migration_role::text from settings
            ) r) = 3
            then 'PASS'
            else 'FAIL'
        end as status,
        format(
            'app=%s readonly=%s migration=%s',
            (select app_role::text from settings),
            (select readonly_role::text from settings),
            (select migration_role::text from settings)
        ) as details
    union all
    select
        'release.app_role.exists' as check_name,
        case when exists (select 1 from app_role_exists) then 'PASS' else 'FAIL' end as status,
        (select app_role::text from settings) as details
    union all
    select
        'release.readonly_role.exists' as check_name,
        case when exists (select 1 from readonly_role_exists) then 'PASS' else 'FAIL' end as status,
        (select readonly_role::text from settings) as details
    union all
    select
        'release.migration_role.exists' as check_name,
        case when exists (select 1 from migration_role_exists) then 'PASS' else 'FAIL' end as status,
        (select migration_role::text from settings) as details
    union all
    select
        'release.app_role.no_system_privilege_escalation' as check_name,
        case
            when not exists (select 1 from app_role) then 'FAIL'
            when exists (
                select 1
                from app_role
                where rolsuper or rolcreatedb or rolcreaterole or rolreplication or rolbypassrls
            ) then 'FAIL'
            else 'PASS'
        end as status,
        'app role must not be superuser, createdb, createrole, replication, or bypassrls' as details
    union all
    select
        'release.readonly_role.no_system_privilege_escalation' as check_name,
        case
            when not exists (select 1 from readonly_role) then 'FAIL'
            when exists (
                select 1
                from readonly_role
                where rolsuper or rolcreatedb or rolcreaterole or rolreplication or rolbypassrls
            ) then 'FAIL'
            else 'PASS'
        end as status,
        'readonly role must not be superuser, createdb, createrole, replication, or bypassrls' as details
    union all
    select
        'release.migration_role.no_superuser_escape' as check_name,
        case
            when not exists (select 1 from migration_role) then 'FAIL'
            when exists (
                select 1
                from migration_role
                where rolsuper or rolreplication or rolbypassrls
            ) then 'FAIL'
            else 'PASS'
        end as status,
        'migration role may own DDL but must not rely on superuser, replication, or bypassrls' as details
    union all
    select
        'release.app_readonly.no_schema_create_or_object_owner' as check_name,
        case
            when not exists (select 1 from schema_exists) then 'FAIL'
            when not exists (select 1 from app_role_exists) then 'FAIL'
            when not exists (select 1 from readonly_role_exists) then 'FAIL'
            when has_schema_privilege((select app_role from settings), (select schema_name from settings), 'CREATE') then 'FAIL'
            when has_schema_privilege((select readonly_role from settings), (select schema_name from settings), 'CREATE') then 'FAIL'
            when exists (select 1 from owned_by_app_or_readonly) then 'FAIL'
            else 'PASS'
        end as status,
        'app/readonly roles must not create schema objects or own release-managed objects' as details
    union all
    select
        'release.migration_role.schema_create' as check_name,
        case
            when not exists (select 1 from schema_exists) then 'FAIL'
            when not exists (select 1 from migration_role_exists) then 'FAIL'
            when has_schema_privilege((select migration_role from settings), (select schema_name from settings), 'CREATE') then 'PASS'
            else 'FAIL'
        end as status,
        'migration role must be able to create release-managed objects in the target schema' as details
    union all
    select
        'release.audit_log.append_only' as check_name,
        case
            when not exists (select 1 from app_role_exists) then 'FAIL'
            when coalesce(has_table_privilege((select app_role from settings), to_regclass(format('%I.%I', (select schema_name from settings), 'audit_log')), 'INSERT'), false)
             and coalesce(has_table_privilege((select app_role from settings), to_regclass(format('%I.%I', (select schema_name from settings), 'audit_log')), 'SELECT'), false)
             and not coalesce(has_table_privilege((select app_role from settings), to_regclass(format('%I.%I', (select schema_name from settings), 'audit_log')), 'UPDATE'), false)
             and not coalesce(has_table_privilege((select app_role from settings), to_regclass(format('%I.%I', (select schema_name from settings), 'audit_log')), 'DELETE'), false)
             and not coalesce(has_table_privilege((select app_role from settings), to_regclass(format('%I.%I', (select schema_name from settings), 'audit_log')), 'TRUNCATE'), false)
            then 'PASS'
            else 'FAIL'
        end as status,
        'requires SELECT/INSERT only on audit_log' as details
    union all
    select
        'release.audit_retention_cleanup.execute_only' as check_name,
        case
            when not exists (select 1 from app_role_exists) then 'FAIL'
            when to_regprocedure(format('%I.%I(timestamp with time zone,integer)', (select schema_name from settings), 'wp1_cleanup_audit_log_before')) is null then 'FAIL'
            when has_function_privilege(
                (select app_role from settings),
                to_regprocedure(format('%I.%I(timestamp with time zone,integer)', (select schema_name from settings), 'wp1_cleanup_audit_log_before')),
                'EXECUTE'
            )
            then 'PASS'
            else 'FAIL'
        end as status,
        'runtime role may execute controlled cleanup function but still must not DELETE audit_log directly' as details
    union all
    select
        'release.secret_local_store.encrypted_access_scoped' as check_name,
        case
            when not exists (select 1 from app_role_exists) then 'FAIL'
            when coalesce(has_table_privilege((select app_role from settings), to_regclass(format('%I.%I', (select schema_name from settings), 'secret_local_store')), 'SELECT'), false)
             and coalesce(has_table_privilege((select app_role from settings), to_regclass(format('%I.%I', (select schema_name from settings), 'secret_local_store')), 'INSERT'), false)
             and coalesce(has_table_privilege((select app_role from settings), to_regclass(format('%I.%I', (select schema_name from settings), 'secret_local_store')), 'UPDATE'), false)
             and not coalesce(has_table_privilege((select app_role from settings), to_regclass(format('%I.%I', (select schema_name from settings), 'secret_local_store')), 'DELETE'), false)
             and not coalesce(has_table_privilege((select app_role from settings), to_regclass(format('%I.%I', (select schema_name from settings), 'secret_local_store')), 'TRUNCATE'), false)
            then 'PASS'
            else 'FAIL'
        end as status,
        'runtime role may read/write encrypted local secret material through WP1 service code but must not DELETE/TRUNCATE it' as details
    union all
    select
        'release.wp5_context_policy_override.runtime_access' as check_name,
        case
            when not exists (select 1 from app_role_exists) then 'FAIL'
            when to_regclass(format('%I.%I', (select schema_name from settings), 'test_design_context_policy_override')) is null then 'FAIL'
            when coalesce(has_table_privilege((select app_role from settings), to_regclass(format('%I.%I', (select schema_name from settings), 'test_design_context_policy_override')), 'SELECT'), false)
             and coalesce(has_table_privilege((select app_role from settings), to_regclass(format('%I.%I', (select schema_name from settings), 'test_design_context_policy_override')), 'INSERT'), false)
             and coalesce(has_table_privilege((select app_role from settings), to_regclass(format('%I.%I', (select schema_name from settings), 'test_design_context_policy_override')), 'UPDATE'), false)
             and not coalesce(has_table_privilege((select app_role from settings), to_regclass(format('%I.%I', (select schema_name from settings), 'test_design_context_policy_override')), 'DELETE'), false)
             and not coalesce(has_table_privilege((select app_role from settings), to_regclass(format('%I.%I', (select schema_name from settings), 'test_design_context_policy_override')), 'TRUNCATE'), false)
            then 'PASS'
            else 'FAIL'
        end as status,
        'runtime role may create, approve and read WP5 context policy metadata but must not DELETE/TRUNCATE override records' as details
    union all
    select
        'release.wp5_context_policy_note.runtime_access' as check_name,
        case
            when not exists (select 1 from app_role_exists) then 'FAIL'
            when to_regclass(format('%I.%I', (select schema_name from settings), 'test_design_context_policy_note')) is null then 'FAIL'
            when coalesce(has_table_privilege((select app_role from settings), to_regclass(format('%I.%I', (select schema_name from settings), 'test_design_context_policy_note')), 'SELECT'), false)
             and coalesce(has_table_privilege((select app_role from settings), to_regclass(format('%I.%I', (select schema_name from settings), 'test_design_context_policy_note')), 'INSERT'), false)
             and not coalesce(has_table_privilege((select app_role from settings), to_regclass(format('%I.%I', (select schema_name from settings), 'test_design_context_policy_note')), 'UPDATE'), false)
             and not coalesce(has_table_privilege((select app_role from settings), to_regclass(format('%I.%I', (select schema_name from settings), 'test_design_context_policy_note')), 'DELETE'), false)
             and not coalesce(has_table_privilege((select app_role from settings), to_regclass(format('%I.%I', (select schema_name from settings), 'test_design_context_policy_note')), 'TRUNCATE'), false)
            then 'PASS'
            else 'FAIL'
        end as status,
        'runtime role may append and read WP5 context policy approval notes but must not UPDATE/DELETE/TRUNCATE note records' as details
    union all
    select
        'release.readonly_role.no_table_dml' as check_name,
        case
            when not exists (select 1 from readonly_role_exists) then 'FAIL'
            when exists (
                select 1
                from tables t
                where coalesce(has_table_privilege((select readonly_role from settings), t.oid, 'INSERT'), false)
                   or coalesce(has_table_privilege((select readonly_role from settings), t.oid, 'UPDATE'), false)
                   or coalesce(has_table_privilege((select readonly_role from settings), t.oid, 'DELETE'), false)
                   or coalesce(has_table_privilege((select readonly_role from settings), t.oid, 'TRUNCATE'), false)
            ) then 'FAIL'
            else 'PASS'
        end as status,
        'readonly role must not have INSERT/UPDATE/DELETE/TRUNCATE on release schema tables' as details
    union all
    select
        'release.readonly_role.no_local_secret_material' as check_name,
        case
            when not exists (select 1 from readonly_role_exists) then 'FAIL'
            when coalesce(has_table_privilege((select readonly_role from settings), to_regclass(format('%I.%I', (select schema_name from settings), 'secret_local_store')), 'SELECT'), false) then 'FAIL'
            else 'PASS'
        end as status,
        'readonly role must not SELECT encrypted local secret material' as details
)
select *
from checks;

select *
from wp1_release_role_checks
order by check_name;

do $$
declare
    failures int;
begin
    select count(*) into failures
    from wp1_release_role_checks
    where status = 'FAIL';

    if failures > 0 then
        raise exception 'WP1 release role validation failed; see release.* FAIL rows above';
    end if;
end
$$;
