create extension if not exists pg_trgm;

create index if not exists idx_asset_requirement_keyword_trgm
    on asset_requirement using gin (
        lower(coalesce(code, '') || ' ' || coalesce(title, '') || ' ' || coalesce(description, '') || ' ' ||
              coalesce(source_ref, '') || ' ' || coalesce(tags, '') || ' ' || coalesce(acceptance_criteria, ''))
        gin_trgm_ops
    )
    where deleted_at is null;

create index if not exists idx_asset_api_keyword_trgm
    on asset_api using gin (
        lower(coalesce(code, '') || ' ' || coalesce(summary, '') || ' ' || coalesce(description, '') || ' ' ||
              coalesce(path, '') || ' ' || coalesce(source_ref, '') || ' ' ||
              coalesce(request_schema::text, '') || ' ' || coalesce(response_schema::text, ''))
        gin_trgm_ops
    )
    where deleted_at is null;

create index if not exists idx_asset_page_keyword_trgm
    on asset_page using gin (
        lower(coalesce(code, '') || ' ' || coalesce(name, '') || ' ' || coalesce(url_pattern, '') || ' ' ||
              coalesce(source_ref, '') || ' ' || coalesce(source_version, '') || ' ' || coalesce(component_tree::text, ''))
        gin_trgm_ops
    )
    where deleted_at is null;

create index if not exists idx_asset_business_flow_keyword_trgm
    on asset_business_flow using gin (
        lower(coalesce(code, '') || ' ' || coalesce(name, '') || ' ' ||
              coalesce(description, '') || ' ' || coalesce(flow_json::text, ''))
        gin_trgm_ops
    )
    where deleted_at is null;

create index if not exists idx_asset_test_case_keyword_trgm
    on asset_test_case using gin (
        lower(coalesce(code, '') || ' ' || coalesce(title, '') || ' ' || coalesce(description, '') || ' ' ||
              coalesce(source_ref, '') || ' ' || coalesce(tags, '') || ' ' ||
              coalesce(preconditions, '') || ' ' || coalesce(test_data_ref, ''))
        gin_trgm_ops
    )
    where deleted_at is null;

create index if not exists idx_asset_test_step_keyword_trgm
    on asset_test_step using gin (
        lower(coalesce(action, '') || ' ' || coalesce(target, '') || ' ' || coalesce(input_data, '') || ' ' ||
              coalesce(expected_result, '') || ' ' || coalesce(data_ref, ''))
        gin_trgm_ops
    );

comment on index idx_asset_requirement_keyword_trgm is
    'WP3 requirement keyword search trigram GIN index for code/title/description/sourceRef/tags/acceptance criteria.';
comment on index idx_asset_api_keyword_trgm is
    'WP3 API keyword search trigram GIN index for code/summary/description/path/sourceRef/schema payloads.';
comment on index idx_asset_page_keyword_trgm is
    'WP3 page keyword search trigram GIN index for code/name/url/sourceRef/sourceVersion/component tree.';
comment on index idx_asset_business_flow_keyword_trgm is
    'WP3 business-flow keyword search trigram GIN index for code/name/description/flow JSON.';
comment on index idx_asset_test_case_keyword_trgm is
    'WP3 test-case keyword search trigram GIN index for code/title/description/sourceRef/tags/preconditions/data ref.';
comment on index idx_asset_test_step_keyword_trgm is
    'WP3 test-step keyword search trigram GIN index for action/target/input/expected/data ref.';
