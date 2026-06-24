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

M5 后端切片已完成 trigger model、管理 API、webhook 签名入口、sourceEventId 幂等事件记录和 cron 元数据摘要；后续 M6/M7 已补前端触发摘要、生产 CRON scanner 和外部 webhook HTTP smoke；M8A 已补 GitHub/GitLab/Jenkins CI 签名样例和 helper；M8C 已补供应商 marketplace 接入包、模板、payload 示例和离线验收脚本。真实 OAuth/App 上架仍不属于当前 WP9 运行时交付。

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

M6A 前端基础闭环已完成 `execution.ts` API client、`#execution` 权限入口、执行工作台主视图、计划列表/单节点创建、DAG 节点摘要、manual run、cancel/retry、trigger 摘要/dryRun 和前端 Vitest/build 验证。M6B 已完成多节点 DAG 草稿编辑、选中计划回填、计划更新/归档操作、触发事件切换查看和 helper 级校验测试。M6C 已新增 Playwright 前端浏览器 smoke，覆盖桌面和 390px 视口下的计划创建、更新、DAG dryRun、手动运行、取消、重试、触发器创建/校验/启停和无横向溢出。M7C 已接入运行详情“导出摘要”按钮，调用脱敏 run export API 并展示 schema、节点状态计数和 redaction policy；生产 cron scanner 和聚合 WP9 quality gate 已在 M7A/M7B 落地，M7D 外部 webhook HTTP smoke 不新增前端页面改动。

## 11. Epic 7：质量门禁和发布准出

| Story | 优先级 | 负责人 | 任务 | 验收 | 验证 |
|---|---|---|---|---|---|
| WP9-7.1 后端测试 | P0 | 质量工程师、服务端架构师 | plan、DAG、run、queue、dispatch、trigger、permission、audit 测试 | 主路径和错误路径覆盖 | `mvn -B -pl platform-api test` |
| WP9-7.2 前端测试 | P0 | 质量工程师、前端工程师 | api helper、权限、计划表单、运行状态 helper | 稳定通过 | `cd portal-web && npm test` |
| WP9-7.3 DB validation | P0 | 质量工程师、服务端架构师 | WP9 表、约束、索引、权限纳入 validation | 临时库迁移和复跑通过 | DB validation |
| WP9-7.4 Scheduler smoke | P0 | 质量工程师 | managed smoke 覆盖 scheduler tick、CRON scanner、WP6 dispatch、report handoff、disabled noop、失败脱敏和配置边界 | 默认不访问外部网络；release gate 显式启用 | `scripts/wp9_scheduler_smoke.sh` |
| WP9-7.5 Webhook HTTP smoke | P0 | 质量工程师、服务端架构师 | managed/external smoke 覆盖真实 HTTP webhook 签名、sourceEventId 幂等、事件证据和 run export 脱敏 | release gate 显式启用；外部入口不泄露 secret/payload | `scripts/wp9_webhook_http_smoke.sh` |
| WP9-7.6 Quality gate | P0 | 质量工程师 | `scripts/wp9_quality_gate.sh` 聚合后端、前端、构建、DB、Playwright、scheduler smoke 和 webhook HTTP smoke | release 模式显式要求 scheduler smoke 与 webhook HTTP smoke | `scripts/wp9_quality_gate.sh` |
| WP9-7.7 Report handoff smoke | P1 | 质量工程师、服务端架构师 | 定向覆盖 `REPORT_HANDOFF` 完成摘要和 run export 脱敏证据 | WP10 交接证据可独立准出；不生成完整报告 | `scripts/wp9_report_handoff_smoke.sh` |
| WP9-7.8 CRON 容量 smoke | P1 | 质量工程师、服务端架构师 | 定向覆盖 missed-fire 不补偿、单次 materialize 和 nextFireAt 推进 | 历史窗口不自动 backfill；重复 tick 不重复创建 run | `scripts/wp9_cron_capacity_smoke.sh` |
| WP9-7.9 CRON backlog smoke | P1 | 质量工程师、服务端架构师 | 定向覆盖 due trigger 积压超过 tick batch 时的限批扫描和接续处理 | 单 tick 不超过 batch 上限；未扫描 trigger 不生成事件或 run | `scripts/wp9_cron_backlog_smoke.sh` |

M7A 已新增 `scripts/wp9_quality_gate.sh` 与 `scripts/wp9_scheduler_smoke.sh`。开发模式默认执行 WP9 脚本语法、后端定向/OpenAPI/权限测试、前端 WP9 Vitest、Playwright smoke、前端构建和 DB validation；release 模式通过 `WP9_GATE_MODE=release WP9_SCHEDULER_SMOKE=managed` 要求 managed scheduler smoke。M7B 已补生产 CRON scanner 最小闭环，scheduler tick 会在 recovery/claim 前扫描到期 CRON trigger，使用 trigger event 和 run requestKey 幂等创建 CRON run，并推进 `nextFireAt`。M7C 已补执行摘要导出，`GET /runs/{id}/export` 返回脱敏 run detail、节点状态计数和 redactionPolicy，前端运行详情可触发导出并展示摘要。M7D 已新增 `scripts/wp9_webhook_http_smoke.sh`，managed 模式本地启动临时 Postgres 和 platform-api，external 模式面向已运行服务，覆盖 webhook 全局启用、项目上下文、WP6 approved bundle、WP9 READY plan、签名拒绝、签名接受、重复 `sourceEventId` 幂等、trigger event 证据和 run export 脱敏；release gate 现在要求同时显式启用 `WP9_SCHEDULER_SMOKE=managed` 与 `WP9_WEBHOOK_HTTP_SMOKE=managed`。M8A 已新增 `scripts/wp9_webhook_sign.sh` 和 `WP9-Webhook签名样例与CI接入说明.md`，覆盖 GitHub Actions、GitLab CI、Jenkins Pipeline 的签名调用样例。M8B 已新增 `WP9-Scheduler-Trigger-Runbook.md`，覆盖 scheduler/webhook/cron 开关、恢复重放、webhook secret 轮换、CRON 运维、排障、回滚和准出记录；M8C 已新增 `integrations/wp9-webhook-marketplace/` 和 `scripts/wp9_marketplace_package_smoke.sh`，覆盖供应商 marketplace manifest、安装说明、模板、payload 示例和离线验收；M8D 已新增 `integrations/wp9-worker-hosting/` 和 `scripts/wp9_worker_hosting_readiness.sh`，覆盖 web、scheduler-active、scheduler-standby 三类托管角色的配置准入；M8E 已新增 `scripts/wp9_report_handoff_smoke.sh` 并纳入 `scripts/wp9_quality_gate.sh`，覆盖 WP10 report handoff 准出证据。M8F 已新增 `scripts/wp9_cron_capacity_smoke.sh` 并纳入 `scripts/wp9_quality_gate.sh`，覆盖 CRON 容量策略和 missed-fire 不补偿。M8G 已新增 `scripts/wp9_cron_backlog_smoke.sh` 并纳入 `scripts/wp9_quality_gate.sh`，覆盖 CRON backlog 超过 tick batch 时的限批扫描和后续 tick 接续处理。M8H 已新增 `WP9-执行编排与任务调度-前端操作说明.md`，覆盖用户在 `#execution` 不依赖 curl 完成计划、DAG、运行、取消、重试、导出和触发解释。M8I 已新增 `WP9-执行编排与任务调度-发布准出说明.md` 和 `WP9-执行编排与任务调度-剩余工作盘点.md`，补齐 WP9-8.3 总准出和当前范围剩余项审计。

## 12. Epic 8：文档和交付

| Story | 优先级 | 负责人 | 任务 | 验收 | 验证 |
|---|---|---|---|---|---|
| WP9-8.1 API 契约更新 | P0 | 服务端架构师 | 技术设计随实现更新真实路径、字段、错误码 | OpenAPI test 一致 | 文档评审 |
| WP9-8.2 Scheduler Runbook | P0 | 质量工程师、服务端架构师 | 编写开关、恢复、重放、webhook secret、cron 排障 | 运维可按步骤处理 | `WP9-Scheduler-Trigger-Runbook.md` |
| WP9-8.3 发布准出说明 | P0 | 项目经理、质量工程师 | 已记录验证命令、跳过项、风险、回滚、远端分支和五角色准出 | 符合仓库模板 | `WP9-执行编排与任务调度-发布准出说明.md` |
| WP9-8.4 前端操作说明 | P1 | 产品经理、前端工程师 | 已说明计划、DAG、运行、取消、重试、导出和触发解释 | 用户无需 curl | `WP9-执行编排与任务调度-前端操作说明.md` |
| WP9-8.5 供应商 webhook 接入样例 | P1 | 质量工程师、服务端架构师 | GitHub/GitLab/Jenkins 签名样例、eventId 策略、排错和验收 | 外部 CI 可按样例联调 | `scripts/wp9_webhook_sign.sh` |
| WP9-8.6 供应商 marketplace 接入包 | P1 | 产品经理、质量工程师、服务端架构师 | manifest、安装变量、GitHub/GitLab/Jenkins 模板、payload 示例和离线验收脚本 | 企业模板库或 marketplace 上架前可审查、可复制、可离线验证 | `scripts/wp9_marketplace_package_smoke.sh` |
| WP9-8.7 Worker 托管 readiness | P1 | 项目经理、质量工程师、服务端架构师 | web/scheduler-active/scheduler-standby env 示例、开关矩阵、故障切换和离线 readiness | 发布前可检查 scheduler 角色、workerId、heartbeat 和 release smoke 证据 | `scripts/wp9_worker_hosting_readiness.sh` |
| WP9-8.8 WP10 交接准出说明 | P1 | 项目经理、产品经理、质量工程师 | 记录 report handoff 目标、非目标、验收命令、风险和剩余 WP10 边界 | WP9 可证明已提供 handoff 摘要和脱敏导出，不抢跑 WP10 报告生成 | `WP9-执行编排与任务调度-M8E-WP10交接准出交付说明.md` |
| WP9-8.9 CRON 容量策略说明 | P1 | 项目经理、服务端架构师、质量工程师 | 记录 missed-fire 不补偿策略、定向 smoke、风险和后续 backfill 边界 | 当前 CRON 只保证单次 materialize，不自动回补历史窗口 | `WP9-执行编排与任务调度-M8F-CRON容量策略交付说明.md` |
| WP9-8.10 CRON backlog 批次准出说明 | P1 | 项目经理、服务端架构师、质量工程师 | 记录 backlog batch 上限、定向 smoke、风险和真实压测边界 | 当前 CRON backlog 按 tick batch 分批处理，未扫描 due trigger 留到后续 tick | `WP9-执行编排与任务调度-M8G-CRON积压批次准出交付说明.md` |

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

## 15. 当前范围完成结论

WP9 当前承诺范围已经完成：M1-M8J 覆盖控制面、状态机、调度、触发、scheduler leader lock、前端、quality gate、运维样例、发布准出和剩余工作盘点。后续只剩 WP7/WP8/WP10、真实供应商 OAuth/App、真正独立 worker 二进制、锁指标/故障切换演练和 CRON 生产容量等专项，不构成本轮 WP9 发布阻断。
