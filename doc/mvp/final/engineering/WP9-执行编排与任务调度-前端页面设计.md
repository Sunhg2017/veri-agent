# WP9 执行编排与任务调度 - 前端页面设计

| 项目 | 内容 |
|---|---|
| 工作包 | WP9 执行编排与任务调度 |
| 角色产出 | 资深前端工程师 |
| 文档性质 | 页面信息架构、交互、权限、状态和可测性设计 |
| 当前口径 | `#execution` 工作台已覆盖执行计划、DAG 预览、手动运行、运行详情、取消重试、导出摘要、触发配置和用户操作说明 |
| 版本 | v0.1 |
| 日期 | 2026-06-13 |

截至 2026-06-14 M8H，`portal-web` 已新增 `#execution` 工作台入口、`execution:read/manage/trigger/export` 权限判断、WP9 API client normalization、调度策略指标、计划列表、多节点计划创建/更新/归档、DAG 摘要与 dryRun、手动触发、运行详情、取消/重试、脱敏执行摘要导出、触发配置摘要、trigger dryRun、触发事件查看和桌面/390px Playwright browser smoke。M8H 已补 `WP9-执行编排与任务调度-前端操作说明.md`，覆盖用户不依赖 curl 完成计划、DAG、运行、取消、重试、导出和触发解释的浏览器路径。外部 webhook HTTP smoke、GitHub/GitLab/Jenkins 接入样例、scheduler/trigger Runbook、供应商 marketplace 接入包、worker 托管 readiness、CRON capacity/backlog 准出均已由脚本、模板和文档覆盖；cron scanner 操作台、真实供应商 OAuth/App 安装向导与独立 worker 管理页面仍按后续切片推进。

## 1. 页面目标

让测试工程师在浏览器内完成 WP9 P0 主链路：创建执行计划、校验 DAG、手动触发运行、查看节点状态、取消或重试失败节点，并能理解触发配置和 WP6/WP7 runner 边界。

## 2. 导航与权限

| 项 | 设计 |
|---|---|
| 路由 | `#execution` |
| 菜单名 | 执行编排 |
| 入口权限 | `execution:read` |
| 创建/编辑 | `execution:manage` |
| 触发/取消/重试 | `execution:trigger` |
| 调度管理 | `execution:admin` |
| 导出 | `execution:export` |

无 `execution:read` 权限时不显示入口；直达路由展示无权限态，不触发业务 API。

## 3. 页面结构

| 区域 | 内容 |
|---|---|
| 顶部指标 | READY 计划数、运行中数、失败数、调度开关、webhook/cron 开关。 |
| 计划列表 | 项目、环境、状态、触发方式、最近运行、更新时间、操作入口。 |
| 计划编辑 | M6B 为页面内多节点 DAG 草稿编辑；后续可再升级为图形化 DAG 编辑抽屉。 |
| DAG 预览 | 节点卡片、依赖边、节点类型、输入摘要、状态色和错误提示。 |
| 运行详情 | run 状态、触发来源、节点列表、耗时、错误码、traceId、外部 run 摘要。 |
| 触发配置 | webhook/cron 配置摘要、启停、dryRun、最近事件。 |
| 导出面板 | 脱敏执行摘要和 WP10 handoff 状态。 |

## 4. 核心交互

### 4.1 创建计划

1. 点击新建。
2. 填写计划名、项目、环境。
3. 添加 API_TEST 或 REPORT_HANDOFF 节点，填写 `apiAutomationBundleId`、`baseUrlRef`、caseIds 和 runtime secretRefs 引用。
4. 添加依赖、失败策略、超时和 retry 次数。
5. 点击 dryRun，展示循环依赖、权限、资源引用和 runner 策略结果。
6. 保存为 DRAFT/READY/DISABLED；选中既有计划后可保存更新或归档。

### 4.2 手动触发

1. READY 计划显示运行按钮。
2. 用户输入 requestKey 和原因。
3. 提交后跳转运行详情。
4. 运行中页面轮询 run 详情。

### 4.3 取消与重试

1. RUNNING/QUEUED run 显示取消按钮。
2. FAILED/TIMEOUT/PARTIAL_SUCCESS run 显示重试按钮。
3. 终态取消返回当前状态，不重复提示错误。
4. 重试展示 retryAttempt 和被重试节点。

## 5. 表单校验

| 字段 | 校验 |
|---|---|
| 计划名 | 必填，1-80 字符。 |
| environmentKey | 必填，必须来自当前项目环境。 |
| DAG node key | 必填，唯一，只允许字母、数字、`-`、`_`。 |
| node type | 必填，P0 支持 API_TEST 和 REPORT_HANDOFF。 |
| dependencies | 只能引用已存在节点，不能形成环。 |
| timeoutSeconds | 1-86400。 |
| retry count | 0-5。 |
| requestKey | 可选，填写时 1-128 字符。 |

前端校验只做体验优化，后端仍必须完整校验。

## 6. 状态展示

| 状态 | UI 表现 |
|---|---|
| DRAFT | 可编辑，不可触发。 |
| READY | 可触发。 |
| DISABLED | 不可触发，显示禁用原因。 |
| QUEUED | 显示排队中和位置摘要。 |
| RUNNING | 显示进度、heartbeat 时间和可取消操作。 |
| SUCCEEDED | 绿色完成，可导出摘要。 |
| PARTIAL_SUCCESS | 黄色完成，展示失败节点和可重试入口。 |
| FAILED/TIMEOUT | 红色，展示错误码、traceId 和重试入口。 |
| CANCELED | 灰色终态。 |

## 7. 安全与脱敏

1. 不展示 webhook secret、secretRef 明文、环境变量值、baseUrl 明文或请求响应正文。
2. 展示 `secretRefCount`、digest、host 摘要和 redactionPolicy。
3. 错误提示使用 errorCode、traceId 和脱敏 message。
4. webhook URL 只展示 masked triggerKey。

## 8. Loading/Empty/Error

| 场景 | 行为 |
|---|---|
| 首次加载 | 骨架屏，不阻塞导航。 |
| 无计划 | 展示空态和新建按钮。 |
| 无权限 | 展示无权限态，不展示创建按钮。 |
| dryRun 失败 | 在 DAG 节点上定位错误，顶部显示 traceId。 |
| run 轮询失败 | 保留最后一次数据，提示刷新。 |
| 后端 409 | 展示状态冲突和建议动作。 |

## 9. 响应式

1. 桌面端使用左列表右详情或主从布局。
2. 390px 窄屏下计划列表、节点卡片和操作按钮纵向堆叠。
3. 长 node key、错误码、traceId 和路径摘要必须换行，不产生横向页面溢出。
4. DAG 在窄屏降级为节点列表和依赖 chips，不强制画复杂图。

## 10. 可测性

1. 关键按钮有稳定 accessible name：新建执行计划、Dry run、保存计划、运行计划、取消运行、重试失败节点、导出摘要。
2. 关键区域使用稳定 data-testid：`execution-workbench`、`execution-plan-list`、`execution-dag-preview`、`execution-run-detail`。
3. Playwright smoke 使用 mock API 覆盖创建、更新、dryRun、运行、取消、重试、触发配置和移动端布局。

## 11. 前端操作说明

完整操作说明见 `WP9-执行编排与任务调度-前端操作说明.md`。当前工作台的用户主链路如下：

1. 具备 `execution:read` 的用户从侧边导航进入 `执行编排`，直达路由为 `#execution`；无权限用户看不到入口，直达后只展示无权限态。
2. 在 `新建计划` 区填写项目、名称、环境、状态和描述，添加 `API_TEST` 或 `REPORT_HANDOFF` DAG 节点，并配置依赖、失败策略、超时、重试次数、`bundleId`、`baseUrlRef`、caseIds 和 secretRefs 摘要。
3. 点击 `创建` 保存计划；选择计划列表中的既有计划后可点击 `保存更新` 或 `归档`。前端会先做字段和 DAG 依赖校验，后端继续校验项目 scope、WP6 bundle、环境和 secretRef。
4. 在 `DAG 与运行` 区点击 `Dry run` 查看 valid、dagDigest 和 issue；填写可选 requestKey 与原因后点击 `运行`，同一 requestKey 会回放既有 run。
5. 在 `运行详情` 区查看 run/node 状态、traceId、sourceEventId 或 requestKey；对 `QUEUED/RUNNING` run 点击 `取消`，对 `FAILED/TIMEOUT/PARTIAL_SUCCESS` run 点击 `重试`。
6. 具备 `execution:export` 时点击 `导出摘要`，页面只展示脱敏 schema、导出时间、节点状态计数和 redactionPolicy 摘要。
7. 在 `触发配置` 区新增 WEBHOOK 或 CRON trigger，点击 trigger `Dry run` 校验配置和全局开关，再按需 `启用`、`暂停` 或查看 `事件`。
8. 页面不展示 secretRef 明文、webhook secret、baseUrl 明文、请求/响应正文、stdout/stderr 或 webhook payload 原文；触发签名和 CI 接入细节由 webhook 样例文档承接。

## 12. 前端验收

1. 无 read 权限不显示入口，直达路由显示无权限态。
2. 可创建 DRAFT/READY 计划并 dryRun。
3. READY 计划可手动触发，运行详情可看到节点状态。
4. RUNNING 可取消，FAILED/PARTIAL_SUCCESS 可重试。
5. 所有错误展示 errorCode 和 traceId，不泄露敏感值。
6. 桌面和 390px 窄屏无明显遮挡或横向页面溢出。
