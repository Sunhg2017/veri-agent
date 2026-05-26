-- WP5 publish idempotency for WP3 test cases.
-- WP5 publishes candidates as AI_GENERATED test cases with source_ref=wp5:{candidateId};
-- this partial unique index prevents concurrent publishes from creating duplicate WP3 assets.
do $$
begin
    if exists (
        select 1
        from (
            select project_id, source, source_ref
            from asset_test_case
            where deleted_at is null
              and source = 'AI_GENERATED'
              and source_ref is not null
            group by project_id, source, source_ref
            having count(*) > 1
        ) duplicated
    ) then
        raise exception 'Duplicate active AI-generated test case source_ref values must be cleaned before WP5 idempotency migration.';
    end if;
end $$;

create unique index if not exists uk_asset_test_case_project_ai_source_ref
    on asset_test_case (project_id, source, source_ref)
    where deleted_at is null
      and source = 'AI_GENERATED'
      and source_ref is not null;

comment on index uk_asset_test_case_project_ai_source_ref is
    'WP5 publish idempotency key for AI-generated WP3 test cases.';
