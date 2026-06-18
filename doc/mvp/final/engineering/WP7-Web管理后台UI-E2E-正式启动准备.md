# WP7 Web 管理后台 UI/E2E - 正式启动准备

| 项目 | 内容 |
|---|---|
| 工作包 | WP7 Web 管理后台 UI/E2E |
| 角色产出 | 资深项目经理 |
| 文档性质 | 正式启动前范围冻结、里程碑、风险和准入清单 |
| 当前口径 | 以 `platform-api` + `portal-web` 为 UI/E2E 控制面；浏览器执行侧优先复用 Playwright 与受控 runner 契约；不抢跑 WP9 复杂调度生产化和 WP10 完整报告产物归档 |
| 版本 | v0.1 |
| 日期 | 2026-06-18 |

## 1. 启动结论

WP7 可以进入正式研发准备完成状态。当前启动包已冻结目标、范围、非目标、跨 WP 依赖、前后端边界、测试策略、里程碑、风险和回滚方式。

WP7 的核心目标是围绕通用 Web 管理后台场景，建立可治理、可复用、可审计的 UI/E2E 自动化控制面，覆盖登录、菜单权限、列表筛选、表单增删改、审批动作、导入导出和审计核验等高频回归路径，并为 WP9 `UI_TEST` 节点和 WP10 报告诊断提供统一的浏览器执行摘要。首期不建设跨地域浏览器资源池，不承诺视觉回归和验证码/短信/第三方登录绕过能力，不直接落地外部容器编排平台。

## 2. 目标

1. 支持页面对象、场景模板、脚本包、运行任务和执行摘要的最小闭环。
2. 支持基于 WP3 页面/业务流摘要和 WP5 已发布测试用例，生成可评审的 Playwright 场景草稿。
3. 支持通过 WP8 `accountLeaseRef` 获取脱敏账号摘要和 `secretRefDigest`，由受控凭据注入 adapter 完成浏览器登录。
4. 支持单次手动运行、受控 smoke、截图/视频/trace 摘要采集和失败分类。
5. 预留对 WP9 `UI_TEST` runner port 的接入契约，使后续 DAG 调度只消费 WP7 脱敏执行摘要。
6. 提供 `#ui-e2e` 前端工作台，覆盖场景、脚本包、运行、Flaky 标记和证据摘要查看。

## 3. 范围

| 范围项 | 说明 |
|---|---|
| 页面与场景资产 | 维护页面、组件区域、关键控件、业务流和通用后台模板场景摘要。 |
| Playwright 场景草稿 | 基于页面/流程/用例摘要生成或维护 Playwright 场景步骤、定位策略和断言摘要。 |
| 脚本包管理 | 生成并评审脚本包摘要、依赖、fixture、标签和风险标记；首期可不回显完整源码。 |
| 浏览器受控执行 | 受控运行 Chromium 场景，限制 baseUrl、并发、超时、artifact 大小和网络边界。 |
| 凭据注入 | 只接受 `accountLeaseRef` 和 `secretRefDigest` 摘要，真实凭据通过受控 SecretProvider adapter 注入，不在 API/前端回显明文。 |
| 证据采集 | 采集截图、视频、trace、控制台错误、步骤级摘要和失败分类；首期以摘要和对象存储引用为主。 |
| Flaky 治理 | 支持失败原因标签、重试策略摘要、等待策略校验和手工标记 `FLAKY_CANDIDATE`。 |
| 前端工作台 | 新增 UI/E2E 工作台，覆盖场景列表、脚本包、运行记录、证据摘要和风险提示。 |

## 4. 非目标

| 非目标 | 说明 | 后续承接 |
|---|---|---|
| 复杂移动端自动化 | WP7 首期聚焦 Web 管理后台，不覆盖移动端 native/hybrid。 | 后续专项 |
| 完整视觉回归平台 | 不建设大规模像素 diff、组件基线图谱和人工验图平台。 | 视觉专项 |
| 真实第三方登录绕过 | 不提供验证码、短信、人机校验和外部身份系统旁路。 | 安全专项 |
| 独立分布式浏览器资源池 | 首期不承诺 Kubernetes/browser farm 多活与跨地域调度。 | 平台化专项 |
| WP9 调度生产化 | WP7 只提供 UI runner port 和手动运行控制面，不承接 DAG/cron/webhook。 | WP9 |
| WP10 原始产物归档与完整报告 | WP7 只输出脱敏执行摘要和产物引用，不生成完整报告。 | WP10 |

## 5. 涉及模块

| 模块 | 影响 |
|---|---|
| `platform-api` | 后续新增 `uie2e` 领域模块，承载场景、脚本包、运行任务、证据摘要和 runner 契约。 |
| WP1 平台基础 | 复用项目、应用、环境、RBAC、审计、traceId、SecretProvider 和资源 scope。 |
| WP3 资产管理 | 读取页面、需求、业务流、测试用例和追踪关系摘要；不直连 WP3 表。 |
| WP5 AI 用例生成 | 读取已发布测试用例摘要和步骤结构，作为 UI 场景草稿输入。 |
| WP8 测试数据与账号池 | 通过 `accountLeaseRef` 和 runner contract 获取账号摘要与 `secretRefDigest`，不接收明文凭据。 |
| WP9 执行编排 | 暴露 `UI_TEST` runner port、运行摘要和错误码；首期可手动运行，不要求同步接通 scheduler。 |
| WP10 报告与诊断 | 输出截图/视频/trace digest、失败分类、Flaky 标记和步骤级摘要给报告工作包。 |
| `portal-web` | 后续新增 `#ui-e2e` 工作台，接入权限、场景、脚本包、运行和证据摘要视图。 |
| `portal-web` Playwright | 当前仓库已具备 Playwright 基础设施，可复用 smoke 写法和浏览器配置。 |
| `scripts` | 后续新增 WP7 quality gate、browser smoke、runner smoke、artifact redaction scan 和 DB validation 扩展。 |

## 6. 五角色启动交付

| 角色 | 本轮交付 | 结论 |
|---|---|---|
| 资深项目经理 | 本启动准备和研发任务拆解，冻结目标、范围、依赖、里程碑、风险和回滚 | 通过 |
| 资深产品经理 | 后续补 WP7 需求文档与 PRD，定义用户价值、业务流、功能范围和验收标准 | 通过 |
| 资深服务端架构师 | 后续补技术设计与接口契约，定义 DB、状态机、runner port、凭据注入边界和 API 契约 | 通过 |
| 资深前端工程师 | 后续补前端页面设计，定义 `#ui-e2e` 路由、列表、详情、运行视图和可测性 | 通过 |
| 资深质量工程师 | 后续补测试策略与用例脚本，定义浏览器 smoke、脱敏扫描、Flaky 评测和准出门禁 | 通过 |

## 7. 里程碑

| 里程碑 | 目标 | 主要交付物 | 准出标准 |
|---|---|---|---|
| M0 启动准备 | 文档、范围、任务拆解冻结 | 启动准备、研发任务拆解和后续文档任务 | 五角色评审无阻断 |
| M1 基础骨架 | 权限、DB、模块骨架、health | `uie2e` 模块、权限 seed、schema、health | OpenAPI contract、DB validation 通过 |
| M2 场景与页面模型 | 页面/业务流映射、场景 CRUD、模板摘要 | scene API、page binding、状态机 | 项目 scope 和敏感字段可测 |
| M3 脚本包与评审 | Playwright 脚本包、静态校验、评审状态流 | bundle API、lint 摘要、review API | 未审批不可运行 |
| M4 受控执行与凭据注入 | 手动运行、浏览器 runner、WP8 lease adapter、证据摘要 | run API、runner port、artifact 引用 | 不泄露明文凭据，超时/失败可测 |
| M5 Flaky 与证据治理 | 失败分类、Flaky 候选、截图/视频/trace 摘要和 redaction | classify API、artifact manifest | 证据摘要脱敏可测 |
| M6 前端闭环 | `#ui-e2e` 工作台主链路 | scene/bundle/run/workbench | Vitest、Playwright smoke、build 通过 |
| M7 质量门禁 | WP7 quality gate、browser smoke、artifact redaction scan | `scripts/wp7_quality_gate.sh` | release 模式准出规则明确 |

## 8. 启动准入清单

| 检查项 | 要求 | 状态 |
|---|---|---|
| 需求范围 | 只做 Web 管理后台 UI/E2E 控制面，不做 WP9 调度和 WP10 完整报告 | 通过 |
| 凭据边界 | 只接受 `accountLeaseRef` 和摘要，不返回密码、token、cookie、`secret://` 原文 | 通过 |
| 输入资产 | WP3 页面/业务流、WP5 已发布用例、WP8 账号租借契约、WP9 `UI_TEST` 占位可复用 | 通过 |
| 执行边界 | runner 必须受 allowlist、超时、并发、artifact 大小和网络边界限制 | 通过 |
| 证据脱敏 | 截图/视频/trace 摘要、控制台错误和 DOM 扫描必须纳入禁止字段检测 | 通过 |
| 验证入口 | 后续必须提供后端、前端、DB validation、browser smoke、runner smoke 和 quality gate | 已纳入 WP7-7.x |

## 9. 风险和回滚

| 风险 | 处置 | 回滚方式 |
|---|---|---|
| UI 自动化脆弱 | 优先使用语义定位、test id、等待策略和失败摘要分类；Flaky 场景标记审计化 | 关闭 runner execute 开关，保留场景和脚本包只读能力 |
| 凭据泄露 | 严格限制凭据注入边界、DOM 扫描和 artifact redaction scan | 回滚凭据注入 adapter，暂停 UI run 入口并轮换 secret |
| 浏览器资源失控 | 设置并发、超时、artifact 大小和场景数上限 | 降低 limits 或关闭 runner enabled |
| 与 WP9 范围混淆 | 文档明确 WP7 只提供 UI runner 和手动执行控制面 | WP9 保持 `EXECUTION_RUNNER_NOT_READY` 或对接已评审 port |
| 场景误伤真实环境 | 仅允许 non-prod allowlist baseUrl 和受控账号租借 | 禁用 baseUrl / environment，保留场景评审功能 |
| 证据产物过大或含敏感字段 | 产物摘要化、对象存储引用和 redaction scan 强校验 | 丢弃超限 artifact，只保留失败摘要和审计 |

## 10. 回滚方式

1. 文档阶段回滚本组 WP7 文档和 README 索引即可，无运行时影响。
2. 后续代码阶段优先通过配置关闭 `veri-agent.ui-e2e.enabled`、`runner-enabled`、`artifact-capture-enabled` 和前端入口。
3. 数据库迁移遵循前滚修复优先，生产环境不做破坏性 drop。
4. 已产生的运行记录、Flaky 标记和导出审计保留，不直接删除执行证据摘要。
5. 若 WP8 租借或凭据注入契约异常，WP7 回退到手工账号占位模式或禁用运行，只保留场景和脚本包评审。

## 11. 验收标准

1. 五角色文档均完成且口径一致。
2. PRD、技术契约、前端设计、测试策略和任务拆解互相引用的范围一致。
3. 明确目标、范围、非目标、跨 WP 依赖、权限、审计、凭据边界、验证入口和回滚方式。
4. 本轮只引入文档，不修改运行时代码、数据库结构和脚本。
5. 文档变更通过格式检查、`git diff --check` 和仓库交付规则要求。
