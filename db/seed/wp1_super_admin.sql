-- Seed the first WP1 SuperAdmin from deployment-time data.
-- Run with psql variables:
--   WP1_SUPER_ADMIN_USERNAME
--   WP1_SUPER_ADMIN_PASSWORD
--   WP1_SUPER_ADMIN_DISPLAY_NAME
--   WP1_SUPER_ADMIN_EMAIL

\set ON_ERROR_STOP on

\if :{?WP1_SUPER_ADMIN_USERNAME}
\else
  \echo 'missing required psql variable: WP1_SUPER_ADMIN_USERNAME'
  \quit 64
\endif

\if :{?WP1_SUPER_ADMIN_PASSWORD}
\else
  \echo 'missing required psql variable: WP1_SUPER_ADMIN_PASSWORD'
  \quit 64
\endif

\if :{?WP1_SUPER_ADMIN_DISPLAY_NAME}
\else
  \set WP1_SUPER_ADMIN_DISPLAY_NAME 'SuperAdmin'
\endif

\if :{?WP1_SUPER_ADMIN_EMAIL}
\else
  \set WP1_SUPER_ADMIN_EMAIL ''
\endif

select
    set_config('seed.super_admin.username', :'WP1_SUPER_ADMIN_USERNAME', false) as seed_username,
    set_config('seed.super_admin.password', :'WP1_SUPER_ADMIN_PASSWORD', false) as seed_password,
    set_config('seed.super_admin.display_name', :'WP1_SUPER_ADMIN_DISPLAY_NAME', false) as seed_display_name,
    set_config('seed.super_admin.email', :'WP1_SUPER_ADMIN_EMAIL', false) as seed_email
\gset

do $$
declare
    v_username text := btrim(current_setting('seed.super_admin.username'));
    v_password text := current_setting('seed.super_admin.password');
    v_display_name text := nullif(btrim(current_setting('seed.super_admin.display_name')), '');
    v_email text := nullif(btrim(current_setting('seed.super_admin.email')), '');
    v_existing_super_admin text;
    v_role_id uuid;
    v_user_id uuid;
    v_trace_id text := 'seed_' || replace(gen_random_uuid()::text, '-', '');
begin
    if v_username !~ '^[A-Za-z0-9_-]{3,64}$' then
        raise exception 'WP1_SUPER_ADMIN_USERNAME must be 3-64 chars and contain only letters, numbers, underscore, or hyphen';
    end if;
    if length(v_password) < 10 then
        raise exception 'WP1_SUPER_ADMIN_PASSWORD must be at least 10 characters';
    end if;
    if v_display_name is null then
        v_display_name := v_username;
    end if;

    select u.username
      into v_existing_super_admin
      from iam_user u
      join rbac_role_binding b on b.subject_type = 'USER'
       and b.subject_id = u.id
       and b.role_code = 'SuperAdmin'
       and b.scope_type = 'PLATFORM'
       and b.scope_id is null
       and b.status = 'ENABLED'
       and b.deleted_at is null
     where u.status = 'ENABLED'
       and u.deleted_at is null
     order by u.created_at asc
     limit 1;

    if v_existing_super_admin is not null then
        if v_existing_super_admin = v_username then
            raise notice 'SuperAdmin "%" already exists; seed skipped', v_username;
            return;
        end if;
        raise exception 'SuperAdmin already exists as "%"; seed aborted', v_existing_super_admin;
    end if;

    if exists (
        select 1
          from iam_user
         where username = v_username
           and deleted_at is null
    ) then
        raise exception 'User "%" already exists without SuperAdmin role; seed aborted', v_username;
    end if;

    select id
      into v_role_id
      from rbac_role
     where code = 'SuperAdmin'
       and status = 'ENABLED'
       and deleted_at is null;

    if v_role_id is null then
        raise exception 'SuperAdmin role is missing; run WP1 permission and role seed migrations first';
    end if;

    insert into iam_user (
        username,
        password_hash,
        display_name,
        email,
        status,
        must_change_password
    )
    values (
        v_username,
        crypt(v_password, gen_salt('bf', 12)),
        v_display_name,
        v_email,
        'ENABLED',
        true
    )
    returning id into v_user_id;

    update iam_user
       set created_by = v_user_id,
           updated_by = v_user_id
     where id = v_user_id;

    insert into rbac_role_binding (
        subject_type,
        subject_id,
        role_id,
        role_code,
        scope_type,
        scope_id,
        status,
        created_by,
        updated_by
    )
    values (
        'USER',
        v_user_id,
        v_role_id,
        'SuperAdmin',
        'PLATFORM',
        null,
        'ENABLED',
        v_user_id,
        v_user_id
    );

    insert into audit_log (
        trace_id,
        actor_type,
        actor_user_id,
        action,
        resource_type,
        resource_id,
        scope_type,
        scope_id,
        result,
        after_json
    )
    values
    (
        v_trace_id,
        'SYSTEM',
        v_user_id,
        'USER_CREATE',
        'iam_user',
        v_user_id::text,
        'PLATFORM',
        null,
        'SUCCESS',
        jsonb_build_object('seed_script', true, 'username', v_username, 'must_change_password', true)
    ),
    (
        v_trace_id,
        'SYSTEM',
        v_user_id,
        'SUPER_ADMIN_INIT',
        'rbac_role_binding',
        v_user_id::text,
        'PLATFORM',
        null,
        'SUCCESS',
        jsonb_build_object('seed_script', true, 'role', 'SuperAdmin')
    );

    raise notice 'SuperAdmin "%" initialized with must_change_password=true; user id %', v_username, v_user_id;
end
$$;

reset seed.super_admin.username;
reset seed.super_admin.password;
reset seed.super_admin.display_name;
reset seed.super_admin.email;
