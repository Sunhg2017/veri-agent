# WP7 Web 管理后台 UI/E2E - 研发任务拆解

| 项目 | 内容 |
|---|---|
| 工作包 | WP7 Web 管理后台 UI/E2E |
| 角色产出 | 五角色联合任务拆解 |
| 文档性质 | 正式研发前可执行 Story/Task 清单 |
| 当前口径 | 以 `platform-api` + `portal-web` 为控制面，受控 Playwright runner 为执行面；P0 不建设独立浏览器资源池和完整报告平台 |
| 版本 | v0.1 |
| 日期 | 2026-06-18 |

## 1. 拆解原则

1. 先冻结场景模型、凭据边界和 runner 安全红线，再做真实浏览器执行。
2. 每个任务必须有项目 scope、权限、审计、traceId、artifact redaction policy 和失败错误码。
3. WP7 不直连 WP8/WP3/WP5/WP9 表；跨 WP 输入必须通过应用服务、导出接口或明确 port。
4. 任何账号凭据注入都不能返回前端或落审计原文，只能通过 `accountLeaseRef` 和受控 SecretProvider adapter 完成。
5. P0 不建设复杂移动端、视觉回归平台、验证码旁路、真实 OAuth/App 自动登录和 WP10 原始产物归档。

## 2. 角色分工

| 角色 | 主要负责 |
|---|---|
| 资深项目经理 | 任务排期、依赖协调、范围控制、里程碑准出、风险和回滚。 |
| 资深产品经理 | 场景模板、菜单权限、审批流、Flaky 标记语义、前端体验和验收标准。 |
| 资深服务端架构师 | DB、领域模块、API 契约、runner port、凭据注入、artifact 策略和跨 WP 集成。 |
| 资深前端工程师 | `#ui-e2e` 工作台路由、表单、列表、详情、运行视图、状态和可测性。 |
| 资深质量工程师 | 浏览器 smoke、runner smoke、脱敏扫描、Flaky 评测、DB validation 和 quality gate。 |

## 3. 总体里程碑

| 里程碑 | 目标 | 退出标准 |
|---|---|---|
| M0 启动准入 | 文档、范围、任务拆解冻结 | 五角色评审无阻断 |
| M1 基础骨架 | 权限、DB、模块骨架、health | 后端契约测试和 DB validation 通过 |
| M2 场景控制面 | 场景、页面绑定、步骤模板和状态机 | 项目 scope、归档和模板规则可测 |
| M3 脚本包评审 | Playwright bundle、静态校验和审批流 | 未审批不可运行 |
| M4 受控 Runner | 手动运行、WP8 lease adapter、浏览器执行摘要 | allowlist、timeout、artifact 限制可测 |
| M5 证据与 Flaky | 截图/视频/trace 摘要、失败分类、Flaky 候选 | 脱敏扫描和重试语义可测 |
| M6 前端闭环 | `#ui-e2e` 工作台主链路 | Vitest、Playwright、build 通过 |
| M7 质量门禁 | WP7 gate、browser smoke、runner smoke、artifact scan | release 模式准出规则明确 |

## 4. Epic 0：启动准入与样本准备

| Story | 优先级 | 负责人 | 任务 | 验收 | 验证 |
|---|---|---|---|---|---|
| WP7-0.1 范围冻结 | P0 | 项目经理 | 确认 WP7 只覆盖 Web 管理后台 UI/E2E 控制面，明确与 WP6/WP8/WP9/WP10 的边界 | 启动文档、PRD、技术设计、前端设计、测试策略和本拆解一致 | 文档评审 |
| WP7-0.2 场景样本清单 | P0 | 质量工程师、产品经理 | 准备登录、菜单权限、列表查询、表单新增/编辑、审批、导入导出、审计日志 fixture 场景 | 后续 smoke 可直接落地 | fixture 清单 |
| WP7-0.3 凭据与环境红线 | P0 | 服务端架构师、质量工程师 | 冻结 `accountLeaseRef`、baseUrl allowlist、artifact redaction、DOM 扫描和禁止字段策略 | 红线被 PRD/技术/测试引用 | 文档评审 |

## 5. Epic 1：权限、DB 和模块骨架

| Story | 优先级 | 负责人 | 任务 | 验收 | 验证 |
|---|---|---|---|---|---|
| WP7-1.1 权限点 seed | P0 | 服务端架构师 | 新增 `uiE2e:read/manage/review/execute/export/flaky` 权限和角色映射 | 默认角色符合 PRD | DB validation、权限测试 |
| WP7-1.2 审计事件字典 | P0 | 服务端架构师、质量工程师 | 定义场景、脚本包、运行、artifact、Flaky 标记和导出审计事件 | payload 只含摘要、digest 和计数 | 审计单测 |
| WP7-1.3 DB schema | P0 | 服务端架构师 | 新增 scene、scene_step、bundle、bundle_review、run、run_step_result、artifact_manifest、flaky_mark 表 | 约束、索引、注释完整 | DB validation |
| WP7-1.4 模块骨架 | P0 | 服务端架构师 | 新建 `uie2e` api/application/domain/infrastructure/config 包 | 不破坏现有模块边界 | `mvn -B -pl platform-api test` |
| WP7-1.5 Health API | P0 | 服务端架构师 | `GET /api/v1/ui-e2e/health` 输出 runner、artifact、credential、allowlist 和当前范围摘要 | 不泄露 secret、cookie 或真实目标地址 | Controller test |

## 6. Epic 2：场景、页面和步骤控制面

| Story | 优先级 | 负责人 | 任务 | 验收 | 验证 |
|---|---|---|---|---|---|
| WP7-2.1 Scene CRUD | P0 | 服务端架构师 | 创建、列表、详情、更新、归档场景 | project scope 和状态保护正确 | Controller test |
| WP7-2.2 Page binding | P0 | 服务端架构师、产品经理 | 绑定 WP3 页面/业务流摘要、应用、环境和角色标签 | 不直连 WP3 表 | Contract test |
| WP7-2.3 Step template model | P0 | 服务端架构师 | 定义登录、导航、查询、表单、审批、导出、断言等步骤模板 | 模板参数和危险字段校验稳定 | Unit test |
| WP7-2.4 Selector strategy | P0 | 服务端架构师、前端工程师 | 保存 locator 策略、testId、文本回退和等待策略摘要 | 禁止保存敏感 DOM 原文快照 | Service test |
| WP7-2.5 Scene state machine | P0 | 服务端架构师 | `DRAFT/REVIEWING/APPROVED/DISABLED/ARCHIVED` 状态保护 | 非 APPROVED 不可执行 | Service test |

## 7. Epic 3：Playwright 脚本包与评审

| Story | 优先级 | 负责人 | 任务 | 验收 | 验证 |
|---|---|---|---|---|---|
| WP7-3.1 Bundle generator | P0 | 服务端架构师 | 生成 Playwright bundle 摘要、依赖、fixtures、spec file 和 digest | 不回显完整敏感源码 | Service test |
| WP7-3.2 Static checks | P0 | 质量工程师、服务端架构师 | 校验 TypeScript 语法、危险 import、未受控网络访问、硬编码凭据和无限等待 | 失败进入 `SCRIPT_STATIC_CHECK_FAILED` | Fixture test |
| WP7-3.3 Review workflow | P0 | 服务端架构师 | submit-review/approve/reject/archive API 和状态流 | 驳回原因必填，审批写审计 | Controller test |
| WP7-3.4 Common template packs | P1 | 产品经理、前端工程师 | 建立登录、菜单权限、列表、表单、审批模板包 | 可被场景复用 | Service test |
| WP7-3.5 Bundle export summary | P1 | 服务端架构师 | 导出脚本包脱敏摘要和 redaction policy | 不含 secret、cookie 或完整业务数据 | Security test |

## 8. Epic 4：受控执行、WP8 凭据注入和 WP9 runner port

| Story | 优先级 | 负责人 | 任务 | 验收 | 验证 |
|---|---|---|---|---|---|
| WP7-4.1 Runner port | P0 | 服务端架构师 | 定义 `UiE2eRunnerPort` validate/run/cancel 契约 | 默认 Disabled adapter 安全返回 | Unit test |
| WP7-4.2 Runner config | P0 | 服务端架构师 | 配置 runner-enabled、timeout、maxScenarios、artifact limits、baseUrl allowlist、browser type | 默认 disabled，显式开启才运行 | Config test |
| WP7-4.3 WP8 lease adapter | P0 | 服务端架构师 | 通过 `TestDataCrossWpReferenceService` 读取 runner account contract | 只接收账号摘要和 `secretRefDigest` | Contract test |
| WP7-4.4 Credential injection adapter | P0 | 服务端架构师、质量工程师 | 以 `accountLeaseRef` 为句柄完成运行时凭据注入，不向控制面返回明文 | 禁止返回密码、token、cookie、`secret://` | Security test |
| WP7-4.5 Run API | P0 | 服务端架构师 | `POST /api/v1/ui-e2e/runs`、`GET /runs/{id}`、`POST /runs/{id}/cancel` | requestKey 幂等，runner disabled/blocked 可解释 | Controller test |
| WP7-4.6 WP9 runner contract | P1 | 服务端架构师 | 输出给 WP9 `UI_TEST` 的摘要字段、错误码和 artifact refs | `EXECUTION_RUNNER_NOT_READY` 可平滑切换 | Contract test |

## 9. Epic 5：证据采集、失败分类和 Flaky 治理

| Story | 优先级 | 负责人 | 任务 | 验收 | 验证 |
|---|---|---|---|---|---|
| WP7-5.1 Artifact manifest | P0 | 服务端架构师 | 固化 screenshot/video/trace/log 摘要 schema、digest、size、storageRef 和 redaction flags | 不保存未经处理的敏感正文 | Unit test |
| WP7-5.2 Failure classifier | P0 | 服务端架构师、产品经理 | 按 locator 失效、权限拒绝、环境超时、数据准备异常、凭据注入失败等分类 | 无模型也可用 | Unit test |
| WP7-5.3 Flaky mark API | P0 | 服务端架构师 | 支持 `NONE/FLAKY_CANDIDATE/CONFIRMED_FLAKY/WAIVED` 标记和理由 | 变更可审计 | Service test |
| WP7-5.4 Retry summary policy | P1 | 服务端架构师、质量工程师 | 定义单次重试和等待策略摘要，不做无限自动重跑 | 重试语义清晰 | Runner smoke |
| WP7-5.5 Artifact redaction scan | P0 | 质量工程师 | 扫描 artifact 摘要、导出结果和前端 DOM 禁止字段 | 命中敏感字段阻断 | Security test |

## 10. Epic 6：前端工作台

| Story | 优先级 | 负责人 | 任务 | 验收 | 验证 |
|---|---|---|---|---|---|
| WP7-6.1 API client | P0 | 前端工程师 | 新增 `portal-web/src/api/uiE2e.ts` 和 normalize helper | snake_case/camelCase 兼容 | Vitest |
| WP7-6.2 权限入口 | P0 | 前端工程师 | 新增 `#ui-e2e` 导航入口和权限判断 | 无 read 权限不显示 | Vitest |
| WP7-6.3 Scene workbench | P0 | 前端工程师 | 场景列表、筛选、详情、步骤模板、审批入口 | loading/empty/error 完整 | Component/helper test |
| WP7-6.4 Bundle/review view | P0 | 前端工程师 | 脚本包摘要、静态校验、评审状态、模板包查看 | 不展示禁止字段 | Vitest |
| WP7-6.5 Run view | P0 | 前端工程师 | 环境、baseUrl、租借摘要、运行状态、失败分类、artifact 摘要 | runner disabled / blocked 可解释 | Vitest |
| WP7-6.6 Flaky view | P1 | 前端工程师 | Flaky 标记、理由、风险提示和筛选 | 审计信息可见 | Vitest |
| WP7-6.7 Responsive smoke | P1 | 前端工程师、质量工程师 | 桌面和 390px 视口主链路 | 无横向溢出，文本不重叠 | Playwright smoke |

## 11. Epic 7：质量门禁和发布准出

| Story | 优先级 | 负责人 | 任务 | 验收 | 验证 |
|---|---|---|---|---|---|
| WP7-7.1 后端测试 | P0 | 质量工程师、服务端架构师 | scene、bundle、run、artifact、permission、audit、lease adapter 测试 | 主路径和错误路径覆盖 | `mvn -B -pl platform-api test` |
| WP7-7.2 前端测试 | P0 | 质量工程师、前端工程师 | api helper、权限、场景视图、运行视图、Flaky 视图 | 稳定通过 | `cd portal-web && npm test` |
| WP7-7.3 DB validation | P0 | 质量工程师、服务端架构师 | WP7 表、约束、索引、权限纳入 validation | 临时库迁移和复跑通过 | DB validation |
| WP7-7.4 Browser smoke | P0 | 质量工程师 | managed smoke 覆盖登录、导航、列表、表单、审批、导出和审计样本 | 默认不访问外部网络 | `scripts/wp7_browser_smoke.sh` |
| WP7-7.5 Runner smoke | P0 | 质量工程师 | 显式开启 runner，覆盖 pass/fail/timeout/cancel/credential injection blocked | release gate 显式启用 | `scripts/wp7_runner_smoke.sh` |
| WP7-7.6 Artifact redaction eval | P0 | 质量工程师、服务端架构师 | 扫描 screenshot/video/trace/log 摘要、导出和 DOM 禁止字段 | 命中 secret/token/cookie/raw prompt 等阻断 | `scripts/wp7_artifact_redaction_eval.sh` |
| WP7-7.7 Quality gate | P0 | 质量工程师 | 新增 `scripts/wp7_quality_gate.sh` 聚合后端、前端、构建、DB、browser smoke、runner smoke 和 artifact scan | release 模式准出规则明确 | `scripts/wp7_quality_gate.sh` |

## 12. Epic 8：文档和交付

| Story | 优先级 | 负责人 | 任务 | 验收 | 验证 |
|---|---|---|---|---|---|
| WP7-8.1 需求文档与 PRD | P0 | 产品经理 | 补齐用户价值、角色、主流程、非目标和验收标准 | 与启动文档一致 | 文档评审 |
| WP7-8.2 技术设计与接口契约 | P0 | 服务端架构师 | 补齐 DB、状态机、runner port、WP8/WP9/WP10 契约、错误码和安全边界 | 可直接指导实现 | 文档评审 |
| WP7-8.3 前端页面设计 | P0 | 前端工程师 | 补齐 `#ui-e2e` 页面、交互、权限、响应式和可测性设计 | 与 PRD/API 契约一致 | 文档评审 |
| WP7-8.4 测试策略与用例脚本 | P0 | 质量工程师 | 补齐浏览器 smoke、runner smoke、DOM/artifact 扫描和 quality gate 策略 | 发布准出路径明确 | 文档评审 |
| WP7-8.5 Runner Runbook | P1 | 质量工程师、服务端架构师 | 编写 runner 开关、allowlist、凭据注入、artifact 排障和回滚 | 运维可按步骤处理 | Runbook 评审 |
| WP7-8.6 发布准出说明 | P0 | 项目经理、质量工程师 | 记录验证命令、跳过项、风险、回滚和五角色准出 | 符合仓库模板 | 发布说明评审 |
| WP7-8.7 剩余工作盘点 | P1 | 项目经理、产品经理 | 区分当前完成项和后续专项，如视觉回归、移动端、第三方登录和浏览器池 | 不把后续专项误标完成 | 文档评审 |

## 13. P0 完成定义

1. 可创建并审批 Web 管理后台 UI/E2E 场景。
2. 可生成并评审 Playwright 脚本包摘要，静态校验失败可解释。
3. 可基于 `accountLeaseRef` 受控执行单次浏览器运行，不回显任何明文凭据。
4. 可采集截图/视频/trace 等 artifact 摘要，并完成失败分类与 Flaky 标记。
5. 可通过 WP9 runner contract 输出 `UI_TEST` 所需脱敏执行摘要。
6. 前端工作台覆盖场景、脚本包、运行、artifact 摘要和 Flaky 视图。
7. `mvn -B -pl platform-api test`、`cd portal-web && npm test`、`cd portal-web && npm run build`、DB validation 和 WP7 quality gate 通过。

## 14. 推荐实施顺序

1. 先做 WP7-8.1 到 WP7-8.4，补齐 PRD、技术设计、前端设计和测试策略四份配套文档。
2. 再做 WP7-1.x，确保权限、DB、health 和凭据红线稳定。
3. 再做 WP7-2.x，完成场景、页面和步骤控制面。
4. 再做 WP7-3.x，完成脚本包生成、静态校验和评审闭环。
5. 再做 WP7-4.x，接通受控 runner、WP8 lease adapter 和手动运行。
6. 再做 WP7-5.x，补齐 artifact manifest、失败分类和 Flaky 治理。
7. 前端 WP7-6.x 与后端契约并行推进，但所有运行、审批、导出按钮必须以后端权限和状态为准。
8. WP7-7.x 从第一轮迁移开始同步建设，避免最后补门禁。

## 15. 当前启动结论

截至 2026-06-20，WP7 已完成文档启动准备、PRD、技术设计、前端页面设计、测试策略、Runner Runbook、发布准出说明和剩余工作盘点，并落地 scene/bundle/run/flaky 控制面、`#ui-e2e` 前端工作台、Playwright 子进程真实浏览器 runner、WP8 凭据注入 adapter、artifact 受控存储/下载、`HAR/JUnit XML` 真实采集、登录免凭据场景视频采集、浏览器 smoke、runner smoke 和 quality gate。按当前基线，WP7 artifact 存储后续已接入平台级统一存储抽象与默认 OSS provider；当前范围无剩余 P0 主链路缺口，后续专项主要为实时日志推送、容器化隔离执行、更细粒度的视频脱敏能力、预签名 URL/CDN/外部分发、多场景批量运行和浏览器池。这些都应按本拆解后续里程碑继续推进，而不应再由 WP8/WP9/WP10 继续占位。
