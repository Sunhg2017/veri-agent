-- WP2 default seed data for db profile smoke usage.
-- These records mirror the local in-memory defaults and contain no plaintext secrets.

insert into ma_model_provider (
    id,
    name,
    provider_type,
    base_url,
    api_key_ref,
    status,
    priority,
    timeout_ms,
    input_cost_per_1k_tokens,
    output_cost_per_1k_tokens,
    created_at,
    updated_at
) values (
    '00000000-0000-0000-0000-000000000201',
    'local-echo-primary',
    'LOCAL_ECHO',
    null,
    'local://echo',
    'ENABLED',
    10,
    3000,
    0.0001,
    0.0002,
    now(),
    now()
) on conflict (id) do update set
    name = excluded.name,
    provider_type = excluded.provider_type,
    base_url = excluded.base_url,
    api_key_ref = excluded.api_key_ref,
    status = excluded.status,
    priority = excluded.priority,
    timeout_ms = excluded.timeout_ms,
    input_cost_per_1k_tokens = excluded.input_cost_per_1k_tokens,
    output_cost_per_1k_tokens = excluded.output_cost_per_1k_tokens,
    updated_at = now();

insert into ma_prompt_template (
    id,
    prompt_key,
    name,
    version,
    content,
    status,
    change_note,
    created_at,
    updated_at
) values (
    '00000000-0000-0000-0000-000000000301',
    'test-case-design',
    '测试用例设计助手',
    1,
    '你是企业级测试设计助手，请基于以下上下文输出结构化建议：{{context}}',
    'ACTIVE',
    'WP2 默认 Prompt',
    now(),
    now()
) on conflict (id) do update set
    name = excluded.name,
    content = excluded.content,
    status = excluded.status,
    change_note = excluded.change_note,
    updated_at = now();

insert into ma_prompt_template (
    id,
    prompt_key,
    name,
    version,
    content,
    status,
    change_note,
    created_at,
    updated_at
) values (
    '00000000-0000-0000-0000-000000000302',
    'wp4-document-requirement-parse',
    'WP4 文档需求解析助手',
    1,
    '{{schemaMarker}}
你是企业级需求解析助手。请只返回 JSON，不要返回 Markdown。
JSON schema: {"requirements":[{"title":"需求标题","description":"需求说明","priority":"CRITICAL|HIGH|MEDIUM|LOW","acceptanceCriteria":"验收标准","tags":["标签"],"confidence":0.0}]}
从用户提供的文本、Markdown 或 JSON 中抽取可人工确认的需求候选项；无法判断时返回空 requirements。',
    'ACTIVE',
    'WP4 AI 文档解析 MVP Prompt',
    now(),
    now()
) on conflict (id) do update set
    name = excluded.name,
    content = excluded.content,
    status = excluded.status,
    change_note = excluded.change_note,
    updated_at = now();
