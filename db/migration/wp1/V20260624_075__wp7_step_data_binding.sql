alter table ui_e2e_scene_step
    add column if not exists data_binding_json jsonb not null default '{}'::jsonb;

alter table ui_e2e_scene_step
    drop constraint if exists ck_ui_e2e_scene_step_json;

alter table ui_e2e_scene_step
    add constraint ck_ui_e2e_scene_step_json check (
        jsonb_typeof(action_summary_json) = 'object'
        and jsonb_typeof(locator_strategy_json) = 'object'
        and jsonb_typeof(assertion_summary_json) = 'object'
        and jsonb_typeof(wait_policy_json) = 'object'
        and jsonb_typeof(data_binding_json) = 'object'
    );

comment on column ui_e2e_scene_step.data_binding_json is 'Optional WP8 data-set binding contract used for step-level runtime placeholder injection.';
