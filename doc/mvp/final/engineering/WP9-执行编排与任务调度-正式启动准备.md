# WP9 执行编排与任务调度 - 正式启动准备

| 项目 | 内容 |
|---|---|
| 工作包 | WP9 执行编排与任务调度 |
| 角色产出 | 资深项目经理 |
| 文档性质 | 正式启动前范围冻结、里程碑、风险和准入清单 |
| 当前口径 | 当前范围已完成：`platform-api` 已承载 WP9 控制面；执行侧已通过 WP6/WP7 应用服务接入，WP8 账号租借已由运行时自动申请/释放，WP10 已消费 `REPORT_HANDOFF` 与脱敏 run export；后续专项聚焦外部 worker、供应商 OAuth/App 和 CRON 容量治理 |
| 版本 | v0.1 |
| 日期 | 2026-06-13 |

## 1. 启动结论

WP9 可以进入正式研发准备完成状态。当前启动包已冻结 P0/P1 口径、非目标、跨 WP 依赖、接口契约、前端入口、测试策略、风险和回滚方式。

WP9 的核心目标是把 WP6 已具备的手动单次接口自动化运行能力提升为可编排、可恢复、可审计的执行计划控制面，为后续 WP7 UI/E2E runner 和 WP10 报告诊断提供统一执行输入。首期不建设独立调度服务或分布式 worker 集群，先在 `platform-api` 内完成可演进控制面。

## 2. 目标

1. 支持测试计划、执行计划、执行任务和 DAG 节点的创建、查询、审批前预览和手动触发。
2. 支持按项目、环境、执行器类型和优先级进行队列化执行、状态收敛、取消、超时和重试。
3. 复用 WP6 已审批脚本包和 runner 结果，预留 WP7 UI/E2E runner 接入契约。
4. 提供基础 CI/CD webhook 触发和定时触发元数据，但首期外部触发默认受签名、幂等和开关约束。
5. 为 WP10 提供聚合执行摘要、节点结果索引、traceId、审计事件和脱敏证据，不直接生成完整报告。

## 3. 范围

| 范围项 | 说明 |
|---|---|
| 执行计划 | 项目内创建执行计划，包含名称、环境、触发方式、执行范围、节点 DAG、超时、重试、并发和门禁策略。 |
| DAG 编排 | 支持 `SETUP/API_TEST/UI_TEST/VERIFY/CLEANUP/REPORT_HANDOFF` 节点类型，P0 先实现 API_TEST 和控制面占位节点。 |
| 手动触发 | 用户可手动触发一次执行，形成 run、node run 和 runner dispatch 记录。 |
| 队列与状态 | 支持 `DRAFT/READY/QUEUED/RUNNING/SUCCEEDED/PARTIAL_SUCCESS/FAILED/CANCELED/TIMEOUT/ARCHIVED` 状态和节点级状态。 |
| 取消与重试 | 支持计划级、任务级和节点级取消；失败节点可按策略重试，重试必须可审计且幂等。 |
| WP6 集成 | 通过 WP6 应用服务创建 API automation run，不直接调用 runner adapter，不绕过 WP6 allowlist/secretRef/redaction。 |
| CI/CD 触发 | P1 提供签名 webhook、幂等 key、来源记录、dryRun 校验和供应商 CI 签名样例；不绑定 marketplace/App 插件。 |
| 定时触发 | P1 提供 cron 元数据、启停、下一次触发时间和审计；首期可只实现控制面，不启生产级分布式调度。 |
| 前端控制台 | 新增执行编排入口，覆盖计划列表、DAG 预览、手动触发、运行详情、取消、重试和 webhook/cron 配置摘要。 |

## 4. 非目标

| 非目标 | 说明 | 后续承接 |
|---|---|---|
| WP6 脚本生成和 runner 安全策略 | WP9 不重新生成接口自动化脚本，不绕过 WP6 runner allowlist、secretRef 和脱敏策略。 | WP6 |
| UI/E2E 脚本生成和浏览器执行器实现 | WP9 只预留 UI_TEST 节点和 runner port，不实现 Playwright 生成和浏览器池。 | WP7 |
| 测试数据构造和账号租借 | WP9 只引用 dataRef/accountLeaseRef，不创建账号池和测试数据。 | WP8 |
| Allure 风格报告和 AI 失败诊断 | WP9 只提供执行摘要、节点结果索引和 report handoff 事件。 | WP10 |
| 独立调度服务、Kubernetes worker 池和跨地域调度 | 首期使用 `platform-api` 控制面和可替换端口，后续可拆 execution-service。 | 后续平台化 |
| 生产压测或破坏性执行 | 不提供无限并发、攻击型流量或生产破坏性写入能力。 | 专项安全/性能工作 |

## 5. 涉及模块

| 模块 | 影响 |
|---|---|
| `platform-api` | 新增 `execution` 领域模块，承载计划、DAG、运行、节点、队列、触发和审计控制面。 |
| WP1 平台基础 | 复用项目、环境、RBAC、SecretProvider、审计、traceId、配置和资源 scope。 |
| WP3 测试资产 | 读取测试用例、API、页面、业务流和追踪关系摘要；不直接写 WP3 表。 |
| WP6 OpenAPI 接口自动化 | 复用已审批 script bundle、run API、result/export 摘要和 runner 安全策略。 |
| WP7 UI/E2E | 预留 UI runner 节点契约和执行结果 schema，不要求 WP7 同步完成。 |
| WP10 报告诊断 | 输出执行摘要、节点结果索引、失败分类种子和 report handoff 事件。 |
| `portal-web` | 新增执行编排工作台，接入权限、计划、运行、节点状态、取消重试和触发配置。 |
| `db/migration/wp1` | 后续新增 WP9 计划、DAG、运行、节点、触发、队列和审计索引表。 |
| `scripts` | 后续新增 WP9 quality gate、调度 smoke、webhook smoke、webhook 签名 helper 和 DB validation 扩展。 |

## 6. 五角色启动交付

| 角色 | 本轮交付 | 结论 |
|---|---|---|
| 资深项目经理 | 本启动准备和研发任务拆解，冻结目标、范围、依赖、里程碑、风险和回滚 | 通过 |
| 资深产品经理 | WP9 需求文档与 PRD，定义用户价值、业务流程和验收标准 | 通过 |
| 资深服务端架构师 | 技术设计与接口契约，定义 DB、状态机、API、runner port 和跨 WP 集成 | 通过 |
| 资深前端工程师 | 前端页面设计，定义入口、页面、状态、权限和可测性 | 通过 |
| 资深质量工程师 | 测试策略与用例脚本，定义测试矩阵、smoke、DB validation 和准出 | 通过 |

## 7. 里程碑

| 里程碑 | 目标 | 主要交付物 | 准出标准 |
|---|---|---|---|
| M0 启动准备 | 文档、范围、任务拆解冻结 | 6 份 WP9 启动文档 | 五角色评审无阻断 |
| M1 基础控制面 | 权限、DB、模块骨架、health | execution 模块、权限 seed、schema | OpenAPI contract、DB validation 通过 |
| M2 计划与 DAG | 创建计划、DAG 校验、dryRun | plan API、DAG validator | DAG 非法边、循环、越权均可测 |
| M3 手动执行 | 手动触发、队列、状态流 | run API、node run、队列认领 | cancel/retry/timeout 幂等 |
| M4 WP6 接入 | API_TEST 节点调用 WP6 run | WP6 adapter、结果聚合 | 不绕过 WP6 安全策略 |
| M5 触发控制面 | webhook/cron 元数据和签名校验 | trigger API、事件记录 | 幂等、签名、禁用态可测 |
| M6 前端闭环 | 工作台完成计划、运行、节点和触发配置 | portal-web 页面 | Vitest、Playwright smoke 通过 |
| M7 准出门禁 | quality gate、DB validation、smoke | `scripts/wp9_quality_gate.sh` | release gate 明确 |

## 8. 启动准入清单

| 检查项 | 要求 | 状态 |
|---|---|---|
| 需求范围 | 只做执行编排与任务调度控制面，不做 WP10 报告和 WP8 账号池 | 通过 |
| 输入资产 | WP3 资产、WP6 script bundle/run、WP1 项目环境可复用 | 通过 |
| 执行边界 | 不直连 runner adapter，不绕过 WP6/WP7 自身安全策略 | 通过 |
| 权限审计 | 新增 execution 权限点和审计事件必须入 DB seed、权限测试和文档 | 已纳入 WP9-1.1、WP9-1.2、WP9-7.3 |
| 数据安全 | 不保存 secret 明文、环境变量值、完整 stdout/stderr、请求响应正文 | 通过 |
| 验证入口 | 后续必须提供后端、前端、DB validation、调度 smoke 和 quality gate | 已纳入 WP9-7.x |

## 9. 风险和回滚

| 风险 | 处置 | 回滚方式 |
|---|---|---|
| 调度状态卡死 | 使用条件认领、heartbeat、超时回收和人工重放入口 | 关闭 scheduler enabled，保留只读查询和手动修复 |
| 重复触发污染执行结果 | 所有触发使用 requestKey/sourceEventId 幂等 | 禁用对应 trigger，保留首个 run 审计 |
| 绕过 WP6 安全边界 | WP9 只调用 WP6 应用服务，不直接调用 runner adapter | 回滚 WP9 dispatcher，保留 WP6 单次运行能力 |
| Cron 或 webhook 被误触发 | 默认禁用外部触发；启用需签名、allowlist 和审计 | 关闭 trigger enabled，撤销 webhook token |
| 队列并发影响环境稳定 | 按项目、环境、runner 类型和优先级限流 | 降低并发或暂停资源池 |
| 与 WP10 范围膨胀 | WP9 只输出摘要和 handoff，不生成完整报告 | 隐藏报告入口，保留运行摘要 |

## 10. 回滚方式

1. 文档阶段回滚本组 WP9 文档和 README 索引即可，无运行时影响。
2. 后续代码阶段优先通过配置关闭 `execution.scheduler-enabled`、`execution.webhook-enabled` 和 `execution.cron-enabled`。
3. 数据库迁移遵循前滚修复优先，生产环境不做破坏性 drop。
4. 已产生的 execution run、node run 和 trigger event 保留审计摘要，禁止直接删除审计证据。
5. 若 WP6 接入异常，回退到 WP6 手动 run，不影响 WP6 已验收能力。

## 11. 验收标准

1. 五角色文档均完成且口径一致。
2. PRD、技术契约、前端设计、测试策略和任务拆解互相引用的范围一致。
3. 明确 P0/P1 边界、非目标、跨 WP 依赖、权限、审计、安全、验证入口和回滚方式。
4. 本轮不引入业务代码、数据库迁移或运行时配置变更。
5. 文档变更通过格式检查并提交推送。
