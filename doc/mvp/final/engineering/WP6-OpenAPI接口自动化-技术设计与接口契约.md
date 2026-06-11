# WP6 OpenAPI 接口自动化 - 技术设计与接口契约

| 项目 | 内容 |
|---|---|
| 工作包 | WP6 OpenAPI 接口自动化 |
| 角色产出 | 资深服务端架构师 |
| 文档性质 | 技术设计、数据模型、接口契约和安全约束 |
| 当前口径 | `platform-api` 承载 WP6 控制面，runner 通过端口适配；不绕过 WP1/WP2/WP3/WP5 应用服务边界 |
| 版本 | v0.3 |
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
| `GET` | `/generation-tasks/{id}` | `apiAutomation:read` | 查询生成任务、用例和脚本包摘要 |
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
- `generationMode` 支持 `FALLBACK_ONLY/MODEL_WITH_FALLBACK`，当前 `MODEL_WITH_FALLBACK` 不调用 WP2，只记录 `MODEL_GENERATION_NOT_WIRED` 后走 fallback。
- `caseCountPerApi` 范围为 1 至 5。
- `requestKey` 为同项目幂等 key；同 key 不同规范化 payload 返回冲突，相同 payload 返回已有任务详情。

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

当前 M4 fallback 切片的持久化策略：

- 生成任务保存 `inputDigest`、`generationMode`、`coverageTypes`、`apiCount`、`caseCount` 和聚合输入摘要。
- 自动化用例草稿保存 method、path、coverageType、expectedStatus、assertionSummary 和 requestTemplate 摘要。
- `assetTestCaseIds` 通过 WP3 测试用例应用服务读取摘要，输入摘要只保存用例 ID、关联 API ID、标题、状态、优先级、标签、步骤数量、最多 3 条步骤摘要和 sourceRef digest。
- `source=FALLBACK` 明确标识确定性模板输出，不伪装为模型输出。
- 健康检查返回 `generationReady=true`、`modelGenerationReady=false`，用于前端和运营准出口径区分。

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
| `RUN_TIMEOUT` | 运行超时 |

## 11. 验证要求

1. 后端单测覆盖 parser、diff、generation fallback、runner disabled、权限和审计。
2. DB validation 覆盖 WP6 表、约束、索引、注释和 runtime role 权限。
3. OpenAPI fixture smoke 覆盖 JSON/YAML、参数、请求体、响应码、非法 schema 和敏感样例脱敏。
4. Runner smoke 默认关闭；发布或显式执行时必须验证 allowlist、timeout、失败结果和脱敏日志。
5. OpenAPI contract 测试必须确认路径、权限、响应 envelope 和 traceId。

## 12. 当前实现切片（2026-06-12）

当前已实现 M1/M2/M3 控制面和 M4 fallback 生成任务切片：

- DB：新增 `api_automation_spec`、`api_automation_endpoint_snapshot`、WP6 权限 seed、角色默认授权和 DB validation。
- DB M3：新增 endpoint snapshot 的 `asset_api_id`、`diff_summary_json`、`last_diff_at`、`synced_at`、`sync_error_summary`，用于持久化 WP3 API 匹配和同步证据。
- DB M4：新增 `api_automation_generation_task` 和 `api_automation_case`，记录生成任务幂等键、inputDigest、coverage、fallback 标识、用例草稿和 endpoint/API 追踪关系。
- 后端：新增 `/api/v1/api-automation/health`、`/specs` 创建/列表、`/specs/{id}` 详情、`/specs/{id}/parse` 重解析。
- 后端 M3：新增 `/specs/{id}/diff` 和 `/specs/{id}/sync`；diff 按 method + path 匹配 WP3 API 资产，输出 `NEW/CHANGED/MATCHED/CONFLICT/SKIPPED`，sync 通过 WP3 `AssetApiService` 创建/更新 API 资产并逐项返回同步明细。
- 后端 M4：新增 `POST /generation-tasks` 和 `GET /generation-tasks/{id}`；生成任务仅允许使用当前项目、已解析 spec 和已同步 WP3 API endpoint，支持 requestKey 幂等和 payload 冲突校验。
- 生成 M4：当前不调用 WP2 模型，`FALLBACK_ONLY` 和 `MODEL_WITH_FALLBACK` 均产出确定性 fallback 用例草稿；`MODEL_WITH_FALLBACK` 的 input summary 标记 `MODEL_GENERATION_NOT_WIRED`。
- WP5/WP3 输入 M4：`assetTestCaseIds` 已通过 WP3 `AssetTestCaseService` 读取发布后的测试用例摘要，校验项目归属和已同步 API 范围；不读取 WP5 候选正文、评审评论或 sourceRef 明文。
- Parser：支持 OpenAPI 3.x JSON/YAML，抽取 method/path/operationId/tags/参数数/requestBody/响应码/schemaDigest，并对 Authorization、apiKey、token、cookie、password、secret 等敏感示例脱敏。
- 权限：除 health 外，规格导入、查询、重解析、diff、sync 和生成任务均按项目 scope 校验 `apiAutomation:*` 权限。
- 前端：新增 `#api-automation` 入口、API helper、权限控制、规格导入/列表/endpoint snapshot、diff 刷新、WP3 API 同步入口、WP3 用例 ID 输入和 fallback 生成用例入口。

本轮未实现：WP2 Prompt 模型调用、模型输出 schema 校验、脚本包评审、runner 执行、运行结果、WP6 quality gate 聚合脚本。这些仍按研发任务拆解的 M4-M7 继续推进。
