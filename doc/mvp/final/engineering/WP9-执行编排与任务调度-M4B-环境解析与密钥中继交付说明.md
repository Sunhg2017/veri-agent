# WP9 执行编排与任务调度 - M4B 环境解析与密钥中继交付说明

| 项目 | 内容 |
|---|---|
| 工作包 | WP9 执行编排与任务调度 |
| 交付阶段 | M4B 环境解析与密钥中继 |
| 覆盖 Story | WP9-4.1、WP9-4.2、WP9-7.1 部分、WP9-8.1 部分 |
| 当前口径 | 在 M4A claimed `API_TEST` dispatch 基础上，补齐 `baseUrlRef=env:<key>` 环境解析、计划 `runtimeSecretRefs` 安全中继、WP6 `FAILED/TIMEOUT` 到 WP9 终态映射测试；不启后台 scheduler loop、不做 webhook/cron、不做前端工作台 |
| 日期 | 2026-06-13 |

## 1. 任务启动口径

| 项 | 结论 |
|---|---|
| 目标 | 让内部 dispatch 在未显式传 `baseUrl` 时可解析同项目 WP1 环境 `api_base_url`，并让计划默认运行密钥引用可安全传给 WP6。 |
| 范围 | dispatch command `baseUrlRef`、WP1 environment runtime ref 查询、`runtimeSecretRefs` DAG 校验、plan 详情脱敏、WP6 dispatch 摘要 digest、WP6 `FAILED/TIMEOUT` 映射测试、WP9 文档。 |
| 非目标 | 不启动后台 scheduler loop，不解析非 `env:<key>` 的 baseUrlRef，不绕过 WP6 allowlist/SecretProvider，不保存 secretRef 明文，不做 WP7/UI、webhook/cron 或 `portal-web`。 |
| 涉及模块 | `platform-api` execution API/application/command、management store runtime ref、MyBatis mapper、WP9 后端测试和文档。 |
| 风险 | plan 默认密钥引用被对外回显；baseUrlRef 跨项目引用；SecretProvider 不可用时误认为 WP9 调度失败；后续 scheduler 重复派发。 |
| 验收标准 | 显式 `baseUrl` 优先，否则只支持 `baseUrlRef=env:<key>`；环境必须同项目且启用；计划 `runtimeSecretRefs` 必须是 `secret://` 引用列表；对外只返回 masked/count/digest；WP6 `FAILED/TIMEOUT` 映射为 WP9 `FAILED/TIMEOUT`。 |
| 回滚方式 | 回滚 M4B 代码和文档；M4A 显式 `baseUrl` dispatch 仍可保留，或临时要求 worker 继续传运行时 baseUrl/secretRefs。 |

需求文档/PRD、技术设计/接口契约、测试策略/脚本已在 WP9 启动包和本轮技术设计中同步。`WP1-WP4-统一发布准出清单.md` 本轮适用性：未改 WP1-WP4 发布流程、密钥解析实现或文档输入链路；仅新增 WP1 management 环境只读 runtime ref 查询供 WP9 scope 校验，因此通过 DB validation 覆盖 mapper/DDL 契约。

## 2. 需求结论

1. `DispatchExecutionNodeRunCommand` 支持可选 `baseUrlRef`；显式 `baseUrl` 仍优先。
2. `baseUrlRef` 当前只接受 `env:<environmentKey>`，解析到同项目、启用环境的 `api_base_url` 后传给 WP6。
3. plan input 可配置 `runtimeSecretRefs` 作为默认 WP6 runtime secretRefs；请求体 `secretRefs` 仍可覆盖。
4. `runtimeSecretRefs` 在 DAG validator 中校验数量、长度和 `secret://` 格式；plan/run 响应不回显完整引用，只返回 count/digest。
5. WP6 SecretProvider 缺失时错误只返回 digest，不返回完整 secretRef 或 raw baseUrl。
6. WP6 `FAILED/TIMEOUT` run 归一为 WP9 node/run `FAILED/TIMEOUT`，并保留安全 WP6 evidence。

## 3. 主要变更

| 模块 | 变更 |
|---|---|
| execution command/API | dispatch 请求新增 `baseUrlRef`，`baseUrl` 变为可选；空请求体构造同步更新。 |
| execution application | `ExecutionRunService` 解析 request/plan baseUrl、同项目环境、plan/runtime secretRefs，摘要增加 `baseUrlSource`、`baseUrlRefDigest`、`runtimeSecretRefDigests`。 |
| plan/DAG | `ExecutionDagValidator` 校验 `runtimeSecretRefs`；`ExecutionPlanService` 对外脱敏 `runtimeSecretRefs` 为 masked/count/digests。 |
| management store | 新增 `EnvironmentRuntimeRef` 和 `findEnvironmentRuntimeRef` mapper，用于 WP9 内部只读解析环境 id/projectId/code/apiBaseUrl/status。 |
| tests | 新增 baseUrlRef 成功 dispatch、runtimeSecretRefs SecretProvider 边界脱敏、WP6 FAILED/TIMEOUT 映射、runtimeSecretRefs 校验和 plan 脱敏断言。 |
| docs | 更新技术设计、测试策略、README，并新增 M4B 交付说明。 |

## 4. 验收标准

1. request `baseUrl` 优先于 `baseUrlRef` 和 plan input。
2. plan `baseUrlRef=env:<key>` 只能解析同项目启用环境；不支持引用返回稳定错误。
3. plan `runtimeSecretRefs` 不得在 plan/run 响应、错误、审计或摘要中以完整值出现。
4. WP9 不直接调用 runner adapter，不绕过 WP6 allowlist、SecretProvider、runner output redaction。
5. WP6 `PASSED/BLOCKED/TIMEOUT/FAILED` 分别归一为 WP9 `SUCCEEDED/BLOCKED/TIMEOUT/FAILED`。
6. DB mapper 查询和 WP9 后端回归通过。

## 5. 验证结果

| 命令 | 结果 |
|---|---|
| `mvn -B -pl platform-api -DskipTests compile` | 通过 |
| `mvn -B -pl platform-api -Dtest=ExecutionRunDispatchControllerTest test` | 通过，4 tests，0 failures，0 errors |
| `mvn -B -pl platform-api -Dtest=ExecutionDagValidatorTest,ExecutionPlanControllerTest,ExecutionRunControllerTest,ExecutionRunDispatchControllerTest,ExecutionHealthControllerTest,OpenApiContractTest test` | 通过，27 tests，0 failures，0 errors |
| `mvn -B -pl platform-api test` | 通过，527 tests，0 failures，0 errors；存在既有 SpringDoc、Mockito dynamic agent、Hikari shutdown warning，不影响结果 |
| `bash db/validation/run_wp1_db_validation.sh` | 通过，WP1 database migration and validation passed |
| `cd portal-web && npm test` | 通过，22 files，178 tests |
| `cd portal-web && npm run build` | 通过；存在既有 dynamic/static import 和 chunk size warning |
| `git diff --check` | 通过 |

## 6. 风险与回滚

| 风险 | 处置 | 回滚 |
|---|---|---|
| `runtimeSecretRefs` 对外泄露 | plan 详情脱敏为 masked/count/digests，dispatch 摘要只保存 digest；测试断言不包含原始引用 | 回滚 `runtimeSecretRefs` 中继，继续要求 worker runtime 传参 |
| `baseUrlRef` 跨项目引用 | environment runtime ref 返回 projectId，WP9 与 plan projectId 严格比对 | 禁用 baseUrlRef，要求显式 baseUrl |
| SecretProvider 缺失导致调度失败 | 保持 WP6 `SECRET_PROVIDER_ERROR`，错误仅含 digest；不在 WP9 层降级或伪造 secret | 配置 SecretProvider 或移除计划 `runtimeSecretRefs` |
| 后续 scheduler loop 重复派发 | M4B 仍只提供内部手动 dispatch endpoint，scheduler 需在 M4C 复用 active claim 约束 | 回滚 scheduler loop，不影响 M4B endpoint |

## 7. 五角色结论

| 角色 | 结论 | 说明 |
|---|---|---|
| 资深项目经理 | 通过 | 本轮只推进 M4B 环境解析、密钥中继和映射测试，不抢跑 scheduler/webhook/frontend。 |
| 资深产品经理 | 通过 | API_TEST 节点可从计划配置解析运行环境和默认密钥引用，降低 worker 手工传参成本，同时不暴露敏感值。 |
| 资深服务端架构师 | 通过 | baseUrlRef scope 绑定 WP1 环境 projectId；WP6 仍负责 URL/allowlist/SecretProvider/runner 脱敏；Java 核心逻辑已有必要注释。 |
| 资深前端工程师 | 无影响 | 本轮未改 `portal-web`；后续工作台可显示 masked/count/digest、baseUrlSource 和 WP6 状态摘要。 |
| 资深质量工程师 | 通过 | compile、WP9 定向测试、全量后端、DB validation、前端测试/构建和 diff check 均已通过；剩余 warning 为既有非阻断项。 |

## 8. 后续任务

1. WP9-4C：实现后台 scheduler loop，由 worker 自动 claim、dispatch、heartbeat 和 recovery。
2. WP9-5.x：补 webhook/cron 触发控制面和幂等事件记录。
3. WP9-6.x：实现 `portal-web` execution 工作台。
