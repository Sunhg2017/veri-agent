-- WP1 audit retention policy.
-- Keeps runtime audit_log append-only while allowing controlled retention cleanup
-- through a SECURITY DEFINER maintenance function.

with configs(config_key, value_json) as (
    values
        ('audit.retention_cleanup_enabled','false'::jsonb),
        ('audit.retention_min_days','30'::jsonb),
        ('audit.retention_cleanup_batch_size','1000'::jsonb)
)
insert into base_config (scope_type, scope_id, config_key, value_kind, value_json, status)
select 'SYSTEM', null, c.config_key, 'PLAIN', c.value_json, 'ENABLED'
from configs c
where not exists (
    select 1
    from base_config bc
    where bc.scope_type = 'SYSTEM'
      and bc.scope_id is null
      and bc.config_key = c.config_key
      and bc.deleted_at is null
);

create table if not exists audit_log_archive (
    like audit_log including defaults including constraints
);

alter table audit_log_archive
    add column if not exists archived_at timestamptz not null default now();

do $$
begin
    if not exists (
        select 1
        from pg_constraint
        where conrelid = 'audit_log_archive'::regclass
          and conname = 'pk_audit_log_archive'
    ) then
        alter table audit_log_archive
            add constraint pk_audit_log_archive primary key (id);
    end if;
end
$$;

create index if not exists idx_audit_log_archive_created_at on audit_log_archive (created_at desc);
create index if not exists idx_audit_log_archive_resource on audit_log_archive (resource_type, resource_id);
create index if not exists idx_audit_log_archive_trace on audit_log_archive (trace_id);

create or replace function wp1_prevent_audit_log_mutation()
returns trigger
language plpgsql
as $$
begin
    if tg_op = 'DELETE' and current_setting('veri_agent.audit_retention_maintenance', true) = 'on' then
        return old;
    end if;

    raise exception 'audit_log is append-only and cannot be %', tg_op
        using errcode = '55000';
end;
$$;

create or replace function wp1_cleanup_audit_log_before(p_cutoff timestamptz, p_batch_size integer default 1000)
returns integer
language plpgsql
security definer
set search_path = public
as $$
declare
    v_batch_size integer := greatest(1, least(coalesce(p_batch_size, 1000), 10000));
    v_deleted integer := 0;
begin
    if p_cutoff is null then
        raise exception 'audit retention cutoff is required'
            using errcode = '22004';
    end if;

    if p_cutoff > now() - interval '30 days' then
        raise exception 'audit retention cutoff must be at least 30 days before now'
            using errcode = '22023';
    end if;

    perform set_config('veri_agent.audit_retention_maintenance', 'on', true);

    with victims as (
        select id
        from audit_log
        where created_at < p_cutoff
        order by created_at asc
        limit v_batch_size
    )
    insert into audit_log_archive (
        id,
        trace_id,
        idempotency_key,
        actor_type,
        actor_user_id,
        actor_service,
        actor_ip,
        user_agent,
        action,
        resource_type,
        resource_id,
        scope_type,
        scope_id,
        result,
        before_json,
        after_json,
        diff_json,
        reason,
        created_at,
        archived_at
    )
    select
        a.id,
        a.trace_id,
        a.idempotency_key,
        a.actor_type,
        a.actor_user_id,
        a.actor_service,
        a.actor_ip,
        a.user_agent,
        a.action,
        a.resource_type,
        a.resource_id,
        a.scope_type,
        a.scope_id,
        a.result,
        a.before_json,
        a.after_json,
        a.diff_json,
        a.reason,
        a.created_at,
        now()
    from audit_log a
    join victims v on v.id = a.id
    on conflict (id) do nothing;

    with victims as (
        select id
        from audit_log
        where created_at < p_cutoff
        order by created_at asc
        limit v_batch_size
    )
    delete from audit_log a
    using victims v
    where a.id = v.id
      and exists (
          select 1
          from audit_log_archive ar
          where ar.id = a.id
      );

    get diagnostics v_deleted = row_count;

    perform set_config('veri_agent.audit_retention_maintenance', 'off', true);

    insert into audit_log (
        trace_id,
        actor_type,
        action,
        resource_type,
        resource_id,
        scope_type,
        result,
        after_json,
        reason
    )
    values (
        null,
        'SYSTEM',
        'audit.retention_cleanup',
        'AUDIT_LOG',
        'audit.retention_cleanup',
        'PLATFORM',
        'SUCCESS',
        jsonb_build_object(
            'cutoff', p_cutoff,
            'deleted', v_deleted,
            'batchSize', v_batch_size
        ),
        'WP1 audit retention cleanup'
    );

    return v_deleted;
exception
    when others then
        perform set_config('veri_agent.audit_retention_maintenance', 'off', true);
        raise;
end;
$$;

revoke all on function wp1_cleanup_audit_log_before(timestamptz, integer) from public;

do $$
declare
    role_name text;
begin
    foreach role_name in array array['wp1_app', 'veri_agent_app']
    loop
        if exists (select 1 from pg_roles where rolname = role_name) then
            execute format(
                'grant execute on function wp1_cleanup_audit_log_before(timestamptz, integer) to %I',
                role_name
            );
        end if;
    end loop;
end
$$;

comment on function wp1_cleanup_audit_log_before(timestamptz, integer)
is 'Deletes audit_log rows older than cutoff in bounded batches through an audited SECURITY DEFINER retention path. Runtime app roles keep no direct DELETE permission on audit_log.';

comment on table audit_log_archive
is 'Archive copy for audit_log rows removed from the online table by the controlled WP1 retention function.';
