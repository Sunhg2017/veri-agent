# WP9 执行编排与任务调度 - 需求文档与 PRD

| 项目 | 内容 |
|---|---|
| 工作包 | WP9 执行编排与任务调度 |
| 角色产出 | 资深产品经理 |
| 文档性质 | 需求文档、产品 PRD 和产品验收标准 |
| 当前口径 | 先完成项目内执行计划、手动触发、基础调度控制面和 WP6 runner 编排；CI/CD 与 cron 为 P1 控制面 |
| 版本 | v0.1 |
| 日期 | 2026-06-13 |

## 1. 背景

WP6 已经完成 OpenAPI 到接口自动化脚本包、受控单次运行和脱敏结果摘要的闭环，但仍缺少统一的执行计划、队列、重试、取消、定时触发、CI/CD 触发和多节点编排能力。WP9 负责把单次 run 提升为企业可运营的执行编排控制面。

## 2. 用户与价值

| 用户 | 诉求 | WP9 价值 |
|---|---|---|
| 测试工程师 | 将接口自动化、后续 UI 自动化和清理校验串成稳定流程 | 通过计划和 DAG 复用执行链路 |
| 测试负责人 | 看到项目下发布门禁、冒烟、回归、巡检任务状态 | 提供统一计划、运行历史、失败摘要和趋势输入 |
| DevOps/CI 管理员 | 从流水线触发平台执行并读取结果 | 提供签名 webhook、幂等和状态查询 |
| 审计/安全人员 | 确认谁触发了什么环境、用到哪些 secretRef、是否越权 | 保留项目 scope、traceId、审计和脱敏摘要 |
| 平台运维 | 控制并发、恢复卡死任务、处理重复触发 | 提供队列、heartbeat、超时回收和人工重放入口 |

## 3. 产品目标

1. 让用户能在浏览器内创建执行计划、查看 DAG、手动触发、取消、重试和查看节点结果。
2. 让 WP6 API automation run 能作为 DAG 节点被统一调度，后续 WP7 runner 以同一节点契约接入。
3. 让执行状态具备可恢复性，避免平台重启、runner timeout 或重复触发导致状态不收敛。
4. 让 CI/CD 和 cron 触发具备安全默认值、幂等和可审计来源。
5. 为 WP10 提供稳定 report handoff 数据，不在 WP9 内扩张完整报告诊断。

## 4. P0/P1 范围

| 功能 | P0 口径 | P1 口径 |
|---|---|---|
| 执行计划 | 创建、查询、编辑草稿、归档、DAG dryRun | 复制计划、模板化计划 |
| DAG 节点 | API_TEST 节点接 WP6；SETUP/VERIFY/CLEANUP/REPORT_HANDOFF 控制面占位 | UI_TEST 接 WP7；复杂跳过条件 |
| 手动触发 | 用户手动触发 run，支持 requestKey 幂等 | 批量手动触发 |
| 队列状态 | QUEUED/RUNNING/SUCCEEDED/FAILED/CANCELED/TIMEOUT/PARTIAL_SUCCESS | 优先级抢占和资源池权重 |
| 取消重试 | 计划级取消、失败节点重试、终态幂等 | 按节点重跑下游 |
| WP6 集成 | 通过 WP6 service 创建 run 并聚合摘要 | 多 script bundle 并行 fan-out |
| CI/CD webhook | 签名、幂等、禁用态、dryRun、GitHub/GitLab/Jenkins 签名样例 | marketplace/App 插件包 |
| Cron | 保存 cron 元数据、启停和下一次触发时间 | 真正定时扫描触发和错过补偿 |
| 前端 | 执行计划工作台、运行详情、DAG 状态、取消重试 | 计划模板和趋势图 |

## 5. 非目标

| 非目标 | 说明 |
|---|---|
| 不生成新脚本 | 脚本生成仍由 WP6/WP7 承接。 |
| 不直连 runner | WP9 不直接执行 Pytest/Playwright，不绕过 runner 安全策略。 |
| 不建设账号池和测试数据服务 | 只引用 dataRef/accountLeaseRef。 |
| 不生成完整报告和 AI 诊断 | WP9 只输出摘要和 handoff，WP10 生成报告。 |
| 不做跨地域调度 | 首期按单 `platform-api` 控制面设计，可演进。 |

## 6. 核心用户流程

### 6.1 创建计划

1. 用户进入 `执行编排`。
2. 选择项目、环境、计划类型、执行范围和触发方式。
3. 添加 DAG 节点：API_TEST 选择 WP6 approved script bundle；其他节点选择占位类型和依赖。
4. 点击 dryRun，系统校验权限、环境、循环依赖、节点输入和 runner 策略。
5. 保存为 `READY` 或 `DRAFT`。

### 6.2 手动触发

1. 用户在 READY 计划上点击运行。
2. 系统生成 execution run 和 node run。
3. API_TEST 节点创建 WP6 run，节点状态随 WP6 run 结果收敛。
4. DAG 按依赖推进；失败策略决定终止、跳过下游或继续。
5. 用户在详情页查看节点状态、耗时、错误码、traceId 和脱敏摘要。

### 6.3 CI/CD 触发

1. 外部流水线带签名和 sourceEventId 调用 webhook。
2. 系统校验全局 webhook 开关、触发器启用状态、HMAC 签名时间窗、计划 READY 状态和 sourceEventId 幂等键。
3. dryRun 只校验配置和策略，不创建 run；真实 webhook 成功时创建或回放 run。
4. 流水线轮询 run 状态或接收后续通知扩展。

## 7. 业务规则

1. 只有 `READY` 计划可触发；`DRAFT/ARCHIVED/DISABLED` 不可触发。
2. 同一 `planId + triggerType + sourceEventId/requestKey` 只能创建一个有效 run。
3. DAG 禁止循环依赖，禁止节点依赖不存在节点，禁止跨项目引用资源。
4. WP9 不保存 secret 明文，只保存 secretRef digest、count 和使用策略摘要。
5. API_TEST 节点必须引用同项目 approved WP6 script bundle。
6. 取消 RUNNING 节点必须调用对应 runner service 的 cancel；终态取消幂等返回当前状态。
7. 重试必须生成 retryAttempt，保留原失败节点结果，不覆盖审计证据。
8. Cron/webhook 默认关闭；启用必须有权限、审计和配置开关。
9. Webhook secret 只保存 `secretRef` 引用和 digest，不保存 secret 明文、签名值或 payload 原文；事件表只保存 requestDigest、错误码和 runId。

## 8. 权限矩阵

| 权限点 | 用途 | 默认角色建议 |
|---|---|---|
| `execution:read` | 查看计划、运行、节点和触发记录 | SuperAdmin、PlatformAdmin、ProjectOwner、AppOwner、Tester、Auditor |
| `execution:manage` | 创建、编辑、归档计划和触发配置 | SuperAdmin、PlatformAdmin、ProjectOwner、AppOwner |
| `execution:trigger` | 手动触发、取消、重试执行 | SuperAdmin、PlatformAdmin、ProjectOwner、AppOwner、Tester |
| `execution:admin` | 启停调度、人工重放、恢复卡死任务 | SuperAdmin、PlatformAdmin |
| `execution:export` | 导出执行摘要和审计聚合 | SuperAdmin、PlatformAdmin、ProjectOwner、Auditor |

前端隐藏菜单和按钮只用于体验优化，后端权限和项目 scope 是准入来源。

## 9. 状态定义

| 对象 | 状态 |
|---|---|
| Plan | `DRAFT/READY/DISABLED/ARCHIVED` |
| Run | `QUEUED/RUNNING/SUCCEEDED/PARTIAL_SUCCESS/FAILED/CANCELED/TIMEOUT` |
| NodeRun | `PENDING/QUEUED/RUNNING/SUCCEEDED/SKIPPED/FAILED/CANCELED/TIMEOUT/BLOCKED` |
| Trigger | `DISABLED/ENABLED/PAUSED` |

## 10. 产品验收标准

1. 用户可不依赖 curl 完成计划创建、DAG 预览、手动触发、运行详情、取消和重试。
2. API_TEST 节点可调度 WP6 已审批脚本包，结果摘要不泄露 baseUrl 明文、secretRef 明文或请求响应正文。
3. 重复 webhook 或重复手动 requestKey 不产生重复运行。
4. 计划、运行、节点和触发动作均按项目 scope 鉴权并写审计。
5. 卡死运行可通过超时回收或人工重放收敛。
6. CI/CD 和 cron 在未启用时不可触发；启用后有签名、幂等和审计。

## 11. 产品风险

| 风险 | 产品处理 |
|---|---|
| 用户误以为 WP9 会生成脚本 | 页面文案限定为“执行编排”，脚本来源显示 WP6/WP7。 |
| 用户误以为结果是完整报告 | 运行详情显示摘要，完整报告入口标注 WP10。 |
| 触发配置误操作 | 触发启停需要 `execution:manage/admin`，危险操作二次确认。 |
| 多节点状态难理解 | DAG 使用状态色、计数和失败策略摘要。 |
