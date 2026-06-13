# WP9 执行编排与任务调度 - M3A 手动触发与运行记录交付说明

| 项目 | 内容 |
|---|---|
| 工作包 | WP9 执行编排与任务调度 |
| 交付阶段 | M3A 手动触发与运行记录 |
| 覆盖 Story | WP9-3.1、WP9-3.2、WP9-3.4 部分、WP9-7.1 部分、WP9-8.1 部分 |
| 当前口径 | 完成 READY 计划手动触发、run/node run 初始化、requestKey 幂等回放、运行列表/详情和后端测试；不实现队列认领、WP6 dispatch、cancel/retry、timeout recovery、webhook/cron 和前端工作台 |
| 日期 | 2026-06-13 |

## 1. 任务启动口径

| 项 | 结论 |
|---|---|
| 目标 | 在 `platform-api` 内推进 WP9 M3A，使 READY 执行计划可以手动触发并生成可查询的 run/node run 控制面记录。 |
| 范围 | 手动触发 API、run 列表/详情 API、run/node run 持久化、requestKey 幂等、状态保护、OpenAPI contract、后端单测和交付文档。 |
| 非目标 | 不认领队列，不调用 WP6 runner，不推进节点状态到 RUNNING/SUCCEEDED，不做 cancel/retry，不做 webhook/cron，不改 `portal-web` 工作台。 |
| 涉及模块 | `platform-api` execution API/application/domain/infrastructure、OpenAPI contract、WP9 文档。 |
| 风险 | 手动触发重复请求可能创建重复 run；非 READY 计划若可触发会污染调度队列；变量或 runner 输出若持久化会扩大敏感信息风险。 |
| 验收标准 | 只有 READY 计划可触发；相同 plan/requestKey 重复请求返回既有 run；初始 node run 根据依赖进入 QUEUED/PENDING；查询结果不包含变量明文、secret 明文或 runner 原始输出；定向和全量测试通过。 |
| 回滚方式 | 回滚本轮 M3A 代码和文档；M1 DB 表结构保持不变，可通过撤销 `execution:trigger` 或隐藏入口禁用手动触发。 |

需求文档/产品 PRD、技术设计/接口契约、测试策略/脚本均已有 WP9 启动包；本轮在技术设计、测试策略和 M3A 交付说明中同步真实实现。`WP1-WP4-统一发布准出清单.md` 本轮不适用，原因是未改 WP1-WP4 发布流程、数据库迁移或文档输入链路；仅复用 WP1 权限/审计能力和 M1 已建 WP9 表。

## 2. 需求结论

M3A 后端运行控制面已形成最小可验收闭环：

1. `POST /api/v1/execution/plans/{id}/runs` 对 READY 计划创建 `QUEUED` run 和初始 node run。
2. 手动触发支持 `requestKey` 幂等回放，相同 plan/requestKey 返回既有 run 且 `idempotentReplay=true`。
3. `GET /api/v1/execution/runs` 支持按 projectId、planId、status 分页查询。
4. `GET /api/v1/execution/runs/{id}` 返回 run 详情和节点状态摘要。
5. 非 READY 计划触发返回 `INVALID_STATE` / `EXECUTION_PLAN_NOT_READY`。

## 3. 主要变更

| 模块 | 变更 |
|---|---|
| `platform-api` execution API | 新增 `ExecutionRunController`，覆盖手动触发、运行列表和运行详情。 |
| execution application | 新增 `ExecutionRunService`，负责 READY 校验、requestKey 幂等、run/node run 初始化、审计和响应映射。 |
| execution domain/view/query | 新增 `ExecutionRun`、`ExecutionNodeRun`、触发命令、运行查询和运行响应 DTO。 |
| persistence | 扩展 `ExecutionRepository`、`JdbcExecutionRepository`、`ExecutionMapper` 和 `ExecutionMapper.xml`，复用 M1 `execution_run` 与 `execution_node_run` 表；node run 始终引用已持久化的 plan node ID。 |
| local test repository | 扩展 `InMemoryExecutionRepository`，提供 run/node run 写入、查询和 requestKey 回放。 |
| health | `manualTriggerReady=true`；`queueClaimReady=false`、`wp6DispatchReady=false` 保持后续口径。 |
| tests | 新增 `ExecutionRunControllerTest`；OpenAPI contract 纳入 `/plans/{id}/runs`、`/runs`、`/runs/{id}`。 |

## 4. 验收标准

1. 手动触发 API 必须受 `execution:trigger` 权限和 plan project scope 约束。
2. run 查询 API 必须受 `execution:read` 权限和 run/project scope 约束。
3. 非 READY 计划不可触发；触发后 run 初始状态为 `QUEUED`。
4. 无依赖节点初始为 `QUEUED`，有依赖节点初始为 `PENDING`。
5. 相同 plan/requestKey 重复触发不得新增 run 或 node run。
6. resultSummary 只保存节点计数、DAG digest、调度策略摘要和变量存在性，不保存变量明文、secret 明文、runner 输出或请求响应正文。

## 5. 验证结果

| 命令 | 结果 |
|---|---|
| `mvn -B -pl platform-api -DskipTests compile` | 通过 |
| `mvn -B -pl platform-api -Dtest='ExecutionRunControllerTest,ExecutionPlanControllerTest,ExecutionHealthControllerTest,OpenApiContractTest,PermissionCodeUsageTest,ModuleLayerDependencyTest,PersistenceProfileBoundaryTest,DbProfileRepositoryContractTest#executionRepositoryPersistsRunsAndNodeRunsThroughJdbc' test` | 通过，41 tests，0 failures，0 errors |
| `mvn -B -pl platform-api test` | 通过，515 tests，0 failures，0 errors；测试日志中有 WP5 定时补偿 teardown 期 JDBC warning，Maven 结果为 success |
| `bash db/validation/run_wp1_db_validation.sh` | 通过，WP1-WP9 当前 migration 与 seed 重放校验通过 |
| `git diff --check` | 通过，无空白错误 |
| `cd portal-web && npm test` | 通过，22 files，178 tests |
| `cd portal-web && npm run build` | 通过；Vite 输出既有 chunk 体积和 auth 模块动静态导入提示 |

## 6. 风险与回滚

| 风险 | 处置 | 回滚 |
|---|---|---|
| 重复点击手动触发造成重复 run | requestKey 命中直接回放既有 run，DB 唯一索引和 `on conflict do nothing` 兜底 | 回滚 M3A 代码或临时撤销 `execution:trigger` |
| 未就绪计划被触发 | service 强制 `READY` 状态校验 | 禁用触发入口并修复状态判断 |
| 调度未接入导致用户误解为已执行 | resultSummary 和 health policy 明确 `runnerDispatched=false`、`queueClaimReady=false` | 保留 run 记录，后续 M4 接 dispatch |
| 变量泄露 | M3A 只记录 variablesAccepted 布尔值，不持久化变量明文 | 加强响应和审计脱敏规则 |

## 7. 五角色结论

| 角色 | 结论 | 说明 |
|---|---|---|
| 资深项目经理 | 通过 | 本轮范围限定 M3A 手动触发和运行记录，不抢跑 queue claim、WP6 dispatch 和前端。 |
| 资深产品经理 | 通过 | READY 计划可触发、可查询、可幂等回放，满足用户查看执行意图和排查重复请求的最小价值。 |
| 资深服务端架构师 | 通过 | 后端分层、权限 scope、状态保护、幂等、审计和敏感信息摘要符合技术契约；Java 核心逻辑已补必要注释。 |
| 资深前端工程师 | 无影响 | 本轮未改 `portal-web`；接口契约已为后续工作台运行列表和详情提供基础。 |
| 资深质量工程师 | 通过 | 定向后端、全量后端、DB validation、前端单测、前端构建和 diff check 均已通过；M3B/M4 以后的 queue/dispatch/cancel/retry/recovery 测试后续补齐。 |

## 8. 后续任务

1. WP9-3.x：补 cancel/retry、queue claim、状态聚合和 timeout recovery。
2. WP9-4.x：通过 WP6 应用服务 dispatch `API_TEST` 节点并同步结果摘要。
3. WP9-5.x：补 webhook/cron 触发控制面和幂等事件记录。
4. WP9-6.x：实现 `portal-web` execution 工作台。
