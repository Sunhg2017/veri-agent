# WP2 模型接入层 - 交付说明

| 项目 | 内容 |
|---|---|
| 工作包 | WP2 模型接入层 |
| 依赖 | WP1 平台基础底座 API 契约 |
| 服务模块 | `model-access` |
| 当前状态 | P0 可交付实现已落地 |

## 1. 交付范围

WP2 P0 交付以下能力：

1. 模型供应商配置中心：支持供应商名称、类型、`base_url`、密钥引用、启停状态、优先级、超时和 token 成本配置，支持创建、更新和就绪检查。
2. Prompt 版本管理：支持按 `prompt_key` 创建版本，每个 key 保证一个 ACTIVE 版本。
3. 统一模型调用入口：渲染 ACTIVE Prompt，组合调用消息，按策略选择供应商。
4. 敏感级别路由策略：支持 `PUBLIC`、`INTERNAL`、`CONFIDENTIAL`、`RESTRICTED`，默认 `INTERNAL`；高敏感请求禁止公开模型路由和显式外部供应商。
5. 脱敏与安全校验：调用前阻断明显密钥、Bearer token、身份证号；日志仅保存 masked preview 和 prompt SHA-256 digest。
6. 预算护栏：支持平台/项目日预算配置，供应商调用前按当前日已发生成本和预估成本阻断超额请求。
7. 失败降级：供应商调用失败后按优先级尝试下一个可用供应商，并记录 `fallback_used`。
8. 成本记录：按输入/输出 token 和供应商单价计算 `total_cost`。
9. 调用审计日志：记录 WP1 资源逻辑归属、敏感级别、调用服务、委托用户、模型、供应商、状态、延迟、成本和错误摘要。
10. 持久化模式：`local` profile 使用内存仓储，`db` profile 使用 PostgreSQL 仓储并自动执行 WP2 Flyway 迁移。

## 2. API 边界

基础路径：`/api/v1/model-access`

| API | 说明 |
|---|---|
| `GET /health` | WP2 健康与配置摘要。 |
| `GET /providers` | 查询模型供应商。 |
| `POST /providers` | 创建模型供应商。 |
| `PUT /providers/{id}` | 更新模型供应商名称、地址、密钥引用、优先级、超时和成本配置。 |
| `POST /providers/{id}/enable` | 启用供应商。 |
| `POST /providers/{id}/disable` | 停用供应商。 |
| `POST /providers/{id}/check` | 对指定供应商执行短探测，返回 `UP`/`DOWN`、延迟和脱敏错误摘要，不写调用日志。 |
| `GET /prompts?prompt_key=` | 查询 Prompt 版本。 |
| `POST /prompts` | 创建 Prompt 版本，可直接激活。 |
| `POST /prompts/{id}/activate` | 激活指定 Prompt 版本。 |
| `POST /invocations` | 发起模型调用。 |
| `GET /invocations` | 分页查询调用日志，支持项目、应用、敏感级别、状态、供应商、调用服务和时间范围筛选。 |
| `GET /invocations/summary` | 按同一组筛选条件汇总调用次数、状态分布、token 和成本。 |
| `GET /invocations/export` | 按同一组筛选条件导出脱敏调用审计 CSV。 |

除健康检查外，API 使用服务令牌：

- `Authorization: Bearer <WP2_SERVICE_TOKEN>`
- `X-Caller-Service`
- `X-Delegated-User-Id`
- `X-Trace-Id`

## 3. WP1 集成约束

WP2 保存 `project_id`、`application_id`、`environment_id` 作为逻辑归属，不直接读写 WP1 表，也不引入租户维度。默认 `WP2_PLATFORM_CONTEXT_VALIDATION=mock` 方便本地开发；切换为 `strict` 后，WP2 会携带服务令牌调用 WP1 `/api/v1/contexts/projects/{projectId}` 和 `/api/v1/contexts/applications/{appId}` 校验上下文，并消费 WP1 返回的 `allow_public_model`、`sensitivity_level`。开启 `WP2_PLATFORM_AUDIT_ENABLED=true` 后，WP2 会向 WP1 `/api/v1/audit/events` 写入不含 Prompt 明文和密钥的调用审计摘要。该 WP1 内部契约已在 `platform-api` 落地，并使用 `WP1_SERVICE_TOKEN` 保护。

`POST /invocations` 可携带 `sensitivity_level`。WP2 会在请求级别和 WP1 上下文之间取更严格的敏感级别；WP1 `STRICT` 会映射为 WP2 `RESTRICTED`。`CONFIDENTIAL` 和 `RESTRICTED` 会在供应商调用前执行模型策略校验：`allow_public_model=true` 或显式指定非 `LOCAL_*` 供应商都会返回 `MODEL_POLICY_VIOLATION`，并写入 `BLOCKED` 调用日志。若 WP1 `allow_public_model=false`，请求也不能开启公开模型路由或显式指定外部供应商。分页查询、CSV 导出和 WP1 审计摘要都会保留归一化后的敏感级别，用于后续合规追溯。

预算护栏默认关闭。设置 `WP2_DAILY_PLATFORM_COST_LIMIT` 或 `WP2_DAILY_PROJECT_COST_LIMIT` 为大于 0 的金额后，WP2 使用 `WP2_BUDGET_ZONE_ID` 对齐日窗口，并用 `WP2_BUDGET_ESTIMATED_OUTPUT_TOKENS` 作为调用前输出 token 预估保留量。超额请求返回 `BUDGET_EXCEEDED`，并记录 `BLOCKED` 调用日志，实际成本为 0。

## 4. 数据库交付

迁移脚本：

- `db/migration/wp2/V20260517_001__wp2_model_access_schema.sql`
- `db/migration/wp2/V20260517_003__wp2_invocation_sensitivity_audit.sql`
- `db/migration/wp2/V20260517_004__wp2_single_platform_scope.sql`

默认种子：`db/migration/wp2/V20260517_002__wp2_default_seed_data.sql`，为 `db` profile 初始化 `local-echo-primary` 和 `test-case-design` ACTIVE Prompt，便于持久化模式直接 smoke。

核心表：

- `ma_model_provider`
- `ma_prompt_template`
- `ma_invocation_log`

校验脚本：

- `db/validation/wp2_model_access_validation.sql`
- `db/validation/run_wp2_db_validation.sh`
- `scripts/wp2_model_access_smoke.sh`
- `scripts/wp2_strict_integration_smoke.sh`

## 5. 验证

已覆盖自动化用例：

1. 健康检查无需服务令牌。
2. 受保护 API 拒绝匿名访问。
3. Prompt 版本创建、激活、模型调用与审计日志写入。
4. 敏感内容阻断。
5. 高敏感级别阻断公开模型路由和显式外部供应商，并记录 `MODEL_POLICY_VIOLATION`。
6. 供应商失败后的降级调用与日志记录。
7. 调用日志分页响应和成本汇总接口。
8. 项目日预算超额时的调用前阻断与 BLOCKED 审计记录。
9. 模型供应商配置更新与 OpenAI-compatible 密钥引用校验。
10. 模型供应商就绪检查，覆盖本地供应商 `UP`、失败供应商 `DOWN`，并验证检查不写调用日志。
11. WP1 内部 context/audit 契约测试，覆盖服务令牌、上下文响应、模型路由策略字段和审计事件接收。
12. WP2 消费 WP1 上下文策略，覆盖平台禁止公开模型路由和平台敏感级别升级。
13. 调用日志查询、汇总和 CSV 导出支持 `sensitivity_level` 筛选，验证响应不走 JSON envelope，包含 `sensitivity_level`，且不暴露 prompt 明文字段。
14. WP2 `db` profile 默认供应商和默认 Prompt 种子校验。
15. 已启动 WP2 服务的 HTTP smoke，覆盖健康检查、供应商就绪检查及 TTL 缓存、模型调用、日志查询、汇总、成本报表、成本告警和 CSV 导出。
16. OpenAPI 契约固定 `sensitivity_level` 查询、成本接口、CSV 导出、无租户维度和无明文密钥字段。
17. 运维指标覆盖模型调用、供应商检查、token/cost 和 WP1 audit 写入结果；audit 写失败不阻断主调用并可通过指标告警。
18. strict 联调 smoke 覆盖 WP1 context 策略读取、公开模型路由阻断和本地模型成功调用。
19. OpenAI-compatible 客户端合同测试覆盖 `/v1/chat/completions` 响应解析，不依赖外网。
20. 供应商调用支持同供应商重试、连续失败短时熔断和候选供应商 fallback。
21. 成本能力支持平台/项目日预算告警和 31 天内日报聚合。

运行命令：

```bash
mvn -pl model-access test
```

```bash
bash db/validation/run_wp2_db_validation.sh
```

```bash
WP2_SERVICE_TOKEN=local-model-access-token bash scripts/wp2_model_access_smoke.sh
```

```bash
WP1_SERVICE_TOKEN=local-platform-service-token \
WP1_AUTH_TOKEN_SECRET=local-auth-secret \
WP2_SERVICE_TOKEN=local-model-access-token \
bash scripts/wp2_strict_integration_smoke.sh
```

```bash
bash scripts/wp2_quality_gate.sh
```

`scripts/wp2_quality_gate.sh` 默认执行 `model-access` 测试和 WP2 数据库 validation；已启动 WP2 服务时设置 `WP2_RUN_HTTP_SMOKE=1`，已启动 WP1/WP2 strict 联调环境时设置 `WP2_RUN_STRICT_SMOKE=1`。

## 6. 1～4 项收敛结果

1. 已增加 OpenAI-compatible 真实协议形态合同测试、供应商重试策略和短时熔断开关。
2. 已增加成本告警和成本日报聚合接口。
3. 已对供应商就绪检查增加短 TTL 缓存，降低外部模型探活成本。
4. 已新增 WP2 聚合质量门禁 `scripts/wp2_quality_gate.sh`，并将 HTTP smoke / strict smoke 纳入可选发布前门禁。
