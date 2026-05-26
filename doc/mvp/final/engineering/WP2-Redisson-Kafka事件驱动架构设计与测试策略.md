# WP2 Redisson + Kafka 事件驱动架构设计与测试策略

| 项目 | 内容 |
|---|---|
| 日期 | 2026-05-27 |
| 覆盖范围 | `platform-api` 公共事件层、WP2 模型异步调用、Redis provider resilience |
| 关联准出 | `WP1-WP4-统一发布准出清单.md` |

## 1. 任务定义

### 1.1 目标

1. 在架构层引入 Redis 与 Kafka，Redis 客户端统一使用 Redisson。
2. 将 WP2 异步模型调用从进程内线程池派发调整为事件驱动：API 提交只保存任务并发布事件，消费者收到事件后执行任务。
3. 异步链路必须保持同一 `traceId`：HTTP 入口、任务记录、事件 envelope、Kafka header、本地事件 worker、消费者执行日志和模型调用日志均可串联。
4. 修正多实例下不合理的本地状态：provider 熔断、限流和并发限制在 `redis` profile 下使用 Redis/Redisson 共享状态。

### 1.2 范围

| 模块 | 范围 |
|---|---|
| `common.event` | 新增 `PlatformEventEnvelope`、`PlatformEventPublisher`、本地事件总线、Kafka publisher/listener 和 trace-aware dispatcher。 |
| `common.redis` | 新增 RedissonClient 配置，启用 `redis` profile 时连接 Redis。 |
| `modelaccess` | WP2 异步模型任务改为 `ma_invocation_job` 持久化 + `model-access.invocation-job.requested` 事件 + executor 消费。 |
| `infra` | docker compose 增加 Redis、Kafka，并让 `platform-api` 使用 `db,redis,kafka` profile。 |
| 文档与测试 | 更新组件选型，补充事件 trace、Redisson 状态和并发控制测试。 |

### 1.3 非目标

1. 不把所有同步 API 改成 MQ；只有天然异步、可幂等重放的链路进入事件总线。
2. 不把 `platform-api` 拆成多个服务；本次仍是模块化单体内的事件边界。
3. 不把审计 `audit_outbox` 立即迁移到 Kafka；审计仍保持事务内写库 + outbox，后续按吞吐和可靠性要求迁移。
4. 不引入复杂延迟消息平台；本地/Kafka publisher 仅保留现有 WP2 dispatch delay 兼容能力，生产建议设置为 0。

### 1.4 风险与回滚

| 风险 | 控制 |
|---|---|
| Kafka 重复投递 | `ma_invocation_job` 使用 `QUEUED -> RUNNING` 条件更新，重复事件无法重复执行。 |
| 进程崩溃留下 RUNNING 任务 | 启动恢复只标记超过 `WP2_ASYNC_JOB_RUNNING_TIMEOUT_MS` 的 stale running 任务失败，避免多实例误伤仍在运行的任务。 |
| Redis/Kafka 不可用 | 默认本地 profile 不强依赖；集成部署启用 `redis,kafka` profile，Kafka 发布失败会以同进程 dispatcher 兜底执行当前事件，故障时也可回滚到仅 `db` profile + local event bus。 |
| traceId 丢失 | 事件 envelope 写入 `traceId`，Kafka header 同步写 `X-Trace-Id`，dispatcher 在 handler 前恢复 `TraceContext` 和 MDC。 |

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

Kafka topic 默认：`veri-agent.model-invocation-job-requested`。

Kafka header：

| Header | 内容 |
|---|---|
| `X-Trace-Id` | 与 envelope `traceId` 一致。 |
| `X-Platform-Event-Type` | 与 envelope `eventType` 一致。 |

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

## 4. 测试策略与用例

| 用例 | 验证方式 |
|---|---|
| 事件 dispatcher 恢复 traceId/MDC | `PlatformEventDispatcherTest`。 |
| WP2 异步任务提交、事件消费、调用日志写入 | `ModelAccessControllerTest#submitsAsyncInvocationJobAndPersistsInvocationLog`。 |
| 排队任务取消后事件到达不执行 | `ModelAccessControllerTest#cancelsQueuedAsyncInvocationJobWithoutWritingInvocationLog`。 |
| Redisson 熔断/限流跨实例共享 | `RedisProviderResilienceStateStoreTest`。 |
| Redisson provider 并发限制跨实例共享 | `RedisProviderResilienceStateStoreTest#limitsProviderConcurrencyAcrossRedissonLimiterInstances`。 |
| DB 任务恢复只处理 stale running | `DbProfileRepositoryContractTest`。 |

## 5. 五角色结论

| 角色 | 结论 | 说明 |
|---|---|---|
| 资深项目经理 | 通过 | 范围限定在公共事件层、WP2 异步任务和 Redisson 状态，回滚可切回非 `redis,kafka` profile。 |
| 资深产品经理 | 通过 | API 契约不变，用户可继续通过任务 ID 查询结果，traceId 排障能力增强。 |
| 资深服务端架构师 | 通过 | Kafka 事件总线、Redisson 状态、幂等消费和 stale recovery 已形成清晰边界。 |
| 资深前端工程师 | 无影响 | 未改变前端路由、表单和响应结构；错误响应仍保留 traceId。 |
| 资深质量工程师 | 有条件通过 | 代码级验证覆盖本地事件与 Redisson；真实 Kafka/Redis 联调需在集成环境按 docker compose 或预发 profile 执行。 |
