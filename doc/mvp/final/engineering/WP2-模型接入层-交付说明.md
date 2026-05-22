# WP2 模型接入层 - 交付说明

| 项目 | 内容 |
|---|---|
| 工作包 | WP2 模型接入层 |
| 依赖 | WP1 平台基础底座 API 契约 |
| 服务模块 | `platform-api` 内聚合模块 `/api/v1/model-access` |
| 当前状态 | P0 可交付实现已落地；WP2-C1 高级路由策略、WP2-C2 预算策略产品化、WP2-C4 Prompt 评审与审批和 WP2-D2 流式响应支持已补齐 |

## 1. 交付范围

WP2 P0 交付以下能力：

1. 模型供应商配置中心：支持供应商名称、类型、`routingGroup`、`capabilities`、`baseUrl`、密钥引用、启停状态、优先级、超时和 token 成本配置，支持创建、更新和就绪检查。
2. Prompt 版本管理：支持按 `promptKey` 创建版本，每个 key 保证一个 ACTIVE 版本；高风险 Prompt 需审批通过后才能激活，并保留审批人、审批时间和版本说明。
3. 统一模型调用入口：渲染 ACTIVE Prompt，组合调用消息，按策略选择供应商；同步调用和 SSE 流式调用复用同一条策略、预算、审计和调用日志链路。
4. 敏感级别路由策略：支持 `PUBLIC`、`INTERNAL`、`CONFIDENTIAL`、`RESTRICTED`，默认 `INTERNAL`；高敏感请求禁止公开模型路由和显式外部供应商。
5. 高级路由策略：支持按项目、敏感级别、调用服务、模型能力和供应商组匹配路由规则；规则可选择 `LOWEST_COST` 在候选组内按预估成本优先。
6. 脱敏与安全校验：调用前阻断明显密钥、Bearer token、身份证号；日志仅保存 masked preview 和 prompt SHA-256 digest。
7. 预算护栏：支持平台、项目、调用服务日预算配置，供应商调用前按当前日已发生成本和预估成本执行阻断或低成本 provider 降级。
8. 失败降级：供应商调用失败后按优先级尝试下一个可用供应商，并记录 `fallbackUsed`。
9. 成本记录：按输入/输出 token 和供应商单价计算 `totalCost`。
10. 调用审计日志：记录 WP1 资源逻辑归属、敏感级别、调用服务、委托用户、模型、供应商、路由规则、路由组、模型能力、状态、延迟、成本和错误摘要。
11. 持久化模式：`local` profile 使用内存仓储，`db` profile 使用 PostgreSQL 仓储并自动执行 WP2 Flyway 迁移。

## 2. API 边界

基础路径：`/api/v1/model-access`

| API | 说明 |
|---|---|
| `GET /health` | WP2 健康与配置摘要。 |
| `GET /providers` | 查询模型供应商。 |
| `POST /providers` | 创建模型供应商，支持 `routingGroup` 和 `capabilities`。 |
| `PUT /providers/{id}` | 更新模型供应商名称、路由组、能力、地址、密钥引用、优先级、超时和成本配置。 |
| `POST /providers/{id}/enable` | 启用供应商。 |
| `POST /providers/{id}/disable` | 停用供应商。 |
| `POST /providers/{id}/check` | 对指定供应商执行短探测，返回 `UP`/`DOWN`、延迟和脱敏错误摘要，不写调用日志。 |
| `GET /providers/{id}/resilience` | 查询供应商熔断状态、连续失败次数、恢复时间、限流和并发配置摘要。 |
| `POST /providers/{id}/circuit/reset` | 手动重置指定供应商短时熔断状态。 |
| `GET /prompts?promptKey=` | 查询 Prompt 版本。 |
| `POST /prompts` | 创建 Prompt 版本；低风险版本可直接激活，高风险版本即使请求激活也会进入待审批状态。 |
| `POST /prompts/{id}/approve` | 审批通过高风险 Prompt 版本，记录审批人、审批时间和审批说明。 |
| `POST /prompts/{id}/reject` | 驳回高风险 Prompt 版本，记录审批人、审批时间和审批说明。 |
| `POST /prompts/{id}/activate` | 激活指定 Prompt 版本；高风险版本必须先审批通过。 |
| `POST /invocations` | 发起模型调用。 |
| `POST /invocations/stream` | 发起 SSE 流式模型调用，返回 `metadata`、`delta`、`done` 事件；MVP 先完整执行同步 invocation 并落盘调用日志，再将响应内容按 UTF-8 SSE 分片输出。 |
| `POST /invocations/jobs` | 提交异步模型调用任务，返回 `202 Accepted`、`jobId` 和 `QUEUED/RUNNING/SUCCEEDED/FAILED/CANCELLED` 状态摘要。 |
| `GET /invocations/jobs/{jobId}` | 查询异步模型调用任务状态、时间戳、关联 `invocationId`、错误摘要和成功响应。 |
| `POST /invocations/jobs/{jobId}/cancel` | 取消异步模型调用任务；未开始任务可稳定取消，运行中任务 best-effort interrupt，已完成任务返回当前终态。 |
| `GET /invocations` | 分页查询调用日志，支持项目、应用、敏感级别、状态、供应商、调用服务和时间范围筛选。 |
| `GET /invocations/summary` | 按同一组筛选条件汇总调用次数、状态分布、token 和成本。 |
| `GET /invocations/export` | 按同一组筛选条件导出脱敏调用审计 CSV。 |
| `GET /cost/alerts?projectId=&actorService=` | 查询平台、项目和调用服务日预算告警，支持按项目或调用服务显式查看 OK/WARNING/EXCEEDED 状态。 |
| `GET /cost/report?startDate=&endDate=&projectId=` | 查询 31 天内项目/应用维度成本日报。 |

除健康检查外，API 支持两类调用身份：

- `Authorization: Bearer <WP2_SERVICE_TOKEN>`
- `X-Caller-Service`
- `X-Delegated-User-Id`
- `X-Trace-Id`
- 登录用户 Bearer token，用于 `portal-web` 模型接入管理台；后端按 `modelAccess:read`、`modelAccess:manage`、`modelAccess:export` 做 RBAC 校验，浏览器不得持有 `WP2_SERVICE_TOKEN`。

## 3. WP1 集成约束

WP2 保存 `projectId`、`applicationId`、`environmentId` 作为逻辑归属，不引入租户维度。WP1、WP2、WP3 在 MVP 中属于同一个 `platform-api` Java 服务内的领域模块，不做服务拆分；WP2 通过 Spring 注入调用 WP1 集成应用服务校验项目/应用上下文，并消费 `allowPublicModel`、`sensitivityLevel`。模型调用审计同样通过同进程应用服务写入 WP1 审计表，不通过 HTTP 回调本服务，也不需要模块间 HTTP 配置。

`POST /invocations` 可携带 `sensitivityLevel`。WP2 会在请求级别和 WP1 上下文之间取更严格的敏感级别；WP1 `STRICT` 会映射为 WP2 `RESTRICTED`。`CONFIDENTIAL` 和 `RESTRICTED` 会在供应商调用前执行模型策略校验：`allowPublicModel=true` 或显式指定非 `LOCAL_*` 供应商都会返回 `MODEL_POLICY_VIOLATION`，并写入 `BLOCKED` 调用日志。若 WP1 `allowPublicModel=false`，请求也不能开启公开模型路由或显式指定外部供应商。分页查询、CSV 导出和 WP1 审计摘要都会保留归一化后的敏感级别，用于后续合规追溯。

`POST /invocations` 还可携带 `capability`，默认 `CHAT`。WP2 会先执行敏感级别和公开模型策略，再按 `veri-agent.model-access.routing-rules` 顺序匹配项目、敏感级别、调用服务、能力和供应商组；未命中规则时回到 `default-priority`。命中规则后仅在匹配的 `routingGroup` 内选择支持该能力的 provider，`costPreference=LOWEST_COST` 时按预估输入 token 和 `WP2_BUDGET_ESTIMATED_OUTPUT_TOKENS` 计算成本并优先选择低成本 provider。调用日志、CSV 导出和 WP1 审计摘要记录 `routingRuleName`、`routingGroup` 和 `modelCapability`，用于追溯路由结果。

Prompt 版本创建可设置 `highRisk=true`。高风险版本默认 `approvalStatus=PENDING`，必须先通过 `POST /prompts/{id}/approve` 变为 `APPROVED` 才能激活；驳回后为 `REJECTED`，可重新审批但不能直接激活。审批信息保存在 `ma_prompt_template.high_risk/approval_status/approved_by/approved_at/approval_note`，Prompt 激活、审批通过和驳回均写 WP1 审计。

敏感内容阻断会在供应商调用前检查 prompt 和消息内容，当前覆盖 key/token/password/Bearer、身份证号、手机号、邮箱、银行卡疑似长号以及 `internal_token`、`corp_secret`、`private_key` 等企业内部密钥模式。命中后返回稳定错误并写入 `BLOCKED` 调用日志，避免敏感明文进入外部 provider。

预算护栏默认关闭。设置 `WP2_DAILY_PLATFORM_COST_LIMIT`、`WP2_DAILY_PROJECT_COST_LIMIT` 或 `WP2_DAILY_CALLER_SERVICE_COST_LIMIT` 为大于 0 的金额后，WP2 使用 `WP2_BUDGET_ZONE_ID` 对齐日窗口，并用 `WP2_BUDGET_ESTIMATED_OUTPUT_TOKENS` 作为调用前输出 token 预估保留量。`WP2_COST_ALERT_WARNING_RATIO` 控制告警阈值，成本告警接口会返回平台、项目和调用服务维度的 `OK`、`WARNING` 或 `EXCEEDED`。`WP2_BUDGET_OVERRUN_ACTION=BLOCK` 时超额请求返回 `BUDGET_EXCEEDED`，并记录 `BLOCKED` 调用日志，实际成本为 0；设置为 `FALLBACK` 时，WP2 会跳过当前预估超预算 provider，继续尝试后续低成本候选，全部候选仍超预算时再阻断。

Provider 级生产保护默认关闭。设置 `WP2_PROVIDER_RATE_LIMIT_MAX_REQUESTS`、`WP2_PROVIDER_RATE_LIMIT_WINDOW_SECONDS` 可开启单 provider 时间窗口限流；设置 `WP2_PROVIDER_MAX_CONCURRENT_REQUESTS` 可开启单 provider 并发保护。触发限流或并发上限时返回 `BUDGET_EXCEEDED`，并记录 `BLOCKED` 调用日志。连续失败熔断仍由 `WP2_PROVIDER_CIRCUIT_FAILURE_THRESHOLD` 和 `WP2_PROVIDER_CIRCUIT_OPEN_MS` 控制，可通过 resilience API 查询和 reset。

`POST /invocations/stream` 复用同步 `POST /invocations` 的请求体和校验链路。当前 MVP 不直接透传外部 provider 原生 token streaming，而是在 provider 调用成功、成本和调用日志已经确定后输出 `text/event-stream;charset=UTF-8`：`metadata` 事件包含 invocation、provider、token、cost 和 traceId 摘要，`delta` 事件承载响应分片，`done` 事件承载结束原因。这样可先保证策略、预算、审计、日志和前端消费契约稳定，后续再替换 provider 适配层为真实 token streaming。

`POST /invocations/jobs` 同样复用同步 `POST /invocations` 的请求体、调用权限、策略、预算、provider 选择和调用日志链路。当前 MVP 使用 `platform-api` 单进程内存 job registry 和可配置工作线程：`WP2_ASYNC_JOB_WORKER_THREADS` 默认 2，`WP2_ASYNC_JOB_DISPATCH_DELAY_MS` 默认 0。任务状态独立于 invocation 日志状态，固定为 `QUEUED`、`RUNNING`、`SUCCEEDED`、`FAILED`、`CANCELLED`；成功或失败后仍由同步 invocation 逻辑写入 `SUCCEEDED`、`FAILED` 或 `BLOCKED` 调用日志和 WP1 审计。取消语义为 best-effort：未开始任务可稳定取消且不写调用日志，运行中任务会尝试 interrupt；如果 provider 调用已经完成，则查询会返回最终成功或失败结果。服务重启后内存 job 不保留，持久化任务表、分布式 worker、重试调度和对象存储大结果引用作为后续生产化增强。

## 4. 数据库交付

迁移脚本：

- `db/migration/wp1/V20260518_009__wp2_model_access_schema.sql`
- `db/migration/wp1/V20260518_010__wp2_default_seed_data.sql`
- `db/migration/wp1/V20260518_011__wp2_invocation_sensitivity_audit.sql`
- `db/migration/wp1/V20260518_012__wp2_single_platform_scope.sql`
- `db/migration/wp1/V20260518_013__wp2_soft_delete_audit_columns.sql`
- `db/migration/wp1/V20260522_025__wp2_advanced_routing_metadata.sql`
- `db/migration/wp1/V20260522_026__wp2_prompt_review_approval.sql`

默认种子：`V20260518_010__wp2_default_seed_data.sql`，为 `db` profile 初始化 `local-echo-primary` 和 `test-case-design` ACTIVE Prompt，便于持久化模式直接 smoke。

核心表：

- `ma_model_provider`
- `ma_prompt_template`
- `ma_invocation_log`

校验脚本：

- `db/validation/wp2_model_access_validation.sql`
- `db/validation/run_wp2_db_validation.sh`
- `scripts/wp2_model_access_smoke.sh`
- `scripts/wp2_module_policy_smoke.sh`

## 5. 验证

已覆盖自动化用例：

1. 健康检查无需服务令牌。
2. 受保护 API 拒绝匿名访问。
3. Prompt 版本创建、激活、模型调用与审计日志写入。
4. 敏感内容阻断，覆盖密钥、token、密码、Bearer、身份证号、手机号、邮箱、银行卡疑似长号和企业内部密钥模式。
5. 高敏感级别阻断公开模型路由和显式外部供应商，并记录 `MODEL_POLICY_VIOLATION`。
6. 供应商失败后的降级调用与日志记录。
7. 调用日志分页响应和成本汇总接口。
8. 项目日预算超额时的调用前阻断与 BLOCKED 审计记录。
9. 调用服务日预算超额时的调用前阻断、按 `actorService` 查询成本告警，以及 `FALLBACK` 超预算策略下的低成本 provider 降级。
10. 模型供应商配置更新与 OpenAI-compatible 密钥引用校验。
11. 模型供应商就绪检查，覆盖本地供应商 `UP`、失败供应商 `DOWN`，并验证检查不写调用日志。
12. WP1 内部 context/audit 契约测试，覆盖服务令牌、上下文响应、模型路由策略字段和审计事件接收。
13. WP2 消费 WP1 上下文策略，覆盖平台禁止公开模型路由和平台敏感级别升级。
14. 调用日志查询、汇总和 CSV 导出支持 `sensitivityLevel` 筛选，验证响应不走 JSON envelope，CSV 使用数据库审计列名且不暴露 prompt 明文字段。
15. WP2 `db` profile 默认供应商和默认 Prompt 种子校验。
16. 已启动 WP2 服务的 HTTP smoke，覆盖健康检查、供应商就绪检查及 TTL 缓存、模型调用、日志查询、汇总、成本报表、成本告警和 CSV 导出。
17. OpenAPI 契约固定 `sensitivityLevel` 查询、成本接口、CSV 导出、无租户维度和无明文密钥字段。
18. 运维指标覆盖模型调用、供应商检查、token/cost 和 WP1 audit 写入结果；audit 写失败不阻断主调用并可通过指标告警。
19. 模块策略 smoke 覆盖 WP2 在同一 `platform-api` 内读取 WP1 context 策略、公开模型路由阻断和本地模型成功调用。
20. OpenAI-compatible 客户端合同测试覆盖 `/v1/chat/completions` 响应解析，不依赖外网。
21. 供应商调用支持同供应商重试、连续失败短时熔断和候选供应商 fallback。
22. 成本能力支持平台/项目/调用服务日预算告警和 31 天内日报聚合。
23. 供应商生产硬化支持 provider 级窗口限流、并发控制、熔断状态查询和手动 reset；限流/并发阻断会返回 `BUDGET_EXCEEDED` 并写入 `BLOCKED` 调用日志。
24. 通用模型评测集框架支持按 `taskType` 评测 Prompt/provider 输出，当前语料覆盖 `case-design`、`defect-triage`、`requirement-summary`，并校验场景通过率、必需术语召回和禁用术语清洁率。
25. 高级路由策略覆盖按项目、调用服务、敏感级别、能力和供应商组命中规则，并验证 `LOWEST_COST` 可在组内选择低成本 provider，调用日志保留路由规则、路由组和模型能力。
26. 高风险 Prompt 覆盖待审批创建、审批通过、驳回后重审、审批人/说明保留、未审批激活拒绝和审批后激活。
27. SSE 流式调用覆盖 `metadata/delta/done` 事件输出、UTF-8 文本响应、异步安全分派和调用日志落盘。
28. 异步 invocation job 覆盖提交 `202`、状态轮询成功、排队取消、匿名/低权限拒绝、未知 job 404、OpenAPI 契约和 portal-web helper 路径/归一化。

运行命令：

```bash
mvn -pl platform-api test
```

```bash
cd portal-web && npm run test -- auth.test.ts bootstrap.test.ts modelAccess.test.ts permissions.test.ts
cd portal-web && npm run build
```

```bash
mvn -B -pl platform-api -Dtest=ModelAccessControllerTest,ModelAccessOpenApiContractTest test
cd portal-web && npm test -- modelAccess.test.ts
```

```bash
bash db/validation/run_wp2_db_validation.sh
```

```bash
bash scripts/wp2_model_quality_eval.sh
WP2_MODEL_EVAL_TASK=case-design bash scripts/wp2_model_quality_eval.sh
```

```bash
WP2_SERVICE_TOKEN=local-model-access-token bash scripts/wp_all_integration_test.sh
```

```bash
WP1_SERVICE_TOKEN=local-platform-service-token \
WP1_AUTH_TOKEN_SECRET=local-auth-secret \
WP2_SERVICE_TOKEN=local-model-access-token \
bash scripts/wp2_module_policy_smoke.sh
```

```bash
bash scripts/wp_all_integration_test.sh
```

当前质量门禁以 `platform-api` 测试、WP2 模型质量评测、portal-web 模型接入相关测试与构建、数据库 validation 和可选 HTTP smoke 为准。历史独立 `model-access` 模块已删除，不再作为交付或测试入口。

## 6. 近期收敛结果

1. 已增加 OpenAI-compatible 真实协议形态合同测试、供应商重试策略和短时熔断开关。
2. 已增加成本告警和成本日报聚合接口。
3. 已对供应商就绪检查增加短 TTL 缓存，降低外部模型探活成本。
4. 已新增 WP2 聚合质量门禁 `scripts/wp2_quality_gate.sh`，覆盖 `platform-api` 测试、portal-web 模型接入相关测试与构建、WP2 DB validation，并将 HTTP smoke / 模块策略 smoke 纳入可选发布前门禁。
5. 已新增 `scripts/wp2_model_quality_eval.sh` 和通用 `ModelEvaluationRunner`，Prompt 或 provider 变更可按任务类型运行离线评测，后续智能任务可复用同一语料结构扩展指标。
6. 已新增 WP2-C1 高级路由策略，provider 可配置路由组和能力，调用可声明能力，路由规则可按项目/敏感级别/调用服务/能力/供应商组匹配，并将路由结果写入调用日志和 WP1 审计摘要。
7. 已新增 WP2-C2 预算策略产品化，支持调用服务日预算、按 `actorService` 查询成本告警，以及 `BLOCK/FALLBACK` 超预算动作配置。
8. 已新增 WP2-C4 Prompt 评审与审批，高风险 Prompt 需审批通过后激活，并在后端、DB 和 portal-web 管理台保留审批状态、审批人、审批时间和审批说明。
9. 已新增 WP2-D2 流式响应支持，提供 `/invocations/stream` SSE 入口和 portal-web 解析/调用 helper，保持同步 invocation 契约、策略、预算和调用日志链路不变。
10. 已新增 WP2-D3 异步长任务调用，提供 `/invocations/jobs` 提交、查询和取消 API 及 portal-web helper；当前为单进程内存 job registry，成功/失败复用既有 invocation 审计链路。

## 7. Provider 生产接入与密钥轮换

外部 provider 接入、OpenAI-compatible 私有网关配置、就绪检查、故障处理和密钥轮换流程已收敛到 `doc/mvp/final/engineering/WP2-Provider接入与SecretRef轮换Runbook.md`。

当前实现中 WP2 外部 provider 的密钥引用字段为 `apiKeyRef`，OpenAI-compatible provider 仅接受 `env:VARIABLE_NAME`。生产环境应由部署系统或外部密钥系统注入环境变量，WP2 库表和 release notes 只保存引用，不保存明文。后续如接入 WP1 SecretProvider，应沿用 runbook 中的双引用、灰度检查、切换、旧引用失效流程。
