# WP6 OpenAPI 接口自动化 - 技术设计与接口契约

| 项目 | 内容 |
|---|---|
| 工作包 | WP6 OpenAPI 接口自动化 |
| 角色产出 | 资深服务端架构师 |
| 文档性质 | 技术设计、数据模型、接口契约和安全约束 |
| 当前口径 | `platform-api` 承载 WP6 控制面，runner 通过端口适配；不绕过 WP1/WP2/WP3/WP5 应用服务边界 |
| 版本 | v0.4 |
| 日期 | 2026-06-12 |

## 1. 架构原则

1. WP6 是接口自动化控制面，不是独立调度平台。
2. OpenAPI import、diff、case generation、script bundle、manual run 和 result summary 均按项目 scope 管理。
3. AI 生成只通过 WP2；WP2 阻断时生成任务可失败或走确定性模板 fallback。
4. WP3 API 资产同步必须通过 WP3 应用服务，不直连 WP3 表做业务写入。
5. runner 必须通过端口适配，默认以可禁用、可超时、可限制目标地址的方式运行。
6. 入库数据采用白名单，不保存明文 secret、完整请求响应正文、环境变量值或未脱敏错误正文。

## 2. 模块划分

| 包 | 职责 |
|---|---|
| `apiautomation.api` | Controller、request/response DTO、OpenAPI contract 注解 |
| `apiautomation.application` | OpenAPI 导入、diff、生成、脚本包、运行任务和审计应用服务 |
| `apiautomation.domain` | 源、endpoint snapshot、automation case、script bundle、run、run result 领域快照 |
| `apiautomation.infrastructure` | MyBatis mapper、OpenAPI parser、runner adapter、WP2/WP3/WP5 client adapter |
| `apiautomation.config` | 大小限制、runner 开关、超时、allowlist、默认 Prompt 和 fallback 配置 |

## 3. 数据模型草案

| 表 | 关键字段 | 说明 |
|---|---|---|
| `api_automation_spec` | `id`、`project_id`、`source_type`、`source_ref`、`name`、`version_label`、`spec_digest`、`sanitized_spec_json`、`status`、`parse_error_summary` | OpenAPI 源和脱敏规格快照 |
| `api_automation_endpoint_snapshot` | `id`、`spec_id`、`project_id`、`service_name`、`operation_id`、`method`、`path`、`schema_digest`、`asset_api_id`、`diff_status` | 解析后的 endpoint 和 WP3 API 对齐结果 |
| `api_automation_generation_task` | `id`、`project_id`、`spec_id`、`request_key`、`input_digest`、`generation_mode`、`coverage_types_json`、`status`、`prompt_key`、`prompt_version`、`model_invocation_id`、`fallback_used`、`api_count`、`case_count`、`input_summary_json` | 接口自动化生成任务 |
| `api_automation_case` | `id`、`project_id`、`task_id`、`spec_id`、`endpoint_snapshot_id`、`asset_api_id`、`asset_test_case_id`、`title`、`method`、`path`、`coverage_type`、`expected_status`、`assertion_summary_json`、`request_template_json`、`source`、`status` | 自动化用例草稿 |
| `api_automation_script_bundle` | `id`、`project_id`、`task_id`、`status`、`bundle_digest`、`file_count`、`static_check_status`、`approved_by` | Pytest 脚本包元数据 |
| `api_automation_run` | `id`、`project_id`、`bundle_id`、`environment_id`、`base_url_digest`、`status`、`timeout_seconds`、`trace_id` | 手动试运行任务 |
| `api_automation_run_result` | `id`、`run_id`、`case_id`、`status`、`duration_ms`、`assertion_summary`、`error_code`、`error_summary` | 用例级运行结果摘要 |

约束要求：

- 所有 WP6 表必须有 `project_id`、`created_at`、`updated_at` 或明确不需要的原因。
- 状态字段必须有 check constraint。
- 规格 digest、任务 input digest、脚本 bundle digest 必须可索引。
- `sanitized_spec_json` 必须是脱敏和裁剪后的 JSON，不允许保存导入时的认证示例值。
- 运行结果不保存完整 request/response body，仅允许状态码、断言类别、耗时和脱敏错误摘要。

## 4. 配置项草案

| 配置 | 默认值 | 说明 |
|---|---|---|
| `veri-agent.api-automation.spec-max-bytes` | `1048576` | OpenAPI 导入大小上限 |
| `veri-agent.api-automation.endpoint-max-count` | `500` | 单规格 endpoint 解析上限 |
| `veri-agent.api-automation.runner-enabled` | `false` | 默认关闭本地执行器 |
| `veri-agent.api-automation.runner-timeout-seconds` | `120` | 单次运行默认超时 |
| `veri-agent.api-automation.runner-max-cases` | `100` | 单次运行用例上限 |
| `veri-agent.api-automation.allowed-base-url-patterns` | 空 | 允许访问的测试目标地址模式 |
| `veri-agent.api-automation.prompt-key` | `wp6-api-automation-v1` | WP2 Prompt key |
| `veri-agent.api-automation.model-fallback-enabled` | `true` | 模型失败时允许确定性模板 fallback |

## 5. 接口契约草案

统一前缀：`/api/v1/api-automation`

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| `GET` | `/health` | 公开健康检查 | 返回 WP6 配置边界、runner 开关和安全策略摘要；不返回 secret、allowlist 明细或运行目标 |
| `POST` | `/specs` | `apiAutomation:import` | 创建 OpenAPI 导入任务，支持 `UPLOAD/TEXT/URL` |
| `GET` | `/specs` | `apiAutomation:read` | 分页查询规格源 |
| `GET` | `/specs/{id}` | `apiAutomation:read` | 查询规格详情和解析摘要 |
| `POST` | `/specs/{id}/parse` | `apiAutomation:import` | 重新解析规格 |
| `GET` | `/specs/{id}/diff` | `apiAutomation:read` | 查询与 WP3 API 资产的 diff |
| `POST` | `/specs/{id}/sync` | `apiAutomation:import` | 确认同步新增/更新 API 资产 |
| `POST` | `/generation-tasks` | `apiAutomation:generate` | 创建接口自动化生成任务 |
| `GET` | `/generation-tasks` | `apiAutomation:read` | 按 project/spec/status 分页查询生成任务摘要 |
| `GET` | `/generation-tasks/{id}` | `apiAutomation:read` | 查询生成任务、用例和脚本包摘要 |
| `POST` | `/generation-tasks/{id}/script-bundles` | `apiAutomation:generate` | 为生成任务创建或返回脚本包摘要 |
| `POST` | `/script-bundles/{id}/submit-review` | `apiAutomation:review` | 提交脚本包评审 |
| `POST` | `/script-bundles/{id}/approve` | `apiAutomation:review` | 审批脚本包 |
| `POST` | `/script-bundles/{id}/reject` | `apiAutomation:review` | 驳回脚本包 |
| `POST` | `/runs` | `apiAutomation:execute` | 触发受控手动试运行 |
| `GET` | `/runs/{id}` | `apiAutomation:read` | 查询运行摘要和用例级结果 |
| `GET` | `/runs/{id}/export` | `apiAutomation:export` | 导出脱敏运行摘要 |

## 6. 关键请求体

### 创建规格

```json
{
  "projectId": "uuid",
  "sourceType": "TEXT",
  "name": "billing-openapi",
  "versionLabel": "2026.06",
  "content": "{...}",
  "sourceRef": null
}
```

### 创建生成任务

```json
{
  "projectId": "uuid",
  "specId": "uuid",
  "assetApiIds": ["uuid"],
  "assetTestCaseIds": ["uuid"],
  "generationMode": "MODEL_WITH_FALLBACK",
  "coverageTypes": ["SMOKE", "FUNCTIONAL", "EXCEPTION"],
  "caseCountPerApi": 3,
  "requestKey": "optional-idempotency-key"
}
```

当前实现约束：

- `assetApiIds` 为空时使用当前 spec 下所有已同步到 WP3 的 endpoint；非空时必须全部属于当前 spec 的已同步 endpoint。
- `coverageTypes` 支持 `SMOKE/FUNCTIONAL/EXCEPTION`，为空默认 `SMOKE`。
- `generationMode` 支持 `FALLBACK_ONLY/MODEL_WITH_FALLBACK`；`MODEL_WITH_FALLBACK` 通过 WP2 `ModelInvocationService` 调用 `wp6-api-automation-v1`，模型成功时保存 `MODEL` 草稿，WP2 阻断、供应商失败或输出 schema 非法时按 `model-fallback-enabled` 走确定性 fallback。
- `caseCountPerApi` 范围为 1 至 5。
- `requestKey` 为同项目幂等 key；同 key 不同规范化 payload 返回冲突，相同 payload 返回已有任务详情。
- `GET /generation-tasks` 只返回生成任务聚合摘要和 `inputSummary`，不返回自动化用例草稿、脚本文件树或原始模型响应；前端历史列表需要详情时再调用 `GET /generation-tasks/{id}`。

### 创建试运行

```json
{
  "projectId": "uuid",
  "bundleId": "uuid",
  "environmentId": "uuid",
  "baseUrl": "https://qa.example.test",
  "secretRefs": ["secret-ref"],
  "caseIds": ["uuid"],
  "timeoutSeconds": 120
}
```

## 7. 生成策略

生成输入只允许包含：

- endpoint 摘要：method、path、operationId、参数名、schema 摘要、响应码。
- WP3 API 资产 ID 和摘要。
- WP5 已确认用例的标题、覆盖类型、步骤摘要和预期摘要。
- 环境键、baseUrl 策略、secretRef digest。

禁止进入模型或持久化的内容：

- secretRef 明文、token、cookie、Authorization header 示例值。
- 完整请求响应样例正文中的疑似密钥字段。
- 未脱敏错误正文和 runner 环境变量值。

当前 M4 生成切片的持久化策略：

- 生成任务保存 `inputDigest`、`generationMode`、`coverageTypes`、`apiCount`、`caseCount` 和聚合输入摘要。
- 自动化用例草稿保存 method、path、coverageType、expectedStatus、assertionSummary 和 requestTemplate 摘要。
- `assetTestCaseIds` 通过 WP3 测试用例应用服务读取摘要，输入摘要只保存用例 ID、关联 API ID、标题、状态、优先级、标签、步骤数量、最多 3 条步骤摘要和 sourceRef digest。
- `MODEL_WITH_FALLBACK` 只通过 WP2 调用模型，任务保存 `modelInvocationId`、`promptVersion`、模型供应商名称、模型名称、模型供应商 fallback 信号和 fallback 原因摘要。
- 模型输出必须符合 `wp6-api-automation-v1` JSON schema，并通过 title、method、path、coverageType、expectedStatus、assertions、requestTemplate 聚合标识、endpoint 范围校验；不保存原始模型响应。
- `source=MODEL` 明确标识模型输出；`source=FALLBACK` 明确标识确定性模板输出，不互相伪装。
- 健康检查返回 `generationReady=true`、`modelGenerationReady=true`，用于前端和运营准出口径区分。

## 8. Runner 契约

`ApiAutomationRunnerPort` 必须至少支持：

| 方法 | 语义 |
|---|---|
| `validateBundle(bundle)` | 静态校验脚本包，不访问外部网络 |
| `run(runRequest)` | 受控执行，返回聚合结果和用例级摘要 |
| `cancel(runId)` | 尽力取消运行 |

Runner 必须执行以下限制：

1. baseUrl 必须匹配 allowlist。
2. timeout、case count、产物大小和并发必须有限制。
3. secretRef 由 SecretProvider 解析，日志只输出 digest。
4. 默认不允许访问内网 metadata 地址、localhost 管理端口或未授权域名。
5. 产物只返回摘要引用，不把完整 stdout/stderr 直接入库。

## 9. 审计事件草案

| 事件 | 资源 |
|---|---|
| `api_automation.spec.created` | OpenAPI 源 |
| `api_automation.spec.parsed` | 解析任务 |
| `api_automation.api_diffed` | WP3 API diff |
| `api_automation.api_synced` | WP3 API sync |
| `api_automation.generation.created` | 生成任务 |
| `api_automation.bundle.generated` | 脚本包 |
| `api_automation.bundle.reviewed` | 脚本包评审 |
| `api_automation.run.started` | 运行任务 |
| `api_automation.run.completed` | 运行任务 |
| `api_automation.run.canceled` | 运行任务取消 |
| `api_automation.exported` | 导出 |

## 10. 错误码草案

| 错误码 | 场景 |
|---|---|
| `OPENAPI_PARSE_FAILED` | 规格非法或超过支持范围 |
| `OPENAPI_TOO_LARGE` | 文件大小或 endpoint 数超过限制 |
| `API_SYNC_CONFLICT` | WP3 API 资产存在冲突，需要人工确认 |
| `GENERATION_POLICY_BLOCKED` | WP2 策略或预算阻断 |
| `SCRIPT_STATIC_CHECK_FAILED` | 脚本静态校验失败 |
| `RUNNER_DISABLED` | runner 未开启 |
| `RUNNER_TARGET_BLOCKED` | baseUrl 不在 allowlist |
| `RUNNER_TIMEOUT` | 运行超时 |

## 11. 验证要求

1. 后端单测覆盖 parser、diff、generation fallback、runner disabled、权限和审计。
2. DB validation 覆盖 WP6 表、约束、索引、注释和 runtime role 权限。
3. OpenAPI fixture smoke 覆盖 JSON/YAML、参数、请求体、响应码、非法 schema 和敏感样例脱敏。
4. Runner smoke 默认关闭；发布或显式执行时必须验证 allowlist、timeout、失败结果和脱敏日志。
5. OpenAPI contract 测试必须确认路径、权限、响应 envelope 和 traceId。

## 12. 当前实现切片（2026-06-12）

当前已实现 M1/M2/M3 控制面、M4 生成任务、M5 脚本包评审和 M6 受控 runner 控制面切片：

- DB：新增 `api_automation_spec`、`api_automation_endpoint_snapshot`、WP6 权限 seed、角色默认授权和 DB validation。
- DB M3：新增 endpoint snapshot 的 `asset_api_id`、`diff_summary_json`、`last_diff_at`、`synced_at`、`sync_error_summary`，用于持久化 WP3 API 匹配和同步证据。
- DB M4：新增 `api_automation_generation_task` 和 `api_automation_case`，记录生成任务幂等键、inputDigest、coverage、fallback 标识、用例草稿和 endpoint/API 追踪关系。
- 后端：新增 `/api/v1/api-automation/health`、`/specs` 创建/列表、`/specs/{id}` 详情、`/specs/{id}/parse` 重解析。
- 后端 M3：新增 `/specs/{id}/diff` 和 `/specs/{id}/sync`；diff 按 method + path 匹配 WP3 API 资产，输出 `NEW/CHANGED/MATCHED/CONFLICT/SKIPPED`，sync 通过 WP3 `AssetApiService` 创建/更新 API 资产并逐项返回同步明细。
- 后端 M4/M6 UI 支撑：新增 `POST /generation-tasks`、`GET /generation-tasks` 和 `GET /generation-tasks/{id}`；生成任务仅允许使用当前项目、已解析 spec 和已同步 WP3 API endpoint，支持 requestKey 幂等和 payload 冲突校验；列表接口按 project/spec/status 返回聚合历史摘要。
- 生成 M4：`FALLBACK_ONLY` 产出确定性 fallback 用例草稿；`MODEL_WITH_FALLBACK` 调用 WP2 `wp6-api-automation-v1` Prompt，模型成功时产出 `source=MODEL` 草稿，模型失败、WP2 阻断或输出非法时按配置产出 `source=FALLBACK` 草稿并保存可解释 fallbackReason。
- 模型输出校验 M4：新增 WP6 输出解析器，拒绝未知字段、非 JSON、非聚合 requestTemplate、敏感文本、非法 status/method/path/assertions 以及不属于当前生成范围的 API。
- WP5/WP3 输入 M4：`assetTestCaseIds` 已通过 WP3 `AssetTestCaseService` 读取发布后的测试用例摘要，校验项目归属和已同步 API 范围；不读取 WP5 候选正文、评审评论或 sourceRef 明文。
- DB M5：新增 `api_automation_script_bundle`，保存脚本包 digest、文件树摘要、依赖摘要、静态校验摘要、评审状态和提交/审批人时间戳。
- DB M6：新增 `api_automation_run` 和 `api_automation_run_result`，保存运行任务、baseUrl host/digest、runnerMode、状态、错误码和用例级结果摘要；不保存 baseUrl 明文、完整请求/响应、stdout/stderr 或 secret。
- 后端 M5：生成任务创建时同步生成 Pytest/httpx 脚本包摘要；也支持 `POST /generation-tasks/{id}/script-bundles` 对历史任务补包，幂等返回已有未归档脚本包。
- 静态校验 M5：在不执行 Python、不访问网络的前提下校验模板括号边界、危险 import/call 和硬编码 secret pattern；失败状态为 `SCRIPT_STATIC_CHECK_FAILED`。
- 评审 M5：`submit-review/approve/reject` 实现 `DRAFT/REVIEWING/APPROVED/REJECTED` 流转，驳回原因必填，审计只记录动作和备注存在性，不写备注正文。
- Runner M6/M8：新增 `ApiAutomationRunnerPort` validate/run/cancel 契约、默认 Disabled adapter、基础 Managed HTTP adapter 和显式 Pytest subprocess adapter，`POST /runs` 在脚本包 `APPROVED`、静态校验通过、baseUrl 安全策略通过后才进入执行路径；默认 disabled 时持久化 `BLOCKED/RUNNER_DISABLED` 摘要，显式 `runner-enabled=true` 默认装配 Managed HTTP adapter，显式 `runner-mode=pytest-subprocess` 时在临时目录重建最小 Pytest/httpx 文件树并执行本地子进程；`secretRefs` 校验格式和数量后仅用完整引用调用 SecretProvider，审计和持久化只记录 `sha256:<digest>`，运行期只注入受控 `X-VA-WP6-Secret-N` header。
- Managed HTTP Runner M8：当前 adapter 使用 JDK `HttpClient`，不跟随重定向、不发送请求体、丢弃响应体，只按生成用例的 method/path/expectedStatus 执行受控 HTTP 探测并返回聚合断言摘要；OpenAPI path template 变量使用稳定占位值，错误摘要不包含原始 URL、请求/响应正文或 secret。
- Pytest Subprocess Runner M8：当前 adapter 通过 `runner-mode=pytest-subprocess` 显式启用，命令默认为 `python3 -m pytest` 且不经 shell 执行；临时工作目录只写入本次 case 元数据重建的 Pytest/httpx 文件树，执行后删除；通过 `WP6_RUNNER_SECRET_HEADERS_JSON` 和 `WP6_RUNNER_SECRET_VALUE_N` 向 Pytest fixture 注入受控 header；结果从 JUnit XML 聚合为 case-level status/duration/errorCode，不持久化生成源码、stdout/stderr 原文、请求/响应正文或 secret。
- Allowlist M6：baseUrl 标准化后仅保存 host 和 SHA-256 digest；阻断 localhost、私网 IPv4、metadata host、`.local` 和未授权 host，错误码为 `RUNNER_TARGET_BLOCKED`。
- Run API M6/M8：新增 `POST /api/v1/api-automation/runs`、`GET /api/v1/api-automation/runs/{id}` 和 `POST /api/v1/api-automation/runs/{id}/cancel`，按脚本包/run 项目 scope 校验 `apiAutomation:execute/read`，返回 run 摘要和 case-level result 摘要；run payload 支持 `secretRefs`，审计仅记录 count 和 digest，不落完整 secretRef；cancel 只对 `QUEUED/RUNNING` 调用 runner port 并在 adapter 接受时标记 `CANCELED/RUNNER_CANCELED`，终态 run 幂等返回当前摘要；runner smoke 已用调度型 runner 控制面模拟覆盖已持久化 `RUNNING` run 的异步取消、状态收敛和 cancel 摘要脱敏。
- Run Export：新增 `GET /api/v1/api-automation/runs/{id}/export`，按 run 项目 scope 校验 `apiAutomation:export`，返回 `schemaVersion/exportedAt/run/results/resultCounts/redactionPolicy`，只导出 baseUrl host/digest、状态、耗时、错误码和聚合断言摘要；不导出 baseUrl 明文、请求响应正文、stdout/stderr 或 secret，并写 `api_automation.exported` 审计。
- Quality Gate M7/M8：`scripts/wp6_quality_gate.sh` 聚合脚本语法、OpenAPI fixture smoke、WP6 后端/OpenAPI 测试、前端 WP6 helper/权限测试、WP6 Playwright smoke、前端构建和 DB validation；支持 `WP6_QUALITY_GATE_PLAN_ONLY=1` 输出计划，开发模式默认不启 runner，可用 `WP6_SKIP_FRONTEND_E2E=1` 显式跳过浏览器 smoke；显式 `WP6_RUNNER_SMOKE=managed|pytest|auto|external` 时调用 runner smoke。
- Frontend Playwright Smoke M8：新增 `scripts/wp6_frontend_e2e_smoke.sh` 和 `portal-web/e2e/wp6-api-automation.smoke.playwright.ts`，通过浏览器 mock 后端契约覆盖导入、diff、同步、生成、脚本包评审、运行、取消和脱敏导出，断言运行结果页面不渲染 baseUrl query token。
- Fixture Smoke M7：新增 `scripts/wp6_openapi_fixture_smoke.sh`、`OpenApiFixtureSmokeTest` 和 `wp6-openapi-fixtures`，覆盖 JSON/YAML、path/query/header/cookie 参数、requestBody、响应码、非法 OpenAPI、endpoint 上限和敏感示例脱敏。
- Runner Smoke M8：新增 `scripts/wp6_runner_smoke.sh`、`ApiAutomationRunnerSmokeTest`、`ManagedHttpApiAutomationRunnerAdapterTest`、`PytestSubprocessApiAutomationRunnerAdapterTest` 和 runner 配置测试，在不访问真实业务网络的前提下验证 runner 执行分支、allowlist 阻断、基础 loopback HTTP pass/fail/path-template/timeout、secretRef digest 传递、SecretProvider 解析、Managed HTTP 受控 header 注入、Pytest subprocess 命令/env/JUnit XML 解析、Pytest runtime secret header 映射、取消 API 幂等返回、调度型 runner 控制面异步取消、失败结果和运行导出脱敏；`external` 模式要求显式配置 `WP6_RUNNER_BASE_URL`。
- Parser：支持 OpenAPI 3.x JSON/YAML，抽取 method/path/operationId/tags/参数数/requestBody/响应码/schemaDigest，并对 Authorization、apiKey、token、cookie、password、secret 等敏感示例脱敏。
- 权限：除 health 外，规格导入、查询、重解析、diff、sync、生成任务和脚本包评审均按项目 scope 校验 `apiAutomation:*` 权限。
- 前端：新增 `#api-automation` 入口、API helper、权限控制、规格导入/列表/endpoint snapshot、diff 刷新和状态筛选、WP3 API 同步入口、生成模式选择、WP3 用例 ID 输入、已同步 API 范围勾选、生成任务历史列表/详情回看、生成用例入口、脚本包评审面板、已审批脚本包运行面板和脱敏运行摘要导出入口。

本轮未实现：真实后台调度、进程级中断和分布式任务回收。调度型 runner 控制面异步 cancel smoke 已覆盖；发布模式下 WP6 quality gate 会要求显式 runner smoke，当前可通过 service contract smoke、secretRef digest/SecretProvider/header 注入测试、Pytest subprocess adapter 契约测试、取消 API 幂等与异步控制面 smoke、基础 Managed HTTP loopback smoke 和 WP6 Playwright smoke 防止执行分支、脱敏、allowlist、取消入口、HTTP/Pytest 探测和前端主链路回归。
