-- Runtime grants for WP6 generation task and automation case tables created after the base WP1 role policy.

do $$
begin
    if to_regrole('wp1_app') is not null then
        grant select, insert, update on
            api_automation_generation_task,
            api_automation_case
        to wp1_app;
    end if;

    if to_regrole('wp1_readonly') is not null then
        grant select on
            api_automation_generation_task,
            api_automation_case
        to wp1_readonly;
    end if;

    if to_regrole('wp1_migration') is not null then
        grant all privileges on
            api_automation_generation_task,
            api_automation_case
        to wp1_migration;
    end if;
end
$$;
