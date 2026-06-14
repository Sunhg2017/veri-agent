alter table execution_trigger
    add column if not exists config_summary_json jsonb not null default '{}'::jsonb,
    add column if not exists secret_ref varchar(256);

alter table execution_trigger
    drop constraint if exists ck_execution_trigger_config_summary_json;

alter table execution_trigger
    add constraint ck_execution_trigger_config_summary_json
        check (jsonb_typeof(config_summary_json) = 'object');

alter table execution_trigger
    drop constraint if exists ck_execution_trigger_secret_ref;

alter table execution_trigger
    add constraint ck_execution_trigger_secret_ref
        check (
            secret_ref is null
            or secret_ref ~ '^secret://[A-Za-z0-9._~:/?#\[\]@!$&''()*+,;=%-]{1,247}$'
        );

create index if not exists idx_execution_trigger_secret_digest
    on execution_trigger (secret_ref_digest)
    where secret_ref_digest is not null;

comment on column execution_trigger.config_summary_json is
    'Safe trigger configuration summary such as cron expression, timezone and source metadata; secrets and payloads are not stored.';
comment on column execution_trigger.secret_ref is
    'Secret reference used for webhook signature verification; the secret value is never stored here.';
