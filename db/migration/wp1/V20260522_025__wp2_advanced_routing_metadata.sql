-- WP2-C1 advanced routing metadata.
-- Provider metadata lets route rules select provider groups and model capabilities.
-- Invocation metadata keeps the selected rule/group/capability auditable.

alter table ma_model_provider
    add column if not exists routing_group varchar(64) not null default 'default',
    add column if not exists capabilities varchar(256) not null default 'CHAT,TEXT,JSON,REQUIREMENT_PARSE';

alter table ma_invocation_log
    add column if not exists routing_rule_name varchar(128),
    add column if not exists routing_group varchar(64),
    add column if not exists model_capability varchar(64) not null default 'CHAT';

create index if not exists idx_ma_model_provider_routing_group
    on ma_model_provider (routing_group, status, priority);

create index if not exists idx_ma_invocation_routing_time
    on ma_invocation_log (routing_rule_name, model_capability, created_at desc);
