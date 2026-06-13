# WP9 执行编排与任务调度 - 研发任务拆解

| 项目 | 内容 |
|---|---|
| 工作包 | WP9 执行编排与任务调度 |
| 角色产出 | 五角色联合任务拆解 |
| 文档性质 | 正式研发前可执行 Story/Task 清单 |
| 当前口径 | 以 `platform-api` + `portal-web` 为控制面，P0 通过 WP6 应用服务调度 API automation run；不抢跑 WP10 完整报告诊断 |
| 版本 | v0.1 |
| 日期 | 2026-06-13 |

## 1. 拆解原则

1. 先控制面和状态机，再做调度认领和外部触发。
2. 每个任务必须有项目 scope、权限、审计、traceId 和脱敏口径。
3. WP9 不直接调用 runner adapter；API_TEST 节点必须通过 WP6 应用服务。
4. 所有队列和触发入口必须幂等，重复事件不能重复污染运行记录。
5. P0 不建设 WP10 完整报告、WP8 账号池、WP7 浏览器执行器和独立分布式调度服务。

## 2. 角色分工

| 角色 | 主要负责 |
|---|---|
| 资深项目经理 | 任务排期、依赖协调、里程碑准出、风险和回滚 |
| 资深产品经理 | 用户流程、计划/触发/运行字段含义、验收标准和非目标 |
| 资深服务端架构师 | DB、领域模块、API 契约、队列、状态机、WP6/WP7/WP10 集成和安全边界 |
| 资深前端工程师 | 工作台路由、计划表单、DAG 预览、运行详情、权限和响应式 |
| 资深质量工程师 | DAG/队列/触发/恢复测试矩阵、smoke、DB validation 和发布准出 |

## 3. 总体里程碑

| 里程碑 | 目标 | 退出标准 |
|---|---|---|
| M0 启动准入 | 文档、范围、任务拆解冻结 | 五角色评审无阻断 |
| M1 基础骨架 | 权限、DB、模块骨架、health | OpenAPI contract 和 DB validation 通过 |
| M2 计划与 DAG | plan CRUD、DAG dryRun、资源校验 | 循环依赖、跨项目、非法输入可测 |
| M3 运行状态机 | 手动触发、run/node run、状态聚合 | 幂等、取消、重试、timeout 可测 |
| M4 WP6 调度接入 | API_TEST 节点创建并跟踪 WP6 run | 不绕过 WP6 安全策略 |
| M5 触发和恢复 | webhook/cron 控制面、队列认领、recovery | 重复触发和卡死状态可恢复 |
| M6 前端闭环 | 执行工作台主链路 | Vitest、Playwright、build 通过 |
| M7 质量门禁 | WP9 quality gate、DB validation、smoke | 发布模式准出规则明确 |

## 4. Epic 0：启动准入

| Story | 优先级 | 负责人 | 任务 | 验收 | 验证 |
|---|---|---|---|---|---|
| WP9-0.1 范围冻结 | P0 | 项目经理 | 确认 WP9 只覆盖执行编排与调度控制面，明确 WP6/WP7/WP8/WP10 边界 | 6 份启动文档口径一致 | 文档评审 |
| WP9-0.2 依赖清单 | P0 | 服务端架构师 | 梳理 WP1/WP3/WP6/WP7/WP10 依赖和可用接口 | 不直接读写跨 WP 表 | 文档评审 |
| WP9-0.3 样本计划 | P0 | 质量工程师 | 准备最小 API_TEST plan、失败 DAG、重复 webhook、timeout fixture | 后续测试可直接落地 | fixture 清单 |

## 5. Epic 1：权限、DB 和模块骨架

| Story | 优先级 | 负责人 | 任务 | 验收 | 验证 |
|---|---|---|---|---|---|
| WP9-1.1 权限点 seed | P0 | 服务端架构师 | 新增 `execution:read/manage/trigger/admin/export` 权限和角色映射 | 默认角色符合 PRD | DB validation、权限测试 |
| WP9-1.2 审计事件字典 | P0 | 服务端架构师、质量工程师 | 定义 plan、run、node、trigger、cancel、retry、export 审计事件 | payload 只含摘要和 digest | 审计单测 |
| WP9-1.3 DB schema | P0 | 服务端架构师 | 新增 plan、plan_node、run、node_run、trigger、trigger_event、queue_claim 表 | 约束、索引、注释完整 | DB validation |
| WP9-1.4 模块骨架 | P0 | 服务端架构师 | 新建 `execution` api/application/domain/infrastructure/config 包 | 不破坏现有模块边界 | `mvn -B -pl platform-api test` |
| WP9-1.5 Health API | P0 | 服务端架构师 | `GET /api/v1/execution/health` 输出调度开关、limits 和安全边界 | 不泄露 secret 或 trigger key | Controller test |

## 6. Epic 2：计划和 DAG

| Story | 优先级 | 负责人 | 任务 | 验收 | 验证 |
|---|---|---|---|---|---|
| WP9-2.1 Plan CRUD | P0 | 服务端架构师 | 创建、列表、详情、更新、归档执行计划 | project scope 和状态保护正确 | Controller test |
| WP9-2.2 DAG validator | P0 | 服务端架构师 | 校验节点 key、类型、依赖、循环、timeout、retry、failurePolicy | 非法 DAG 返回 `EXECUTION_DAG_INVALID` | Unit test |
| WP9-2.3 Resource resolver | P0 | 服务端架构师 | 校验 WP6 bundle、WP3 asset、environmentKey 同项目归属 | 跨项目返回 `EXECUTION_RESOURCE_SCOPE_DENIED` | Service test |
| WP9-2.4 DryRun API | P0 | 服务端架构师、产品经理 | `POST /plans/{id}/dry-run` 返回校验结果和节点策略摘要 | 不创建 run，不触发 runner | Controller test |
| WP9-2.5 Plan 状态机 | P0 | 服务端架构师 | `DRAFT/READY/DISABLED/ARCHIVED` 状态保护 | 非 READY 不可触发 | Service test |

## 7. Epic 3：运行、队列和状态收敛

| Story | 优先级 | 负责人 | 任务 | 验收 | 验证 |
|---|---|---|---|---|---|
| WP9-3.1 手动触发 API | P0 | 服务端架构师 | `POST /plans/{id}/runs` 创建 run 和 node run | requestKey 幂等 | Controller test |
| WP9-3.2 Run 查询 | P0 | 服务端架构师 | `GET /runs`、`GET /runs/{id}` 返回节点和摘要 | 不泄露敏感字段 | Controller test |
| WP9-3.3 Queue claim | P0 | 服务端架构师 | 条件认领 QUEUED node，记录 claimToken 和 heartbeat | 多 worker 不重复认领 | Repository test |
| WP9-3.4 状态聚合 | P0 | 服务端架构师 | 根据 node run 聚合 run 状态 | partial/failed/timeout 规则稳定 | Unit test |
| WP9-3.5 Cancel API | P0 | 服务端架构师 | 取消 QUEUED/RUNNING run 和 node，终态幂等 | 写 `execution.run.canceled` | Service test |
| WP9-3.6 Retry API | P0 | 服务端架构师 | 重试 FAILED/TIMEOUT 节点，生成新 attempt | 原失败证据保留 | Service test |
| WP9-3.7 Timeout recovery | P1 | 服务端架构师、质量工程师 | 扫描 heartbeat 过期节点并收敛状态 | 卡死运行可恢复 | Scheduler smoke |

## 8. Epic 4：WP6/WP7/WP10 集成

| Story | 优先级 | 负责人 | 任务 | 验收 | 验证 |
|---|---|---|---|---|---|
| WP9-4.1 WP6 dispatch port | P0 | 服务端架构师 | API_TEST 节点通过 WP6 service 创建 run | 不直接调用 runner adapter | Service test |
| WP9-4.2 WP6 result sync | P0 | 服务端架构师 | 聚合 WP6 run status、resultCounts、errorCode、digest | 不保存 WP6 禁止字段 | Service test |
| WP9-4.3 WP7 placeholder | P1 | 服务端架构师 | UI_TEST 节点未启用时返回 `EXECUTION_RUNNER_NOT_READY` | 前端可解释 | Unit test |
| WP9-4.4 WP10 handoff | P1 | 服务端架构师、产品经理 | REPORT_HANDOFF 节点生成报告输入摘要事件 | 不生成完整报告 | Contract test |

## 9. Epic 5：触发控制面

| Story | 优先级 | 负责人 | 任务 | 验收 | 验证 |
|---|---|---|---|---|---|
| WP9-5.1 Trigger model | P0 | 服务端架构师 | webhook/cron trigger 表和状态 | secret 只存引用或 digest | DB validation |
| WP9-5.2 Webhook API | P1 | 服务端架构师、质量工程师 | 签名、时间窗口、sourceEventId 幂等 | disabled 默认拒绝 | Security test |
| WP9-5.3 Cron metadata | P1 | 服务端架构师 | 保存 cron、timezone、nextFireAt 和启停 | 不启用时不触发 | Service test |
| WP9-5.4 Trigger events | P1 | 服务端架构师 | 保存触发事件、错误码、runId | 重复事件不重复 run | Repository test |

M5 后端切片已完成 trigger model、管理 API、webhook 签名入口、sourceEventId 幂等事件记录和 cron 元数据摘要；生产 cron scanner、供应商 webhook 插件样例和前端触发配置页仍归后续切片。

## 10. Epic 6：前端工作台

| Story | 优先级 | 负责人 | 任务 | 验收 | 验证 |
|---|---|---|---|---|---|
| WP9-6.1 API client | P0 | 前端工程师 | 新增 `portal-web/src/api/execution.ts` 和 normalize helper | camelCase/snake_case 兼容 | Vitest |
| WP9-6.2 权限入口 | P0 | 前端工程师 | 新增 `#execution` 入口和权限判断 | 无 read 权限不显示 | Vitest |
| WP9-6.3 计划列表 | P0 | 前端工程师 | 列表、筛选、空态、错误态、指标 | loading/empty/error 完整 | Component/helper test |
| WP9-6.4 计划编辑 | P0 | 前端工程师 | 表单、DAG 节点、dryRun、保存 | 校验和 traceId 展示 | Vitest |
| WP9-6.5 运行详情 | P0 | 前端工程师 | run/node 状态、取消、重试、导出 | 不展示禁止字段 | Vitest |
| WP9-6.6 触发配置 | P1 | 前端工程师 | webhook/cron 摘要、启停、dryRun | secret masked | Vitest |
| WP9-6.7 响应式 smoke | P1 | 前端工程师、质量工程师 | 桌面和 390px 视口主链路 | 无横向溢出 | Playwright smoke |

M6A 前端基础闭环已完成 `execution.ts` API client、`#execution` 权限入口、执行工作台主视图、计划列表/单节点创建、DAG 节点摘要、manual run、cancel/retry、trigger 摘要/dryRun 和前端 Vitest/build 验证。M6B 继续完成多节点 DAG 草稿编辑、选中计划回填、计划更新/归档操作、触发事件切换查看和 helper 级校验测试；Playwright 390px smoke、导出文件落地和生产 cron scanner 仍归后续 M6C/M7。

## 11. Epic 7：质量门禁和发布准出

| Story | 优先级 | 负责人 | 任务 | 验收 | 验证 |
|---|---|---|---|---|---|
| WP9-7.1 后端测试 | P0 | 质量工程师、服务端架构师 | plan、DAG、run、queue、dispatch、trigger、permission、audit 测试 | 主路径和错误路径覆盖 | `mvn -B -pl platform-api test` |
| WP9-7.2 前端测试 | P0 | 质量工程师、前端工程师 | api helper、权限、计划表单、运行状态 helper | 稳定通过 | `cd portal-web && npm test` |
| WP9-7.3 DB validation | P0 | 质量工程师、服务端架构师 | WP9 表、约束、索引、权限纳入 validation | 临时库迁移和复跑通过 | DB validation |
| WP9-7.4 Scheduler smoke | P0 | 质量工程师 | managed smoke 覆盖手动触发、WP6 dispatch、cancel、retry、timeout | 默认不访问外部网络 | `scripts/wp9_scheduler_smoke.sh` |
| WP9-7.5 Quality gate | P0 | 质量工程师 | `scripts/wp9_quality_gate.sh` 聚合后端、前端、构建、DB 和 smoke | release 模式显式要求 scheduler smoke | `scripts/wp9_quality_gate.sh` |

## 12. Epic 8：文档和交付

| Story | 优先级 | 负责人 | 任务 | 验收 | 验证 |
|---|---|---|---|---|---|
| WP9-8.1 API 契约更新 | P0 | 服务端架构师 | 技术设计随实现更新真实路径、字段、错误码 | OpenAPI test 一致 | 文档评审 |
| WP9-8.2 Scheduler Runbook | P0 | 质量工程师、服务端架构师 | 编写开关、恢复、重放、webhook secret、cron 排障 | 运维可按步骤处理 | Runbook 评审 |
| WP9-8.3 发布准出说明 | P0 | 项目经理、质量工程师 | 记录验证命令、跳过项、风险、回滚和远端分支 | 符合仓库模板 | PR/交付检查 |
| WP9-8.4 前端操作说明 | P1 | 产品经理、前端工程师 | 说明计划、DAG、运行、取消、重试和触发解释 | 用户无需 curl | 产品验收 |

## 13. P0 完成定义

1. 可创建 READY 执行计划并通过 DAG dryRun。
2. 可手动触发执行并生成 run/node run。
3. API_TEST 节点可通过 WP6 应用服务创建 run 并聚合结果。
4. cancel/retry/timeout/recovery 形成最小状态收敛闭环。
5. webhook/cron 控制面默认关闭，启用有签名、幂等和审计。
6. 前端工作台覆盖计划、DAG、运行、取消、重试和脱敏摘要。
7. `mvn -B -pl platform-api test`、`cd portal-web && npm test`、`cd portal-web && npm run build`、DB validation 和 WP9 quality gate 通过。

## 14. 推荐实施顺序

1. 先做 WP9-1.x，确保权限、DB 和 health 稳定。
2. 再做 WP9-2.x，完成计划和 DAG 校验。
3. 再做 WP9-3.x，完成运行状态机、队列和恢复。
4. 再做 WP9-4.x，接入 WP6 dispatch。
5. 再做 WP9-5.x，补 webhook/cron 控制面。
6. 前端 WP9-6.x 与后端契约并行推进，但触发、取消、重试按钮必须以后端权限和状态为准。
7. WP9-7.x 从第一轮迁移开始同步建设，避免最后补门禁。

## 15. 前期准备完成结论

WP9 前期准备已形成启动包：正式启动准备、需求 PRD、技术设计与接口契约、前端页面设计、测试策略与用例脚本、研发任务拆解。后续可按 Epic 1 进入 DB/权限/模块骨架实现。
