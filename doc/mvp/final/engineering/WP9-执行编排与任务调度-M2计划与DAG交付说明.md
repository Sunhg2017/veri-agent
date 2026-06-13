# WP9 执行编排与任务调度 - M2 计划与 DAG 交付说明

| 项目 | 内容 |
|---|---|
| 工作包 | WP9 执行编排与任务调度 |
| 交付阶段 | M2 计划与 DAG |
| 覆盖 Story | WP9-2.1、WP9-2.2、WP9-2.3、WP9-2.4、WP9-2.5、WP9-7.1 部分、WP9-8.1 部分 |
| 当前口径 | 完成 plan CRUD、DAG validator、WP6 bundle scope resolver、dry-run、状态保护和后端测试；不实现 run 创建、队列认领、WP6 dispatch、外部触发和前端工作台 |
| 日期 | 2026-06-13 |

## 1. 任务启动口径

| 项 | 结论 |
|---|---|
| 目标 | 在 `platform-api` 内推进 WP9 M2，使执行计划可创建、查询、更新、归档，并可 dry-run 校验 DAG 和资源引用。 |
| 范围 | 后端 plan API、DAG 校验、状态保护、WP6 bundle scope 校验、OpenAPI contract、后端单测和交付文档。 |
| 非目标 | 不创建 run/node run，不认领队列，不调用 WP6 runner，不做 webhook/cron，不改 `portal-web` 工作台。 |
| 涉及模块 | `platform-api` execution、WP6 api-automation bundle scope、MyBatis mapper、OpenAPI contract、WP9 文档。 |
| 风险 | DAG 校验误放行会导致后续触发失败；跨 WP 资源校验如果直读表会破坏边界；secretRef 如果原样返回会泄露敏感引用。 |
| 验收标准 | plan CRUD/dry-run 统一 envelope 和权限生效；非法 DAG、跨项目 bundle、未审批 bundle 不可持久化为可用计划；dry-run 不创建 run、不调 runner、不返回 secret 明文；后端定向和全量测试通过。 |
| 回滚方式 | 回滚本轮 execution M2 代码和文档；DB M1 表结构保持不变，入口可通过权限或隐藏菜单禁用。 |

需求文档/产品 PRD、技术设计/接口契约、测试策略/脚本均已有 WP9 启动包；本轮在技术设计、测试策略和 M2 交付说明中同步真实实现。`WP1-WP4-统一发布准出清单.md` 本轮不适用，原因是未改 WP1-WP4 发布流程、权限模型基础表、数据库迁移或文档输入链路；仅复用 WP1 权限/审计能力和 M1 已建 WP9 表。

## 2. 需求结论

M2 后端计划控制面已形成可验收闭环：

1. `POST /api/v1/execution/plans` 创建计划并持久化 normalized DAG。
2. `GET /api/v1/execution/plans`、`GET /api/v1/execution/plans/{id}` 查询计划列表和详情。
3. `PATCH /api/v1/execution/plans/{id}` 更新草稿、READY 或 DISABLED 计划；ARCHIVED 计划不可更新。
4. `POST /api/v1/execution/plans/{id}/dry-run` 重新校验当前 DAG、资源和 runner 策略，不创建 run。
5. `POST /api/v1/execution/plans/{id}/archive` 归档计划；禁止通过 create/update 直接写入 `ARCHIVED`。

## 3. 主要变更

| 模块 | 变更 |
|---|---|
| `platform-api` execution API | 新增 `ExecutionPlanController`，覆盖创建、列表、详情、更新、dry-run 和归档。 |
| execution application | 新增 `ExecutionPlanService`、`ExecutionDagValidator`、`ExecutionPermissionScopeResolver`、`ExecutionActorResolver` 和平台上下文审计 client。 |
| execution domain/port | 新增 plan、plan node 领域记录和 `ExecutionRepository` 端口。 |
| WP6 边界 | 新增 `ApiAutomationBundleScopeService`，WP9 只读取 WP6 应用服务暴露的 bundle scope，不直读 WP6 表。 |
| persistence | 新增 `JdbcExecutionRepository`、`ExecutionMapper` 和 `mapper/execution/ExecutionMapper.xml`，复用 M1 表。 |
| safety | DAG 输入对 secret/token/password/authorization/cookie/apiKey 等 key 做摘要脱敏；dry-run policy 明确不创建 run、不调 runner。 |
| tests | 新增 DAG validator 和 plan controller 测试；OpenAPI contract 纳入 execution plan 路径；权限字符串架构测试纳入 `execution:*`。 |
| health | `planCrudReady=true`、`dagDryRunReady=true`，`wp6DispatchReady=false` 保持后续口径。 |

## 4. 验收标准

1. 计划 API 必须受 `execution:read/manage` 权限约束，匿名访问业务计划接口返回 403。
2. `API_TEST` 节点必须引用同项目且 `APPROVED` 的 WP6 脚本包。
3. DAG validator 必须识别重复节点、非法 key、缺失依赖、自依赖、循环依赖、非法 timeout、非法 failurePolicy 和未就绪 runner 类型。
4. create/update 持久化前必须校验 DAG；dry-run 重新校验存量 DAG 且不产生运行记录。
5. `ARCHIVED` 只能通过 archive endpoint 进入，归档后不可更新。
6. 返回和审计 payload 不包含 secret 明文、secretRef 原文、runner 原始输出或请求响应正文。

## 5. 验证结果

| 命令 | 结果 |
|---|---|
| `mvn -B -pl platform-api -Dtest='ExecutionDagValidatorTest,ExecutionHealthControllerTest,ExecutionPlanControllerTest,PermissionCodeUsageTest,ModuleLayerDependencyTest,PersistenceProfileBoundaryTest,OpenApiContractTest' test` | 通过，40 tests，0 failures，0 errors |
| `mvn -B -pl platform-api test` | 通过，512 tests，0 failures，0 errors；测试日志中有 WP5 定时补偿 teardown 期 JDBC warning，Maven 结果为 success |
| `bash db/validation/run_wp1_db_validation.sh` | 通过，WP1-WP9 当前 migration 与 seed 重放校验通过 |
| `git diff --check` | 通过，无空白错误 |
| `cd portal-web && npm test` | 通过，22 files，178 tests |
| `cd portal-web && npm run build` | 通过；Vite 输出既有 chunk 体积和 auth 模块动静态导入提示 |

## 6. 风险与回滚

| 风险 | 处置 | 回滚 |
|---|---|---|
| 后续 run 触发使用未审批或跨项目 bundle | M2 已在 DAG validator 中阻断 | 回滚 M2 代码或临时禁止 `execution:manage` |
| dry-run 和 create 校验口径不一致 | create/update/dry-run 复用同一 validator | 修复 validator 并重跑定向测试 |
| secretRef 泄露 | 输入摘要对敏感 key 只返回 masked/count | 回滚接口或加强脱敏规则 |
| MyBatis 与 local 测试仓储语义漂移 | DB 实现只做 M1 表读写，local 仓储仅在测试 profile 生效 | 后续 DB profile contract 测试补齐 |

## 7. 五角色结论

| 角色 | 结论 | 说明 |
|---|---|---|
| 资深项目经理 | 通过 | 本轮范围限定 M2 后端计划与 DAG，不抢跑 M3 run/queue 和 M6 前端。 |
| 资深产品经理 | 通过 | 计划、DAG、dry-run、归档和错误边界符合 PRD；用户价值聚焦执行计划可配置和可预检。 |
| 资深服务端架构师 | 通过 | 后端分层、权限、状态保护、WP6 端口边界和敏感信息脱敏符合技术契约；Java 核心逻辑已补必要注释。 |
| 资深前端工程师 | 无影响 | 本轮未改 `portal-web`；接口契约已为后续 WP9 工作台提供 plan/dry-run 后端基础。 |
| 资深质量工程师 | 通过 | 定向后端、全量后端、DB validation、前端单测、前端构建和 diff check 均已通过；M3 以后的 run/queue/dispatch/webhook/cron/前端工作台测试后续补齐。 |

## 8. 后续任务

1. WP9-3.x：实现手动触发 run、node run 初始化、状态聚合、cancel/retry 和 queue claim。
2. WP9-4.x：通过 WP6 应用服务 dispatch `API_TEST` 节点并同步结果摘要。
3. WP9-5.x：补 webhook/cron 触发控制面和幂等事件记录。
4. WP9-6.x：实现 `portal-web` execution 工作台。
