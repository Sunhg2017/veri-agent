-- WP1 audit immutability guard.

create or replace function wp1_prevent_audit_log_mutation()
returns trigger
language plpgsql
as $$
begin
    raise exception 'audit_log is append-only and cannot be %', tg_op
        using errcode = '55000';
end;
$$;

drop trigger if exists trg_audit_log_prevent_update_delete on audit_log;

create trigger trg_audit_log_prevent_update_delete
before update or delete on audit_log
for each row
execute function wp1_prevent_audit_log_mutation();
