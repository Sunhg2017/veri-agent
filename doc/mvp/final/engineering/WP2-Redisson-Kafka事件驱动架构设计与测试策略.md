# WP2 Redisson + Kafka 事件驱动架构设计与测试策略

| 项目 | 内容 |
|---|---|
| 日期 | 2026-05-27 |
| 覆盖范围 | `platform-api` 公共事件层、WP1 会话/权限/审计热路径、WP2 模型异步调用、Redis provider resilience |
| 关联准出 | `WP1-WP4-统一发布准出清单.md` |

## 1. 任务定义

### 1.1 目标

1. 在架构层引入 Redis 与 Kafka，Redis 客户端统一使用 Redisson。
2. 将 WP2 异步模型调用从进程内线程池派发调整为事件驱动：API 提交只保存任务并发布事件，消费者收到事件后执行任务。
3. 异步链路必须保持同一 `traceId`：HTTP 入口、任务记录、事件 envelope、Kafka header、本地事件 worker、消费者执行日志和模型调用日志均可串联。
4. 修正多实例下不合理的本地状态：provider 熔断、限流和并发限制在 `redis` profile 下使用 Redis/Redisson 共享状态。
5. 将认证会话、RBAC 高频鉴权和审计写入纳入 Redis/Kafka 架构边界：`redis` profile 下缓存会话与权限，`kafka` profile 下审计先发布事件再异步落库。

### 1.2 范围

| 模块 | 范围 |
|---|---|
| `common.event` | 新增 `PlatformEventEnvelope`、`PlatformEventPublisher`、本地事件总线、Kafka publisher/listener 和 trace-aware dispatcher。 |
| `common.redis` | 新增 RedissonClient 配置，启用 `redis` profile 时连接 Redis。 |
| `auth` | `db,redis` profile 使用 `RedisAuthSessionStore`，会话写穿 PostgreSQL，热点校验和刷新令牌索引走 Redisson 短 TTL 缓存。 |
| `authorization` | `db,redis` profile 使用 `RedisPermissionResolver`，角色权限聚合和资源作用域鉴权结果走 Redisson 短 TTL 缓存。 |
| `common.audit` | `db,kafka` profile 使用 `KafkaAuditLogWriter` 发布 `audit.log-recorded` 事件，由事件 handler 恢复 trace 后异步写 `audit_log`。 |
| `modelaccess` | WP2 异步模型任务改为 `ma_invocation_job` 持久化 + `model-access.invocation-job.requested` 事件 + executor 消费。 |
| `infra` | docker compose 增加 Redis、Kafka，并让 `platform-api` 使用 `db,redis,kafka` profile。 |
| 文档与测试 | 更新组件选型，补充事件 trace、Redisson 状态和并发控制测试。 |

### 1.3 非目标

1. 不把所有同步 API 改成 MQ；只有天然异步、可幂等重放的链路进入事件总线。
2. 不把 `platform-api` 拆成多个服务；本次仍是模块化单体内的事件边界。
3. 不把事件驱动等同于拆分服务；WP4 导入、发布和 webhook 本轮仍由 `platform-api` 内的本地/Kafka 事件消费者处理，后续如拆独立 worker 需复用同一事件契约。
4. 不引入复杂延迟消息平台；本地/Kafka publisher 仅保留现有 WP2 dispatch delay 兼容能力，生产建议设置为 0。

### 1.4 风险与回滚

| 风险 | 控制 |
|---|---|
| Kafka 重复投递 | `ma_invocation_job` 使用 `QUEUED -> RUNNING` 条件更新，重复事件无法重复执行。 |
| 进程崩溃留下 RUNNING 任务 | 启动恢复只标记超过 `WP2_ASYNC_JOB_RUNNING_TIMEOUT_MS` 的 stale running 任务失败，避免多实例误伤仍在运行的任务。 |
| Redis/Kafka 不可用 | 默认本地 profile 不强依赖；集成部署启用 `redis,kafka` profile，Kafka 发布失败会以同进程 dispatcher 兜底执行当前事件，故障时也可回滚到仅 `db` profile + local event bus。 |
| traceId 丢失 | 事件 envelope 写入 `traceId`，Kafka header 同步写 `X-Trace-Id`，dispatcher 在 handler 前恢复 `TraceContext` 和 MDC。 |
| 权限或会话缓存陈旧 | 会话和 scope 鉴权只使用短 TTL；注销/刷新路径会主动写回撤销状态，权限绑定变更最多在 TTL 窗口内收敛。 |

## 2. 产品 PRD

| 项目 | 内容 |
|---|---|
| 用户价值 | 用户提交耗时模型任务后，API 快速返回任务 ID；后台异步执行不受单实例本地队列限制，排障时可用一个 traceId 串联全链路。 |
| 用户入口 | `POST /api/v1/model-access/invocations/jobs`、`GET /api/v1/model-access/invocations/jobs/{jobId}`、取消接口保持不变。 |
| 成功标准 | 提交返回 `QUEUED`；事件消费后任务进入 `SUCCEEDED/FAILED/CANCELLED`；响应和日志 traceId 与任务记录一致。 |
| 边界条件 | 取消已排队任务必须不写调用日志；运行中取消记录 cancel requested，不强杀跨实例消费者线程。 |
| 非目标 | 不在前端新增页面；现有 API 契约不破坏。 |

## 3. 技术设计与接口契约

### 3.1 事件 envelope

```json
{
  "eventId": "uuid",
  "eventType": "model-access.invocation-job.requested",
  "aggregateId": "<jobId>",
  "traceId": "trc_xxx",
  "occurredAt": "2026-05-27T00:00:00Z",
  "payload": {
    "jobId": "<jobId>"
  }
}
```

Kafka topic 默认：

| 事件 | Topic |
|---|---|
| `model-access.invocation-job.requested` | `veri-agent.model-invocation-job-requested` |
| `audit.log-recorded` | `veri-agent.audit-log-recorded` |

Kafka header：

| Header | 内容 |
|---|---|
| `X-Trace-Id` | 与 envelope `traceId` 一致。 |
| `X-Platform-Event-Type` | 与 envelope `eventType` 一致。 |

WP4 文档输入本轮补充的事件：

| 事件 | Topic | 触发点 | 消费动作 |
|---|---|---|---|
| `document-input.import.requested` | `veri-agent.document-input-import-requested` | 文本、Markdown、Word、PDF、OCR、`CUSTOM_API` 导入记录保存为 `MODEL_PARSE_QUEUED` 后 | 抽取原文、调用 WP2 模型解析或规则 fallback，生成候选并更新导入状态。 |
| `document-input.publish.requested` | `veri-agent.document-input-publish-requested` | 非 dryRun 发布将导入和候选标记为 `PUBLISH_QUEUED` 后 | 调用 WP3 应用服务 upsert 需求资产，写发布记录和候选结果。 |
| `document-input.webhook.accepted` | `veri-agent.document-input-webhook-accepted` | Webhook 通过来源、签名、幂等、限流和大小校验并落库后 | 解析 webhook payload，驱动导入解析，并把 webhook 事件更新为 `PROCESSED/FAILED/DEAD_LETTER`。 |

### 3.2 任务状态机

```text
HTTP submit
  -> ma_invocation_job: QUEUED(trace_id)
  -> publish ModelInvocationJobRequestedEvent
  -> local/Kafka consumer
  -> conditional update QUEUED -> RUNNING
  -> invoke provider
  -> SUCCEEDED / FAILED
```

取消语义：

```text
QUEUED -> CANCELLED
RUNNING -> RUNNING + error_code=CANCEL_REQUESTED
terminal -> no-op
```

### 3.3 Redis/Redisson 使用

| 能力 | Redisson 结构 |
|---|---|
| provider 熔断状态 | `RMapCache` + provider 维度 `RLock`。 |
| provider 限流窗口 | `RAtomicLong` + TTL。 |
| provider 并发限制 | `RSemaphore`，跨实例共享可用 permit。 |
| 认证会话缓存 | `RMapCache` 保存 session 记录和 refresh token 索引，写穿 PostgreSQL，活动会话最大缓存 30 秒。 |
| 权限缓存 | `RMapCache` 保存角色权限集合和 scope 鉴权布尔结果，按 30-60 秒短 TTL 收敛。 |

### 3.4 截图架构清单处理结论

| 条目 | 处理 |
|---|---|
| AuthSessionStore 无缓存抽象 | 已处理：新增 `RedisAuthSessionStore`，`db,redis` profile 自动切换。 |
| Provider 限流器本地内存 | 已处理：上一轮已新增 `ProviderConcurrencyLimiter` 和 `RedissonProviderConcurrencyLimiter`，限流窗口也在 Redisson。 |
| PermissionResolver 无缓存 | 已处理：新增 `RedisPermissionResolver`，角色权限和资源 scope 决策使用短 TTL Redis 缓存。 |
| WP4 -> WP2 模型解析同步阻塞 | 已处理：导入接口先保存 raw payload 和 `MODEL_PARSE_QUEUED` 状态，提交 `document-input.import.requested` 事件，消费者幂等认领后执行二进制抽取、WP2 模型解析和规则 fallback。 |
| WP4 -> WP3 资产创建同步调用 | 已处理：发布接口将导入和候选置为 `PUBLISH_QUEUED`，提交 `document-input.publish.requested` 事件，消费者进入 `PUBLISHING` 后调用 WP3 应用服务并写发布结果。 |
| 审计写入同步 `REQUIRES_NEW` | 已处理：`db,kafka` profile 下发布 `audit.log-recorded` 事件异步落库；非 Kafka profile 仍保留同步写库便于本地和测试。 |
| Webhook 事件处理同步编排 | 已处理：webhook 入口只做安全校验、幂等落库并返回 `ACCEPTED` 事件回执，随后发布 `document-input.webhook.accepted` 事件；消费者再创建导入批次、解析 payload 并更新事件状态。重放和自动重试同样只提交事件并保留可查询状态。 |

### 3.5 WP4 三条同步链路改造

本轮将 WP4 三条原同步链路切到事件驱动，API ingress 不再把长耗时解析、模型调用或 WP3 写入放在 HTTP 请求线程内完成。

| 链路 | API 返回语义 | 后台状态机 | 幂等与重放 |
|---|---|---|---|
| 导入解析 | `POST /api/v1/document-input/imports` 和 multipart 返回 `MODEL_PARSE_QUEUED`，`totalParsed=0`。 | `MODEL_PARSE_QUEUED -> MODEL_PARSE_RUNNING -> SUCCEEDED/FAILED`。 | `DocumentImportPayload` 保存 raw payload；消费者通过条件更新认领，重复事件只返回当前记录。 |
| 发布写入 | 非 dryRun `POST /imports/{id}/publish` 返回 `PUBLISH_QUEUED`。 | import/candidate `PUBLISH_QUEUED -> PUBLISHING -> PUBLISHED/PUBLISH_FAILED`，import 完成后汇总数量。 | WP3 upsert 仍使用 `externalRequirementId/sourceRef`，重复事件不会重复创建资产。 |
| Webhook 接收 | `POST /webhooks/{sourceCode}` 在安全校验后返回 `ACCEPTED` webhook 事件回执；导入批次由后台消费者创建，失败解析在事件状态中查询。 | webhook `ACCEPTED -> PROCESSING -> PROCESSED/FAILED/DEAD_LETTER/REPLAYED`。 | 保留 eventId + idempotencyKey 去重；重复投递返回同一 webhook 事件，不重复创建候选；人工 replay 和自动 retry 只重新发布 accepted 事件。 |

`DocumentInputEventRecoveryService` 会在服务启动和定时任务中扫描持久化队列态记录，并重新发布 `MODEL_PARSE_QUEUED` 导入解析事件、`PUBLISH_QUEUED` 发布事件和 `ACCEPTED` webhook 事件。恢复服务不直接改写状态，实际处理权仍由消费者的条件状态更新认领；这样即使本地事件总线或 Kafka 投递中断，重发也只补偿未被消费的队列态记录。运行中状态超时回滚暂不纳入本轮，避免长文档解析或 WP3 写入被误判为 stale。

Trace 串联方式保持统一：HTTP 入口的 `TraceContext` 写入 `PlatformEventEnvelope.traceId`、审计日志和 webhook 重放记录，Kafka header 同步写 `X-Trace-Id`，消费者由 `PlatformEventDispatcher` 恢复 MDC 后再进入业务 handler。日志中可通过同一 `trace_id` 串起 ingress、事件发布、Kafka/local dispatch、模型解析、WP3 写入和失败重放。

## 4. 测试策略与用例

| 用例 | 验证方式 |
|---|---|
| 事件 dispatcher 恢复 traceId/MDC | `PlatformEventDispatcherTest`。 |
| WP2 异步任务提交、事件消费、调用日志写入 | `ModelAccessControllerTest#submitsAsyncInvocationJobAndPersistsInvocationLog`。 |
| 排队任务取消后事件到达不执行 | `ModelAccessControllerTest#cancelsQueuedAsyncInvocationJobWithoutWritingInvocationLog`。 |
| Redisson 熔断/限流跨实例共享 | `RedisProviderResilienceStateStoreTest`。 |
| Redisson provider 并发限制跨实例共享 | `RedisProviderResilienceStateStoreTest#limitsProviderConcurrencyAcrossRedissonLimiterInstances`。 |
| Redis 会话缓存和撤销写回 | `RedisAuthSessionStoreTest`。 |
| Redis 权限聚合和 scope 决策缓存 | `RedisPermissionResolverTest`。 |
| Kafka 审计事件发布保持 traceId | `KafkaAuditLogWriterTest`。 |
| DB 任务恢复只处理 stale running | `DbProfileRepositoryContractTest`。 |
| WP4 导入、发布、webhook 事件驱动状态机 | `DocumentInputControllerTest`、`DocumentInputModelParseControllerTest`、`DocumentBinaryImportControllerTest`、`DocumentWebhookAutoRetryServiceTest`。 |
| WP4 队列态事件补偿发布 | `DocumentInputEventRecoveryServiceTest`。 |

## 5. 五角色结论

| 角色 | 结论 | 说明 |
|---|---|---|
| 资深项目经理 | 通过 | 范围限定在公共事件层、WP2 异步任务和 Redisson 状态，回滚可切回非 `redis,kafka` profile。 |
| 资深产品经理 | 通过 | API 契约不变，用户可继续通过任务 ID 查询结果，traceId 排障能力增强。 |
| 资深服务端架构师 | 通过 | Kafka 事件总线、Redisson 状态、幂等消费和 stale recovery 已形成清晰边界。 |
| 资深前端工程师 | 无影响 | 未改变前端路由、表单和响应结构；错误响应仍保留 traceId。 |
| 资深质量工程师 | 有条件通过 | 代码级验证覆盖本地事件与 Redisson；真实 Kafka/Redis 联调需在集成环境按 docker compose 或预发 profile 执行。 |
