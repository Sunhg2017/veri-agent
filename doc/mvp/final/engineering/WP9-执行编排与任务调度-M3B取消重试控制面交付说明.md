# WP9 执行编排与任务调度 - M3B 取消重试控制面交付说明

| 项目 | 内容 |
|---|---|
| 工作包 | WP9 执行编排与任务调度 |
| 交付阶段 | M3B 取消重试控制面 |
| 覆盖 Story | WP9-3.3、WP9-3.4 部分、WP9-7.1 部分、WP9-8.1 部分 |
| 当前口径 | 完成 run cancel/retry API、控制面状态收敛、失败节点 retry attempt、幂等保护、JDBC/local 仓储更新和后端测试；不实现队列认领、WP6 dispatch、runner cancel、timeout recovery、webhook/cron 和前端工作台 |
| 日期 | 2026-06-13 |

## 1. 任务启动口径

| 项 | 结论 |
|---|---|
| 目标 | 在 `platform-api` 内推进 WP9 M3B，使 M3A 已创建的 run 可以取消或对失败节点进行控制面重试。 |
| 范围 | `POST /runs/{id}/cancel`、`POST /runs/{id}/retry`、run/node run 状态更新、失败节点新 attempt、审计、OpenAPI contract、DB profile contract 和交付文档。 |
| 非目标 | 不认领队列，不调用 WP6/WP7 runner，不推进节点到 RUNNING/SUCCEEDED，不做 timeout recovery，不做 webhook/cron，不改 `portal-web` 工作台。 |
| 涉及模块 | `platform-api` execution API/application/infrastructure、OpenAPI contract、WP9 文档。 |
| 风险 | 重复点击 retry 可能重复生成 attempt；终态 run 若被 cancel/retry 可能破坏执行证据；未接 runner 时若声明真实取消会造成用户误解。 |
| 验收标准 | 非终态 run 可取消且重复取消幂等；只有 FAILED/PARTIAL_SUCCESS/TIMEOUT run 可重试；重试只为最新 FAILED/TIMEOUT/BLOCKED 节点创建新 attempt；响应和审计不包含 runner 原始输出或敏感明文；定向和全量测试通过。 |
| 回滚方式 | 回滚本轮 M3B 代码和文档；M1 DB 表结构保持不变，可通过撤销 `execution:trigger` 或隐藏取消/重试入口禁用。 |

需求文档/产品 PRD、技术设计/接口契约、测试策略/脚本均已有 WP9 启动包；本轮在技术设计、测试策略和 M3B 交付说明中同步真实实现。`WP1-WP4-统一发布准出清单.md` 本轮不适用，原因是未改 WP1-WP4 发布流程、数据库迁移或文档输入链路；仅复用 WP1 权限/审计能力和 M1 已建 WP9 表。

## 2. 需求结论

M3B 后端运行控制面补齐取消和重试入口：

1. `POST /api/v1/execution/runs/{id}/cancel` 将 QUEUED/RUNNING run 收敛为 `CANCELED`，并关闭 PENDING/QUEUED/RUNNING node run。
2. 终态 run 重复取消幂等返回当前详情，不重复写入节点状态。
3. `POST /api/v1/execution/runs/{id}/retry` 只允许 `FAILED`、`PARTIAL_SUCCESS`、`TIMEOUT` run。
4. retry 在同一 run 下为最新失败、超时或阻断节点新增 attempt，保留原失败 attempt 作为证据。
5. retry 后 run 回到 `QUEUED`、`triggerType=RETRY`、`retryInFlight=true`；重复 retry 不重复创建 attempt。

## 3. 主要变更

| 模块 | 变更 |
|---|---|
| `platform-api` execution API | `ExecutionRunController` 新增 `/runs/{id}/cancel` 和 `/runs/{id}/retry`。 |
| execution application | `ExecutionRunService` 新增 cancel/retry 状态收敛、终态/可重试校验、失败节点 latest attempt 选择和审计。 |
| persistence | `ExecutionRepository`、`JdbcExecutionRepository`、`ExecutionMapper` 和 XML 增加 run/node run 更新；node run 查询按 nodeKey/attempt 稳定排序。 |
| local test repository | `InMemoryExecutionRepository` 增加 run/node run 更新并对 retry attempt 稳定排序。 |
| health | `cancelRetryReady=true`；`queueClaimReady=false`、`wp6DispatchReady=false` 保持后续口径。 |
| tests | `ExecutionRunControllerTest` 覆盖取消幂等、非重试态拒绝、失败节点 retry attempt 和重复 retry 幂等；OpenAPI contract 纳入 cancel/retry；DB profile contract 覆盖 update 和 attempt 查询。 |

## 4. 验收标准

1. cancel/retry API 必须受 `execution:trigger` 权限和 run project scope 约束。
2. cancel 不调用 runner port，响应和摘要明确 `runnerCancelAttempted=false`。
3. retry 不创建新 run，不覆盖旧失败节点，只插入新 attempt。
4. retry already queued 时直接返回当前详情，不重复插入 node run。
5. resultSummary 只保存计数、状态、retry/cancel 策略摘要，不保存变量明文、secret 明文、runner 输出或请求响应正文。

## 5. 验证结果

| 命令 | 结果 |
|---|---|
| `mvn -B -pl platform-api -DskipTests compile` | 通过 |
| `mvn -B -pl platform-api -Dtest='ExecutionRunControllerTest,ExecutionPlanControllerTest,ExecutionHealthControllerTest,OpenApiContractTest,PermissionCodeUsageTest,ModuleLayerDependencyTest,PersistenceProfileBoundaryTest,DbProfileRepositoryContractTest#executionRepositoryPersistsRunsAndNodeRunsThroughJdbc' test` | 通过，43 tests，0 failures，0 errors |
| `mvn -B -pl platform-api test` | 通过，517 tests，0 failures，0 errors |
| `bash db/validation/run_wp1_db_validation.sh` | 通过，WP1-WP9 migration rerun 和 seed validation passed |
| `git diff --check` | 通过，无 whitespace error |
| `cd portal-web && npm test` | 通过，22 files，178 tests，0 failures |
| `cd portal-web && npm run build` | 通过；保留既有 Vite 警告：`auth.ts` 同时动态/静态导入，单个产物 chunk 超过 500 kB |

## 6. 风险与回滚

| 风险 | 处置 | 回滚 |
|---|---|---|
| 重复 retry 造成重复 attempt | `retryInFlight=true` 的 QUEUED/RETRY run 直接幂等返回 | 回滚 M3B 代码或临时隐藏 retry 入口 |
| 未接 runner 被误认为真实进程取消 | cancel 摘要固定 `runnerCancelAttempted=false`、`runnerDispatched=false` | 后续 M4 接入 WP6 runner cancel/dispatch 后再扩展语义 |
| 旧失败证据丢失 | retry 插入新 attempt，不更新原失败 node run | 回滚 retry 逻辑并保留现有 run/node run 记录 |
| SQL 与 local 仓储语义漂移 | DB profile contract 覆盖 run/node run update 和 attempt 排序 | 修复 mapper 后重跑 DB contract |

## 7. 五角色结论

| 角色 | 结论 | 说明 |
|---|---|---|
| 资深项目经理 | 通过 | 本轮范围限定 M3B 取消和控制面重试，不抢跑 queue claim、WP6 dispatch 和前端。 |
| 资深产品经理 | 通过 | 用户可以撤销未执行 run，并对失败节点发起可追踪重试，满足运行控制面最小闭环。 |
| 资深服务端架构师 | 通过 | 后端分层、权限 scope、幂等、attempt 证据保留和敏感信息摘要符合技术契约；Java 核心逻辑已补必要注释。 |
| 资深前端工程师 | 无影响 | 本轮未改 `portal-web`；接口契约已为后续工作台取消/重试按钮提供基础。 |
| 资深质量工程师 | 通过 | 定向后端、全量后端、DB validation、前端单测、前端构建和 diff check 均已通过；前端 build 仅保留既有 Vite chunk/import 警告。 |

## 8. 后续任务

1. WP9-3.x：补 queue claim、状态聚合和 timeout recovery。
2. WP9-4.x：通过 WP6 应用服务 dispatch `API_TEST` 节点并同步结果摘要。
3. WP9-5.x：补 webhook/cron 触发控制面和幂等事件记录。
4. WP9-6.x：实现 `portal-web` execution 工作台。
