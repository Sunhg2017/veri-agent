# WP9 Scheduler Worker Hosting

| 项 | 内容 |
|---|---|
| 目标 | 将 WP9 scheduler 托管方式从“所有实例都可能启用”收敛为可审查的 web / active worker / standby worker 三类角色 |
| 验证入口 | `bash scripts/wp9_worker_hosting_readiness.sh` |
| 运行时代码变更 | 无 |

## 1. 推荐角色

| 角色 | scheduler | cron | webhook | 用途 |
|---|---|---|---|---|
| `web` | 关闭 | 关闭 | 按需开启 | 承载前端/API 查询、计划管理、手动触发和 webhook ingress。 |
| `scheduler-active` | 开启 | 按需开启 | 关闭 | 专用 scheduler 实例，执行 recovery、CRON scan、queue claim 和 dispatch。 |
| `scheduler-standby` | 关闭 | 关闭 | 关闭 | 备用 worker 配置，保留相同镜像和 DB/Kafka/Redis 连接，故障切换时改为 active。 |

生产环境建议仅保留一个 `scheduler-active`，避免多个实例竞争同一个 workerId 带来排障困难。WP9 仓储层已有 claim 条件更新和 active claim 约束，多个 worker 不应重复认领同一节点；但发布与排障仍应优先保持 workerId 唯一、角色清晰、证据可追踪。

## 2. 示例文件

| 文件 | 说明 |
|---|---|
| `web.env.example` | web/API 实例示例，scheduler 和 cron 默认关闭，可按需启用 webhook ingress。 |
| `scheduler-active.env.example` | 专用 active worker 示例，scheduler 开启，cron 可开启，webhook 关闭。 |
| `scheduler-standby.env.example` | standby worker 示例，所有外部触发和 scheduler 均关闭。 |

## 3. Readiness

验证当前 shell：

```bash
bash scripts/wp9_worker_hosting_readiness.sh
```

验证示例文件：

```bash
WP9_WORKER_HOSTING_ENV_FILE=integrations/wp9-worker-hosting/scheduler-active.env.example \
bash scripts/wp9_worker_hosting_readiness.sh
```

验证 release 角色时，必须显式声明 release smoke 证据：

```bash
WP9_WORKER_HOSTING_ROLE=scheduler-active \
WP9_SCHEDULER_ENABLED=true \
WP9_CRON_ENABLED=true \
WP9_WEBHOOK_ENABLED=false \
WP9_SCHEDULER_WORKER_ID=wp9-prod-active-01 \
WP9_RELEASE_GATE=1 \
WP9_SCHEDULER_SMOKE=managed \
WP9_WEBHOOK_HTTP_SMOKE=managed \
bash scripts/wp9_worker_hosting_readiness.sh
```

## 4. 切换与回滚

1. active worker 故障时，先将原实例 `WP9_SCHEDULER_ENABLED=false`，保留日志和 traceId。
2. 在 standby 实例上设置唯一 `WP9_SCHEDULER_WORKER_ID`，再启用 `WP9_SCHEDULER_ENABLED=true`。
3. 切换后执行 `scripts/wp9_scheduler_smoke.sh` 或受控 release gate，并记录 workerId、开关状态和 smoke 结果。
4. 回滚时先关闭新 active，再恢复旧 active；不要同时打开两个相同 workerId 的 scheduler 实例。
