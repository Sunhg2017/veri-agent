# WP5 AI 用例生成与评审 - 前端页面设计

| 项目 | 内容 |
|---|---|
| 工作包 | WP5 AI 用例生成与评审 |
| 角色产出 | 资深前端工程师 |
| 文档性质 | 前端页面、路由、权限、状态和可测性设计 |
| 当前口径 | 基于 `portal-web` React + TypeScript + Vite 管理台扩展，已纳入任务质量、Prompt 趋势、Prompt 版本准出分布、任务诊断、本域审计链摘要面板、跨 WP 审计链策略摘要、显式上下文资产输入、服务端下发的上下文裁剪口径、上下文策略诊断摘要、权限/资源作用域策略摘要、评测语料运营策略摘要、发布准出审批策略摘要、归档策略摘要和报告清单策略摘要 |
| 版本 | v0.12 |
| 日期 | 2026-05-30 |

## 1. 页面目标

WP5 前端页面负责让用户在浏览器内完成用例生成主流程：

1. 选择项目和需求。
2. 配置生成策略。
3. 创建并观察生成任务。
4. 评审、编辑和批量处理候选。
5. 执行发布 dryRun。
6. 将确认候选发布到 WP3 测试用例并跳转查看。

页面应保持管理台工具型风格，重点是清晰、密集、可扫描、可操作，不做营销型或装饰型页面。

## 2. 信息架构

建议新增页面 key：

| 页面 | 路由建议 | 读权限 | 说明 |
|---|---|---|---|
| 用例生成 | `test-design` | `testDesign:read` | WP5 独立工作台入口。 |

如首轮希望减少导航复杂度，也可把入口放在资产库内部 tab：`asset-library/test-design`。但权限仍建议独立为 `testDesign:*`，避免和手工资产维护混淆。

## 3. 页面布局

```text
┌──────────────────────────────────────────────────────────────┐
│ 顶部筛选：项目 / 任务状态 / 创建时间 / 关键词 / 刷新 / 新建任务 │
├───────────────────────┬──────────────────────────────────────┤
│ 左侧任务列表          │ 右侧详情                              │
│ - 任务状态            │ - 任务摘要 / 模型 / 质量提示          │
│ - 候选数/确认数       │ - 候选评审 Tab                        │
│ - traceId             │ - 发布预览 Tab                        │
│ - 最近错误            │ - 发布记录 Tab                        │
└───────────────────────┴──────────────────────────────────────┘
```

响应式规则：

1. 桌面端使用左右分栏，左侧任务列表固定最小宽度，右侧内容自适应。
2. 窄屏下改为上下结构，任务列表和详情通过 tabs 或折叠面板切换。
3. 表格列需设置最小宽度和换行策略，长标题、traceId、错误摘要不能撑破布局。
4. 批量操作栏固定在候选列表上方，不遮挡表格内容。

## 4. 组件拆分

| 组件 | 职责 |
|---|---|
| `TestDesignWorkbench` | 页面容器，维护筛选、选中任务、刷新和权限状态。 |
| `TestDesignTaskList` | 任务列表、状态 badge、错误摘要、traceId、分页。 |
| `TestDesignTaskCreateDialog` | 创建任务表单，选择项目、需求、策略和上下文选项。 |
| `TestDesignTaskSummary` | 任务详情摘要、模型调用信息、质量提示、成本、耗时和本域审计链摘要。 |
| `TestDesignCandidateReviewPanel` | 候选列表、筛选、批量操作和候选详情。 |
| `TestDesignCandidateEditor` | 候选编辑表单，维护标题、步骤、预期、优先级和标签。 |
| `TestDesignPublishPreviewPanel` | dryRun 结果、重复风险、失败明细和发布按钮。 |
| `TestDesignPublishRecordsPanel` | 发布记录、WP3 用例跳转和失败排查。 |

## 5. API Client 设计

建议新增 `portal-web/src/api/testDesign.ts`，类型和函数包括：

| 类型或函数 | 说明 |
|---|---|
| `TestDesignTaskView` | 任务响应类型。 |
| `TestDesignCandidateView` | 候选响应类型。 |
| `TestDesignPublishPreview` | dryRun 和发布结果类型。 |
| `fetchTestDesignTasks(filters)` | 分页查询任务。 |
| `createTestDesignTask(payload)` | 创建生成任务。 |
| `fetchTestDesignTask(id)` | 查询任务详情。 |
| `retryTestDesignTask(id)` | 重试任务。 |
| `cancelTestDesignTask(id)` | 取消任务。 |
| `fetchTestDesignCandidates(filters)` | 查询候选。 |
| `updateTestDesignCandidate(id, payload)` | 编辑候选。 |
| `confirmTestDesignCandidate(id, version)` | 确认候选。 |
| `rejectTestDesignCandidate(id, payload)` | 驳回候选。 |
| `ignoreTestDesignCandidate(id, payload)` | 忽略候选。 |
| `batchActionTestDesignCandidates(payload)` | 批量操作。 |
| `batchResolveTestDesignConflicts(payload)` | 批量处理发布冲突。 |
| `previewTestDesignPublish(taskId, payload)` | 发布 dryRun。 |
| `publishTestDesignCandidates(taskId, payload)` | 正式发布。 |
| `fetchTestDesignTaskQualitySummary(taskId)` | 查询任务全量质量摘要和任务准出状态。 |
| `fetchTestDesignPromptTrend(filters)` | 查询 Prompt 版本趋势、版本级准出摘要和准出状态分布。 |
| `fetchTestDesignTaskAuditSummary(taskId)` | 查询任务本域审计链摘要。 |

API client 需复用现有 `requestJson` 和 `ApiError` 处理，保留响应中的 `trace_id` 或统一 traceId 字段，供页面展示。

## 6. 权限规则

建议在 `portal-web/src/permissions.ts` 增加：

| 类型 | key | 权限 |
|---|---|---|
| PageKey | `test-design` | `testDesign:read` |
| ButtonKey | `testDesign:generate` | `testDesign:generate` |
| ButtonKey | `testDesign:review` | `testDesign:review` |
| ButtonKey | `testDesign:publish` | `testDesign:publish` |
| ButtonKey | `testDesign:export` | `testDesign:export` |

按钮规则：

| 操作 | 权限 |
|---|---|
| 新建任务 | `testDesign:generate` |
| 重试任务 | `testDesign:generate` |
| 取消任务 | `testDesign:generate` |
| 编辑候选 | `testDesign:review` |
| 确认/驳回/忽略候选 | `testDesign:review` |
| 发布 dryRun | `testDesign:publish` |
| 正式发布 | `testDesign:publish` |
| 导出摘要 | `testDesign:export` |

前端权限只控制可见性和交互状态，后端仍是最终鉴权来源。

## 7. 表单校验

### 7.1 创建任务

| 字段 | 校验 |
|---|---|
| 项目 | 必填。 |
| 需求 | 至少选择 1 条，建议最多 100 条。 |
| 生成数量 | 1 到 20 的整数。 |
| 覆盖类型 | 至少 1 个。 |
| 任务名称 | 必填，最长 160 字符。 |
| 上下文选项 | 至少保留需求上下文；API/页面/流程/历史用例可通过追踪关系自动带入，也可在生成配置中显式输入 API/页面/业务流 ID。前端占位文案、任务诊断的上下文规模、上下文策略摘要、作用域策略摘要、评测语料摘要、发布准出摘要、审计链摘要、归档策略摘要和报告清单摘要优先读取健康接口返回的 `contextLimits`、`scopePolicy`、`evaluationCorpusPolicy`、`releaseReadinessPolicy`、`auditChainPolicy`、`archivePolicy`、`reportManifestPolicy` 与任务 `contextSummary.limits/contextSummary.scopePolicy/contextSummary.evaluationCorpusPolicy/contextSummary.releaseReadinessPolicy/contextSummary.auditChainPolicy/contextSummary.archivePolicy/contextSummary.reportManifestPolicy`。 |

### 7.2 候选编辑

| 字段 | 校验 |
|---|---|
| 标题 | 必填，最长 200 字符。 |
| 优先级 | `CRITICAL/HIGH/MEDIUM/LOW`。 |
| 覆盖类型 | 固定枚举。 |
| 步骤 | 至少 1 步。 |
| 步骤 action | 必填。 |
| 步骤 expectedResult | 必填。 |
| 驳回/忽略原因 | 对 reject/ignore 必填。 |

校验错误要定位到字段，不能只展示笼统错误。

## 8. 状态设计

| 状态 | 页面行为 |
|---|---|
| 未登录 | 显示登录态引导，不请求 WP5 列表。 |
| 无读权限 | 不展示入口；直接访问时显示权限不足。 |
| loading | 列表、详情、候选和发布预览分别有局部 loading。 |
| empty | 无任务、无候选、无发布记录分别展示不同空态。 |
| error | 展示错误 message、traceId、重试按钮和下一步建议。 |
| partial success | 候选生成或发布部分成功时展示成功数、失败数和明细。 |
| version conflict | 提示刷新候选后再操作，保留用户正在编辑内容。 |
| model blocked | 展示 WP2 阻断原因摘要，不展示敏感内容。 |
| prompt readiness | Prompt 趋势中展示 `PASSED/WARNING/BLOCKED` 准出状态分布；每个版本展示准出状态、阻断数和风险数；该状态仅用于运营比较，不禁用发布按钮。 |
| evaluation corpus | 任务诊断展示 `evaluationCorpusPolicy` 的 golden set 基线、手动可选 AI 评测、部署配置阈值、项目作用域、质量门禁接入、准出分布/Prompt 版本跟踪和运营后台 pending；该状态只说明当前评测语料边界，不代表真实样本维护或长期校准后台已就绪。 |
| release readiness | 任务诊断展示 `releaseReadinessPolicy` 的 advisory-only、发布阻断关闭、审批流 pending、人工准出、自动发布关闭和候选确认要求；该状态只说明当前准出边界，不代表真实审批流已就绪。 |
| audit chain | 任务诊断展示 `auditChainPolicy` 的 WP1 审计写入、WP2 调用引用、WP3 发布引用、WP5 本域事件、项目作用域、trace 信号、跨 WP 看板 pending 和 outbox 看板 pending；该状态只说明当前审计链观测边界，不代表真实跨 WP 审计看板或 outbox 重放看板已就绪。 |
| archive policy | 任务诊断展示 `archivePolicy` 的策略版本、保留天数、`platformManaged` 存储策略、审批要求、审批流 pending、真实归档存储 pending、外发开关、保留策略跟踪和细节导出关闭；该状态只说明当前归档治理边界，不代表真实归档存储、审批流、外发流程或工单流转已就绪。 |
| report manifest policy | 任务诊断展示 `reportManifestPolicy` 的策略版本、报告 schema/字段集版本、清单模式、行数/完成状态跟踪、归档核验和细节导出关闭；该状态只说明当前报告清单聚合核验边界，不代表行级完整性值、候选 ID、trace ID 或审计 ID 明细索引已开放。 |

## 9. 候选评审交互

1. 候选列表支持多选，但默认不跨页选择。
2. 批量确认前需要二次确认，展示选中数量和风险摘要。
3. 编辑候选时步骤使用可增删、上移、下移的固定高度行，避免布局跳动。
4. 质量提示以 badge 或紧凑提示展示，如“缺断言”“重复风险”“低置信度”。
5. 发布前必须先执行 dryRun 或由发布接口自动返回预览确认状态；前端不应直接静默发布。
6. 发布成功后候选行显示 WP3 用例编码和跳转按钮。

## 10. 可测性要求

| 项 | 要求 |
|---|---|
| API mock | 前端测试可 mock 任务、候选、dryRun、发布成功和发布失败响应。 |
| 选择器 | 对关键按钮和状态增加稳定文本或 data 属性，便于 Playwright smoke。 |
| Trace ID | 错误态和成功结果展示 traceId，便于自动化断言。 |
| 权限测试 | 覆盖无入口、无按钮和接口 403 三类情况。 |
| 大数据量 | 候选列表至少按 100 条测试布局和批量操作。 |

## 11. 验收清单

1. `testDesign:read` 用户可进入用例生成工作台。
2. 无 `testDesign:generate` 时不展示新建、重试、取消按钮。
3. 无 `testDesign:review` 时候选编辑和确认按钮不可见。
4. 无 `testDesign:publish` 时发布 dryRun 和正式发布按钮不可见。
5. 创建任务表单字段校验完整，提交失败保留表单内容。
6. 候选列表、详情、编辑、批量确认、驳回和忽略主流程可用。
7. dryRun 结果能区分创建、重复、跳过和失败。
8. 发布成功后可跳转 WP3 测试用例详情或列表筛选结果。
9. loading/empty/error/partial success/version conflict/model blocked 状态均有可读展示。
10. 任务诊断只展示上下文计数、裁剪策略、作用域策略、评测语料策略、发布准出审批策略、审计链策略和归档治理策略聚合标记，不展示显式资产 ID、schema、页面树、流程 JSON、需求正文、候选 ID、角色规则、服务令牌原值、评测语料行、候选正文、评审评论、候选级准出证据、审批备注、阈值规则明细、平台审计标识原值、traceId 原值、模型调用 ID 原值、发布 sourceRef、资产 ID、归档路径、归档备注、审批说明、工单 URL 或原始 Prompt。
11. `cd portal-web && npm test`、`cd portal-web && npm run build`、WP5 前端 smoke 均通过。
