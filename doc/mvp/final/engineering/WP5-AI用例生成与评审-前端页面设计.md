# WP5 AI 用例生成与评审 - 前端页面设计

| 项目 | 内容 |
|---|---|
| 工作包 | WP5 AI 用例生成与评审 |
| 角色产出 | 资深前端工程师 |
| 文档性质 | 前端页面、路由、权限、状态和可测性设计 |
| 当前口径 | 基于 `portal-web` React + TypeScript + Vite 管理台扩展，已纳入任务质量、Prompt 趋势、Prompt 版本准出、任务诊断和本域审计链摘要面板 |
| 版本 | v0.3 |
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
| `fetchTestDesignPromptTrend(filters)` | 查询 Prompt 版本趋势和版本级准出摘要。 |
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
| 上下文选项 | 至少保留需求上下文，API/页面/流程/历史用例可选。 |

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
| prompt readiness | Prompt 趋势中每个版本展示 `PASSED/WARNING/BLOCKED` 准出状态、阻断数和风险数；该状态仅用于运营比较，不禁用发布按钮。 |

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
10. `cd portal-web && npm test`、`cd portal-web && npm run build`、WP5 前端 smoke 均通过。
