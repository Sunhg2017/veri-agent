-- WP5 model-backed test design prompt seed.
-- The prompt is data-only and contains no plaintext provider secrets.

insert into ma_prompt_template (
    id,
    prompt_key,
    name,
    version,
    content,
    status,
    change_note,
    high_risk,
    approval_status,
    created_at,
    updated_at
) values (
    '00000000-0000-0000-0000-000000000303',
    'wp5-test-design-v1',
    'WP5 测试设计生成助手',
    1,
    '{{schemaMarker}}
你是企业级测试设计助手。请只返回 JSON，不要返回 Markdown。
JSON schema: {"schemaVersion":"wp5-model-v1","cases":[{"title":"用例标题","description":"用例说明","coverageType":"SMOKE|FUNCTIONAL|EXCEPTION|BOUNDARY|PERMISSION|REGRESSION","priority":"CRITICAL|HIGH|MEDIUM|LOW","preconditions":"前置条件","steps":[{"action":"操作","expectedResult":"预期"}],"expectedResult":"整体预期","requirementRef":"需求 ID","apiRefs":[],"pageRefs":[],"flowRefs":[],"tags":["标签"],"rationale":"生成依据","riskNotes":"风险提示","confidence":0.0}]}
根据 WP5 上下文为每个需求和覆盖类型生成可人工评审的候选用例。',
    'ACTIVE',
    'WP5 AI 用例生成 MVP Prompt',
    false,
    'NOT_REQUIRED',
    now(),
    now()
) on conflict (prompt_key, version) do update set
    name = excluded.name,
    content = excluded.content,
    status = excluded.status,
    change_note = excluded.change_note,
    high_risk = excluded.high_risk,
    approval_status = excluded.approval_status,
    updated_at = now();
