# WP1-WP4 指标命名与看板规范

| 项目 | 内容 |
|---|---|
| 覆盖范围 | WP1 平台、WP2 模型接入、WP3 资产、WP4 文档输入 |
| 用途 | 统一 metrics 命名、Grafana 看板、告警和 traceId 串联口径 |
| 日期 | 2026-05-20 |

## 1. 命名规范

1. Micrometer 指标统一使用 `veri.agent.<domain>.<object>.<event>`。
2. `<domain>` 使用小写 snake_case，例如 `platform`、`model_access`、`asset`、`document_input`。
3. Counter 使用复数业务对象或动作，例如 `imports`、`invocations`、`webhooks`；Timer 使用 `.latency` 后缀；DistributionSummary 使用数量对象，例如 `.tokens`、`.cost`、`.records`。
4. 标签使用小写 snake_case，值使用稳定枚举；不要把密钥、原文、完整错误、用户输入放入标签。
5. `traceId` 不进入 metrics 标签；它必须出现在响应 envelope、应用日志、审计日志或调用日志中。
6. `projectId`、`actorService` 属于高基数字段，默认放在审计/调用日志和结构化日志中做 drilldown；如需项目级 Prometheus SLO，只允许使用明确 allowlist 或离线聚合后的低基数标签。

## 2. 当前已落地指标

| WP | 指标 | 类型 | 关键标签 | 说明 |
|---|---|---|---|---|
| WP2 | `veri.agent.model_access.invocations` | Counter | `status`、`sensitivity_level`、`provider_type`、`fallback_used`、`error_code` | 模型调用审计行计数 |
| WP2 | `veri.agent.model_access.invocation.latency` | Timer | `status`、`provider_type` | 模型调用延迟，包含策略和 provider 调用 |
| WP2 | `veri.agent.model_access.tokens` | DistributionSummary | `direction`、`provider_type`、`sensitivity_level` | 输入/输出 token 用量 |
| WP2 | `veri.agent.model_access.cost` | DistributionSummary | `provider_type`、`sensitivity_level` | 调用成本 |
| WP2 | `veri.agent.model_access.provider.checks` | Counter | `provider_type`、`status`、`error_code` | provider 就绪检查结果 |
| WP2 | `veri.agent.model_access.provider.check.latency` | Timer | `provider_type`、`status` | provider 就绪检查延迟 |
| WP4 | `veri.agent.document_input.imports` | Counter | `source_type`、`status` | 导入批次计数 |
| WP4 | `veri.agent.document_input.import.requirements` | DistributionSummary | `source_type`、`status` | 每次导入解析出的候选数 |
| WP4 | `veri.agent.document_input.candidate.actions` | Counter | `action`、`result` | 候选确认/忽略动作 |
| WP4 | `veri.agent.document_input.publishes` | Counter | `dry_run`、`result` | 发布到 WP3 的 dryRun/正式发布 |
| WP4 | `veri.agent.document_input.publish.records` | DistributionSummary | `dry_run`、`result` | 每次发布记录数 |
| WP4 | `veri.agent.document_input.webhooks` | Counter | `signature_status`、`event_status`、`event_type` | webhook 接入事件 |
| WP4 | `veri.agent.document_input.model_parse` | Counter | `result` | AI 文档解析尝试 |
| WP4 | `veri.agent.document_input.model_parse.candidates` | DistributionSummary | `result` | AI 解析候选数 |

## 3. 后续新增指标口径

| WP | 建议指标 | 标签 | 触发点 |
|---|---|---|---|
| WP1 | `veri.agent.platform.auth.requests` | `action`、`result` | 登录、刷新、注销、改密 |
| WP1 | `veri.agent.platform.context.requests` | `resource_type`、`status`、`caller_service` | 内部 context 查询 |
| WP1 | `veri.agent.platform.audit.events` | `resource_type`、`action`、`result`、`caller_service` | 审计写入成功/失败/拒绝 |
| WP1 | `veri.agent.platform.secret.resolves` | `provider_type`、`purpose`、`result` | SecretProvider resolve |
| WP3 | `veri.agent.asset.requirements` | `action`、`status`、`source` | 需求资产创建/更新/状态变更 |
| WP3 | `veri.agent.asset.upserts` | `asset_type`、`source`、`result` | WP4 或后续 WP 写入资产 |
| WP3 | `veri.agent.asset.trace_links` | `action`、`result` | requirement-api-case 追踪关系 |
| WP4 | `veri.agent.document_input.extract.latency` | `source_type`、`result` | Word/PDF/OCR 文本抽取 |
| WP4 | `veri.agent.document_input.webhook.replays` | `result`、`event_type` | 人工或自动重放 |

## 4. Grafana 看板建议

| 行 | 面板 | 查询关注点 | Drilldown |
|---|---|---|---|
| 平台总览 | API 健康、错误率、P95 延迟 | `platform-api` health、HTTP status、JVM | 响应 `traceId` |
| WP1 平台治理 | 认证、context、audit、secret resolve | WP1 指标或审计日志聚合 | `projectId`、`actorService`、`resourceType` |
| WP2 模型接入 | invocation 成功率、fallback、provider check、成本、token | `veri.agent.model_access.*` | invocation log：`projectId`、`actorService`、`status` |
| WP3 资产 | requirement/API/case 写入、trace link、upsert 冲突 | WP3 指标和资产表聚合 | assetId、sourceRef、traceId |
| WP4 文档输入 | import、candidate、publish、webhook、model parse | `veri.agent.document_input.*` | importId、eventId、sourceCode、traceId |
| 发布窗口 | 本次版本错误、阻断、回滚信号 | 以上面板按时间窗口过滤 | release notes 中记录的 project/source/provider |

## 5. 告警建议

| 告警 | 触发条件 | 处理入口 |
|---|---|---|
| WP1 audit 写入失败 | audit 失败计数持续大于 0 或 outbox 堆积 | WP1 owner，检查数据库权限和 outbox |
| WP1 SecretProvider 失败 | secret resolve `result=FAILED` 持续出现 | WP1/安全 owner，检查 provider 状态、用途、作用域、过期 |
| WP2 provider 不可用 | provider check `status=DOWN` 或 invocation `MODEL_PROVIDER_UNAVAILABLE` 升高 | `WP2-Provider接入与SecretRef轮换Runbook.md` |
| WP2 策略/预算阻断异常 | `MODEL_POLICY_VIOLATION` 或 `BUDGET_EXCEEDED` 激增 | WP2 + 项目 owner，确认项目策略和预算 |
| WP4 webhook 签名失败激增 | `signature_status=INVALID/EXPIRED/MISSING` 突增 | `WP4-Webhook签名样例与联调说明.md` |
| WP4 publish 冲突 | `result=CONFLICT_REVIEW_REQUIRED` 或失败发布升高 | WP4 + WP3 owner，确认 WP3 需求状态 |
| WP4 AI parse 质量下降 | `wp4_ai_parse_quality_eval.sh` 低于阈值 | WP4 + WP2 owner，回滚 prompt 或解析器 |

## 6. TraceId 串联

1. 外部请求优先传 `X-Trace-Id`；未传时由服务端生成并出现在响应 envelope。
2. 服务间同进程调用仍使用同一个 trace 上下文，不新增 WP2/WP3/WP4 到 WP1 的 HTTP 回调 trace。
3. WP2 调用日志必须保留 `actorService`、`projectId`、`status`、`providerName`、`errorCode`，用于从 WP4 AI 解析候选反查模型调用。
4. WP4 webhook 必须记录 `eventId`、`idempotencyKey`、`sourceCode`、`signatureStatus`、`traceId`；签名失败也要能定位但不能泄露密钥或完整签名。
5. 发布事故复盘按 `release notes -> traceId -> audit/invocation/webhook/asset record` 的顺序串联证据。
