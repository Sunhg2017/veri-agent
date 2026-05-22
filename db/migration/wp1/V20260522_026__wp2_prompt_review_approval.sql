-- WP2 prompt review and approval metadata.

alter table ma_prompt_template
    add column if not exists high_risk boolean not null default false,
    add column if not exists approval_status varchar(32) not null default 'NOT_REQUIRED',
    add column if not exists approved_by varchar(128),
    add column if not exists approved_at timestamptz,
    add column if not exists approval_note varchar(512);

alter table ma_prompt_template
    drop constraint if exists ck_ma_prompt_template_approval_status;

alter table ma_prompt_template
    add constraint ck_ma_prompt_template_approval_status
        check (approval_status in ('NOT_REQUIRED', 'PENDING', 'APPROVED', 'REJECTED'));

create index if not exists idx_ma_prompt_template_approval_status
    on ma_prompt_template (approval_status, updated_at desc);
