# WP9 执行编排与任务调度 - M8D 外部 Worker 托管策略交付说明

| 项 | 内容 |
|---|---|
| 交付阶段 | M8D 外部 Worker 托管策略 |
| 交付日期 | 2026-06-14 |
| 交付范围 | web / scheduler-active / scheduler-standby 三类托管角色、env 示例、离线 readiness 脚本、Runbook、quality gate 接入；M8J 增量补齐 scheduler leader lock |
| 非目标 | 新增独立 worker 二进制或 Maven 模块、修改数据库结构、前端页面改动 |
| 涉及模块 | `platform-api` WP9 scheduler/health、`integrations/wp9-worker-hosting/`、`scripts/wp9_worker_hosting_readiness.sh`、`scripts/wp9_quality_gate.sh`、WP9 文档 |
| 回滚方式 | 优先关闭 `WP9_SCHEDULER_LEADER_LOCK_ENABLED` 并缩容到单 active；必要时回退本次 Java、脚本、示例和文档 commit |

## 1. 目标与范围

M8D 目标是把 WP9 scheduler 的托管方式从“知道有开关”推进到“发布前可审查、可离线校验”。本轮定义 `web`、`scheduler-active`、`scheduler-standby` 三类角色，并提供 env 示例和 readiness 脚本，防止 web/API 实例误启 scheduler、active worker 误开 webhook ingress、standby worker 未切换就抢占队列。

本切片不新增外部 worker 二进制或容器进程；当前 WP9 scheduler 仍由同一 `platform-api` 镜像和 `executionSchedulerJob` XXL-JOB handler 承载。生产可用同一镜像部署专用 scheduler 实例，通过环境变量决定角色。M8J 增量已在 scheduler tick 前接入 leader lock，允许多个 active worker 部署但只有锁持有者执行 CRON scan、recovery、queue claim 和 dispatch。

## 2. 主要变更

1. 新增 `integrations/wp9-worker-hosting/README.md`，说明 web、scheduler-active、scheduler-standby 的开关策略、切换和回滚。
2. 新增 `web.env.example`、`scheduler-active.env.example`、`scheduler-standby.env.example` 三个 env 示例。
3. 新增 `scripts/wp9_worker_hosting_readiness.sh`，离线校验角色、`PLATFORM_XXL_JOB_ENABLED`、scheduler/webhook/cron 开关、workerId、interval、initialDelay、batch、heartbeat timeout、recovery batch、Redis profile、leader lock 租约和 release smoke 证据。
4. `scripts/wp9_quality_gate.sh` 增加 worker hosting readiness，默认校验三类 env 示例。
5. `ExecutionSchedulerService` 在 tick 执行前获取 `ExecutionSchedulerLock`；`redis` profile 下使用 Redisson 分布式锁，非 redis profile 使用本地 JVM 锁作为开发兜底。
6. `ExecutionHealthResponse` 暴露 `schedulerLeaderLockProvider`、`schedulerLeaderLockDistributed`、`schedulerLeaderLockReady` 和 `schedulerMultiActiveReady`。
7. 更新 Scheduler/Trigger Runbook、技术设计、测试策略和研发拆解，记录 M8D/M8J 已覆盖托管策略和多活锁控制面，真实独立 worker 二进制仍是后续生产增强。

## 3. 验收入口

```bash
bash -n scripts/wp9_worker_hosting_readiness.sh
WP9_WORKER_HOSTING_ENV_FILE=integrations/wp9-worker-hosting/web.env.example bash scripts/wp9_worker_hosting_readiness.sh
WP9_WORKER_HOSTING_ENV_FILE=integrations/wp9-worker-hosting/scheduler-active.env.example bash scripts/wp9_worker_hosting_readiness.sh
WP9_WORKER_HOSTING_ENV_FILE=integrations/wp9-worker-hosting/scheduler-standby.env.example bash scripts/wp9_worker_hosting_readiness.sh
mvn -B -pl platform-api -Dtest=ExecutionSchedulerServiceTest,ExecutionHealthControllerTest,ExecutionHealthServiceTest,WorkerJobHandlerTest test
WP9_QUALITY_GATE_PLAN_ONLY=1 bash scripts/wp9_quality_gate.sh
git diff --check
```

## 4. 风险与后续

1. readiness 只校验配置形态，不连接真实 DB、Redis、Kafka 或 platform-api；真实发布仍需执行 WP9 quality gate 和 scheduler smoke。
2. 多 active worker 依赖 Redisson leader lock；若 Redis profile 缺失会退化为本地 JVM 锁，readiness 已对 `scheduler-active` 强制校验 `redis` profile 和 Redis 地址。
3. 当前已接入分布式锁和 leader election 控制面，但未新增锁指标看板或真实 Redis 多节点故障切换演练。
4. 错过多次 CRON fire 的不补偿策略已由 M8F smoke 覆盖；生产压测和未来 backfill 设计仍按后续 WP9 运维增强推进。

## 5. 五角色结论

| 角色 | 结论 | 说明 |
|---|---|---|
| 资深项目经理 | 通过 | M8D 范围限定为托管策略、env 示例和离线 readiness，回滚路径清晰。 |
| 资深产品经理 | 通过 | 覆盖运维和发布负责人对 scheduler 角色、切换和回滚的可理解诉求。 |
| 资深服务端架构师 | 通过 | 不改运行时契约，复用现有 scheduler loop、claim/recovery 和配置边界。 |
| 资深前端工程师 | 无影响 | 本切片不改 `portal-web`，前端仍展示既有调度状态和触发摘要。 |
| 资深质量工程师 | 通过 | 新增 readiness 脚本并纳入 WP9 quality gate，覆盖三类部署角色的配置准入。 |
