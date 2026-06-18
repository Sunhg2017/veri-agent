-- WP1 in-app notifications foundation for authenticated users.
-- Stores aggregate-only, user-scoped site notifications for async task completions and other platform events.

create table if not exists iam_user_notification (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references iam_user(id) on delete cascade,
    type varchar(64) not null,
    title varchar(160) not null,
    body varchar(2000) not null,
    link varchar(512),
    metadata_json jsonb not null default '{}'::jsonb,
    read_at timestamptz,
    created_by varchar(128),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint ck_iam_user_notification_type check (type in (
        'ASYNC_TASK_COMPLETED',
        'ASYNC_TASK_FAILED',
        'REPORT_READY',
        'REPORT_FAILED',
        'SYSTEM_INFO'
    )),
    constraint ck_iam_user_notification_metadata_json check (
        jsonb_typeof(metadata_json) = 'object'
    )
);

create index if not exists idx_iam_user_notification_user_created
    on iam_user_notification (user_id, created_at desc);
create index if not exists idx_iam_user_notification_user_unread
    on iam_user_notification (user_id, created_at desc)
    where read_at is null;
create index if not exists idx_iam_user_notification_type_created
    on iam_user_notification (type, created_at desc);

comment on table iam_user_notification is 'WP1 站内通知表，保存面向登录用户的聚合通知消息。';
comment on column iam_user_notification.id is '通知主键 ID。';
comment on column iam_user_notification.user_id is '通知所属用户 ID。';
comment on column iam_user_notification.type is '通知类型枚举，如报告完成、失败或系统消息。';
comment on column iam_user_notification.title is '通知标题。';
comment on column iam_user_notification.body is '通知正文内容。';
comment on column iam_user_notification.link is '通知跳转链接或站内 hash 路由。';
comment on column iam_user_notification.metadata_json is '通知附带的聚合元数据 JSON。';
comment on column iam_user_notification.read_at is '用户标记已读时间。';
comment on column iam_user_notification.created_by is '通知创建主体标识。';
comment on column iam_user_notification.created_at is '通知创建时间。';
comment on column iam_user_notification.updated_at is '通知最近更新时间。';
