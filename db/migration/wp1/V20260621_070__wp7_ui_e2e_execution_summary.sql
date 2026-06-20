alter table ui_e2e_run
    add column if not exists execution_summary_json jsonb not null default '{}'::jsonb;

do
$$
begin
    if not exists (
        select 1
        from pg_constraint
        where conname = 'ck_ui_e2e_run_execution_summary_json'
          and conrelid = 'ui_e2e_run'::regclass
    ) then
        alter table ui_e2e_run
            add constraint ck_ui_e2e_run_execution_summary_json
                check (jsonb_typeof(execution_summary_json) = 'object');
    end if;
end
$$;

comment on column ui_e2e_run.execution_summary_json is 'Aggregate execution summary including browser matrix and visual regression metadata.';
