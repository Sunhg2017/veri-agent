-- WP1 security validation.
-- Run after DDL migrations and seed migrations. Every query returns: check_name, status, details.
-- Replace the role names in the privilege checks if your environment uses different DB roles.

with expected(table_name, column_name) as (
    values
        ('iam_user','password_hash'),
        ('base_environment_variable','value_kind'),
        ('base_environment_variable','plain_value'),
        ('base_environment_variable','secret_ref'),
        ('base_environment_variable','secret_provider'),
        ('base_environment_variable','secret_version'),
        ('base_environment_variable','masked_value'),
        ('base_config','value_kind'),
        ('base_config','value_json'),
        ('base_config','secret_ref'),
        ('base_config','masked_value'),
        ('secret_provider','config_json'),
        ('secret_reference','provider_id'),
        ('secret_reference','secret_ref'),
        ('secret_reference','scope_type'),
        ('secret_reference','scope_id'),
        ('secret_reference','purpose'),
        ('secret_reference','masked_value'),
        ('secret_reference','secret_version'),
        ('secret_reference','status'),
        ('secret_local_store','secret_ref_id'),
        ('secret_local_store','cipher_text'),
        ('secret_local_store','iv'),
        ('secret_local_store','auth_tag'),
        ('secret_local_store','algorithm'),
        ('secret_local_store','master_key_version'),
        ('secret_local_store','status')
),
missing as (
    select e.table_name || '.' || e.column_name as item
    from expected e
    left join information_schema.columns c
        on c.table_schema = current_schema()
       and c.table_name = e.table_name
       and c.column_name = e.column_name
    where c.column_name is null
)
select
    'security.sensitive_columns_exist' as check_name,
    case when count(*) = 0 then 'PASS' else 'FAIL' end as status,
    coalesce(string_agg(item, ', ' order by item), 'sensitive-value columns are split into hash/plain/secret/ref/mask/cipher fields') as details
from missing;

select
    'security.secret_local_store_exists' as check_name,
    case when to_regclass(current_schema() || '.secret_local_store') is not null then 'PASS' else 'FAIL' end as status,
    coalesce(to_regclass(current_schema() || '.secret_local_store')::text, 'missing') as details;

select
    'security.audit_log_exists' as check_name,
    case when to_regclass(current_schema() || '.audit_log') is not null then 'PASS' else 'FAIL' end as status,
    coalesce(to_regclass(current_schema() || '.audit_log')::text, 'missing') as details;

select
    'security.audit_log_archive_exists' as check_name,
    case when to_regclass(current_schema() || '.audit_log_archive') is not null then 'PASS' else 'FAIL' end as status,
    coalesce(to_regclass(current_schema() || '.audit_log_archive')::text, 'missing') as details;

with found as (
    select table_name || '.tenant_id' as item
    from information_schema.columns
    where table_schema = current_schema()
      and column_name = 'tenant_id'
)
select
    'security.no_tenant_id_columns' as check_name,
    case when count(*) = 0 then 'PASS' else 'FAIL' end as status,
    coalesce(string_agg(item, ', ' order by item), 'no tenant_id columns remain in security/business tables') as details
from found;

with expected(table_name, index_name) as (
    values
        ('audit_log','idx_audit_log_time'),
        ('audit_log','idx_audit_log_resource'),
        ('audit_log','idx_audit_log_actor_time'),
        ('audit_log','idx_audit_log_trace'),
        ('audit_log_archive','idx_audit_log_archive_created_at'),
        ('audit_log_archive','idx_audit_log_archive_resource'),
        ('audit_log_archive','idx_audit_log_archive_trace'),
        ('audit_outbox','idx_audit_outbox_pending'),
        ('secret_local_store','uk_secret_local_store_ref'),
        ('secret_local_store','idx_secret_local_store_status'),
        ('secret_reference','uk_secret_reference_ref'),
        ('secret_reference','idx_secret_reference_scope')
),
missing as (
    select e.table_name || '.' || e.index_name as item
    from expected e
    left join pg_indexes i
        on i.schemaname = current_schema()
       and i.tablename = e.table_name
       and i.indexname = e.index_name
    where i.indexname is null
)
select
    'security.audit_secret_indexes_exist' as check_name,
    case when count(*) = 0 then 'PASS' else 'FAIL' end as status,
    coalesce(string_agg(item, ', ' order by item), 'audit and secret lookup indexes exist') as details
from missing;

with bad as (
    select key
    from base_environment_variable
    where deleted_at is null
      and value_kind = 'PLAIN'
      and key ~* '(password|passwd|pwd|secret|token|api[_-]?key|cookie|credential|private[_-]?key)'
)
select
    'security.no_sensitive_env_key_saved_as_plain' as check_name,
    case when count(*) = 0 then 'PASS' else 'FAIL' end as status,
    coalesce(string_agg(key, ', ' order by key), 'no suspicious sensitive environment variable key is saved as PLAIN') as details
from bad;

with bad as (
    select config_key
    from base_config
    where deleted_at is null
      and value_kind = 'PLAIN'
      and config_key ~* '(password|passwd|pwd|secret|token|api[_.-]?key|cookie|credential|private[_.-]?key)'
      and config_key not in ('secret.default_provider', 'session.access_token_ttl_minutes')
      and value_json::text !~ '("secret-ref:|\\*\\*\\*|\\$\\{[A-Za-z0-9_]+\\})'
)
select
    'security.no_sensitive_config_key_saved_as_plain' as check_name,
    case when count(*) = 0 then 'PASS' else 'FAIL' end as status,
    coalesce(string_agg(config_key, ', ' order by config_key), 'no suspicious sensitive config key is saved as readable plain JSON') as details
from bad;

with expected(column_name) as (
    values ('before_json'), ('after_json'), ('diff_json'), ('reason')
),
missing as (
    select e.column_name
    from expected e
    left join information_schema.columns c
        on c.table_schema = current_schema()
       and c.table_name = 'audit_log'
       and c.column_name = e.column_name
    where c.column_name is null
)
select
    'security.audit_diff_columns_exist' as check_name,
    case when count(*) = 0 then 'PASS' else 'FAIL' end as status,
    coalesce(string_agg(column_name, ', ' order by column_name), 'audit_log can store before/after/diff/reason for success, failed, and denied events') as details
from missing;

with bad as (
    select id::text as id
    from base_environment_variable
    where deleted_at is null
      and value_kind in ('SECRET','SECRET_REF')
      and plain_value is not null
)
select
    'security.secret_env_plain_value_empty' as check_name,
    case when count(*) = 0 then 'PASS' else 'FAIL' end as status,
    coalesce(string_agg(id, ', ' order by id), 'SECRET/SECRET_REF variables have null plain_value') as details
from bad;

with bad as (
    select id::text as id
    from secret_local_store
    where cipher_text is null
       or btrim(cipher_text) = ''
       or iv is null
       or btrim(iv) = ''
       or auth_tag is null
       or btrim(auth_tag) = ''
       or master_key_version is null
       or btrim(master_key_version) = ''
)
select
    'security.secret_local_cipher_material_present' as check_name,
    case when count(*) = 0 then 'PASS' else 'FAIL' end as status,
    coalesce(string_agg(id, ', ' order by id), 'local secret rows have cipher text, iv, auth tag, and key version') as details
from bad;

with app_roles(role_name) as (
    values
        ('wp1_app'),
        ('veri_agent_app')
),
audit_privileges as (
    select
        r.role_name,
        has_table_privilege(r.role_name, format('%I.%I', current_schema(), 'audit_log'), 'UPDATE') as can_update,
        has_table_privilege(r.role_name, format('%I.%I', current_schema(), 'audit_log'), 'DELETE') as can_delete,
        has_table_privilege(r.role_name, format('%I.%I', current_schema(), 'audit_log'), 'TRUNCATE') as can_truncate
    from app_roles r
    where exists (select 1 from pg_roles pr where pr.rolname = r.role_name)
),
bad as (
    select role_name || '(update=' || can_update || ', delete=' || can_delete || ', truncate=' || can_truncate || ')' as item
    from audit_privileges
    where can_update or can_delete or can_truncate
)
select
    'security.audit_log_app_role_append_only' as check_name,
    case
        when not exists (select 1 from audit_privileges) then 'WARN'
        when count(*) = 0 then 'PASS'
        else 'FAIL'
    end as status,
    case
        when not exists (select 1 from audit_privileges) then 'no known app DB role found; replace wp1_app/veri_agent_app in this script with your real application role'
        else coalesce(string_agg(item, ', ' order by item), 'known app DB roles cannot UPDATE/DELETE/TRUNCATE audit_log')
    end as details
from bad;

with found as (
    select p.oid
    from pg_proc p
    join pg_namespace n on n.oid = p.pronamespace
    where n.nspname = current_schema()
      and p.proname = 'wp1_cleanup_audit_log_before'
      and p.pronargs = 2
      and p.proargtypes[0] = 'timestamp with time zone'::regtype
      and p.proargtypes[1] = 'integer'::regtype
)
select
    'security.audit_retention_cleanup_function_exists' as check_name,
    case when exists (select 1 from found) then 'PASS' else 'FAIL' end as status,
    coalesce((select oid::regprocedure::text from found limit 1), 'missing') as details;

with app_roles(role_name) as (
    values
        ('wp1_app'),
        ('veri_agent_app')
),
fn as (
    select p.oid
    from pg_proc p
    join pg_namespace n on n.oid = p.pronamespace
    where n.nspname = current_schema()
      and p.proname = 'wp1_cleanup_audit_log_before'
      and p.pronargs = 2
      and p.proargtypes[0] = 'timestamp with time zone'::regtype
      and p.proargtypes[1] = 'integer'::regtype
    limit 1
),
privileges as (
    select
        r.role_name,
        has_function_privilege(r.role_name, (select oid from fn), 'EXECUTE') as can_execute
    from app_roles r
    where exists (select 1 from pg_roles pr where pr.rolname = r.role_name)
      and exists (select 1 from fn)
),
bad as (
    select role_name || '(execute=' || can_execute || ')' as item
    from privileges
    where not can_execute
)
select
    'security.audit_retention_cleanup_execute_grant' as check_name,
    case
        when not exists (select 1 from fn) then 'FAIL'
        when not exists (select 1 from privileges) then 'WARN'
        when count(*) = 0 then 'PASS'
        else 'FAIL'
    end as status,
    case
        when not exists (select 1 from fn) then 'wp1_cleanup_audit_log_before is missing'
        when not exists (select 1 from privileges) then 'no known app DB role found; grant EXECUTE to the real app role through the runtime policy'
        else coalesce(string_agg(item, ', ' order by item), 'known app DB roles can execute only the controlled audit retention function')
    end as details
from bad;

drop table if exists wp1_audit_retention_validation_result;
create temporary table wp1_audit_retention_validation_result (
    deleted integer not null
) on commit preserve rows;

insert into audit_log (
    id,
    actor_type,
    action,
    resource_type,
    resource_id,
    scope_type,
    result,
    created_at
)
values (
    '00000000-0000-0000-0000-000000019001',
    'SYSTEM',
    'validation.audit_retention_old',
    'AUDIT_LOG',
    'validation-old',
    'PLATFORM',
    'SUCCESS',
    now() - interval '400 days'
)
on conflict (id) do nothing;

insert into audit_log (
    id,
    actor_type,
    action,
    resource_type,
    resource_id,
    scope_type,
    result,
    created_at
)
values (
    '00000000-0000-0000-0000-000000019002',
    'SYSTEM',
    'validation.audit_retention_fresh',
    'AUDIT_LOG',
    'validation-fresh',
    'PLATFORM',
    'SUCCESS',
    now() - interval '10 days'
)
on conflict (id) do nothing;

insert into wp1_audit_retention_validation_result (deleted)
select wp1_cleanup_audit_log_before(now() - interval '365 days', 10);

select
    'security.audit_retention_cleanup_deletes_only_expired' as check_name,
    case
        when r.deleted >= 1
         and not exists (
             select 1
             from audit_log
             where id = '00000000-0000-0000-0000-000000019001'
         )
         and exists (
             select 1
             from audit_log_archive
             where id = '00000000-0000-0000-0000-000000019001'
         )
         and exists (
             select 1
             from audit_log
             where id = '00000000-0000-0000-0000-000000019002'
         )
         and exists (
             select 1
             from audit_log
             where action = 'audit.retention_cleanup'
               and after_json ? 'deleted'
         )
        then 'PASS'
        else 'FAIL'
    end as status,
    'deleted=' || r.deleted || ', freshRowKept=' || exists (
        select 1
        from audit_log
        where id = '00000000-0000-0000-0000-000000019002'
    ) || ', oldRowArchived=' || exists (
        select 1
        from audit_log_archive
        where id = '00000000-0000-0000-0000-000000019001'
    ) as details
from wp1_audit_retention_validation_result r;

with app_roles(role_name) as (
    values
        ('wp1_app'),
        ('veri_agent_app')
),
history_privileges as (
    select
        r.role_name,
        has_table_privilege(r.role_name, format('%I.%I', current_schema(), 'asset_version_history'), 'SELECT') as can_select,
        has_table_privilege(r.role_name, format('%I.%I', current_schema(), 'asset_version_history'), 'INSERT') as can_insert,
        has_table_privilege(r.role_name, format('%I.%I', current_schema(), 'asset_version_history'), 'UPDATE') as can_update,
        has_table_privilege(r.role_name, format('%I.%I', current_schema(), 'asset_version_history'), 'DELETE') as can_delete,
        has_table_privilege(r.role_name, format('%I.%I', current_schema(), 'asset_version_history'), 'TRUNCATE') as can_truncate
    from app_roles r
    where exists (select 1 from pg_roles pr where pr.rolname = r.role_name)
      and to_regclass(current_schema() || '.asset_version_history') is not null
),
bad as (
    select role_name || '(select=' || can_select || ', insert=' || can_insert || ', update=' || can_update || ', delete=' || can_delete || ', truncate=' || can_truncate || ')' as item
    from history_privileges
    where not can_select or not can_insert or can_update or can_delete or can_truncate
)
select
    'security.asset_version_history_app_role_append_only' as check_name,
    case
        when not exists (select 1 from history_privileges) then 'WARN'
        when count(*) = 0 then 'PASS'
        else 'FAIL'
    end as status,
    case
        when not exists (select 1 from history_privileges) then 'no known app DB role found or asset_version_history missing; replace wp1_app/veri_agent_app with your real application role'
        else coalesce(string_agg(item, ', ' order by item), 'known app DB roles can SELECT/INSERT but cannot UPDATE/DELETE/TRUNCATE asset_version_history')
    end as details
from bad;

with app_roles(role_name) as (
    values
        ('wp1_app'),
        ('veri_agent_app')
),
runtime_tables(table_name) as (
    values
        ('base_department'),
        ('base_department_manager'),
        ('base_department_member'),
        ('iam_user'),
        ('iam_session'),
        ('base_project'),
        ('base_project_department'),
        ('base_project_member'),
        ('base_application'),
        ('base_environment'),
        ('base_environment_variable'),
        ('base_config'),
        ('rbac_role'),
        ('rbac_role_permission'),
        ('rbac_role_binding'),
        ('ma_model_provider'),
        ('ma_prompt_template'),
        ('ma_invocation_log'),
        ('asset_requirement'),
        ('asset_api'),
        ('asset_page'),
        ('asset_business_flow'),
        ('asset_test_case'),
        ('asset_test_step'),
        ('asset_link'),
        ('document_input_field_mapping'),
        ('document_input_source'),
        ('document_input_import'),
        ('document_input_candidate'),
        ('document_input_webhook_event'),
        ('audit_outbox')
),
role_table_privileges as (
    select
        r.role_name,
        t.table_name,
        has_table_privilege(r.role_name, format('%I.%I', current_schema(), t.table_name), 'SELECT') as can_select,
        has_table_privilege(r.role_name, format('%I.%I', current_schema(), t.table_name), 'INSERT') as can_insert,
        has_table_privilege(r.role_name, format('%I.%I', current_schema(), t.table_name), 'UPDATE') as can_update
    from app_roles r
    cross join runtime_tables t
    where exists (select 1 from pg_roles pr where pr.rolname = r.role_name)
),
bad as (
    select role_name || '.' || table_name || '(select=' || can_select || ', insert=' || can_insert || ', update=' || can_update || ')' as item
    from role_table_privileges
    where not (can_select and can_insert and can_update)
)
select
    'security.app_role_runtime_table_dml' as check_name,
    case
        when not exists (select 1 from role_table_privileges) then 'WARN'
        when count(*) = 0 then 'PASS'
        else 'FAIL'
    end as status,
    case
        when not exists (select 1 from role_table_privileges) then 'no known app DB role found; replace wp1_app/veri_agent_app in this script with your real application role'
        else coalesce(string_agg(item, ', ' order by item), 'known app DB roles have SELECT/INSERT/UPDATE on WP1-WP4 runtime tables')
    end as details
from bad;

with audit_triggers as (
    select tgname
    from pg_trigger t
    join pg_class c on c.oid = t.tgrelid
    join pg_namespace n on n.oid = c.relnamespace
    where n.nspname = current_schema()
      and c.relname = 'audit_log'
      and not t.tgisinternal
),
rules as (
    select rulename
    from pg_rules
    where schemaname = current_schema()
      and tablename = 'audit_log'
),
has_protection as (
    select count(*) as protection_count from audit_triggers
    union all
    select count(*) from rules
)
select
    'security.audit_log_mutation_protection_hint' as check_name,
    case when sum(protection_count) > 0 then 'PASS' else 'WARN' end as status,
    case when sum(protection_count) > 0
        then 'audit_log has trigger/rule protection candidates; still verify app role grants'
        else 'no audit_log trigger/rule found; ensure DB grants or operational controls prevent UPDATE/DELETE by app role'
    end as details
from has_protection;
