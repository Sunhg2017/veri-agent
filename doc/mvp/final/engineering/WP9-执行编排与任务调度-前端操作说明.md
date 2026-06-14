# WP9 执行编排与任务调度 - 前端操作说明

| 项目 | 内容 |
|---|---|
| 工作包 | WP9 执行编排与任务调度 |
| 文档性质 | 浏览器操作说明、产品验收辅助材料 |
| 当前口径 | 用户可在 `#execution` 工作台完成计划、DAG、运行、取消、重试、导出和触发配置主链路，不依赖 curl |
| 日期 | 2026-06-14 |

## 1. 适用范围

本文面向测试工程师、测试负责人、项目负责人和发布负责人，说明如何通过 `portal-web` 的 `执行编排` 工作台完成 WP9 当前已交付的浏览器操作。本文不替代 scheduler/trigger 运维 Runbook，也不覆盖外部 CI webhook 的 HMAC 生成细节；外部 webhook 联调仍参考 `WP9-Webhook签名样例与CI接入说明.md` 和 `WP9-Scheduler-Trigger-Runbook.md`。

## 2. 入口与权限

| 操作 | 权限 |
|---|---|
| 进入 `#execution`、查看计划/运行/触发事件 | `execution:read` |
| 新建、更新、归档计划，新增和启停触发配置 | `execution:manage` |
| 手动运行、取消、重试 | `execution:trigger` |
| 导出脱敏执行摘要 | `execution:export` |

无 `execution:read` 时侧边导航不展示 `执行编排`，直达 `#execution` 只展示无权限态，不拉取业务数据。按钮置灰不代表后端放行；所有操作仍以服务端权限、项目 scope、计划状态和触发开关为准。

## 3. 页面结构

| 区域 | 用途 |
|---|---|
| 顶部指标 | 查看 READY 计划、运行中、失败/超时和启用触发数量。 |
| 调度策略 | 查看 Scheduler、Webhook、Cron、Cron scanner、WP6 dispatch 和 Recovery 配置摘要。 |
| 新建/编辑计划 | 填写项目、名称、环境、状态、描述和 DAG 节点。 |
| 计划列表 | 选择已有计划，进入编辑模式并加载触发配置。 |
| DAG 与运行 | 执行 plan dryRun，填写手动 requestKey/原因并触发运行。 |
| 运行详情 | 查看 run/node 状态，执行刷新、取消、重试和导出摘要。 |
| 触发配置 | 新增 WEBHOOK/CRON trigger，执行 trigger dryRun、启停和事件查看。 |

## 4. 新建或编辑执行计划

1. 进入 `执行编排` 后，先查看 `调度策略`，确认 WP6 dispatch、Scheduler、Webhook/Cron 等策略是否符合本次使用场景。
2. 在 `新建计划` 区填写 `项目`、`名称`、`环境`、`状态` 和 `描述`。只有 `READY` 计划可被手动、Webhook 或 CRON 触发；`DRAFT` 适合保存草稿，`DISABLED` 用于停用。
3. 在 `DAG 节点` 中配置至少一个节点。当前浏览器工作台支持 `API_TEST` 和 `REPORT_HANDOFF`。
4. `API_TEST` 节点需要填写已审批的 `bundleId`、`baseUrlRef`、可选 `caseIds` 和 `secretRefs`。推荐使用 `baseUrlRef=env:<environmentKey>`，避免在页面中输入真实 baseUrl。
5. 多节点计划可用 `依赖` 填写上游 node key，多个依赖按逗号分隔；`失败策略` 支持 `FAIL_FAST`、`CONTINUE`、`BLOCK_DOWNSTREAM`。
6. 设置 `超时秒` 和 `重试次数` 后点击 `创建`。选择计划列表中的既有计划后会切换为编辑模式，可点击 `保存更新` 或 `归档`。
7. 页面本地校验会拦截缺失字段、重复 node key、非法依赖和依赖环；后端仍会重复校验项目 scope、WP6 bundle 状态、环境和密钥引用。

## 5. DAG 校验与手动运行

1. 在 `计划列表` 选择目标计划。
2. 点击 `DAG 与运行` 区的 `Dry run`，查看 `VALID/INVALID`、dagDigest 和 issue 数量；如果存在 issue，按 severity、nodeKey 和 code 定位问题。
3. 对需要幂等的手动运行填写 `requestKey`；同一计划下重复 requestKey 会回放既有 run，不重复创建。
4. 在 `原因` 中填写本次运行目的，便于审计和排障。
5. 点击 `运行` 后页面会选择新 run 并进入 `运行详情`；如果返回幂等回放，页面提示已回放既有运行。

## 6. 运行详情、取消、重试和导出

1. `运行详情` 顶部展示 run 短 ID、traceId、sourceEventId 或 requestKey，以及当前账号是否允许导出。
2. 左侧运行记录按最近加载结果展示，点击记录可切换详情；节点卡片展示 nodeKey、状态、runnerType、attempt 和外部 run 摘要。
3. `QUEUED` 或 `RUNNING` run 可点击 `取消`。终态 run 重复取消会保持终态，不应视为失败。
4. `FAILED`、`TIMEOUT` 或 `PARTIAL_SUCCESS` run 可点击 `重试`。重试会生成新的 attempt，并保留原失败证据。
5. 具备 `execution:export` 时可点击 `导出摘要`。导出结果只展示 schemaVersion、导出时间、节点状态计数和 redactionPolicy 摘要。
6. 页面不会展示 secretRef 明文、webhook secret、baseUrl 明文、请求/响应正文、stdout/stderr 或 webhook payload 原文；如发现敏感内容，应停止发布并回归 run export 脱敏测试。

## 7. 触发配置与事件查看

1. 选择计划后，在 `触发配置` 区新增 WEBHOOK 或 CRON trigger。
2. WEBHOOK trigger 填写 `source`、`eventType` 和可选 `secretRef`；CRON trigger 填写 `cron` 和 `timezone`。新增时可先保存为 `DISABLED`，完成 dryRun 和外部联调后再启用。
3. 点击 trigger 的 `Dry run`，查看全局开关是否开启、配置是否 valid、是否会创建 run。
4. 点击 `启用` 或 `暂停` 切换 trigger 状态。即使 trigger 为 `ENABLED`，全局 webhook/cron 开关关闭时仍不会触发。
5. 点击 `事件` 查看最近 trigger events。事件列表展示 status、sourceEventId、traceId 或 requestDigest，以及关联 runId 摘要。
6. 外部 webhook 的签名 header、稳定 eventId、raw body 和 CI 日志脱敏要求由接入样例文档说明；页面只负责配置摘要、dryRun、启停和事件证据查看。

## 8. 状态解释

| 状态 | 用户含义 |
|---|---|
| `DRAFT` | 草稿计划，不可触发。 |
| `READY` | 可触发计划。 |
| `DISABLED` | 已停用计划或触发器，不会触发。 |
| `ARCHIVED` | 已归档计划，只保留历史证据。 |
| `QUEUED` | run 或 node 等待 scheduler/worker 认领。 |
| `RUNNING` | 已认领或执行中，可取消。 |
| `SUCCEEDED` | 全部必要节点成功。 |
| `PARTIAL_SUCCESS` | 部分节点失败或被阻断，但允许部分成功收敛，可重试失败部分。 |
| `FAILED` | 执行失败，可按错误码和 traceId 排障后重试。 |
| `TIMEOUT` | 节点或 run 超时，可排查 worker heartbeat、runner timeout 和环境连通性后重试。 |
| `CANCELED` | 用户或系统取消后的终态。 |
| `PAUSED` | 触发器暂停，保留配置但不触发。 |

## 9. 常见排障

| 现象 | 处理 |
|---|---|
| 看不到 `执行编排` | 检查账号是否具备 `execution:read`。 |
| 创建按钮置灰 | 检查 `execution:manage` 权限和登录状态。 |
| `Dry run` 返回 invalid | 按 issue 的 nodeKey/code 修正依赖、bundle、环境或输入摘要。 |
| READY 计划运行失败 | 查看 run traceId、节点 errorCode 和 WP6 dispatch 策略摘要。 |
| 取消无效果 | 确认 run 是否已进入终态；终态取消是幂等成功。 |
| 重试按钮置灰 | 只有 `FAILED/TIMEOUT/PARTIAL_SUCCESS` run 支持控制面重试。 |
| 导出被阻断 | 检查 `execution:export` 权限；导出接口也会按项目 scope 鉴权。 |
| trigger enabled 但不触发 | 检查全局 webhook/cron 开关、trigger dryRun、计划 READY 状态和 sourceEventId 幂等记录。 |
| CRON 处理慢 | 查看 scheduler tick batch、worker 托管 readiness 和 CRON backlog runbook，不在页面内手工批量回补。 |

## 10. 产品验收清单

1. 用户可不依赖 curl 完成计划创建、DAG dryRun、手动运行、运行详情、取消、重试和脱敏摘要导出。
2. 用户可不依赖 curl 新增 WEBHOOK/CRON trigger、执行 trigger dryRun、启停 trigger 并查看事件证据。
3. 无权限、空态、加载中、错误、按钮置灰和 traceId/errorCode 展示均可解释。
4. 页面不展示 secretRef 明文、webhook secret、baseUrl 明文、请求/响应正文、stdout/stderr 或 webhook payload 原文。
5. 桌面和 390px 窄屏主链路由 WP9 Playwright smoke 覆盖，无横向页面溢出和按钮重叠。
