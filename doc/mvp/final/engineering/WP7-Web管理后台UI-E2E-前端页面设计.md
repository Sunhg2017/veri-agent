# WP7 Web 管理后台 UI/E2E - 前端页面设计

| 项目 | 内容 |
|---|---|
| 工作包 | WP7 Web 管理后台 UI/E2E |
| 角色产出 | 资深前端工程师 |
| 文档性质 | 页面信息架构、交互、权限、状态和可测性设计 |
| 当前口径 | 在 `portal-web` 管理台新增 `#ui-e2e` 工作台，覆盖场景、脚本包、运行、artifact 摘要和 Flaky 视图；首期只展示脱敏摘要与状态，不展示原始凭据和完整产物正文 |
| 版本 | v0.1 |
| 日期 | 2026-06-18 |

## 1. 页面目标

让测试工程师和自动化工程师在浏览器内完成 WP7 P0 主链路：

1. 创建和维护 UI/E2E 场景。
2. 查看并评审 Playwright bundle 摘要与静态校验结果。
3. 手动发起单次运行并观察状态。
4. 查看失败分类、artifact 摘要和 Flaky 标记。
5. 在不暴露敏感信息的前提下，为 WP9/WP10 交接执行摘要。

## 2. 导航与权限

| 项 | 设计 |
|---|---|
| 路由 | `#ui-e2e` |
| 菜单名 | UI/E2E |
| 入口权限 | `uiE2e:read` |
| 场景维护 | `uiE2e:manage` |
| 审批 | `uiE2e:review` |
| 运行 | `uiE2e:execute` |
| 导出 | `uiE2e:export` |
| Flaky | `uiE2e:flaky` |

无 `uiE2e:read` 权限时不显示入口；直达路由展示无权限态，不触发业务 API。

## 3. 页面结构

| 区域 | 内容 |
|---|---|
| 顶部指标 | 已审批场景数、待评审 bundle 数、运行中数、最近失败数、runner 状态、allowlist 状态。 |
| 左侧主列表 | 场景列表，支持项目、应用、环境、状态、标签、风险级别筛选。 |
| 右侧工作区 | 场景详情、步骤、bundle 摘要、运行面板、artifact 摘要、Flaky 面板。 |
| 底部或侧边详情 | 运行步骤明细、失败分类、traceId、审计时间线。 |

建议布局：

1. 桌面端使用左侧场景列表 + 右侧详情工作区。
2. 窄屏下改为顶部筛选 + tabs 切换 `场景`、`Bundle`、`运行`、`Flaky`。

## 4. 主要视图

### 4.1 场景列表

列表字段建议：

1. 场景名称
2. 项目/应用/环境
3. 状态
4. 风险级别
5. 来源摘要计数
6. 最近 bundle 状态
7. 最近运行状态
8. 更新时间

操作建议：

1. 新建场景
2. 编辑
3. 提交评审
4. 查看 bundle
5. 运行
6. 归档

### 4.2 场景详情

详情区域展示：

1. 基础信息
2. 来源摘要：页面、业务流、测试用例引用
3. 步骤模板列表
4. 定位策略和等待策略摘要
5. 最近 bundle 和最近运行摘要

不展示：

1. 原始 DOM 片段
2. 凭据字段
3. 完整 Playwright 源码

### 4.3 Bundle 评审视图

展示内容：

1. bundle digest
2. 依赖摘要
3. fixture 摘要
4. 静态校验结果
5. 评审状态和评审意见

操作：

1. 提交评审
2. 审批通过
3. 驳回并填写原因
4. 查看静态校验失败项

### 4.4 运行视图

运行面板包含：

1. 环境
2. `baseUrlRef` 摘要
3. `accountLeaseRef` 和账号摘要
4. 状态、耗时、步骤计数
5. 失败分类
6. artifact 摘要
7. 取消按钮

按钮可见性：

1. 未具备 `uiE2e:execute` 不显示运行和取消。
2. runner disabled 时按钮显示但禁用，并展示解释文案。

### 4.5 Flaky 视图

展示内容：

1. Flaky 状态
2. 标记理由
3. 关联运行数
4. 最近失败分类
5. 审计信息

支持：

1. 标记 `FLAKY_CANDIDATE`
2. 确认 `CONFIRMED_FLAKY`
3. `WAIVED`
4. 按状态筛选

## 5. 核心交互

### 5.1 创建场景

1. 点击 `新建场景`。
2. 选择项目、应用、环境。
3. 填写名称、风险级别、标签。
4. 选择来源摘要：页面、业务流、测试用例。
5. 增加步骤模板，填写定位策略、等待策略和断言摘要。
6. 保存为 `DRAFT`。

### 5.2 提交评审

1. `DRAFT` 场景或 bundle 显示提交评审按钮。
2. 点击后写入评审状态。
3. 审批通过后变为 `APPROVED`。
4. 驳回时必须填写原因。

### 5.3 手动运行

1. 仅 `APPROVED` 场景和 bundle 显示运行按钮。
2. 用户选择环境、`baseUrlRef` 和 `accountLeaseRef`。
3. 提交后跳转运行详情并轮询状态。
4. 失败时突出显示错误码、traceId、失败分类和 artifact 摘要。

### 5.4 取消运行

1. `QUEUED/RUNNING` 可取消。
2. 取消中按钮进入 loading。
3. 已终态时再次取消返回幂等提示。

## 6. API Client 设计

建议新增 `portal-web/src/api/uiE2e.ts`：

| 类型或函数 | 说明 |
|---|---|
| `UiE2eSceneView` | 场景响应类型。 |
| `UiE2eBundleView` | bundle 响应类型。 |
| `UiE2eRunView` | 运行响应类型。 |
| `UiE2eArtifactManifestView` | artifact 摘要类型。 |
| `UiE2eFlakyMarkView` | Flaky 标记类型。 |
| `fetchUiE2eHealth()` | 查询健康摘要。 |
| `fetchUiE2eScenes(filters)` | 查询场景列表。 |
| `createUiE2eScene(payload)` | 创建场景。 |
| `updateUiE2eScene(id, payload)` | 更新场景。 |
| `submitUiE2eSceneReview(id)` | 提交评审。 |
| `fetchUiE2eBundles(filters)` | 查询 bundle。 |
| `approveUiE2eBundle(id)` | 审批 bundle。 |
| `rejectUiE2eBundle(id, payload)` | 驳回 bundle。 |
| `createUiE2eRun(payload)` | 发起运行。 |
| `fetchUiE2eRun(id)` | 查询运行详情。 |
| `cancelUiE2eRun(id)` | 取消运行。 |
| `exportUiE2eRun(id)` | 导出运行摘要。 |
| `upsertUiE2eFlakyMark(payload)` | 创建或更新 Flaky 标记。 |

API client 需兼容后端 camelCase/snake_case 差异，并统一保留 `traceId`。

## 7. 表单校验

| 字段 | 校验 |
|---|---|
| 场景名称 | 必填，1-120 字符。 |
| 项目/应用/环境 | 必填，且必须来自当前可见 scope。 |
| 风险级别 | `LOW/MEDIUM/HIGH/CRITICAL`。 |
| 步骤列表 | 至少 1 步。 |
| stepType | 必填，限定枚举。 |
| 定位策略 | 必填，限制 key/value 长度。 |
| `accountLeaseRef` | 运行时必填，必须是 UUID。 |
| `baseUrlRef` | 运行时必填，首期仅接受 `env:<key>`。 |
| 驳回原因 | reject 时必填。 |
| Flaky 原因 | 标记时必填。 |

前端校验仅优化体验，后端仍必须完整校验。

## 8. 状态展示

| 状态 | UI 表现 |
|---|---|
| `DRAFT` | 可编辑，不可执行。 |
| `REVIEWING` | 显示待评审 badge。 |
| `APPROVED` | 可执行，显示绿色 badge。 |
| `DISABLED` | 展示禁用说明，不可执行。 |
| `ARCHIVED` | 只读，不显示维护按钮。 |
| `QUEUED` | 显示排队中与请求 key 摘要。 |
| `RUNNING` | 显示进度、步骤计数、可取消按钮。 |
| `SUCCEEDED` | 绿色完成，可查看 artifact 摘要。 |
| `FAILED/TIMEOUT` | 红色失败，展示失败分类、errorCode、traceId。 |
| `CANCELED` | 灰色终态。 |
| `BLOCKED` | 展示被策略阻断的原因，如 runner disabled、lease invalid。 |

## 9. 安全与脱敏

1. 不展示 `secretRef` 原文、密码、token、cookie、Authorization。
2. `baseUrl` 只显示 digest 或 host 摘要，不显示完整敏感 query。
3. artifact 只显示 `artifactType/digest/size/storageRef status`。
4. 错误提示展示 `errorCode + traceId + 脱敏 message`。
5. 任何 DOM 扫描命中禁止字段时，只展示策略阻断，不显示命中值。

## 10. Loading / Empty / Error

| 场景 | 行为 |
|---|---|
| 首次加载 | 骨架屏，不阻塞导航。 |
| 无场景 | 展示空态和新建按钮。 |
| 无 bundle | 展示空态并提示先生成 bundle。 |
| 无运行记录 | 展示空态和运行入口。 |
| 无权限 | 展示无权限态，不展示操作按钮。 |
| 后端 409 | 展示状态冲突和建议动作。 |
| runner disabled | 展示当前受控范围说明和错误码。 |
| run 轮询失败 | 保留上次数据并提供刷新。 |

## 11. 响应式

1. 桌面端使用主从布局，右侧详情区可包含 tabs。
2. 390px 窄屏下指标区折叠为两列摘要卡，主视图区使用分段 tabs。
3. 长名称、digest、traceId 和 errorCode 必须自动换行。
4. 操作按钮在窄屏下折叠为图标 + tooltip 或更多菜单，不产生横向溢出。

## 12. 可测性

1. 关键按钮需有稳定 accessible name：`新建场景`、`提交评审`、`审批通过`、`运行场景`、`取消运行`、`标记 Flaky`。
2. 关键区域建议加入 data-testid：`ui-e2e-workbench`、`ui-e2e-scene-list`、`ui-e2e-bundle-panel`、`ui-e2e-run-detail`、`ui-e2e-flaky-panel`。
3. Playwright smoke 需覆盖桌面和 390px 视口。
4. DOM 禁止字段扫描要覆盖 `secret://`、`Authorization`、`Bearer `、`token=`、`password=` 等样本。

## 13. 前端验收

1. 无 `uiE2e:read` 权限时不显示入口，直达路由展示无权限态。
2. 可创建、编辑并查看场景详情。
3. 可查看 bundle 摘要和静态校验结果，并完成审批流。
4. 可手动触发运行，查看状态、失败分类和 artifact 摘要。
5. 可对运行或场景执行 Flaky 标记。
6. 所有错误态都展示 `errorCode` 和 `traceId`。
7. 页面在桌面和 390px 下无明显遮挡、溢出和文本重叠。
