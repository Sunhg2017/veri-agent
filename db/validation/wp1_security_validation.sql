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
