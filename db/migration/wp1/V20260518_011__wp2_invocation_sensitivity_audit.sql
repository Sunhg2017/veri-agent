-- Add sensitivity-level audit dimension for WP2 invocation logs.
-- Existing rows default to INTERNAL because older WP2 requests did not carry this field.

alter table ma_invocation_log
    add column if not exists sensitivity_level varchar(32) not null default 'INTERNAL';

do $$
begin
    if not exists (
        select 1
        from pg_constraint
        where conname = 'ck_ma_invocation_sensitivity'
          and conrelid = 'ma_invocation_log'::regclass
    ) then
        alter table ma_invocation_log
            add constraint ck_ma_invocation_sensitivity
            check (sensitivity_level in ('PUBLIC', 'INTERNAL', 'CONFIDENTIAL', 'STRICT'));
    end if;
end $$;

create index if not exists idx_ma_invocation_sensitivity_time
    on ma_invocation_log (sensitivity_level, created_at desc);
