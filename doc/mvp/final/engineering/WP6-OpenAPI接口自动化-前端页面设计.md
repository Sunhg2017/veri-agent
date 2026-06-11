# WP6 OpenAPI 接口自动化 - 前端页面设计

| 项目 | 内容 |
|---|---|
| 工作包 | WP6 OpenAPI 接口自动化 |
| 角色产出 | 资深前端工程师 |
| 文档性质 | 页面、路由、权限、状态和可测性设计 |
| 当前口径 | portal-web 新增接口自动化工作台；保持工具型、密集、可扫描的后台体验 |
| 版本 | v0.1 |
| 日期 | 2026-06-11 |

## 1. 页面目标

前端目标是让测试工程师在浏览器内完成 WP6 P0 主链路：导入 OpenAPI、查看 diff、同步 API 资产、生成接口自动化用例、审查脚本包、触发受控试运行和查看结果摘要。

## 2. 信息架构

| 区域 | 内容 |
|---|---|
| 顶部筛选 | 项目、服务、规格版本、状态、关键词 |
| 规格管理 | 导入入口、规格列表、解析状态、digest、最近解析时间 |
| API diff | endpoint 列表、新增/变更/匹配/跳过分类、同步预览 |
| 生成任务 | 选择 API、WP5 用例、覆盖类型、生成模式、生成状态 |
| 脚本包 | 文件摘要、静态校验、评审状态、审批/驳回 |
| 试运行 | 环境、baseUrl、secretRef 引用、用例范围、运行状态 |
| 结果摘要 | pass/fail/skip/error、耗时、断言摘要、脱敏错误、traceId |

## 3. 路由和入口

建议新增入口：

- 路由：`#api-automation`
- 菜单文案：`接口自动化`
- 入口位置：测试资产或 AI 生产链路后的一级入口。

权限控制：

| 权限 | 前端行为 |
|---|---|
| `apiAutomation:read` | 显示入口、列表、详情和结果摘要 |
| `apiAutomation:import` | 显示导入、解析、sync 按钮 |
| `apiAutomation:generate` | 显示生成任务表单 |
| `apiAutomation:review` | 显示提交评审、审批、驳回按钮 |
| `apiAutomation:execute` | 显示试运行按钮 |
| `apiAutomation:export` | 显示导出按钮 |

后端仍是最终鉴权来源，前端只做体验控制。

## 4. 组件拆分

| 组件 | 职责 |
|---|---|
| `ApiAutomationWorkbench` | 页面容器，维护项目、筛选、权限、刷新和 toast |
| `OpenApiSpecPanel` | 规格列表、导入对话框、解析状态 |
| `OpenApiDiffTable` | endpoint diff 展示和同步选择 |
| `ApiAutomationGenerationPanel` | 生成任务创建、覆盖类型和 WP5 用例选择 |
| `ScriptBundlePanel` | 脚本包摘要、静态校验、评审操作 |
| `ApiAutomationRunPanel` | 手动试运行表单、运行状态、取消入口 |
| `ApiAutomationResultSummary` | 运行结果聚合、用例级结果、脱敏错误 |
| `ApiAutomationPolicySummary` | 展示 runner 开关、allowlist、模型/fallback 策略和导出红线 |

## 5. 表单规则

| 表单 | 校验 |
|---|---|
| OpenAPI 导入 | 项目必填；`sourceType` 必填；TEXT/UPLOAD/URL 三选一；名称最长 128；版本最长 64 |
| API sync | 至少选择 1 个 `NEW/CHANGED` endpoint；冲突项必须显式确认 |
| 生成任务 | 项目、规格、API 范围必填；每 API 数量 1-10；覆盖类型限定枚举 |
| 试运行 | 脚本包必须 approved 或允许开发模式；baseUrl 必填并提示 allowlist；timeout 10-600 秒 |
| 审批/驳回 | 驳回原因必填；审批备注可选但最长 500 |

## 6. 状态展示

| 状态 | 展示要求 |
|---|---|
| loading | 表格和详情局部 loading，不阻塞全页 |
| empty | 指导用户导入 OpenAPI 或切换项目 |
| error | 展示稳定错误码、traceId 和脱敏 message |
| 403 | 提示缺少对应权限，不展示操作按钮 |
| 409 | 展示冲突 endpoint、资产引用和刷新入口 |
| partial success | sync 或运行部分成功时展示成功/失败/跳过计数 |
| runner disabled | 试运行区展示只读策略摘要，不显示误导性运行入口 |
| policy blocked | WP2 或 runner 策略阻断时展示策略类别，不展示敏感细节 |

## 7. 结果摘要字段

前端只展示白名单字段：

- `runId` 的短展示或 bounded ref。
- `status`、`startedAt`、`finishedAt`、`durationMs`。
- `passedCount`、`failedCount`、`skippedCount`、`errorCount`。
- 用例标题、method、path、断言类别、脱敏错误摘要。
- traceId 用于复制和排障。

禁止展示：

- secretRef 明文。
- baseUrl query 中的 token。
- 完整 request/response body。
- stdout/stderr 全文。
- 环境变量值。

## 8. 可测性

1. API helper 必须提供 normalize 函数，兼容 snake_case/camelCase。
2. 页面状态和权限判断抽成可单测 helper。
3. 运行结果摘要聚合 helper 单独测试。
4. 表单 payload 构造单独测试，确保不提交空 secret 或敏感正文。
5. 关键按钮有稳定 accessible name，方便后续 Playwright smoke。

## 9. 响应式要求

1. 桌面端采用左右或上下分区，优先保证表格可扫描。
2. 窄屏下 diff、脚本包和结果表格转为可横向滚动或分段列表。
3. 所有按钮文案不得被挤压重叠，长 path、operationId 和错误摘要必须换行。
4. 不使用营销式 hero，不把工具内容放进嵌套卡片。

## 10. 前端验收

1. 无权限用户不可见入口，直达路由后能看到 403/无权限态。
2. 导入、diff、生成、审查、运行、结果每个阶段都有 loading/empty/error。
3. runner disabled 和策略阻断不会误导用户重复点击。
4. `npm test` 覆盖 API helper、权限 helper、结果摘要和表单 payload。
5. `npm run build` 通过，移动和桌面无明显重叠。
