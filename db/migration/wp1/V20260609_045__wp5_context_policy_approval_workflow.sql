-- WP5 context policy approval workflow, work orders, notes and policy body management.

alter table test_design_context_policy_override
    add column if not exists work_order_key varchar(128),
    add column if not exists work_order_title varchar(256),
    add column if not exists work_order_url varchar(512),
    add column if not exists work_order_status varchar(32),
    add column if not exists policy_body text,
    add column if not exists policy_body_digest varchar(64),
    add column if not exists policy_body_version int,
    add column if not exists policy_diff_summary text,
    add column if not exists request_note text,
    add column if not exists review_note text,
    add column if not exists reviewed_at timestamptz;

update test_design_context_policy_override
set work_order_key = coalesce(work_order_key, 'WP5-CTX-' || substr(id::text, 1, 8)),
    work_order_status = coalesce(work_order_status, case status when 'APPROVED' then 'APPROVED' when 'REJECTED' then 'REJECTED' else 'OPEN' end),
    policy_body_version = coalesce(policy_body_version, 1)
where work_order_key is null
   or work_order_status is null
   or policy_body_version is null;

alter table test_design_context_policy_override
    alter column work_order_key set not null,
    alter column work_order_status set not null,
    alter column policy_body_version set not null;

do $$
begin
    if not exists (
        select 1 from pg_constraint where conname = 'ck_test_design_context_policy_override_work_order_status'
    ) then
        alter table test_design_context_policy_override
            add constraint ck_test_design_context_policy_override_work_order_status
            check (work_order_status in ('OPEN', 'IN_REVIEW', 'APPROVED', 'REJECTED', 'CANCELLED'));
    end if;
    if not exists (
        select 1 from pg_constraint where conname = 'ck_test_design_context_policy_override_policy_body_version'
    ) then
        alter table test_design_context_policy_override
            add constraint ck_test_design_context_policy_override_policy_body_version
            check (policy_body_version >= 1);
    end if;
    if not exists (
        select 1 from pg_constraint where conname = 'ck_test_design_context_policy_override_body_digest'
    ) then
        alter table test_design_context_policy_override
            add constraint ck_test_design_context_policy_override_body_digest
            check (policy_body_digest is null or policy_body_digest ~ '^[0-9a-f]{64}$');
    end if;
    if not exists (
        select 1 from pg_constraint where conname = 'ck_test_design_context_policy_override_work_order_lengths'
    ) then
        alter table test_design_context_policy_override
            add constraint ck_test_design_context_policy_override_work_order_lengths
            check (
                char_length(work_order_key) between 1 and 128
                and (work_order_title is null or char_length(work_order_title) between 1 and 256)
                and (work_order_url is null or char_length(work_order_url) between 1 and 512)
            );
    end if;
    if not exists (
        select 1 from pg_constraint where conname = 'ck_test_design_context_policy_override_text_lengths'
    ) then
        alter table test_design_context_policy_override
            add constraint ck_test_design_context_policy_override_text_lengths
            check (
                (policy_body is null or char_length(policy_body) between 1 and 4000)
                and (policy_diff_summary is null or char_length(policy_diff_summary) between 1 and 1000)
                and (request_note is null or char_length(request_note) between 1 and 1000)
                and (review_note is null or char_length(review_note) between 1 and 1000)
            );
    end if;
end $$;

create index if not exists idx_test_design_context_policy_override_work_order
    on test_design_context_policy_override (work_order_key);

create table if not exists test_design_context_policy_note (
    id uuid primary key default gen_random_uuid(),
    override_id uuid not null references test_design_context_policy_override(id),
    note_type varchar(32) not null,
    note_text text not null,
    created_by varchar(128),
    created_at timestamptz not null default now(),
    constraint ck_test_design_context_policy_note_type check (
        note_type in ('REQUEST', 'REVIEW', 'COMMENT', 'WORK_ORDER')
    ),
    constraint ck_test_design_context_policy_note_text check (
        char_length(note_text) between 1 and 1000
    )
);

create index if not exists idx_test_design_context_policy_note_override_created
    on test_design_context_policy_note (override_id, created_at asc);

comment on column test_design_context_policy_override.work_order_key is 'WP5 context policy approval work order key.';
comment on column test_design_context_policy_override.work_order_title is 'Bounded approval work order title.';
comment on column test_design_context_policy_override.work_order_url is 'Optional approval work order URL; never exported to task diagnostics or reports.';
comment on column test_design_context_policy_override.work_order_status is 'Approval work order status.';
comment on column test_design_context_policy_override.policy_body is 'Bounded policy document body for operators; source context, raw prompt and model payload are prohibited.';
comment on column test_design_context_policy_override.policy_body_digest is 'SHA-256 digest of the bounded policy body.';
comment on column test_design_context_policy_override.policy_body_version is 'Operator-managed policy body version.';
comment on column test_design_context_policy_override.policy_diff_summary is 'Bounded policy diff summary for approval review.';
comment on column test_design_context_policy_override.request_note is 'Bounded requester note.';
comment on column test_design_context_policy_override.review_note is 'Bounded reviewer note.';
comment on column test_design_context_policy_override.reviewed_at is 'Approval or rejection timestamp.';
comment on table test_design_context_policy_note is 'WP5 context policy approval work order note timeline.';
comment on column test_design_context_policy_note.id is 'Primary key of a context policy approval note.';
comment on column test_design_context_policy_note.override_id is 'Context policy override owning this note.';
comment on column test_design_context_policy_note.note_type is 'Approval note type: REQUEST, REVIEW, COMMENT or WORK_ORDER.';
comment on column test_design_context_policy_note.note_text is 'Bounded approval note text; raw context, prompt and secrets are prohibited.';
comment on column test_design_context_policy_note.created_by is 'Operator or service actor who appended the note.';
comment on column test_design_context_policy_note.created_at is 'Time when the approval note was appended.';
