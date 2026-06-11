# WP6 OpenAPI 接口自动化 - 研发任务拆解

| 项目 | 内容 |
|---|---|
| 工作包 | WP6 OpenAPI 接口自动化 |
| 角色产出 | 五角色联合任务拆解 |
| 文档性质 | 正式研发前可执行 Story/Task 清单 |
| 当前口径 | 以 `platform-api` + `portal-web` 为控制面，runner 通过受控端口接入；不抢跑 WP9 调度和 WP10 完整诊断报告 |
| 版本 | v0.3 |
| 日期 | 2026-06-12 |

## 1. 拆解原则

1. 先控制面、后执行器；先导入和 diff、后生成和运行。
2. 每个任务必须有项目 scope、权限、审计、traceId 和脱敏口径。
3. 任何模型生成必须通过 WP2；任何 API 资产写入必须通过 WP3 应用服务。
4. runner 默认关闭，只有显式配置和 allowlist 通过后才允许运行。
5. P0 不建设 WP9 调度、WP10 完整报告、WP8 账号池和 WP7 UI 自动化。

## 2. 角色分工

| 角色 | 主要负责 |
|---|---|
| 资深项目经理 | 任务排期、依赖协调、里程碑准出、风险和回滚 |
| 资深产品经理 | 用户流程、权限口径、字段含义、验收标准和非目标 |
| 资深服务端架构师 | DB、领域模块、API 契约、runner 端口、WP2/WP3/WP5 集成和安全边界 |
| 资深前端工程师 | 工作台路由、页面组件、状态、权限、表单和可测性 |
| 资深质量工程师 | fixture、测试矩阵、质量门禁、smoke、DB validation 和发布准出 |

## 3. 总体里程碑

| 里程碑 | 目标 | 退出标准 |
|---|---|---|
| M0 启动准入 | 文档、范围、任务拆解冻结 | 五角色评审无阻断 |
| M1 基础骨架 | 权限、DB、模块骨架、health | 后端契约测试和 DB validation 通过 |
| M2 OpenAPI 导入 | 规格导入、解析、脱敏、endpoint snapshot | JSON/YAML fixture 和非法样本测试通过 |
| M3 API diff/sync | 与 WP3 API 资产对齐并可确认同步 | 新增/变更/冲突/跳过路径可审计 |
| M4 用例与脚本生成 | 生成接口自动化用例和 Pytest 脚本包 | WP2 成功、阻断、fallback 均可追踪 |
| M5 Runner 试运行 | 手动受控运行和结果采集 | disabled/allowlist/timeout/fail/pass 覆盖 |
| M6 前端闭环 | 页面完成导入、diff、生成、审查、运行、结果 | 前端测试和构建通过 |
| M7 质量门禁 | WP6 gate、DB validation、fixture smoke | 发布模式准出规则明确 |

## 4. Epic 0：启动准入与样本准备

| Story | 优先级 | 负责人 | 任务 | 验收 | 验证 |
|---|---|---|---|---|---|
| WP6-0.1 范围冻结 | P0 | 项目经理 | 确认 WP6 P0 只覆盖 OpenAPI 接口自动化；明确 WP7/WP8/WP9/WP10 边界 | 启动文档、PRD、技术设计、前端设计、测试策略和本拆解一致 | 文档评审 |
| WP6-0.2 样本收集 | P0 | 质量工程师 | 准备最小 JSON、YAML、diff v1/v2、敏感样例、非法 schema、超大 schema fixture | fixture 覆盖 parser、脱敏、diff 和错误路径 | 后续 parser 单测 |
| WP6-0.3 真实样本脱敏 | P1 | 产品经理、质量工程师 | 从真实项目收集 1 份脱敏 OpenAPI 和 1 个本地 smoke 服务 baseUrl | 真实样本不含密钥、域名可配置、接口不产生破坏性写入 | 人工复核 |

## 5. Epic 1：权限、DB 和模块骨架

| Story | 优先级 | 负责人 | 任务 | 验收 | 验证 |
|---|---|---|---|---|---|
| WP6-1.1 权限点 seed | P0 | 服务端架构师 | 新增 `apiAutomation:read/import/generate/review/execute/export` 权限和默认角色映射 | SuperAdmin/PlatformAdmin/ProjectOwner/AppOwner/Tester/Auditor 权限符合 PRD | DB validation、权限测试 |
| WP6-1.2 审计事件字典 | P0 | 服务端架构师、质量工程师 | 定义 spec、parse、sync、generation、bundle、review、run、export 审计事件 | 事件名、资源类型、项目 scope 和 traceId 口径统一 | 审计单测 |
| WP6-1.3 DB schema | P0 | 服务端架构师 | 新增 spec、endpoint snapshot、generation task、case、script bundle、run、run result 表 | 约束、索引、注释、runtime role 权限完整 | `run_wp1_db_validation.sh` |
| WP6-1.4 模块骨架 | P0 | 服务端架构师 | 新建 `apiautomation` api/application/domain/infrastructure/config 包 | 不破坏现有模块边界；Java 文件规模满足架构门禁 | `mvn -B -pl platform-api test` |
| WP6-1.5 Health API | P0 | 服务端架构师 | `GET /api/v1/api-automation/health` 输出 runner、limits、prompt、export redline | 只输出固定策略和数字，不泄露配置 secret | Controller test |

## 6. Epic 2：OpenAPI 导入、解析和脱敏

| Story | 优先级 | 负责人 | 任务 | 验收 | 验证 |
|---|---|---|---|---|---|
| WP6-2.1 导入 API | P0 | 服务端架构师 | `POST /specs` 支持 TEXT/UPLOAD/URL 元数据；P0 可先禁用 URL 拉取或只记录 URL | 文件大小、sourceType、项目权限校验完整 | Controller test |
| WP6-2.2 Parser | P0 | 服务端架构师 | 支持 OpenAPI 3.x JSON/YAML，抽取 method/path/operationId/tags/params/requestBody/responses/schema digest | 非法 schema 返回稳定错误码，不抛裸异常 | Parser fixture test |
| WP6-2.3 脱敏与裁剪 | P0 | 服务端架构师、质量工程师 | 对 Authorization、apiKey、token、cookie、password、secret 示例值脱敏；超长 schema 摘要化 | 入库和响应不含敏感示例值 | 敏感样例测试 |
| WP6-2.4 Endpoint snapshot | P0 | 服务端架构师 | 解析后写 endpoint snapshot，记录 schemaDigest 和 diff 初始状态 | 重复解析幂等，不重复污染 snapshot | Repository test |
| WP6-2.5 解析状态机 | P0 | 服务端架构师 | `UPLOADED/PARSING/PARSED/PARSE_FAILED/ARCHIVED` 状态保护 | 失败可重试，归档不可再 sync | Service test |

## 7. Epic 3：WP3 API diff 和同步

| Story | 优先级 | 负责人 | 任务 | 验收 | 验证 |
|---|---|---|---|---|---|
| WP6-3.1 API 匹配规则 | P0 | 服务端架构师、产品经理 | 按 project + serviceName + method + path 匹配 WP3 API，operationId 作为辅助 | 匹配规则文档化，冲突需要人工确认 | Service test |
| WP6-3.2 Diff 查询 | P0 | 服务端架构师 | `GET /specs/{id}/diff` 返回 NEW/CHANGED/MATCHED/CONFLICT/SKIPPED | 分页、筛选、traceId 和项目 scope 正确 | Controller test |
| WP6-3.3 Sync preview | P0 | 服务端架构师 | 生成待创建/待更新 API 资产 payload 摘要 | 不自动写 WP3，不展示敏感 schema 明细 | Service test |
| WP6-3.4 Confirm sync | P0 | 服务端架构师 | `POST /specs/{id}/sync` 通过 WP3 应用服务创建/更新 API | 部分成功有明细，失败不影响已成功项审计 | Integration test |
| WP6-3.5 追踪关系 | P1 | 服务端架构师 | 为 synced endpoint 和后续 automation case 预留 asset link 关系 | CASE/SCRIPT 关系不破坏 WP3 现有约束 | DB validation |

## 8. Epic 4：接口自动化用例生成

| Story | 优先级 | 负责人 | 任务 | 验收 | 验证 |
|---|---|---|---|---|---|
| WP6-4.1 生成任务 API | P0 | 服务端架构师 | `POST /generation-tasks` 接收 spec、assetApiIds、assetTestCaseIds、coverageTypes、generationMode | requestDigest 幂等，不同 payload 复用 key 返回冲突 | Controller test |
| WP6-4.2 WP5 输入适配 | P0 | 服务端架构师 | 读取 WP5 已发布到 WP3 的测试用例或 WP3 case 摘要作为生成输入 | 不读取 WP5 候选正文和评审评论明细 | Service test |
| WP6-4.3 WP2 Prompt | P0 | 服务端架构师 | 新增 `wp6-api-automation-v1` Prompt seed，模型输出结构化自动化用例 | 保存 modelInvocationId/promptVersion/fallbackUsed | Model integration test |
| WP6-4.4 输出校验 | P0 | 质量工程师、服务端架构师 | 校验 title、method、path、assertions、requestTemplate、expectedStatus | 非法模型输出阻断或 fallback | Parser/schema test |
| WP6-4.5 确定性 fallback | P0 | 服务端架构师 | 基于 OpenAPI response code 和参数生成 smoke/negative 模板 | fallback 候选明确标记，不伪装模型输出 | Service test |
| WP6-4.6 生成审计 | P0 | 服务端架构师 | 记录输入 digest、coverage、apiCount、caseCount、模型/fallback 结果 | 审计不含 schema 明细、请求正文或 secret | Audit test |

## 9. Epic 5：脚本包生成、静态校验和评审

| Story | 优先级 | 负责人 | 任务 | 验收 | 验证 |
|---|---|---|---|---|---|
| WP6-5.1 脚本包模型 | P0 | 服务端架构师 | 生成 Pytest 文件树摘要、依赖清单、bundleDigest、fileCount | 不直接暴露密钥和环境变量值 | Service test |
| WP6-5.2 脚本模板 | P0 | 服务端架构师 | 产出 requests/httpx 风格模板，统一 baseUrl、headers、assertion helper | 生成脚本可静态解析 | Static check test |
| WP6-5.3 静态校验 | P0 | 质量工程师、服务端架构师 | 校验 Python 语法、禁止危险 import、禁止硬编码 secret | 失败进入 `SCRIPT_STATIC_CHECK_FAILED` | Fixture test |
| WP6-5.4 评审状态 | P0 | 服务端架构师 | `DRAFT/REVIEWING/APPROVED/REJECTED/ARCHIVED` 状态流 | 未审批默认不可 release gate 执行 | Service test |
| WP6-5.5 评审 API | P0 | 服务端架构师 | submit-review/approve/reject 接口和备注 | 驳回原因必填，审批写审计 | Controller test |

## 10. Epic 6：受控 Runner 和运行结果

| Story | 优先级 | 负责人 | 任务 | 验收 | 验证 |
|---|---|---|---|---|---|
| WP6-6.1 Runner port | P0 | 服务端架构师 | 定义 `ApiAutomationRunnerPort` 的 validate/run/cancel 契约 | 默认 Noop/Disabled adapter 安全返回 | Unit test |
| WP6-6.2 Runner 配置 | P0 | 服务端架构师 | runner-enabled、timeout、maxCases、allowlist、artifact size 配置 | 默认 disabled，开启需显式配置 | Config test |
| WP6-6.3 Run API | P0 | 服务端架构师 | `POST /runs` 创建手动运行任务，校验 bundle/environment/baseUrl/caseIds | 未授权或 runner disabled 返回稳定错误 | Controller test |
| WP6-6.4 Allowlist | P0 | 服务端架构师、质量工程师 | baseUrl 必须匹配 allowlist，阻断 localhost metadata 和未授权域名 | `RUNNER_TARGET_BLOCKED` 可测 | Security test |
| WP6-6.5 结果采集 | P0 | 服务端架构师 | 保存用例级 status、duration、assertionSummary、errorCode、errorSummary | 不保存完整 request/response/stdout/stderr | Repository test |
| WP6-6.6 Timeout/cancel | P1 | 服务端架构师 | 超时标记 `TIMEOUT`，取消为尽力取消 | 状态收敛，重复事件幂等 | Runner smoke |

## 11. Epic 7：前端工作台

| Story | 优先级 | 负责人 | 任务 | 验收 | 验证 |
|---|---|---|---|---|---|
| WP6-7.1 API client | P0 | 前端工程师 | 新增 `portal-web/src/api/apiAutomation.ts` 和 normalize helper | snake_case/camelCase 兼容 | Vitest |
| WP6-7.2 权限入口 | P0 | 前端工程师 | 新增 `#api-automation` 入口和权限判断 | 无 read 权限不显示入口，直达可见无权限态 | Vitest |
| WP6-7.3 规格面板 | P0 | 前端工程师 | 导入表单、规格列表、解析状态和错误 traceId | loading/empty/error 完整 | Component/helper test |
| WP6-7.4 Diff 面板 | P0 | 前端工程师 | NEW/CHANGED/MATCHED/CONFLICT/SKIPPED 分类、筛选、sync 确认 | 部分成功摘要清晰 | Component/helper test |
| WP6-7.5 生成面板 | P0 | 前端工程师 | API 范围、WP5 用例、覆盖类型、生成模式和任务状态 | payload 不含敏感正文 | Vitest |
| WP6-7.6 脚本包评审 | P0 | 前端工程师 | 静态校验、文件摘要、提交评审、审批、驳回 | 状态和权限按钮正确 | Vitest |
| WP6-7.7 运行面板 | P0 | 前端工程师 | 环境、baseUrl、secretRef 引用、caseIds、运行状态 | runner disabled 显示策略摘要 | Vitest |
| WP6-7.8 结果摘要 | P0 | 前端工程师 | pass/fail/skip/error、耗时、断言摘要和脱敏错误 | 不展示禁止字段 | Vitest |
| WP6-7.9 响应式 | P1 | 前端工程师 | 窄屏表格处理、长 path 换行、按钮不重叠 | 桌面/移动截图无明显重叠 | Playwright smoke |

## 12. Epic 8：质量门禁和发布准出

| Story | 优先级 | 负责人 | 任务 | 验收 | 验证 |
|---|---|---|---|---|---|
| WP6-8.1 后端测试 | P0 | 质量工程师、服务端架构师 | parser、service、controller、permission、audit、runner disabled 单测 | 主路径和错误路径覆盖 | `mvn -B -pl platform-api test` |
| WP6-8.2 前端测试 | P0 | 质量工程师、前端工程师 | api helper、权限、payload、结果摘要、状态 helper | 测试稳定通过 | `cd portal-web && npm test` |
| WP6-8.3 DB validation | P0 | 质量工程师、服务端架构师 | WP6 表/约束/索引/注释/runtime role 权限纳入 validation | 临时库迁移和复跑通过 | `bash db/validation/run_wp1_db_validation.sh` |
| WP6-8.4 Fixture smoke | P0 | 质量工程师 | OpenAPI fixture parser smoke 脚本 | JSON/YAML/非法/敏感/超大样本覆盖 | `bash scripts/wp6_openapi_fixture_smoke.sh` |
| WP6-8.5 Quality gate | P0 | 质量工程师 | 新增 `scripts/wp6_quality_gate.sh` 聚合后端、前端、构建、DB 和 fixture smoke | 日常默认不启 runner；发布模式显式要求 runner 策略 | `bash scripts/wp6_quality_gate.sh` |
| WP6-8.6 Runner smoke | P1 | 质量工程师 | 显式开启 runner，对 mock HTTP 服务执行 pass/fail/timeout | 无真实服务时可 external/managed 两种模式 | `WP6_RUNNER_SMOKE=1 ...` |

## 13. Epic 9：文档、Runbook 和交付说明

| Story | 优先级 | 负责人 | 任务 | 验收 | 验证 |
|---|---|---|---|---|---|
| WP6-9.1 API 契约更新 | P0 | 服务端架构师 | 技术设计随实现更新真实路径、字段、错误码 | 文档和 OpenAPI 测试一致 | 文档评审 |
| WP6-9.2 Runner Runbook | P0 | 质量工程师、服务端架构师 | 编写 runner 开关、allowlist、secretRef、timeout、日志排障 | 运维可按步骤开启/关闭 runner | Runbook 评审 |
| WP6-9.3 发布准出说明 | P0 | 项目经理、质量工程师 | 记录验证命令、跳过项、风险、回滚和远端分支 | 符合仓库交付模板 | PR/交付检查 |
| WP6-9.4 前端操作说明 | P1 | 产品经理、前端工程师 | 说明导入、diff、生成、评审、运行和结果解释 | 用户无需 curl 完成主链路 | 产品验收 |

## 14. P0 完成定义

1. OpenAPI JSON/YAML fixture 可导入、解析、脱敏和生成 endpoint snapshot。
2. API diff 可展示新增、变更、匹配、冲突和跳过；sync 通过 WP3 应用服务并写审计。
3. 接口自动化生成任务可基于 API 资产和 WP5/WP3 用例生成自动化用例草稿。
4. 脚本包可生成、静态校验、提交评审、审批和驳回。
5. runner 默认关闭；显式开启时受 allowlist、timeout、case limit 和 secretRef 约束。
6. 运行结果只保存和展示白名单摘要，不泄露请求响应正文或敏感值。
7. 前端工作台覆盖导入、diff、生成、脚本包、运行和结果摘要。
8. `mvn -B -pl platform-api test`、`cd portal-web && npm test`、`cd portal-web && npm run build`、DB validation 和 WP6 quality gate 通过。

## 15. 推荐实施顺序

1. 先做 WP6-1.1 至 WP6-1.5，确保权限、DB 和 health 稳定。
2. 再做 WP6-2.x 和 WP6-3.x，完成 OpenAPI 到 WP3 API 资产的控制面闭环。
3. 再做 WP6-4.x 和 WP6-5.x，完成生成与脚本包评审闭环。
4. 再做 WP6-6.x，接入 runner disabled/allowlist/结果采集，最后开启真实 runner smoke。
5. 前端 WP6-7.x 与后端契约并行推进，但 sync、生成、运行按钮必须以后端权限和状态为准。
6. WP6-8.x 从第一轮迁移开始同步建设，避免最后补门禁。

## 16. 当前推进状态（2026-06-12）

当前完成 M1/M2/M3 控制面和 M4 fallback 生成任务 + WP3 用例输入摘要切片，退出标准以“OpenAPI 规格可导入、解析、脱敏、查询、生成 endpoint snapshot，可对 WP3 API 资产 diff/sync，并可基于已同步 API 和已发布 WP3 测试用例摘要生成确定性 fallback 自动化用例草稿”为准。

| Story | 状态 | 说明 |
|---|---|---|
| WP6-1.1 权限点 seed | 已完成 | 新增 `apiAutomation:read/import/generate/review/execute/export`，DB seed 和 local/test 角色目录同步。 |
| WP6-1.2 审计事件字典 | 部分完成 | 实现 `api_automation.spec.parsed`、`api_automation.spec.parse_failed`、`api_automation.api_diffed`、`api_automation.api_synced`、`api_automation.generation.created` 写审计；bundle/run/export 事件待后续能力落地。 |
| WP6-1.3 DB schema | 部分完成 | 已建 spec、endpoint snapshot、generation task、automation case 表并纳入 validation；script bundle、run、run result 表待 M5-M6。 |
| WP6-1.4 模块骨架 | 已完成 | 新增 `apiautomation` controller/application/domain/infrastructure/config 包。 |
| WP6-1.5 Health API | 已完成 | `GET /api/v1/api-automation/health` 公开返回配置边界、runner disabled 策略和当前功能边界。 |
| WP6-2.1 导入 API | 已完成 | `POST /specs` 支持 TEXT 内容导入；URL P0 仅保存脱敏 sourceRef，不主动拉取。 |
| WP6-2.2 Parser | 已完成 | 支持 OpenAPI 3.x JSON/YAML，非法样本返回 `OPENAPI_PARSE_FAILED`。 |
| WP6-2.3 脱敏与裁剪 | 已完成 | 对敏感字段名和值脱敏，响应不返回原始规格正文。 |
| WP6-2.4 Endpoint snapshot | 已完成 | 解析后写 endpoint snapshot，包含 schemaDigest 和 `UNKNOWN` diff 初始状态。 |
| WP6-2.5 解析状态机 | 部分完成 | 已支持 `UPLOADED/PARSING/PARSED/PARSE_FAILED/ARCHIVED` 状态约束和重解析；失败后不持久化原始未脱敏内容。 |
| WP6-3.1 API 匹配规则 | 已完成 | 当前按 project + method + path 匹配 WP3 API，schemaDigest 判断 MATCHED/CHANGED；serviceName 受 WP3 API 领域模型限制暂不作为强匹配键。 |
| WP6-3.2 Diff 查询 | 已完成 | 新增 `GET /specs/{id}/diff`，返回 `NEW/CHANGED/MATCHED/CONFLICT/SKIPPED`、assetApiId 和 diffSummary。 |
| WP6-3.3 Sync preview | 部分完成 | diffSummary 已提供 create/update/review/skip 摘要；独立 preview API 未拆出，当前由 diff 响应承载。 |
| WP6-3.4 Confirm sync | 已完成 | 新增 `POST /specs/{id}/sync`，通过 WP3 `AssetApiService` 创建/更新 API 资产，逐项容错并写 `api_automation.api_synced` 审计。 |
| WP6-3.5 追踪关系 | 部分完成 | endpoint snapshot 已持久化 `asset_api_id` 和 sync 证据；automation case 已关联 spec、endpoint snapshot、assetApiId；script 关系待 M5。 |
| WP6-4.1 生成任务 API | 已完成 | 新增 `POST /generation-tasks` 和 `GET /generation-tasks/{id}`，支持 project/spec/assetApiIds/assetTestCaseIds/coverageTypes/generationMode/caseCountPerApi/requestKey。 |
| WP6-4.2 WP5 输入适配 | 已完成 | `assetTestCaseIds` 通过 WP3 `AssetTestCaseService` 读取已发布测试用例摘要，校验项目归属和已同步 API 范围；只保存标题、状态、优先级、标签、步骤摘要、sourceRef digest，不读取 WP5 候选正文或评审评论明细。 |
| WP6-4.3 WP2 Prompt | 未完成 | 当前未接入 `wp6-api-automation-v1` 模型调用，`modelGenerationReady=false`；`MODEL_WITH_FALLBACK` 仅记录模型未接入原因并走 fallback。 |
| WP6-4.4 输出校验 | 部分完成 | fallback 输出写入前校验 coverageTypes、generationMode、caseCountPerApi、已同步 assetApiIds，并只保存 assertion/request aggregate 摘要；模型输出 schema 校验待 WP2 接入后补齐。 |
| WP6-4.5 确定性 fallback | 已完成 | 基于已同步 endpoint 的 method/path/response status/schemaDigest 生成 `SMOKE/FUNCTIONAL/EXCEPTION` 用例草稿，source 明确为 `FALLBACK`。 |
| WP6-4.6 生成审计 | 已完成 | 生成任务写入 inputDigest、apiCount、caseCount、coverageTypes、generationMode、fallbackUsed，并记录 `api_automation.generation.created`；审计不保存 schema 明细或请求正文。 |
| WP6-7.1 API client | 已完成 | 新增 `portal-web/src/api/apiAutomation.ts` 和 Vitest。 |
| WP6-7.2 权限入口 | 已完成 | 新增 `#api-automation` 导航入口和 `apiAutomation:read/import` 控制。 |
| WP6-7.3 规格面板 | 已完成 | 已完成导入表单、规格列表、状态、错误提示和 endpoint snapshot。 |
| WP6-7.4 Diff 面板 | 部分完成 | 已支持刷新 diff、展示状态/assetApiId/reason、触发同步；更细筛选和部分成功详情表待 M6 UI 收敛。 |
| WP6-7.5 生成面板 | 部分完成 | 工作台新增“生成用例”入口和 WP3 用例 ID 输入，按当前规格触发 fallback 生成并展示任务/用例摘要；API 范围选择、用例列表选择和任务历史列表待 M6 收敛。 |
| WP6-8.1 后端测试 | 部分完成 | 已覆盖 parser、controller、权限、OpenAPI 契约、安全配置和 fallback 生成任务；全量回归仍需发布前执行。 |
| WP6-8.2 前端测试 | 部分完成 | 已覆盖 API helper、权限 helper、生成任务 normalize 和生成 API 调用；复杂页面交互待后续 Playwright smoke。 |
| WP6-8.3 DB validation | 已完成 | `run_wp1_db_validation.sh` 已纳入 WP6 schema/权限校验。 |

下一步建议继续 M4/M5：接入 WP2 Prompt 调用与模型输出 schema 校验，然后推进脚本包生成、静态校验和评审流。
