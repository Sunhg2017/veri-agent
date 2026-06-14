# WP9 Scheduler 与 Trigger Runbook

| 项目 | 内容 |
|---|---|
| 工作包 | WP9 执行编排与任务调度 |
| 文档性质 | scheduler、cron、webhook、恢复、重放、密钥和发布准出 runbook |
| 当前口径 | 已提供后台 scheduler loop、生产 CRON scanner、webhook HTTP smoke、CRON 容量/backlog smoke、CI 签名样例、worker 托管 readiness 和 release gate |
| 日期 | 2026-06-14 |

## 1. 适用范围

本 Runbook 适用于 WP9 开发、预发和生产发布准出。WP9 当前仍由 `platform-api` 承载执行编排控制面，默认关闭 scheduler、webhook 和 cron 外部触发；只有显式开启配置和 trigger 状态后才会认领队列或接受外部事件。WP9 不直接调用 runner adapter，`API_TEST` 节点只通过 WP6 应用服务调度已审批 script bundle。

## 2. 开关和配置

| 配置 | 默认 | 说明 |
|---|---|---|
| `WP9_SCHEDULER_ENABLED` / `veri-agent.execution.scheduler-enabled` | `false` | 是否启用后台 scheduler loop。 |
| `WP9_WEBHOOK_ENABLED` / `veri-agent.execution.webhook-enabled` | `false` | 是否允许 `/api/v1/execution/webhooks/{id}` 接收外部触发。 |
| `WP9_CRON_ENABLED` / `veri-agent.execution.cron-enabled` | `false` | 是否启用 CRON trigger 扫描。 |
| `WP9_WEBHOOK_CLOCK_SKEW_SECONDS` / `veri-agent.execution.webhook-clock-skew-seconds` | `300` | webhook 签名时间戳允许偏移，服务端按 `1..86400` 归一化。 |
| `veri-agent.execution.scheduler-interval-ms` | `5000` | scheduler fixed delay，服务端按安全边界归一化。 |
| `veri-agent.execution.scheduler-initial-delay-ms` | `30000` | scheduler 初始延迟。 |
| `veri-agent.execution.scheduler-worker-id` | `wp9-managed-worker` | scheduler 写入 queue claim 的 workerId。 |
| `veri-agent.execution.scheduler-tick-batch-size` | `4` | 单次 tick 最大认领节点数。 |
| `veri-agent.execution.node-heartbeat-timeout-seconds` | `180` | node heartbeat/claim 恢复参考超时。 |
| `veri-agent.execution.recovery-batch-size` | `50` | 单次恢复扫描批量。 |

生产建议分阶段启用：先 `scheduler-enabled=true` 验证手动 run 调度，再启 `webhook-enabled=true` 联调外部 CI，最后启 `cron-enabled=true` 扫描到期 CRON trigger。

## 2.1 Worker 托管角色

| 角色 | 关键开关 | 说明 |
|---|---|---|
| `web` | `WP9_SCHEDULER_ENABLED=false`、`WP9_CRON_ENABLED=false`、`WP9_WEBHOOK_ENABLED` 按需开启 | 承载 API、前端查询、计划管理、手动触发和 webhook ingress。 |
| `scheduler-active` | `WP9_SCHEDULER_ENABLED=true`、`WP9_WEBHOOK_ENABLED=false`、`WP9_CRON_ENABLED` 按需开启 | 专用 scheduler 实例，执行 recovery、CRON scan、queue claim 和 dispatch。 |
| `scheduler-standby` | scheduler、cron、webhook 均关闭 | 备用实例，故障切换时先确认旧 active 已关闭，再改为 active。 |

离线 readiness：

```bash
WP9_WORKER_HOSTING_ENV_FILE=integrations/wp9-worker-hosting/scheduler-active.env.example \
bash scripts/wp9_worker_hosting_readiness.sh
```

生产建议同一环境只保留一个 `scheduler-active` workerId。多 worker 条件认领不会重复执行同一 node run，但会增加 claim、heartbeat 和故障排查复杂度。

## 3. 日常验证

开发默认 gate：

```bash
bash scripts/wp9_quality_gate.sh
```

仅验证 scheduler：

```bash
bash scripts/wp9_scheduler_smoke.sh
```

仅验证 CRON 容量策略：

```bash
bash scripts/wp9_cron_capacity_smoke.sh
```

仅验证 CRON 积压批次策略：

```bash
bash scripts/wp9_cron_backlog_smoke.sh
```

仅验证 worker 托管配置：

```bash
bash scripts/wp9_worker_hosting_readiness.sh
```

仅验证 webhook HTTP 入口：

```bash
WP9_WEBHOOK_SMOKE_MODE=managed bash scripts/wp9_webhook_http_smoke.sh
```

仅验证外部 CI 签名 helper：

```bash
WP9_WEBHOOK_OUTPUT=signature \
WP9_WEBHOOK_SECRET='secret' \
WP9_WEBHOOK_TRIGGER_ID='trigger-1' \
WP9_WEBHOOK_TIMESTAMP='100' \
WP9_WEBHOOK_EVENT_ID='evt-1' \
WP9_WEBHOOK_PAYLOAD='{"ok":true}' \
bash scripts/wp9_webhook_sign.sh
```

发布模式必须显式启用 scheduler smoke 和 webhook HTTP smoke：

```bash
WP9_GATE_MODE=release \
WP9_SCHEDULER_SMOKE=managed \
WP9_WEBHOOK_HTTP_SMOKE=managed \
bash scripts/wp9_quality_gate.sh
```

## 4. 发布准出检查点

1. release/preprod/prod gate 未显式配置 `WP9_SCHEDULER_SMOKE=managed` 必须阻断。
2. release/preprod/prod gate 未显式配置 `WP9_WEBHOOK_HTTP_SMOKE=managed` 或已评审的 `external` 必须阻断。
3. `GET /api/v1/execution/health` 只展示开关、limits 和 readiness，不展示 secret、allowlist 明细或 trigger key。
4. WEBHOOK trigger 启用必须配置 `secretRef`，签名串只能是 `timestamp.eventId.rawBody`。
5. CRON trigger 必须通过服务端 `CronExpression` 和 `ZoneId` 校验；不做错过多次 fire 的批量补偿。
6. run detail、trigger event、audit 和 export 不得包含 webhook secret、signature、raw payload、secretRef 明文、raw baseUrl 或请求响应正文。
7. scheduler tick 先 recovery，再扫描 due CRON，再 claim/dispatch；dispatch 只通过 WP6 应用服务。
8. web/API 实例不得误启 scheduler；专用 scheduler worker 不承载 webhook ingress；standby worker 未切换前必须保持 scheduler/cron/webhook 关闭。

## 5. 恢复和重放

| 场景 | 推荐操作 | 证据 |
|---|---|---|
| RUNNING 节点 claim 过期 | 调用内部 recovery 或等待 scheduler tick 自动恢复；未超过节点 timeout 的节点会回到 QUEUED。 | `execution_queue_claim.status=EXPIRED`、run detail node 状态。 |
| 节点已超过 timeout | recovery 将节点收敛为 `TIMEOUT`，fail-fast 下游节点会 `BLOCKED`。 | run detail、node resultSummary errorCode。 |
| 手动触发重放 | 使用原 `requestKey` 重新触发 READY plan；服务端返回既有 run。 | run detail `idempotentReplay=true`。 |
| webhook 重放 | 使用同一 `X-VA-Event-Id` 和同一 raw body 重发；服务端返回 DUPLICATE/幂等回放。 | trigger events 中同一 `sourceEventId` 和 requestDigest。 |
| cron 重放 | 不直接改库；确认 `nextFireAt`、trigger 状态和 cron 配置后，由下一次 scheduler scan 触发。 | trigger event `sourceEventId` 形如 `cron:*`，run summary `cronPayloadStored=false`。 |
| 失败节点重试 | 对 `FAILED/PARTIAL_SUCCESS/TIMEOUT` run 调用 retry 控制面；服务端只插入最新失败/超时/阻断节点的新 attempt。 | run detail 中 retryAttempt 和新 node run。 |

不要直接删除 run、node run、trigger event 或 queue claim。生产修复优先通过控制面重试、恢复和禁用 trigger 完成；确需数据修复时必须保留审计工单、SQL、影响范围和回滚动作。

## 6. Webhook Secret 轮换

1. 在 WP1 SecretProvider 中创建新 `WEBHOOK_SIGNING` secret，scope 为对应项目。
2. 先保留旧 CI secret，更新 WP9 trigger `secretRef` 指向新引用。
3. 在 CI secret store 中更新对应明文值；CI 侧保存 secret 明文，不保存 `secret://` 引用。
4. 使用 `scripts/wp9_webhook_sign.sh` 或 M7D managed/external smoke 验证新 secret 签名可用。
5. 观察一个发布窗口后停用或撤销旧 secret；保留轮换工单和 trigger event 证据。

轮换期间不要在日志、release notes、run export 或 CI artifact 中输出 secret 明文和 signature。误泄露时立即禁用 trigger、撤销 secret、更新 CI secret store，并重跑 webhook smoke。

## 7. CRON 运维策略

| 项 | 策略 |
|---|---|
| 新增 CRON trigger | 先 dryRun，再启用；未传 `nextFireAt` 时由服务端计算。 |
| 暂停触发 | 将 trigger 状态改为 `DISABLED`，不要清空 cron 配置。 |
| 变更频率 | 更新 cron/timezone 后确认下一次 `nextFireAt`，避免高频误触发。 |
| 错过多次 fire | 当前只按每个 due `nextFireAt` 创建一次 run，不批量补偿历史窗口；需要历史回补时必须单独设计 backfill 限额。 |
| 积压批次 | 单次 scheduler tick 只扫描 `schedulerTickBatchSize` 上限内的 due CRON trigger，未扫描的 due trigger 留到后续 tick 接续处理。 |
| 容量保护 | 高风险计划先保持 `manualEnabled=true`，观察 scheduler queue 后再启 cron；积压明显时优先扩容 scheduler worker 或下调 trigger 频率。 |
| 排障证据 | 记录 triggerId、sourceEventId、requestDigest、runId、nextFireAt 和 traceId。 |

## 8. 排障表

| 现象 | 常见原因 | 处理 |
|---|---|---|
| release gate 提示 scheduler smoke required | 未设置 `WP9_SCHEDULER_SMOKE=managed` | 补环境变量后重跑 gate。 |
| release gate 提示 webhook HTTP smoke required | 未设置 `WP9_WEBHOOK_HTTP_SMOKE=managed/external` | 本地发布优先 managed；外部目标需先人工评审。 |
| `EXECUTION_TRIGGER_DISABLED` | 全局 webhook/cron 关闭、trigger 禁用、计划 triggerPolicy 不允许 | 检查 health、plan triggerPolicy 和 trigger 状态。 |
| `EXECUTION_TRIGGER_SIGNATURE_INVALID` | raw body 被改写、secret 错误、timestamp 过期、canonical string 拼错 | 使用 `scripts/wp9_webhook_sign.sh` 固定 raw body 和 header，校准 CI 时钟。 |
| webhook 重试创建新 run | eventId 变化或 payload 变化 | 同一外部事件重试必须复用 eventId 和 raw body。 |
| scheduler 不认领 QUEUED 节点 | scheduler 未启、batch size 为 0、并发上限命中、计划资源不可用 | 查看 health、scheduler 配置、run detail 和 WP6 bundle 状态。 |
| standby worker 抢占队列 | standby 实例误设 `WP9_SCHEDULER_ENABLED=true` 或复用了 active workerId | 立即关闭 standby scheduler，运行 `scripts/wp9_worker_hosting_readiness.sh`，保留 queue claim 和日志证据。 |
| API_TEST 节点失败 | WP6 runner disabled、allowlist 阻断、secretRef 解析失败、baseUrlRef 环境停用 | 查看 node errorCode、WP6 runner runbook 和 WP1 环境配置。 |
| cron 未触发 | `cron-enabled=false`、trigger disabled、`nextFireAt` 未到、cron/timezone 无效 | dryRun trigger，查询 trigger detail 和 events。 |
| missed fire 反复补偿 | `nextFireAt` 未推进或触发器被反复重放 | 查看 `wp9_cron_capacity_smoke.sh`，确认每个 due 时间只 materialize 一次。 |
| CRON 积压处理过慢 | due trigger 数量超过单 tick batch 或 scheduler worker 数不足 | 查看 `wp9_cron_backlog_smoke.sh`，确认限批语义；再评估 batch、worker 数和 trigger 频率。 |
| run export 泄露敏感内容 | redaction 策略回归或上游摘要夹带敏感字段 | 立即禁用 trigger/scheduler，修复脱敏后重跑 WP9 quality gate。 |

## 9. 回滚

发现误触发、调度异常或敏感泄露时按影响面执行：

1. 关闭外部触发：设置 `WP9_WEBHOOK_ENABLED=false` 和/或将相关 WEBHOOK trigger 置为 `DISABLED`。
2. 暂停定时触发：设置 `WP9_CRON_ENABLED=false` 或禁用具体 CRON trigger。
3. 暂停后台调度：设置 `WP9_SCHEDULER_ENABLED=false`，保留控制面只读查询和手动修复。
4. worker 故障切换：先关闭旧 active，再将 standby 改为唯一 `scheduler-active`，并使用唯一 `WP9_SCHEDULER_WORKER_ID`。
5. 撤销或轮换 webhook secret，更新 CI secret store。
6. 保留 run、node run、trigger event、queue claim 和 audit 证据；不要直接删除审计数据。
7. 修复后按 `scripts/wp9_quality_gate.sh` release 模式重跑准出，再分阶段恢复开关。

## 10. 准出记录

发布或外部 CI/cron 接入工单至少记录：

1. WP9 quality gate 命令和结果。
2. scheduler/webhook/cron 开关状态和目标环境。
3. 涉及 planId、triggerId、项目 scope、secretRef digest、CI 系统名。
4. webhook smoke 或 CI 签名样例验证结果。
5. cron 表达式、timezone、nextFireAt 和是否允许补偿历史窗口。
6. 任何跳过项、豁免、回滚开关和责任人。
7. worker 托管角色、workerId、standby 切换记录和 readiness 结果。
