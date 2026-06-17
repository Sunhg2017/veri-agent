# WP10 报告与失败诊断 - 需求文档与 PRD

| 项目 | 内容 |
|---|---|
| 工作包 | WP10 报告与失败诊断 |
| 角色产出 | 资深产品经理 |
| 文档性质 | 需求文档、产品 PRD 和产品验收标准 |
| 当前口径 | 当前范围已推进至 M10：基于 WP9 脱敏 run export、`REPORT_HANDOFF` 摘要、WP8/WP3/WP5 aggregate-only evidence 和 WP2 模型能力，已交付可审计的报告、可选异步生成 worker、同项目历史快照对比、失败诊断、缺陷草稿、JSON/Markdown 导出、报告完成 webhook 回调和 `#reports` 控制面；外部缺陷写入、完整报告和趋势/BI 保持后续专项 |
| 版本 | v0.2 |
| 日期 | 2026-06-17 |

## 1. 背景

WP9 已经提供执行计划、运行状态、节点摘要、`REPORT_HANDOFF` 节点和脱敏 run export，但测试负责人仍缺少面向发布准出和问题分流的统一报告视图。当前用户需要在运行详情、执行摘要、数据准备证据、资产追踪关系和模型调用记录之间手工拼接失败原因，容易遗漏证据、误传敏感信息或把临时判断写成确定结论。

WP10 负责把这些已脱敏输入组织成版本化报告快照，并提供规则失败分类、AI 诊断建议、缺陷草稿和脱敏导出。当前已完成 WP9/WP8/WP3/WP5 聚合证据主链路、`#reports` 工作台、质量门禁和本地准出执行记录；WP10 不重新执行测试、不读取 runner 原始产物、不自动写入外部缺陷系统。

## 2. 用户与价值

| 用户 | 诉求 | WP10 价值 |
|---|---|---|
| 测试工程师 | 快速定位失败节点、证据摘要和下一步排查动作 | 聚合 run、节点、数据、资产和诊断信息，减少手工查找 |
| 测试负责人 | 评估发布风险、确认失败类型和阻断范围 | 提供报告状态、失败分类、缺陷草稿和导出摘要 |
| 研发负责人 | 获取可复现、可分派、带证据引用的问题说明 | 缺陷草稿包含标题、复现摘要、影响范围和证据引用 |
| 审计/安全人员 | 确认报告和导出没有泄露 secret、token、payload 或原始日志 | 固化 redaction policy、manifest 和审计事件 |
| 平台运维 | 处理报告生成失败、诊断降级和导出阻断 | 提供状态机、traceId、错误码和回滚开关 |

## 3. 产品目标

1. 用户可基于 WP9 execution run 生成一个版本化报告快照，并看到生成状态和幂等回放结果。
2. 报告详情展示 run summary、节点状态计数、失败分类、证据 manifest、redaction policy 和 traceId。
3. 失败诊断在无模型时可给出规则分类；模型可用时通过 WP2 生成脱敏 AI 建议，并明确置信度和人工确认要求。
4. 用户可从报告生成缺陷草稿，草稿只保存平台内内容，不自动写外部缺陷系统。
5. 用户可导出脱敏 JSON/Markdown 摘要和 export manifest，不导出 runner 原始 stdout/stderr、请求响应正文、webhook payload、Prompt 原文、secret 或账号凭据。
6. 报告、诊断、导出、草稿均按项目 scope 鉴权并写审计。
7. 报告到达终态后可按全局配置异步回调一个 aggregate-only webhook，不阻断报告成功或失败落库。

## 4. P0/P1 范围

| 功能 | P0 口径 | P1 口径 |
|---|---|---|
| 报告生成 | `POST /api/v1/reports` 基于 `executionRunId` 创建快照，支持 `requestKey` 幂等；默认同步生成，可通过开关进入 `QUEUED` 并由后台 worker 收敛到 `READY/FAILED` | 批量生成优先级、租户级容量配额 |
| 报告查询 | 列表、详情、状态、run summary、节点计数、失败分类、证据 manifest 和同项目历史快照对比 | 趋势报表 |
| 证据聚合 | 当前消费 WP9 run export、REPORT_HANDOFF、WP8 report evidence、WP3 asset summary 和 WP5 manifest；仅保存 digest、状态、计数、summary keys 和 redaction flags | 受控 artifact 索引和审批后明细归档 |
| 失败分类 | 基于 errorCode、status、nodeType、runnerMode、timeout、blocked、lease release 和 trigger 来源分类 | 可配置分类规则和团队标签 |
| AI 诊断 | 通过 WP2 生成脱敏建议，输出候选根因、置信度、依据和下一步动作 | 反馈闭环和诊断模型评测看板 |
| 缺陷草稿 | 生成标题、复现摘要、影响范围、优先级建议和证据引用，支持 DRAFT/REVIEWED/DISMISSED | 外部 Jira/禅道/飞书写入由 WP11 承接 |
| 导出摘要 | JSON/Markdown 脱敏摘要和 export manifest | PDF/Word 完整报告由合规审批专项承接 |
| 前端 | `#reports` 工作台、列表、详情、诊断、草稿、导出和状态反馈 | 报告订阅、趋势图、团队视图 |
| webhook 回调 | 默认关闭的全局 outbound callback，在报告 `READY/FAILED` 终态时发送 aggregate-only 完成通知 | 订阅管理、回放补偿和租户级路由 |

## 5. 非目标

| 非目标 | 说明 |
|---|---|
| 不触发执行或调度 | WP10 只消费 WP9 输出，不创建、取消、重试 execution run。 |
| 不读取 runner 原始产物 | 不读取 stdout/stderr、截图、视频、请求响应正文、源码包或 webhook payload。 |
| 不绕过跨 WP 契约 | WP10 不直连 WP8/WP9/WP3/WP5 表，必须通过应用服务、导出接口或明确 port。 |
| 不生成确定性 AI 结论 | AI 输出只能作为建议，必须展示置信度、依据摘要和人工确认标记。 |
| 不自动写外部缺陷系统 | P0 只保存缺陷草稿和 payload preview，外部同步由 WP11 处理。 |
| 不建设 BI 数仓 | 不承诺长期趋势、组织 KPI、容量报表和生产压测。 |

## 6. 核心用户流程

### 6.1 生成报告

1. 用户进入 `报告诊断` 工作台。
2. 选择项目和 WP9 execution run，或从 WP9 运行详情点击生成报告。
3. 前端提交 `executionRunId`、`requestKey` 和生成原因。
4. 服务端校验 `report:generate`、项目 scope、WP9 run export 可读性和 `REPORT_HANDOFF` 摘要。
5. 系统创建或回放报告快照；默认同步返回 `READY/FAILED`，异步开关开启时先返回 `QUEUED`，后台 worker 认领为 `GENERATING` 并收敛到 `READY` 或 `FAILED`。
6. 用户进入报告详情查看摘要、证据、失败分类和 redaction policy。

### 6.2 查看失败诊断

1. 用户在 READY 报告详情点击诊断。
2. 系统先展示规则分类结果。
3. 若启用 AI 诊断且用户有 `report:diagnose`，服务端通过 WP2 发送脱敏 bounded 上下文。
4. 页面展示候选根因、置信度、依据引用、下一步动作和 `manualReviewRequired`。
5. WP2 预算、provider 错误或策略阻断时，页面保留规则分类和降级原因。

### 6.3 生成缺陷草稿

1. 用户在诊断详情点击生成草稿。
2. 系统基于报告摘要、失败分类和诊断建议生成缺陷标题、复现摘要、影响范围、优先级建议和证据引用。
3. 草稿状态为 `DRAFT`，用户可标记 `REVIEWED` 或 `DISMISSED`。
4. P0 不发送外部系统；payload preview 只用于人工复制或后续 WP11 对接。

### 6.4 导出摘要

1. 用户点击导出 JSON 或 Markdown。
2. 服务端校验 `report:export` 和报告状态。
3. 系统返回脱敏摘要、export manifest、digest、schemaVersion、fieldSetVersion 和 redactionPolicy。
4. 前端展示导出结果和敏感字段拦截状态，不下载包含敏感明细的完整文件。

### 6.5 报告对比

1. 用户在报告详情选择同项目历史报告作为 baseline。
2. 系统对比当前报告与 baseline 的 aggregate-only metadata、summary、最新诊断、evidence manifest 指纹和缺陷草稿状态分布。
3. 页面仅展示 changed fields、计数变化和 digest/状态层面的差异，不展示原始 evidence、Prompt、模型正文、secret、token 或跨 WP 内部 ID 清单。
4. 基线报告与当前报告相同、跨项目或无 baseline 时，页面给出明确阻断提示。

## 7. 业务规则

1. 只有同项目 scope 下可读的 WP9 run 才能生成报告。
2. 同一 `executionRunId + requestKey` 必须幂等，重复请求返回既有报告。
3. 报告快照一旦 `READY`，详情展示使用快照数据，不因源 run 后续变化静默改变。
4. `FAILED` 报告可重试生成，但必须保留失败 traceId、错误码和审计。
5. 异步生成 worker 只能处理 WP10 自身 `report_execution_report` 队列，不触发、取消、重试或认领 WP9 execution run。
6. `ARCHIVED` 报告只读，不允许新增诊断、草稿或导出。
7. 诊断必须先有规则分类；AI 失败时不阻断报告详情查看。
8. AI 诊断上下文不得包含 raw prompt、raw response、secret、token、cookie、Authorization、lease token、stdout/stderr、请求响应正文、webhook payload 或账号凭据。
9. 缺陷草稿只能引用 evidenceRef、digest、状态、计数和摘要 key，不复制原始 evidence 正文；WP3/WP5 证据同样不得包含资产正文、候选正文、Prompt 原文或模型载荷。
10. 导出 manifest 必须声明 `aggregateOnly=true`、字段集版本、生成时间、digest 和禁止字段清单。
11. 前端按钮显隐只做体验优化，最终准入以后端权限、项目 scope、报告状态和配置开关为准。
12. 报告对比只允许同项目既有快照之间进行，且不重新读取 WP9/WP8/WP3/WP5 原始来源。
13. webhook 回调只发送 aggregate-only report 元数据、summary 和最新诊断摘要；失败仅写告警与审计，不回滚报告状态。

## 8. 权限矩阵

| 权限点 | 用途 | 默认角色建议 |
|---|---|---|
| `report:read` | 查看报告列表、详情、诊断摘要和草稿摘要 | SuperAdmin、PlatformAdmin、ProjectOwner、AppOwner、Tester、Developer、Auditor |
| `report:generate` | 基于 execution run 创建或重试报告 | SuperAdmin、PlatformAdmin、ProjectOwner、AppOwner、Tester |
| `report:diagnose` | 触发 AI 失败诊断和查看诊断详情 | SuperAdmin、PlatformAdmin、ProjectOwner、AppOwner、Tester |
| `report:export` | 导出脱敏 JSON/Markdown 摘要和 manifest | SuperAdmin、PlatformAdmin、ProjectOwner、Auditor |
| `report:manage` | 归档报告、审阅或 dismiss 缺陷草稿、管理开关 | SuperAdmin、PlatformAdmin、ProjectOwner |

## 9. 状态定义

| 对象 | 状态 |
|---|---|
| Report | `QUEUED/GENERATING/READY/FAILED/ARCHIVED` |
| Diagnosis | `NOT_REQUESTED/RULE_READY/AI_RUNNING/AI_READY/AI_FAILED` |
| DefectDraft | `DRAFT/REVIEWED/DISMISSED/EXPORTED` |
| ExportManifest | `CREATED/BLOCKED` |

## 10. 产品验收标准

1. 用户可不依赖 curl 从 WP9 run 生成报告、查看详情、触发诊断、生成草稿和导出摘要。
2. 报告详情可解释 run 结果、节点状态计数、失败分类、证据来源和脱敏策略。
3. 无模型或模型失败时，规则分类仍可用，页面明确显示降级原因。
4. AI 诊断必须显示置信度、依据引用、下一步动作和人工确认提示。
5. 缺陷草稿不写外部系统，外部 payload preview 默认 masked。
6. 报告详情、导出摘要和前端 DOM 不包含禁止字段。
7. 权限、项目 scope、审计、traceId、schemaVersion、redactionPolicy 覆盖所有主链路。

## 11. 产品风险

| 风险 | 产品处理 |
|---|---|
| 用户把 AI 建议当成确定根因 | 页面固定展示置信度、依据引用和人工确认提示。 |
| 报告被误认为完整原始证据包 | 文案限定为脱敏摘要和 evidence manifest，不展示原始产物入口。 |
| 失败分类过粗 | P0 提供规则分类和人工复核，P1 再引入可配置规则。 |
| 缺陷草稿误发外部系统 | P0 不提供发送动作，payload preview 标注 masked。 |
| 导出泄露敏感值 | 导出前安全扫描命中禁止字段则 `BLOCKED`，并记录审计。 |
