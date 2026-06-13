# WP9 执行编排与任务调度 - M3D 心跳续约与超时恢复交付说明

| 项目 | 内容 |
|---|---|
| 工作包 | WP9 执行编排与任务调度 |
| 交付阶段 | M3D 心跳续约与超时恢复 |
| 覆盖 Story | WP9-3.7、WP9-7.1 部分、WP9-8.1 部分 |
| 当前口径 | 完成内部 claim heartbeat、过期 claim recovery、RUNNING 节点重排或 TIMEOUT 收敛、run 聚合和 JDBC/local 仓储更新；不启后台 scheduler 线程、不接 WP6 dispatch、不做 webhook/cron 和前端工作台 |
| 日期 | 2026-06-13 |

## 1. 任务启动口径

| 项 | 结论 |
|---|---|
| 目标 | 在 `platform-api` 内推进 WP9 M3D，使 M3C 已认领的 RUNNING node run 可以续约 heartbeat，并在 claim 过期或节点超时后由内部恢复入口收敛状态。 |
| 范围 | `POST /internal/queue/claims/heartbeat`、`POST /internal/queue/recover-expired`、claim 条件续约、过期 claim 条件释放、RUNNING node 重排或 TIMEOUT、run 聚合、OpenAPI contract、DB profile contract 和交付文档。 |
| 非目标 | 不启动后台 scheduler 线程，不调用 WP6/WP7 runner，不实现 runner cancel/dispatch，不做 webhook/cron，不改 `portal-web` 工作台。 |
| 涉及模块 | `platform-api` execution API/application/infrastructure、MyBatis mapper、local/db 测试仓储、OpenAPI contract、WP9 文档。 |
| 风险 | heartbeat 与 recovery 并发可能误释放有效 claim；旧 worker 可能在 recovery 后继续完成节点；timeout 聚合错误可能误导 run 状态。 |
| 验收标准 | active claim 可 heartbeat 续约；过期或非 active claim 不能 heartbeat 或 complete；过期 claim 未超过节点 timeout 时节点重排为 QUEUED；超过节点 timeout 时节点 TIMEOUT 并聚合 run；DB/local 仓储条件语义一致。 |
| 回滚方式 | 回滚本轮 M3D 代码和文档；M1 DB 表结构保持不变，可隐藏 internal recovery/heartbeat 入口并保留 M3C 手动 complete。 |

需求文档/产品 PRD、技术设计/接口契约、测试策略/脚本均已有 WP9 启动包；本轮在技术设计、测试策略和 M3D 交付说明中同步真实实现。`WP1-WP4-统一发布准出清单.md` 本轮不适用，原因是未改 WP1-WP4 发布流程、数据库迁移或文档输入链路；仅复用 WP1 权限/审计能力和 M1 已建 WP9 表。

## 2. 需求结论

M3D 后端恢复控制面补齐 heartbeat 与过期恢复：

1. `POST /api/v1/execution/internal/queue/claims/heartbeat` 由 `execution:admin` 调用，使用 claimToken 续约 active claim。
2. heartbeat 仅允许 `CLAIMED` 且未过期的 claim，并要求 node run 仍为 `RUNNING`。
3. `POST /api/v1/execution/internal/queue/recover-expired` 扫描过期 claim，先将 claim 条件标记为 `EXPIRED`，释放 active 唯一索引。
4. 未超过 plan node timeout 的 RUNNING 节点会重排为 `QUEUED`，旧 claimToken 不能再 complete。
5. 已超过 plan node timeout 的 RUNNING 节点会标记为 `TIMEOUT`，下游 fail-fast 依赖阻断并聚合 run。
6. 未携带 active claim 的 stale RUNNING 节点在超过 node timeout 后也会收敛为 `TIMEOUT`。

## 3. 主要变更

| 模块 | 变更 |
|---|---|
| `platform-api` execution API | `ExecutionRunController` 增加内部 heartbeat 和 recover-expired endpoint，权限限定为 `execution:admin`。 |
| execution application | `ExecutionRunService` 增加 claim heartbeat、过期 claim 恢复、节点重排、节点 TIMEOUT 和 recovery 聚合计数。 |
| command/view | 新增 `HeartbeatExecutionQueueClaimCommand` 和 `ExecutionQueueRecoveryResponse`。 |
| persistence | `ExecutionRepository`、`JdbcExecutionRepository`、`ExecutionMapper` 和 XML 增加 claim 条件更新、过期扫描和 stale RUNNING node 查询。 |
| local test repository | `InMemoryExecutionRepository` 增加 heartbeat/recovery 相关条件语义。 |
| health | 增加 `heartbeatRecoveryReady=true`；`schedulerEnabled=false`、`wp6DispatchReady=false` 保持后续口径。 |
| tests | `ExecutionRunControllerTest` 覆盖 heartbeat 续约、过期 claim 重排、旧 claimToken 拒绝和 TIMEOUT recovery；OpenAPI contract 与 DB profile contract 同步覆盖。 |
| docs | README、技术设计、测试策略和 M3D 交付说明同步。 |

## 4. 验收标准

1. heartbeat/recovery endpoint 必须受 `execution:admin` 平台权限保护。
2. claimToken 过期、claim 非 active 或 node 非 RUNNING 时必须拒绝 heartbeat 和 complete。
3. 过期 claim 标记 `EXPIRED` 必须带 `expires_at <= scanTime` 条件，避免覆盖并发续约。
4. recovery 未超过 node timeout 时只释放 claim 并把 node 重排为 `QUEUED`。
5. recovery 超过 node timeout 时 node 标记 `TIMEOUT`，run 聚合为 `TIMEOUT` 或包含 timeout 计数的终态。
6. health 必须明确 heartbeat/recovery 控制面就绪，但后台 scheduler 和 WP6 dispatch 仍未就绪。

## 5. 验证结果

| 命令 | 结果 |
|---|---|
| `mvn -B -pl platform-api -DskipTests compile` | 通过 |
| `mvn -B -pl platform-api -Dtest='ExecutionRunControllerTest,ExecutionHealthControllerTest,OpenApiContractTest' test` | 通过，16 tests，0 failures，0 errors |
| `mvn -B -pl platform-api -Dtest='ExecutionRunControllerTest,ExecutionPlanControllerTest,ExecutionHealthControllerTest,OpenApiContractTest,PermissionCodeUsageTest,ModuleLayerDependencyTest,PersistenceProfileBoundaryTest,DbProfileRepositoryContractTest#executionRepositoryPersistsRunsAndNodeRunsThroughJdbc' test` | 通过，47 tests，0 failures，0 errors |
| `mvn -B -pl platform-api test` | 通过，521 tests，0 failures，0 errors |
| `bash db/validation/run_wp1_db_validation.sh` | 通过，WP1-WP9 migration rerun 和 seed validation passed |
| `cd portal-web && npm test` | 通过，22 files，178 tests；npm 输出 `electron_mirror` 配置警告 |
| `cd portal-web && npm run build` | 通过；Vite 输出动态导入和 chunk size 警告 |
| `git diff --check` | 通过，无 whitespace error |

## 6. 风险与回滚

| 风险 | 处置 | 回滚 |
|---|---|---|
| heartbeat 与 recovery 并发误释放有效 claim | `updateExpiredQueueClaim` 同时校验 `status='CLAIMED'` 和 `expires_at <= scanTime` | 回滚 M3D 代码或隐藏 recovery 入口 |
| 旧 worker 在 recovery 后继续完成节点 | complete 校验 claim 未过期、状态仍 `CLAIMED` 且 node 仍 `RUNNING` | 保留 EXPIRED claim 证据，重试新 claim |
| 节点 timeout 过短造成误判 | 按 plan node timeout 优先，缺失时使用配置默认值 | 调整计划 timeout 或暂不调用 recovery |
| 未接 runner 被误解为真实执行恢复 | resultSummary 保持 `runnerDispatched=false`，health 保持 `wp6DispatchReady=false` | 隐藏内部 endpoint，仅保留状态查询 |

## 7. 五角色结论

| 角色 | 结论 | 说明 |
|---|---|---|
| 资深项目经理 | 通过 | 本轮范围限定 M3D heartbeat/recovery，不抢跑 WP6 dispatch、webhook/cron 和前端。 |
| 资深产品经理 | 通过 | 运行记录从“可认领/可完成”推进到“可续约/可恢复”，满足继续接入 runner 前的可恢复性要求。 |
| 资深服务端架构师 | 通过 | 后端分层、内部权限、条件续约、过期释放、节点重排、TIMEOUT 聚合和并发边界符合技术契约；Java 核心逻辑已补必要注释。 |
| 资深前端工程师 | 无影响 | 本轮未改 `portal-web`；health 字段为后续工作台展示 recovery readiness 提供依据。 |
| 资深质量工程师 | 通过 | 定向后端、OpenAPI、DB profile contract、全量后端、DB validation、前端单测、前端构建和 diff check 均已通过。 |

## 8. 后续任务

1. WP9-4.x：通过 WP6 应用服务 dispatch `API_TEST` 节点并同步结果摘要。
2. WP9-5.x：补 webhook/cron 触发控制面和幂等事件记录。
3. WP9-6.x：实现 `portal-web` execution 工作台。
4. WP9-7.x：聚合 WP9 quality gate 和 scheduler smoke 脚本。
