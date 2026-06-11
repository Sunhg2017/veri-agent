-- WP6 model-backed API automation prompt seed.
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
    '00000000-0000-0000-0000-000000000304',
    'wp6-api-automation-v1',
    'WP6 接口自动化用例生成助手',
    1,
    '{{schemaMarker}}
你是企业级接口自动化用例生成助手。请只返回 JSON，不要返回 Markdown。
JSON schema: {"schemaVersion":"wp6-api-automation-v1","cases":[{"assetApiId":"uuid","title":"用例标题","method":"GET|POST|PUT|PATCH|DELETE|HEAD|OPTIONS","path":"/openapi/path","coverageType":"SMOKE|FUNCTIONAL|EXCEPTION","expectedStatus":200,"assertions":["STATUS_CODE"],"requestTemplate":{"aggregateOnly":true,"bodyTemplateStored":false,"secretValuesStored":false},"rationale":"生成依据"}]}
只能基于用户提供的 endpoint 聚合摘要生成用例；不得输出请求正文、响应正文、secret、token、cookie 或 Authorization 示例值。
assetApiId 必须来自输入，method/path 必须与输入 endpoint 一致，requestTemplate 必须保持 aggregateOnly=true、bodyTemplateStored=false、secretValuesStored=false。',
    'ACTIVE',
    'WP6 API automation model prompt',
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
