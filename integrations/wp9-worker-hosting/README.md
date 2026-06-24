# WP9 Scheduler Worker Hosting

| 项 | 内容 |
|---|---|
| 目标 | 将 WP9 scheduler 托管方式从“所有实例都可能启用”收敛为可审查的 web / active worker / standby worker 三类角色 |
| 验证入口 | `bash scripts/wp9_worker_hosting_readiness.sh` |
| 运行时代码变更 | WP9 scheduler tick 已接入 leader lock；health 会同步暴露 XXL-JOB 调度载体和锁 provider 是否就绪 |

## 1. 推荐角色

| 角色 | scheduler | cron | webhook | 用途 |
|---|---|---|---|---|
| `web` | 关闭 | 关闭 | 按需开启 | 承载前端/API 查询、计划管理、手动触发和 webhook ingress。 |
| `scheduler-active` | 开启 | 按需开启 | 关闭 | 专用 scheduler 实例，执行 recovery、CRON scan、queue claim 和 dispatch。 |
| `scheduler-standby` | 关闭 | 关闭 | 关闭 | 备用 worker 配置，保留相同镜像和 DB/Kafka/Redis 连接，故障切换时改为 active。 |

三类角色都应保持 `PLATFORM_XXL_JOB_ENABLED=true`，这样共享 XXL-JOB handler 始终完成注册；真正决定 WP9 scheduler/cron 是否执行的是 `WP9_SCHEDULER_ENABLED` 与 `WP9_CRON_ENABLED`。

生产环境允许部署多个 `scheduler-active` 实例，但必须启用 `WP9_SCHEDULER_LEADER_LOCK_ENABLED=true` 并让 `SPRING_PROFILES_ACTIVE` 包含 `redis`，由 Redisson 分布式锁保证同一时刻只有一个实例执行 CRON scan、recovery、queue claim 和 dispatch。每个 active 实例仍应使用唯一 `WP9_SCHEDULER_WORKER_ID`，方便排障和审计。

本仓库当前的独立外部 worker 形态复用同一 `platform-api` 镜像和 `executionSchedulerJob` XXL-JOB handler，通过 env 角色隔离 web/API ingress 与 scheduler 执行面；真正拆分新的 worker 二进制或 Maven 模块仍属于后续平台化封装。

## 2. Leader Lock

| 配置 | 建议值 | 说明 |
|---|---|---|
| `WP9_SCHEDULER_LEADER_LOCK_ENABLED` | `true` | active worker 必须启用；紧急回滚到单活时可临时关闭。 |
| `WP9_SCHEDULER_LEADER_LOCK_NAME` | `wp9:execution:scheduler:leader` | 多实例共享同一个锁名。 |
| `WP9_SCHEDULER_LEADER_LOCK_WAIT_MS` | `0` | 锁被占用时立即跳过本 tick，等待下一次 XXL-JOB 调度。 |
| `WP9_SCHEDULER_LEADER_LOCK_LEASE_MS` | `120000` | 租约必须大于 scheduler interval，避免正常 tick 中途过期。 |
| `SPRING_PROFILES_ACTIVE` | `db,redis` | active worker 必须包含 `redis`，否则只会得到本地 JVM 锁，不能支撑多活。 |
| `PLATFORM_REDIS_ADDRESS` | `redis://redis:6379` | Redisson 连接地址，和平台 Redis profile 共用。 |

锁竞争失败的 tick 会返回 `leaderLockAcquired=false`、`skipReason=LEADER_LOCK_BUSY`，不会触碰 CRON scanner、claim recovery 或 queue claim。`redis` profile 未启用时，服务端使用 `LOCAL_JVM` 锁作为开发/单进程兜底，health 中 `schedulerMultiActiveReady=false`。

## 3. 示例文件

| 文件 | 说明 |
|---|---|
| `web.env.example` | web/API 实例示例，scheduler 和 cron 默认关闭，可按需启用 webhook ingress。 |
| `scheduler-active.env.example` | 专用 active worker 示例，scheduler 开启，cron 可开启，webhook 关闭，Redis leader lock 开启。 |
| `scheduler-standby.env.example` | standby worker 示例，所有外部触发和 scheduler 均关闭，保留 Redis leader lock 配置便于切换。 |

## 4. Readiness

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
PLATFORM_XXL_JOB_ENABLED=true \
WP9_SCHEDULER_ENABLED=true \
WP9_CRON_ENABLED=true \
WP9_WEBHOOK_ENABLED=false \
WP9_SCHEDULER_WORKER_ID=wp9-prod-active-01 \
SPRING_PROFILES_ACTIVE=db,redis \
PLATFORM_REDIS_ADDRESS=redis://redis:6379 \
WP9_SCHEDULER_LEADER_LOCK_ENABLED=true \
WP9_RELEASE_GATE=1 \
WP9_SCHEDULER_SMOKE=managed \
WP9_WEBHOOK_HTTP_SMOKE=managed \
bash scripts/wp9_worker_hosting_readiness.sh
```

## 5. 切换与回滚

1. active worker 故障时，优先保留 `WP9_SCHEDULER_LEADER_LOCK_ENABLED=true`，扩容或切换到另一个带 Redis profile 的 active 实例。
2. 从 standby 切换时，设置唯一 `WP9_SCHEDULER_WORKER_ID`，确认 `SPRING_PROFILES_ACTIVE` 包含 `redis`，再启用 `WP9_SCHEDULER_ENABLED=true`。
3. 切换后执行 `scripts/wp9_scheduler_smoke.sh` 或受控 release gate，并记录 workerId、锁 provider、traceId 和 smoke 结果。
4. 如 Redis 不可用且必须紧急单活回滚，先只保留一个 scheduler 实例，再临时设置 `WP9_SCHEDULER_LEADER_LOCK_ENABLED=false`；恢复 Redis 后重新开启锁并重跑 readiness。
