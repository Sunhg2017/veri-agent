# WP7 Web 管理后台 UI/E2E - 需求文档与 PRD

| 项目 | 内容 |
|---|---|
| 工作包 | WP7 Web 管理后台 UI/E2E |
| 角色产出 | 资深产品经理 |
| 文档性质 | 需求文档、产品边界和验收标准 |
| 当前口径 | 基于 `platform-api` + `portal-web` 建设 Web 管理后台 UI/E2E 控制面，执行侧优先复用 Playwright；账号与凭据只通过 WP8 `accountLeaseRef` 和 `secretRefDigest` 摘要流转；WP9 只消费 `UI_TEST` 脱敏执行摘要；WP10 只消费失败分类、artifact manifest 和运行聚合信息 |
| 版本 | v0.1 |
| 日期 | 2026-06-18 |

## 1. 背景

WP1-WP4 已提供平台底座、模型接入、资产库和需求输入，WP5 正在沉淀结构化测试用例，WP6 已具备 OpenAPI 接口自动化，WP8 提供账号租借引用，WP9 具备执行编排控制面，WP10 具备报告与失败诊断能力。当前平台缺少一条面向 Web 管理后台的可治理 UI/E2E 自动化能力，导致登录、菜单权限、列表筛选、表单操作、审批流、导入导出和审计核验等高频回归路径仍依赖人工执行。

WP7 的目标是在平台内建立可评审、可执行、可审计、可复用的 Web 管理后台 UI/E2E 控制面，让测试工程师在不暴露明文账号和敏感产物的前提下，完成从场景建模、脚本包评审到单次浏览器运行和失败归因摘要的闭环。

## 2. 用户与价值

| 用户 | 典型场景 | 价值 |
|---|---|---|
| 测试工程师 | 维护登录、菜单权限、列表、表单、审批等 UI 场景并执行回归 | 降低重复手工回归成本，提升回归覆盖稳定性 |
| 自动化工程师 | 将 WP3 页面/业务流、WP5 用例沉淀为 Playwright 场景草稿并治理定位策略 | 统一脚本结构和执行边界，减少脚本散落 |
| 测试负责人 | 查看场景审批状态、运行结果、Flaky 候选和证据摘要 | 更快判断版本回归质量和自动化成熟度 |
| 项目负责人 | 关注关键后台链路是否可自动回归、是否存在高风险失败分类 | 缩短提测与回归决策时间 |
| 审计/安全人员 | 确认账号凭据、artifact 摘要和导出结果不泄露明文敏感信息 | 保持合规、安全和可追踪 |

## 3. 产品目标

| 目标 | 指标 |
|---|---|
| 建立 UI/E2E 控制面闭环 | 支持场景、脚本包、单次运行、失败分类和证据摘要主链路 |
| 复用现有跨 WP 资产 | 可基于 WP3 页面/业务流摘要和 WP5 已发布用例生成可评审场景草稿 |
| 严格控制凭据边界 | UI/E2E 不回显密码、token、cookie、`secret://` 原文 |
| 为执行编排与报告提供输入 | 可向 WP9 `UI_TEST` 和 WP10 报告输出 aggregate-only 执行摘要 |
| 具备可治理性 | 运行、审批、Flaky 标记、导出和风险提示均可审计 |

## 4. MVP 范围

| 功能 | MVP 口径 | 验收关注点 |
|---|---|---|
| 场景控制面 | 维护项目内 UI 场景、步骤模板、页面绑定、风险标签和状态流 | 只允许同项目资源引用，非 APPROVED 场景不可执行 |
| Playwright 脚本包摘要 | 生成或维护 bundle 摘要、依赖、定位策略、fixture 和 digest | 不回显完整敏感源码，不允许危险 import 和任意网络访问 |
| 人工评审 | 支持提交评审、通过、驳回、归档和意见记录 | 审批状态和原因可追踪 |
| 受控浏览器运行 | 支持手动触发单场景浏览器矩阵运行，采集运行状态、步骤结果、失败分类和产物摘要 | 默认禁用 runner，显式开启后仍受 allowlist、超时、并发和产物大小限制 |
| WP8 账号租借接入 | 运行时通过 `accountLeaseRef` 获取账号摘要与 `secretRefDigest` | 控制面不返回密码、token、cookie 或 `secret://` 原文 |
| Artifact 摘要 | 记录 screenshot/trace/runner log、`HAR`、`JUNIT_XML` 以及登录免凭据场景 `VIDEO` 的 digest、size、storageRef 和 redaction flags，并支持受权下载受控存储内的原始文件 | 下载链路只暴露受权端点和 opaque storageRef，不回显宿主机路径或存储凭据；含 `LOGIN` 场景的视频必须阻断 |
| Flaky 治理 | 标记 `FLAKY_CANDIDATE`、重试摘要和失败原因标签 | 不做无限自动重跑，不做人工验图平台 |
| 前端工作台 | 提供 `#ui-e2e` 场景、脚本包、运行、证据摘要和 Flaky 面板 | 桌面和 390px 窄屏均可完成主链路 |

## 5. 非目标

| 非目标 | 说明 |
|---|---|
| 移动端 native/hybrid 自动化 | 首期只覆盖 Web 管理后台 |
| 完整视觉回归平台 | 不做大规模像素 diff、基线图库和人工验图体系，但允许单场景截图 Diff 与阈值判定 |
| 验证码/短信/第三方登录绕过 | 不提供外部身份体系的自动旁路能力 |
| 独立分布式浏览器集群 | 不建设 Kubernetes/browser farm 多地域资源池 |
| WP9 调度生产化 | WP7 只提供 `UI_TEST` 运行契约和手动运行控制面 |
| WP10 完整报告与原始产物归档 | WP7 只输出脱敏执行摘要和产物引用 |
| 外部 Runner 回调协议扩展 | 首期不定义复杂外部回调注册与订阅能力 |

## 6. 核心对象

| 对象 | 说明 |
|---|---|
| UI 场景 `UiE2eScene` | 代表一个可评审的后台 UI 测试场景，绑定项目、应用、环境和业务目标 |
| 场景步骤 `UiE2eSceneStep` | 代表登录、导航、查询、表单、审批、导出、断言等结构化步骤 |
| 脚本包 `UiE2eBundle` | 场景对应的 Playwright bundle 摘要、依赖、静态校验结果和 digest |
| Bundle 评审 `UiE2eBundleReview` | 记录评审状态、意见、审批人与审批时间 |
| 运行 `UiE2eRun` | 一次浏览器执行记录，关联场景、bundle、账号租借摘要和执行摘要 |
| 步骤结果 `UiE2eRunStepResult` | 每一步的状态、耗时、失败分类和 artifact 引用摘要 |
| Artifact Manifest | 运行产生的 screenshot/trace/runner log、`HAR`、`JUNIT_XML` 和受控 `VIDEO` 摘要 |
| Flaky 标记 `UiE2eFlakyMark` | 对场景或运行添加波动性标记和理由 |

## 7. 主流程

### 7.1 场景建模与评审

1. 测试工程师选择项目、应用和环境。
2. 选择已有 WP3 页面/业务流摘要、WP5 已发布测试用例摘要作为场景输入。
3. 配置步骤模板、定位策略、断言摘要和风险标签。
4. 提交场景评审，审核通过后场景进入 `APPROVED`。
5. 审批未通过时保留意见和版本，允许继续修改。

### 7.2 脚本包生成与静态校验

1. 自动化工程师基于场景生成 Playwright bundle 摘要，或手工维护 bundle 元数据。
2. 平台执行静态校验，检查 TypeScript 语法、危险 import、硬编码凭据、无限等待和未受控网络访问。
3. 校验通过后 bundle 可进入评审。
4. 非 APPROVED bundle 不允许执行。

### 7.3 手动运行

1. 用户选择场景、环境和账号租借输入。
2. 平台仅接收 `accountLeaseRef`，通过 WP8 读取账号摘要与 `secretRefDigest`。
3. 受控凭据注入 adapter 在运行时完成登录注入，控制面不返回明文。
4. Runner 执行单浏览器或多浏览器矩阵场景，并返回 aggregate-only 步骤摘要、失败分类、截图 Diff 结果和 artifact manifest。
5. 运行详情页展示状态、traceId、失败分类、步骤结果和产物摘要引用。

### 7.4 Flaky 与证据治理

1. 失败运行根据 locator 失效、权限拒绝、环境超时、账号问题、数据准备异常等输出分类。
2. 用户可将运行或场景标记为 `FLAKY_CANDIDATE` 或 `CONFIRMED_FLAKY`。
3. Artifact 摘要、导出结果和前端 DOM 均通过禁止字段扫描。

### 7.5 与 WP9/WP10 的交接

1. WP9 `UI_TEST` 节点后续只消费 WP7 运行摘要、错误码和 artifact refs。
2. WP10 只消费失败分类、步骤级摘要、Flaky 标记和 artifact manifest，不读取原始明文产物。

## 8. 权限模型

| 权限 | 产品语义 |
|---|---|
| `uiE2e:read` | 查看场景、脚本包、运行和证据摘要 |
| `uiE2e:manage` | 创建、更新、归档场景和步骤模板 |
| `uiE2e:review` | 提交评审、审批或驳回 bundle/scene |
| `uiE2e:execute` | 发起运行、取消运行、查看凭据策略摘要 |
| `uiE2e:export` | 导出场景、运行或证据脱敏摘要 |
| `uiE2e:flaky` | 标记或处理 Flaky 状态 |

默认建议：

1. `SuperAdmin` 和平台管理员拥有全部权限。
2. 项目负责人和自动化负责人拥有 `read/manage/review/execute/export/flaky`。
3. 测试工程师默认拥有 `read/manage/execute/flaky`，是否拥有 `review/export` 由项目策略决定。
4. 审计人员只拥有 `read/export` 的脱敏视图。

## 9. 状态与业务规则

| 对象 | 状态 |
|---|---|
| 场景 | `DRAFT/REVIEWING/APPROVED/DISABLED/ARCHIVED` |
| Bundle | `DRAFT/STATIC_CHECK_FAILED/REVIEWING/APPROVED/REJECTED/ARCHIVED` |
| 运行 | `QUEUED/RUNNING/SUCCEEDED/FAILED/TIMEOUT/CANCELED/BLOCKED` |
| Flaky 标记 | `NONE/FLAKY_CANDIDATE/CONFIRMED_FLAKY/WAIVED` |

业务规则：

1. 场景和 bundle 必须绑定项目 scope，跨项目资源引用被阻断。
2. 非 `APPROVED` 场景或 bundle 不允许新运行。
3. Runner 默认关闭；关闭状态下 API 需返回可解释错误码，而不是 silent failure。
4. `accountLeaseRef` 必须来自同项目、同环境允许范围，且租借处于可执行状态。
5. API、前端、导出、审计、artifact 摘要均不得包含密码、token、cookie、Authorization、`secret://` 原文。
6. 原始 screenshot/trace/log 下载仅限受控存储内已通过脱敏策略的 artifact，并且必须走 `uiE2e:export` 权限校验。
7. 运行取消必须能回传给受控 runner 或返回稳定 `CANCEL_NOT_SUPPORTED`/`RUNNER_NOT_READY` 类错误。

## 10. 产品验收标准

| 编号 | 验收项 |
|---|---|
| A1 | 用户可在项目内创建、查看、编辑和归档 UI/E2E 场景。 |
| A2 | 场景可绑定 WP3 页面/业务流摘要和 WP5 已发布测试用例摘要。 |
| A3 | 场景可生成或维护 Playwright bundle 摘要，并经过静态校验与人工评审。 |
| A4 | 系统仅接受 `accountLeaseRef` 和 `secretRefDigest` 摘要，不展示凭据明文。 |
| A5 | 用户可手动触发单次浏览器运行，并查看运行状态、失败分类、traceId、浏览器矩阵摘要和 artifact 摘要。 |
| A5.1 | 用户可选填 `browsers`、`baselineRunId`、`visualMismatchThreshold` 并启用视觉回归，系统按浏览器生成截图 Diff 结果。 |
| A6 | 失败分类至少覆盖 locator 失效、权限拒绝、环境超时、账号异常、数据准备异常和 runner 未就绪。 |
| A7 | 用户可对场景或运行做 Flaky 标记，且操作有审计。 |
| A8 | 前端工作台覆盖 loading、empty、error、403、409、runner disabled 和 blocked 状态。 |
| A9 | 桌面与 390px 视口下主链路可用，无明显文本重叠和横向溢出。 |
| A10 | 向 WP9/WP10 输出的均为 aggregate-only 摘要，不泄露敏感原文。 |

## 11. 当前产品口径

截至 2026-06-20，WP7 已完成场景、bundle、run、Flaky、`#ui-e2e` 前端工作台、Playwright 子进程 runner、WP8 凭据注入和 artifact 受控下载的 P0 主链路。当前产品口径更新为：

1. Web 管理后台 UI/E2E 已形成独立工作包实现，不再由 WP8/WP9/WP10 继续代为占位。
2. P0 聚焦后台 UI 场景治理、受控执行、摘要化证据和受控 artifact 下载；按当前基线，平台级对象存储抽象已可复用，但仍不承诺完整原始产物平台、预签名/CDN/外部分发等分享链路。
3. 所有凭据边界复用 WP8，所有调度边界复用 WP9，所有报告边界复用 WP10。
4. 后续任何范围扩大，如移动端、大规模视觉回归平台、分布式浏览器池、WebSocket 实时日志、外部回调体系或预签名/CDN/多介质外部分发，都必须补充独立 PRD 和准出文档。
