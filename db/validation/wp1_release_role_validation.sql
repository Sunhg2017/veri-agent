-- Validate the real preprod/production application database role before release.
-- Usage:
--   psql "$WP1_RELEASE_DATABASE_URL" -v WP1_RELEASE_APP_ROLE=wp1_app -f db/validation/wp1_release_role_validation.sql

with role_input as (
    select :'WP1_RELEASE_APP_ROLE'::name as role_name
),
role_exists as (
    select r.role_name
    from role_input r
    join pg_roles p on p.rolname = r.role_name
),
checks as (
    select
        'release.role.exists' as check_name,
        case when exists (select 1 from role_exists) then 'PASS' else 'FAIL' end as status,
        (select role_name::text from role_input) as details
    union all
    select
        'release.audit_log.append_only' as check_name,
        case
            when not exists (select 1 from role_exists) then 'FAIL'
            when has_table_privilege((select role_name from role_input), format('%I.%I', current_schema(), 'audit_log'), 'INSERT')
             and has_table_privilege((select role_name from role_input), format('%I.%I', current_schema(), 'audit_log'), 'SELECT')
             and not has_table_privilege((select role_name from role_input), format('%I.%I', current_schema(), 'audit_log'), 'UPDATE')
             and not has_table_privilege((select role_name from role_input), format('%I.%I', current_schema(), 'audit_log'), 'DELETE')
             and not has_table_privilege((select role_name from role_input), format('%I.%I', current_schema(), 'audit_log'), 'TRUNCATE')
            then 'PASS'
            else 'FAIL'
        end as status,
        'requires SELECT/INSERT only on audit_log' as details
    union all
    select
        'release.audit_retention_cleanup.execute_only' as check_name,
        case
            when not exists (select 1 from role_exists) then 'FAIL'
            when to_regprocedure(current_schema() || '.wp1_cleanup_audit_log_before(timestamp with time zone,integer)') is null then 'FAIL'
            when has_function_privilege(
                (select role_name from role_input),
                to_regprocedure(current_schema() || '.wp1_cleanup_audit_log_before(timestamp with time zone,integer)'),
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
            when not exists (select 1 from role_exists) then 'FAIL'
            when has_table_privilege((select role_name from role_input), format('%I.%I', current_schema(), 'secret_local_store'), 'SELECT')
             and has_table_privilege((select role_name from role_input), format('%I.%I', current_schema(), 'secret_local_store'), 'INSERT')
             and has_table_privilege((select role_name from role_input), format('%I.%I', current_schema(), 'secret_local_store'), 'UPDATE')
             and not has_table_privilege((select role_name from role_input), format('%I.%I', current_schema(), 'secret_local_store'), 'DELETE')
             and not has_table_privilege((select role_name from role_input), format('%I.%I', current_schema(), 'secret_local_store'), 'TRUNCATE')
            then 'PASS'
            else 'FAIL'
        end as status,
        'runtime role may read/write encrypted local secret material through WP1 service code but must not DELETE/TRUNCATE it' as details
)
select *
from checks;

do $$
declare
    failures int;
begin
    with role_input as (
        select :'WP1_RELEASE_APP_ROLE'::name as role_name
    ),
    role_exists as (
        select r.role_name
        from role_input r
        join pg_roles p on p.rolname = r.role_name
    ),
    failed as (
        select 1
        where not exists (select 1 from role_exists)
        union all
        select 1
        where exists (select 1 from role_exists)
          and (
            not has_table_privilege((select role_name from role_input), format('%I.%I', current_schema(), 'audit_log'), 'INSERT')
            or not has_table_privilege((select role_name from role_input), format('%I.%I', current_schema(), 'audit_log'), 'SELECT')
            or has_table_privilege((select role_name from role_input), format('%I.%I', current_schema(), 'audit_log'), 'UPDATE')
            or has_table_privilege((select role_name from role_input), format('%I.%I', current_schema(), 'audit_log'), 'DELETE')
            or has_table_privilege((select role_name from role_input), format('%I.%I', current_schema(), 'audit_log'), 'TRUNCATE')
          )
        union all
        select 1
        where exists (select 1 from role_exists)
          and (
            to_regprocedure(current_schema() || '.wp1_cleanup_audit_log_before(timestamp with time zone,integer)') is null
            or not has_function_privilege(
                (select role_name from role_input),
                to_regprocedure(current_schema() || '.wp1_cleanup_audit_log_before(timestamp with time zone,integer)'),
                'EXECUTE'
            )
          )
        union all
        select 1
        where exists (select 1 from role_exists)
          and (
            not has_table_privilege((select role_name from role_input), format('%I.%I', current_schema(), 'secret_local_store'), 'SELECT')
            or not has_table_privilege((select role_name from role_input), format('%I.%I', current_schema(), 'secret_local_store'), 'INSERT')
            or not has_table_privilege((select role_name from role_input), format('%I.%I', current_schema(), 'secret_local_store'), 'UPDATE')
            or has_table_privilege((select role_name from role_input), format('%I.%I', current_schema(), 'secret_local_store'), 'DELETE')
            or has_table_privilege((select role_name from role_input), format('%I.%I', current_schema(), 'secret_local_store'), 'TRUNCATE')
          )
    )
    select count(*) into failures from failed;

    if failures > 0 then
        raise exception 'WP1 release role validation failed for %', :'WP1_RELEASE_APP_ROLE';
    end if;
end
$$;
