-- WP4 event-driven document input state machine and lightweight import payload store.

alter table document_input_import
    drop constraint if exists ck_document_input_import_status;

alter table document_input_import
    add constraint ck_document_input_import_status check (status in (
        'MODEL_PARSE_QUEUED',
        'MODEL_PARSE_RUNNING',
        'PUBLISH_QUEUED',
        'PUBLISHING',
        'SUCCEEDED',
        'FAILED'
    ));

alter table document_input_candidate
    drop constraint if exists ck_document_input_candidate_status;

alter table document_input_candidate
    add constraint ck_document_input_candidate_status check (status in (
        'PENDING',
        'CONFIRMED',
        'IGNORED',
        'PUBLISH_QUEUED',
        'PUBLISHING',
        'PUBLISHED',
        'PUBLISH_FAILED'
    ));

alter table document_input_webhook_event
    drop constraint if exists ck_document_input_webhook_status;

alter table document_input_webhook_event
    add constraint ck_document_input_webhook_status check (status in (
        'ACCEPTED',
        'PROCESSING',
        'REJECTED',
        'PROCESSED',
        'FAILED',
        'DEAD_LETTER',
        'REPLAYED'
    ));

create table if not exists document_input_import_payload (
    import_id uuid primary key references document_input_import(id) on delete cascade,
    mapping_id uuid references document_input_field_mapping(id) on delete set null,
    parse_fallback_title varchar(256),
    content text not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

comment on table document_input_import_payload is 'WP4 异步导入待解析原始内容，事件仅携带 import_id 以避免 Kafka 大消息';
comment on column document_input_import_payload.import_id is '文档导入任务 ID';
comment on column document_input_import_payload.mapping_id is '解析使用的字段映射 ID';
comment on column document_input_import_payload.parse_fallback_title is '解析标题兜底值';
comment on column document_input_import_payload.content is '原始待解析内容';
comment on column document_input_import_payload.created_at is '记录创建时间';
comment on column document_input_import_payload.updated_at is '记录更新时间';
