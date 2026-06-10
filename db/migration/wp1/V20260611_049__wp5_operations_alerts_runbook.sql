-- WP5 fine-grained queue alert subscriptions and operations runbook support.

create table if not exists test_design_queue_alert_subscription (
    id uuid primary key default gen_random_uuid(),
    project_id varchar(64) not null,
    prompt_key varchar(128),
    alert_type varchar(64) not null,
    channel varchar(32) not null,
    target_ref varchar(180) not null,
    threshold_seconds integer,
    enabled boolean not null default true,
    created_by varchar(128),
    updated_by varchar(128),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint ck_test_design_queue_alert_subscription_alert_type check (alert_type in (
        'GENERATION_QUEUE_LAG',
        'GENERATION_TIMEOUT',
        'PUBLISH_QUEUE_LAG',
        'PUBLISH_TIMEOUT',
        'COMPENSATION_FAILURE',
        'AUDIT_OUTBOX_REPLAY_ELIGIBLE'
    )),
    constraint ck_test_design_queue_alert_subscription_channel check (channel in (
        'OPS_CONSOLE',
        'EMAIL',
        'WEBHOOK'
    )),
    constraint ck_test_design_queue_alert_subscription_threshold check (
        threshold_seconds is null or (threshold_seconds >= 0 and threshold_seconds <= 86400)
    ),
    constraint ck_test_design_queue_alert_subscription_target_ref check (
        target_ref ~ '^[A-Za-z0-9@._:/#-]{1,180}$'
    ),
    constraint ck_test_design_queue_alert_subscription_prompt_key check (
        prompt_key is null or prompt_key ~ '^[A-Za-z0-9_.:-]{1,128}$'
    )
);

create unique index if not exists uk_test_design_queue_alert_subscription_scope
    on test_design_queue_alert_subscription (
        project_id,
        coalesce(prompt_key, ''),
        alert_type,
        channel,
        target_ref
    );

create index if not exists idx_test_design_queue_alert_subscription_project_enabled
    on test_design_queue_alert_subscription (project_id, enabled, updated_at desc);

create index if not exists idx_test_design_queue_alert_subscription_prompt_alert
    on test_design_queue_alert_subscription (project_id, prompt_key, alert_type, enabled);

comment on table test_design_queue_alert_subscription is
    'WP5 queue alert subscriptions for aggregate operations alerts; stores bounded routing references only.';
comment on column test_design_queue_alert_subscription.id is 'Queue alert subscription ID.';
comment on column test_design_queue_alert_subscription.project_id is 'Owning project scope ID.';
comment on column test_design_queue_alert_subscription.prompt_key is 'Optional prompt template key for fine-grained queue alerts.';
comment on column test_design_queue_alert_subscription.alert_type is 'Aggregate alert type; no task/candidate/event identifiers are stored.';
comment on column test_design_queue_alert_subscription.channel is 'Notification channel category.';
comment on column test_design_queue_alert_subscription.target_ref is
    'Bounded non-secret target reference such as ops-console key, email group or webhook alias.';
comment on column test_design_queue_alert_subscription.threshold_seconds is 'Optional per-subscription threshold override in seconds.';
comment on column test_design_queue_alert_subscription.enabled is 'Whether this subscription is active.';
comment on column test_design_queue_alert_subscription.created_by is 'Actor that created the subscription.';
comment on column test_design_queue_alert_subscription.updated_by is 'Actor that last updated the subscription.';
comment on column test_design_queue_alert_subscription.created_at is 'Subscription creation timestamp.';
comment on column test_design_queue_alert_subscription.updated_at is 'Subscription update timestamp.';
