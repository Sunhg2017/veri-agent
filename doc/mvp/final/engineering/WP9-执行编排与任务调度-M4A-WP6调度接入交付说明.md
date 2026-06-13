# WP9 执行编排与任务调度 - M4A WP6 调度接入交付说明

| 项目 | 内容 |
|---|---|
| 工作包 | WP9 执行编排与任务调度 |
| 交付阶段 | M4A WP6 调度接入 |
| 覆盖 Story | WP9-4.1、WP9-7.1 部分、WP9-8.1 部分 |
| 当前口径 | 完成 claimed `API_TEST` node run 通过 WP6 应用服务创建 API automation run，并把 WP6 结果脱敏聚合回 WP9；不启后台 scheduler loop、不解析 `baseUrlRef`、不做计划 secretRef 自动中继、不做 WP7/UI 或前端工作台 |
| 日期 | 2026-06-13 |

## 1. 任务启动口径

| 项 | 结论 |
|---|---|
| 目标 | 在 M3D 队列认领和恢复能力基础上，让内部 worker 可以把 active claimed `API_TEST` 节点派发给 WP6 `ApiAutomationService#createRun`，并同步 WP6 run 的终态摘要。 |
| 范围 | `POST /internal/queue/node-runs/{id}/dispatch`、运行时 dispatch command、WP6 应用服务调用、WP6 状态到 WP9 节点状态映射、脱敏 resultSummary、health、OpenAPI contract、后端测试和 WP9 文档。 |
| 非目标 | 不启动后台 scheduler 线程，不解析 `baseUrlRef`，不从 plan 中自动还原或中继 `secretRefs`，不直连 WP6 runner adapter，不做 runner cancel 扩展，不做 webhook/cron，不改 `portal-web`。 |
| 涉及模块 | `platform-api` execution API/application/command、WP6 application service、OpenAPI contract、WP9 文档与测试。 |
| 风险 | 同步 WP6 run 耗时超过 claim lease；dispatch 摘要误存 raw baseUrl 或 secretRef；WP6 runner disabled/allowlist 阻断语义与 WP9 聚合不一致；后续 scheduler loop 接入时重复派发。 |
| 验收标准 | active claim 且 node 为 `RUNNING/API_TEST/WP6_API` 才能 dispatch；WP9 只调用 WP6 应用服务，不直连 runner adapter；WP6 `PASSED/BLOCKED/TIMEOUT/FAILED` 映射到 WP9 `SUCCEEDED/BLOCKED/TIMEOUT/FAILED`；摘要不包含 raw baseUrl、secretRef、请求响应或 stdout/stderr；health 标记 `wp6DispatchReady=true`。 |
| 回滚方式 | 回滚本轮 M4A 代码和文档；保留 M3D claim/heartbeat/recovery/complete 入口，可临时隐藏 dispatch endpoint 或撤销 `execution:admin` 调用。 |

需求文档/产品 PRD、技术设计/接口契约、测试策略/脚本均已有 WP9 启动包；本轮在技术设计、测试策略和 M4A 交付说明中同步真实实现。`WP1-WP4-统一发布准出清单.md` 本轮不适用，原因是未改 WP1-WP4 发布流程、数据库迁移或文档输入链路；仅复用 WP1 权限/审计能力和 M1 已建 WP9 表，并调用既有 WP6 应用服务。

## 2. 需求结论

M4A 后端 dispatch 切片完成以下能力：

1. `POST /api/v1/execution/internal/queue/node-runs/{id}/dispatch` 由 `execution:admin` 调用，使用 claimToken 校验 active claim。
2. dispatch 仅支持 `RUNNING` 状态下的 `API_TEST/WP6_API` 节点，非 API_TEST 节点返回 `EXECUTION_NODE_DISPATCH_UNSUPPORTED`。
3. 请求体携带运行时 `baseUrl`、可选 `environmentId/caseIds/secretRefs`；WP9 不保存 raw `baseUrl` 或 secretRef 明文。
4. WP9 通过 `ApiAutomationService#createRun` 创建 WP6 run，复用 WP6 runner disabled、allowlist、secretRef 解析、host/digest 和 runner 输出脱敏能力。
5. WP6 run 终态被归一化到 WP9 node run，并触发 M3C/M3D 已有依赖推进和 run 聚合。
6. health policy 更新为 `wp6DispatchReady=true` 和 `wp6DispatchViaApplicationService=true`，同时 `directRunnerAdapterCallAllowed=false` 保持不变。

## 3. 主要变更

| 模块 | 变更 |
|---|---|
| execution API | `ExecutionRunController` 新增内部 dispatch endpoint，路径为 `/api/v1/execution/internal/queue/node-runs/{id}/dispatch`。 |
| execution command | 新增 `DispatchExecutionNodeRunCommand`，承载 claimToken、运行时 baseUrl、environmentId、caseIds 和 secretRefs。 |
| execution application | `ExecutionRunService` 注入 `ApiAutomationService`，新增 active claim 校验、dispatch lease 续约、WP6 `createRun` 调用、状态映射、安全摘要和 run 聚合。 |
| health | `wp6DispatchReady=true`，新增 `wp6DispatchViaApplicationService=true`。 |
| tests | `ExecutionRunControllerTest` 覆盖 runner disabled 阻断与敏感值不落摘要；`ExecutionRunDispatchControllerTest` 覆盖 WP6 managed success 聚合；`OpenApiContractTest` 覆盖 dispatch endpoint；`ExecutionHealthControllerTest` 覆盖 health policy。 |
| docs | 技术设计、测试策略和 M4A 交付说明同步。 |

## 4. 验收标准

1. dispatch endpoint 必须受 `execution:admin` 平台权限保护。
2. claimToken 必须与路径 nodeRunId 匹配，claim 非 active 或节点非 RUNNING 必须拒绝。
3. API_TEST dispatch 必须通过 WP6 应用服务；不得直接注入或调用 runner adapter。
4. WP6 blocked run 应归一为 WP9 `BLOCKED` node，并按 fail-fast 依赖阻断下游。
5. WP6 passed run 应归一为 WP9 `SUCCEEDED` node，并聚合 run 为 `SUCCEEDED`。
6. WP9 响应、落库摘要和审计不得包含 raw baseUrl、secretRef 明文、请求响应正文、stdout/stderr 或 runner artifact。

## 5. 验证结果

| 命令 | 结果 |
|---|---|
| `mvn -B -pl platform-api -DskipTests compile` | 通过 |
| `mvn -B -pl platform-api -Dtest=ExecutionRunControllerTest,ExecutionRunDispatchControllerTest,ExecutionHealthControllerTest,OpenApiContractTest test` | 通过，18 tests，0 failures，0 errors |
| `mvn -B -pl platform-api test` | 通过，523 tests，0 failures，0 errors |
| `bash db/validation/run_wp1_db_validation.sh` | 通过，WP1-WP9 migration/seed 重放和 runtime role policy validation passed；日志目录 `build/wp1-db-validation` |
| `cd portal-web && npm test` | 通过，22 test files，178 tests |
| `cd portal-web && npm run build` | 通过；存在既有 Vite dynamic import/chunk size warning，无构建失败 |
| `git diff --check` | 通过 |

## 6. 风险与回滚

| 风险 | 处置 | 回滚 |
|---|---|---|
| WP6 同步执行时间超过短 heartbeat lease | dispatch 前按 plan node timeout 续约 claim，避免 recovery 误收敛 active dispatch | 隐藏 dispatch endpoint，回到 M3D 手动 complete |
| raw baseUrl 或 secretRef 泄露到 WP9 | `baseUrl/secretRefs` 仅作为 WP6 runtime command 输入，WP9 摘要只保存 host/digest 和计数 | 加强断言后重跑定向测试 |
| WP6 runner disabled 被误认为系统错误 | WP6 `BLOCKED/RUNNER_DISABLED` 映射为 WP9 node `BLOCKED`，下游按 fail-fast 阻断 | 保留 WP6 run 证据，调整映射策略 |
| 后续 scheduler loop 重复派发 | M4A 只暴露内部手动 dispatch endpoint；后续 loop 必须在 claim active 且 node RUNNING 时调用 | 回滚 scheduler loop，不影响本轮 endpoint |

## 7. 五角色结论

| 角色 | 结论 | 说明 |
|---|---|---|
| 资深项目经理 | 通过 | 本轮范围限定 M4A API_TEST 到 WP6 应用服务 dispatch，不抢跑 scheduler loop、webhook/cron 或前端。 |
| 资深产品经理 | 通过 | `API_TEST` 节点从控制面占位推进到可创建 WP6 run 并展示执行摘要，满足 P0 用户价值；环境解析和计划 secretRef 中继明确后续处理。 |
| 资深服务端架构师 | 通过 | 调用边界保持在 WP6 应用服务，复用 WP6 安全策略；状态映射、claim 校验、摘要脱敏和 run 聚合符合接口契约；Java 核心逻辑已补必要注释。 |
| 资深前端工程师 | 无影响 | 本轮未改 `portal-web`；run detail 和 health 已提供后续工作台展示 dispatch readiness 与 WP6 run 摘要所需字段。 |
| 资深质量工程师 | 通过 | 定向后端测试覆盖 runner disabled、managed success、OpenAPI 和 health；全量后端、DB validation、前端测试/构建和 diff check 均已通过。 |

## 8. 后续任务

1. WP9-4B：补 baseUrlRef 环境解析、计划 secretRef 安全中继和 WP6 timeout/failure dispatch 测试。
2. WP9-4C：接后台 scheduler loop，由 worker 自动 claim、dispatch、heartbeat 和 recovery。
3. WP9-5.x：补 webhook/cron 触发控制面和幂等事件记录。
4. WP9-6.x：实现 `portal-web` execution 工作台。
