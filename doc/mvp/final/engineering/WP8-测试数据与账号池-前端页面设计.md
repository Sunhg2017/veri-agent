# WP8 测试数据与账号池 - 前端页面设计

| 项目 | 内容 |
|---|---|
| 工作包 | WP8 测试数据与账号池 |
| 角色产出 | 资深前端工程师 |
| 文档性质 | 页面、路由、权限、状态和可测性设计 |
| 当前口径 | `portal-web` 新增测试数据工作台，面向高频维护和执行前准备，保持后台工具型体验 |
| 版本 | v0.1 |
| 日期 | 2026-06-15 |

## 1. 页面目标

让测试工程师在浏览器内完成 WP8 P0 主链路：维护数据集、维护账号池、申请和释放账号租借、查看清理任务和导出脱敏摘要。前端只做体验控制，后端仍是权限、scope、状态和敏感字段保护的最终来源。

## 2. 路由和入口

| 项目 | 设计 |
|---|---|
| 路由 | `#test-data` |
| 菜单文案 | `测试数据` |
| 入口位置 | 测试资产、接口自动化和执行编排之间的一级入口 |
| 入口权限 | `testData:read` |

权限控制：

| 权限 | 前端行为 |
|---|---|
| `testData:read` | 显示入口、列表、详情、策略摘要和只读导出结果。 |
| `testData:manage` | 显示新建/编辑/归档数据集、账号池和账号按钮。 |
| `testData:lease` | 显示租借、续租、释放按钮。 |
| `testData:cleanup` | 显示创建、重试、确认清理任务按钮。 |
| `testData:export` | 显示导出脱敏摘要按钮。 |

## 3. 信息架构

| 区域 | 内容 |
|---|---|
| 顶部筛选 | 项目、应用、环境、状态、角色标签、关键词。 |
| 策略摘要 | WP8 开关、默认 TTL、最大 TTL、清理开关、导出红线和 traceId。 |
| 数据集面板 | 数据集列表、schema 摘要、敏感字段计数、记录计数、状态、清理策略。 |
| 账号池面板 | 账号池列表、角色标签、可用/租借/锁定计数、默认 TTL、健康摘要。 |
| 租借面板 | active lease、holder、过期时间、续租、释放、过期/撤销记录。 |
| 清理任务面板 | 准备、刷新、清理、回滚任务列表，状态、错误码、重试入口。 |
| 导出面板 | 脱敏导出摘要、字段白名单、redaction policy。 |

## 4. 组件拆分

| 组件 | 职责 |
|---|---|
| `TestDataWorkbench` | 页面容器，维护筛选、权限、刷新和 toast。 |
| `TestDataPolicySummary` | 展示 WP8 开关、租借 TTL、清理开关和导出红线。 |
| `DataSetPanel` | 数据集列表、详情、创建、编辑、归档和记录摘要。 |
| `DataSetForm` | 数据集基本信息、schema、敏感字段、清理策略表单。 |
| `AccountPoolPanel` | 账号池列表、详情、角色标签和账号计数。 |
| `AccountForm` | 新增/更新账号摘要、角色标签、secretRef 输入和替换确认。 |
| `LeasePanel` | 租借申请、active lease、续租、释放、过期记录。 |
| `CleanupTaskPanel` | 清理任务创建、状态查看、重试和错误摘要。 |
| `TestDataExportPanel` | 脱敏摘要导出和 redaction policy 展示。 |

## 5. 表单规则

| 表单 | 校验 |
|---|---|
| 数据集 | 项目必填；code 只允许字母、数字、短横线和下划线；名称最长 128；至少 1 个 schema 字段；敏感字段必须标注。 |
| 数据记录 | 单次导入条数和摘要大小受后端限制；敏感字段只能显示 masked 值或 digest。 |
| 账号池 | 项目、应用、环境必填；默认 TTL 不超过最大 TTL；并发策略必须显式选择。 |
| 账号 | accountKey 必填；secretRef 必填且不回显旧值；角色标签至少 1 个；替换 secretRef 需要二次确认。 |
| 租借 | pool、角色标签、holderType、holderRef、TTL 必填；TTL 超上限时本地阻断。 |
| 清理任务 | taskType、targetRef、reason 必填；自动清理开关关闭时只允许创建待确认任务。 |

## 6. 状态展示

| 状态 | 展示要求 |
|---|---|
| loading | 分面板局部 loading，不阻塞全页筛选。 |
| empty | 数据集、账号池、租借和任务分别展示空态和可创建入口。 |
| error | 展示错误码、traceId 和脱敏 message。 |
| 403 | 直达路由展示无权限态，隐藏所有写操作按钮。 |
| conflict | 租借冲突展示 `ACCOUNT_LEASE_CONFLICT` 和刷新入口。 |
| expired | active lease 到期后清晰标记，释放按钮变为确认过期/清理。 |
| locked | 账号锁定时展示原因摘要和人工复核入口。 |
| cleanup disabled | 展示只读策略摘要，不误导用户以为已执行清理。 |

## 7. 展示白名单

允许展示：

- 数据集 code/name/status/schema 摘要、敏感字段计数、record digest。
- 账号 accountKey/displayName/status/roleTags/secretRefDigest。
- 租借 holderType/holderRef/status/expiresAt/releaseReason。
- 清理任务 taskType/status/errorCode/errorSummary。
- traceId 和审计摘要。

禁止展示：

- 密码、token、cookie、Authorization header。
- `secretRef` 原文。
- 完整数据记录原文。
- runner 环境变量、登录 cookie 或会话 token。
- 生产数据外部引用的完整 URL query 或访问密钥。

## 8. 交互细节

1. 数据集和账号池列表采用密集表格，详情在右侧或下方分区展示，不使用营销式 hero。
2. secretRef 输入框新增时可输入，编辑时只显示 digest 和“替换”按钮。
3. 租借按钮点击后展示选中账号摘要、TTL 和过期时间，不展示凭据。
4. 释放租借时允许勾选“创建清理任务”，但清理是否执行由后端策略决定。
5. 账号锁定和清理失败必须要求用户填写处理备注。
6. 长 code、holderRef、traceId 和错误摘要必须换行，不造成按钮或表格重叠。

## 9. 响应式要求

1. 桌面端优先使用双列或上下分区，保证列表扫描效率。
2. 390px 窄屏下筛选折叠为多行，表格转为分段列表或横向滚动容器。
3. 工具栏图标按钮需要 tooltip，文字按钮只用于明确命令。
4. 表单字段在窄屏下单列排列，按钮不溢出容器。
5. 租借倒计时和状态标签不能撑开卡片或覆盖相邻内容。

## 10. 可测性

1. `portal-web/src/api/testData.ts` 提供 API client 和 normalize helper，兼容后端 camelCase 响应。
2. 权限按钮显隐抽成 helper 单测。
3. 租借 payload、续租 payload、释放 payload、清理任务 payload 单独测试。
4. redaction policy 和展示白名单 helper 单独测试，确保 secretRef 原文不进入 UI state。
5. 关键按钮使用稳定 accessible name，便于 Playwright smoke。
6. 主链路浏览器 smoke 覆盖桌面和 390px：创建数据集、创建账号池、新增账号、租借、续租、释放、创建清理任务、导出摘要。

## 11. 前端验收

1. 无 `testData:read` 权限用户不可见入口，直达 `#test-data` 展示无权限态。
2. 数据集、账号池、租借、清理任务均具备 loading/empty/error。
3. secretRef 原文不出现在 DOM、toast、导出面板或错误提示中。
4. 租借冲突、过期、账号锁定和清理失败均有稳定错误码和 traceId。
5. `npm test` 覆盖 API helper、权限 helper、payload 构造和脱敏展示。
6. `npm run build` 和 WP8 前端 smoke 通过。
