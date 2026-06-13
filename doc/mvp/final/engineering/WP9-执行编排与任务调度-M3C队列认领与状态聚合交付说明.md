# WP9 执行编排与任务调度 - M3C 队列认领与状态聚合交付说明

| 项目 | 内容 |
|---|---|
| 工作包 | WP9 执行编排与任务调度 |
| 交付阶段 | M3C 队列认领与状态聚合 |
| 覆盖 Story | WP9-3.3、WP9-3.4、WP9-7.1 部分、WP9-8.1 部分 |
| 当前口径 | 完成内部 queue claim、node complete、依赖推进、run 聚合、脱敏和 JDBC/local 仓储更新；不实现后台 scheduler 线程、heartbeat 续约、timeout recovery、WP6 dispatch、webhook/cron 和前端工作台 |
| 日期 | 2026-06-13 |

## 1. 任务启动口径

| 项 | 结论 |
|---|---|
| 目标 | 在 `platform-api` 内推进 WP9 M3C，使 M3A/M3B 已创建的 queued node run 可以被内部 worker 条件认领、完成回传，并驱动依赖和 run 状态收敛。 |
| 范围 | `POST /internal/queue/claims`、`POST /internal/queue/node-runs/{id}/complete`、`execution_queue_claim` 持久化读写、node 条件更新、节点完成脱敏、依赖推进、run 聚合、OpenAPI contract、DB profile contract 和交付文档。 |
| 非目标 | 不启动后台调度线程，不调用 WP6/WP7 runner，不做 heartbeat 续约或超时恢复，不做 webhook/cron，不改 `portal-web` 工作台。 |
| 涉及模块 | `platform-api` execution API/application/domain/infrastructure、MyBatis mapper、local/db 测试仓储、OpenAPI contract、WP9 文档。 |
| 风险 | 多 worker 并发可能重复认领同一节点；完成回传可能夹带 runner 原始输出或 secret；依赖失败策略聚合错误会误导运行状态；未接 WP6 时若声明真实执行会造成用户误解。 |
| 验收标准 | 同一 node 只允许一个 active claim；claimToken 与 nodeRunId 必须匹配；完成成功节点可推动下游 PENDING 到 QUEUED；fail-fast 依赖失败会阻断下游；resultSummary 不保存危险 key 或敏感文本；定向后端测试和 DB contract 通过。 |
| 回滚方式 | 回滚本轮 M3C 代码和文档；M1 DB 表结构保持不变，可通过撤销 `execution:admin` 或隐藏内部队列入口禁用认领。 |

需求文档/产品 PRD、技术设计/接口契约、测试策略/脚本均已有 WP9 启动包；本轮在技术设计、测试策略和 M3C 交付说明中同步真实实现。`WP1-WP4-统一发布准出清单.md` 本轮不适用，原因是未改 WP1-WP4 发布流程、数据库迁移或文档输入链路；仅复用 WP1 权限/审计能力和 M1 已建 WP9 表。

## 2. 需求结论

M3C 后端运行状态机补齐队列认领和完成回传闭环：

1. `POST /api/v1/execution/internal/queue/claims?workerId=...` 由 `execution:admin` 调用，按 run 创建时间和 nodeKey 认领一个 queued node。
2. 认领先写 `execution_queue_claim`，再用 `QUEUED` 条件更新把 node run 推进到 `RUNNING`，避免多 worker 重复拥有同一节点。
3. `POST /api/v1/execution/internal/queue/node-runs/{id}/complete` 使用 claimToken 完成节点，支持 `SUCCEEDED/SKIPPED/FAILED/TIMEOUT/BLOCKED`。
4. 成功或可继续的依赖完成后，下游 `PENDING` 节点推进到 `QUEUED`；fail-fast 依赖失败时下游节点标记为 `BLOCKED`。
5. run 根据最新 attempt 节点状态聚合为 `RUNNING`、`SUCCEEDED`、`FAILED`、`PARTIAL_SUCCESS` 或 `TIMEOUT`。
6. 完成回传只保存脱敏摘要，丢弃 `stdout/stderr/requestBody/responseBody/variables/environment/secret/token/password/authorization` 等危险 key。

## 3. 主要变更

| 模块 | 变更 |
|---|---|
| `platform-api` execution API | `ExecutionRunController` 增加内部 claim 和 node complete endpoint，权限限定为 `execution:admin`。 |
| execution application | `ExecutionRunService` 增加 queue claim、claim 释放、节点完成、依赖状态判断、run 聚合和 resultSummary 递归脱敏。 |
| execution domain/view/command | 新增 `ExecutionQueueClaim`、`ExecutionQueueClaimResponse` 和 `CompleteExecutionNodeRunCommand`。 |
| persistence | `ExecutionRepository`、`JdbcExecutionRepository`、`ExecutionMapper` 和 XML 增加 queued node 查询、node 条件更新、claim insert/update/query。 |
| local test repository | `InMemoryExecutionRepository` 增加 queue claim、queued node、active claim 和条件更新语义。 |
| health | `queueClaimReady=true`、`stateAggregationReady=true`；`schedulerEnabled=false`、`wp6DispatchReady=false` 保持后续口径。 |
| tests | `ExecutionRunControllerTest` 覆盖 claim、成功完成推动下游、失败完成阻断下游和脱敏；OpenAPI contract 纳入内部 endpoint；DB profile contract 覆盖 active claim 唯一、条件更新、token 查询和完成后不再 active。 |
| docs | README、技术设计、测试策略和 M3C 交付说明同步。 |

## 4. 验收标准

1. 内部队列 endpoint 必须受 `execution:admin` 平台权限保护。
2. claimToken 必须与路径 nodeRunId 匹配，claim 非 active 或节点非 RUNNING 必须拒绝。
3. active claim 唯一索引和 `updateNodeRunIfStatus(..., "QUEUED")` 共同防重复认领。
4. 节点完成后立即聚合 run，并对下游依赖执行 QUEUED/BLOCKED 推进。
5. 响应、落库和审计不得包含 runner 原始输出、请求响应正文、变量明文、secret 明文或授权 token。
6. health 必须明确 queue claim 控制面就绪，但后台 scheduler 和 WP6 dispatch 仍未就绪。

## 5. 验证结果

| 命令 | 结果 |
|---|---|
| `mvn -B -pl platform-api -DskipTests compile` | 通过 |
| `mvn -B -pl platform-api -Dtest='ExecutionRunControllerTest,ExecutionHealthControllerTest,OpenApiContractTest' test` | 通过，14 tests，0 failures，0 errors |
| `mvn -B -pl platform-api -Dtest='ExecutionRunControllerTest,ExecutionPlanControllerTest,ExecutionHealthControllerTest,OpenApiContractTest,PermissionCodeUsageTest,ModuleLayerDependencyTest,PersistenceProfileBoundaryTest,DbProfileRepositoryContractTest#executionRepositoryPersistsRunsAndNodeRunsThroughJdbc' test` | 通过，45 tests，0 failures，0 errors |
| `mvn -B -pl platform-api test` | 通过，519 tests，0 failures，0 errors |
| `bash db/validation/run_wp1_db_validation.sh` | 通过 |
| `cd portal-web && npm test` | 通过，22 files，178 tests；npm 输出 `electron_mirror` 配置警告 |
| `cd portal-web && npm run build` | 通过；Vite 输出动态导入和 chunk size 警告 |
| `git diff --check` | 通过，无 whitespace error |

## 6. 风险与回滚

| 风险 | 处置 | 回滚 |
|---|---|---|
| 多 worker 重复认领 | `execution_queue_claim` active unique index 加条件更新双重保护 | 回滚 M3C 代码或临时撤销 `execution:admin` |
| 完成回传夹带敏感信息 | 危险 key 丢弃，安全 key 字符串递归脱敏并截断 | 加强脱敏规则后重跑定向测试 |
| 未接 runner 被误解为真实执行 | resultSummary 固定 `runnerDispatched=false`，health 保持 `wp6DispatchReady=false` | 隐藏内部 endpoint，仅保留手动触发/查询 |
| 依赖推进规则不符合产品预期 | 当前按 dependency node failurePolicy 判断 fail-fast/continue；后续 WP6 dispatch 接入前可调整 | 回滚聚合逻辑并保留节点证据 |

## 7. 五角色结论

| 角色 | 结论 | 说明 |
|---|---|---|
| 资深项目经理 | 通过 | 本轮范围限定 M3C 队列认领和状态聚合，不抢跑 WP6 dispatch、timeout recovery 和前端。 |
| 资深产品经理 | 通过 | 运行记录从“已排队”推进到“可认领、可完成、可解释依赖状态”，满足继续接入 runner 的前置产品闭环。 |
| 资深服务端架构师 | 通过 | 后端分层、内部权限、claim 幂等、条件更新、状态聚合和脱敏边界符合技术契约；Java 核心逻辑已补必要注释。 |
| 资深前端工程师 | 无影响 | 本轮未改 `portal-web`；health 和 run detail 已为后续工作台展示 queue/aggregation 状态提供字段。 |
| 资深质量工程师 | 通过 | 定向后端、DB profile contract、全量后端、DB validation、前端单测、前端构建和 diff check 均已通过；现有 npm/Vite 警告不影响本轮 WP9 后端交付。 |

## 8. 后续任务

1. WP9-3.7：补 heartbeat 续约、timeout recovery 和卡死 claim 回收。
2. WP9-4.x：通过 WP6 应用服务 dispatch `API_TEST` 节点并同步结果摘要。
3. WP9-5.x：补 webhook/cron 触发控制面和幂等事件记录。
4. WP9-6.x：实现 `portal-web` execution 工作台。
