# WP5 企业级产品化剩余工作拆解

| 项目 | 内容 |
|---|---|
| 工作包 | WP5 AI 用例生成与评审 |
| 文档性质 | 企业级产品化缺口审查、工作拆解和推进记录 |
| 当前状态 | WP5 已具备事件驱动规则模板生成、WP2 模型生成可开关接入、模型失败降级、显式上下文资产输入、上下文裁剪策略配置化、上下文策略诊断与任务报告聚合、任务报告安全扫描策略聚合、任务报告归档策略聚合、任务报告导出安全扫描、前端生成来源区分、运行中超时回收、候选服务端分页评审、任务全量质量摘要、任务质量准出阈值摘要、Prompt 版本级准出摘要、Prompt 准出分布、任务本域审计链摘要、可配置相似度冲突策略、人工冲突链接入口、后端批量冲突处理接口、应用服务职责拆分、前端发布冲突搜索与批量复用、评审历史页摘要、人工反馈回流摘要、后端任务全量报告导出、任务模型观测摘要、服务端筛选导出、批量字段编辑、候选/发布摘要导出、dryRun、发布到 WP3 的最小闭环；仍未达到企业级产品完成态 |
| 版本 | v0.41 |
| 日期 | 2026-05-30 |

## 1. 目标

从企业级产品视角，WP5 必须从“可演示闭环”升级为“可在真实项目中稳定使用、可观测、可回归、可扩展、可审计”的 AI 测试设计工作台。

本拆解聚焦剩余工作，不重复 WP5 前期 PRD、技术设计、前端设计和测试策略已有内容。

## 2. 范围

| 范围 | 企业级要求 |
|---|---|
| 模型生成 | 通过 WP2 统一模型接入生成候选，记录模型调用、成本、延迟、Prompt 版本和失败原因。 |
| 上下文装配 | 从 WP3/WP4 装配需求、API、页面、业务流、历史用例摘要，并完成裁剪、脱敏和 inputDigest。 |
| 质量门禁 | 对模型输出做 JSON Schema 校验、空字段阻断、重复检测、敏感泄露检测和 golden set 评测。 |
| 任务编排 | 支持任务状态机、取消、重试、幂等、部分成功、失败可解释和后续异步化。 |
| 权限审计 | 所有任务、候选、批量操作、发布动作必须按项目 scope 鉴权并写审计。 |
| 前端工作台 | 具备任务创建、任务列表、候选评审、批量操作、dryRun、发布结果、错误定位和权限态。 |
| 准出验证 | 后端、前端、DB validation、HTTP smoke、质量评测脚本纳入 WP5 quality gate。 |

## 3. 非目标

| 非目标 | 说明 |
|---|---|
| 自动化脚本生成 | 仍由 WP6/WP7 承接，WP5 只沉淀测试用例资产。 |
| 测试执行调度 | 仍由 WP9 承接。 |
| 执行报告和缺陷诊断 | 仍由 WP10 承接。 |
| 绕过人工评审自动发布 | 企业级 MVP 不允许模型候选直接成为 APPROVED 测试用例。 |

## 4. 剩余工作总览

| 优先级 | 工作项 | 牵头角色 | 当前缺口 | 准出标准 |
|---|---|---|---|---|
| P0 | 接入 WP2 模型生成 | 服务端架构师 | 已补 `MODEL` / `MODEL_WITH_FALLBACK` 生成模式，通过 `ModelInvocationService` 调 WP2、解析 WP5 JSON 输出并回写 `modelInvocationId/provider/model`；仍需真实 provider 与 Prompt 质量联调。 | 成功、失败、预算阻断、fallback 全部可追踪；不直连厂商。 |
| P0 | 企业级上下文装配 | 服务端架构师 | 已补 WP3 需求、WP4 来源摘要、追踪关联资产、历史用例摘要、显式 API/页面/业务流输入、`veri-agent.test-design.context-*` 驱动的裁剪策略快照，以及任务诊断/报告中的安全聚合口径；仍缺按项目/环境的运营后台和策略变更审批。 | 上下文有裁剪、脱敏、inputDigest、来源引用和不泄露原文的策略可观测性。 |
| P0 | 输出 Schema 和质量门禁 | 质量工程师 | 已补候选 JSON Schema、模型原始输出 JSON Schema/parser、生成/编辑落库前质量门禁、重复键和敏感泄露阻断；仍缺真实 WP2 响应接入联调和运营化阈值报表。 | 不合格输出不得静默落库；golden set 阈值可回归。 |
| P0 | 重复和冲突治理 | 服务端架构师 | 已补 `LINK_EXISTING`、同需求高相似 `DUPLICATE_REVIEW_REQUIRED`、可配置相似度阈值策略、人工链接既有用例服务端入口、后端批量冲突处理接口、前端发布冲突搜索、批量复用入口和跨页候选版本快照；仍缺补偿后台和完整资产冲突运营台。 | 发布前能识别同 sourceRef、同需求高相似和已发布候选；人工确认后可审计地链接既有用例。 |
| P0 | 任务编排与幂等 | 项目经理、服务端架构师 | 已补 retry/cancel 契约、状态保护、创建任务幂等键、前端稳定幂等提交键、requestDigest 冲突检测、DB 事务锁、`QUEUED -> RUNNING` 条件认领、事件恢复扫描和运行中超时失败回收；仍缺跨 WP 补偿后台和多实例压测。 | 重试不重复污染候选；取消可阻断排队/运行中任务；重复请求、重复事件和运行中卡死可识别。 |
| P0 | 权限和资源作用域加固 | 服务端架构师、质量工程师 | 已补批量候选操作和候选导出按候选/任务项目 scope 鉴权，仍需覆盖评测和未来异步回调。 | 项目角色不能操作其他项目候选；服务令牌调用可审计。 |
| P0 | 发布幂等和补偿 | 服务端架构师 | 已补 AI 生成用例 `sourceRef` 服务层回放、事务级锁、数据库唯一约束和部分成功补链重试；仍缺补偿后台和异步跨 WP 事务编排。 | 重复发布不重复建用例；失败有记录和可重试策略。 |
| P0 | HTTP smoke 常态化 | 质量工程师 | 已补 managed HTTP smoke runtime，显式开启时可自启动临时 PostgreSQL 和 `platform-api`；默认 gate 仍避免无意启动 Docker。 | 发布前执行 `WP5_RUN_HTTP_SMOKE=1 bash scripts/wp5_quality_gate.sh`，或对既有环境执行 `WP5_RUN_HTTP_SMOKE=external WP5_SMOKE_BASE_URL=... bash scripts/wp5_quality_gate.sh`。 |
| P1 | 前端产品化 | 前端工程师 | 已补任务筛选、候选筛选、候选服务端分页联动、候选批量评审、批量字段编辑、批量/发布二次确认摘要、勾选候选发布、候选/发布摘要导出、后端任务全量报告导出、任务全量质量摘要、任务质量准出阈值摘要、Prompt 版本趋势面板、评审历史页摘要、人工反馈回流摘要、冲突搜索与单条/批量复用、发布结果跳转 WP3 资产追踪、候选编辑字段级质量提示、任务/候选生成来源区分和排队/运行中任务自动刷新；仍需运营化体验补齐。 | 用户无需 curl 可完成主链路；移动/窄屏无重叠。 |
| P1 | AI 质量运营 | 产品经理、质量工程师 | 已补后端任务全量质量摘要、Prompt 版本趋势聚合接口、可配置默认准出阈值摘要、Prompt 版本级准出摘要、Prompt 准出状态分布、前端看板和人工反馈回流聚合，覆盖任务级和版本级状态/覆盖/优先级分布、可评审/可发布、步骤/最终预期完整性、低置信度、错误、缺需求、缺标题、重复键碰撞、人工修正、驳回/忽略和调优样本说明覆盖计数；仍缺样本集维护和真实 Prompt 变更后的长期趋势校准。 | 每次 Prompt 变更可比较覆盖率、重复率、泄露数、有效步骤率、版本级准出状态和准出状态分布。 |
| P1 | 审计与观测 | 服务端架构师 | 生成、评审、发布已有部分审计；任务响应已补脱敏模型观测摘要，前端诊断面板可查看 prompt/model、`modelInvocationId`、异步 job traceId、成本、延迟、token、fallback、`inputDigest`、`idempotencyKey`、错误摘要、创建/更新时间、上下文键摘要和上下文策略摘要；任务报告已补本域审计链摘要，聚合 WP5 任务、评审记录和发布记录的事件数、说明覆盖、失败/冲突和最近事件；仍缺跨 WP1 audit_log 的端到端统一审计链看板。 | 任务详情能定位模型调用、错误原因、上下文策略和审计链。 |
| P1 | 导出和报告摘要 | 产品经理、前端工程师 | 已补服务端按筛选条件的候选 CSV 导出、导出审计、前端筛选导出、已选候选、发布结果摘要导出、评审历史页摘要、后端任务全量报告导出、模型观测聚合行、上下文策略聚合行、`exportGovernance` 聚合行、`safetyScanPolicy` 聚合行、`archivePolicy` 聚合行和报告最终安全扫描；仍缺真实归档存储、归档审批流和更完整的审计报表。 | 导出不含原始 Prompt、密钥、token、受控上下文正文、安全扫描命中详情、归档路径或审批备注，命中敏感模式时失败关闭。 |
| P2 | 策略运营后台 | 产品经理 | 缺覆盖策略、模型开关、预算阈值的配置化运营。 | 非研发人员可按项目调整生成策略。 |

## 5. 本次已推进

| 项 | 结果 |
|---|---|
| 批量评审权限 | `POST /api/v1/test-design/candidates/batch-action` 已按候选所属项目做 scope 校验。 |
| 任务状态契约 | 新增 `POST /api/v1/test-design/tasks/{id}/retry` 和 `/cancel`，失败、部分成功、取消状态可重试或取消。 |
| 生成文本安全兜底 | 规则模板输出对明显 `apiKey`、`token`、`secret`、`bearer` 等文本做本地脱敏，作为 WP2 完整上下文脱敏前的兜底。 |
| 质量评估入口 | 新增 `scripts/wp5_case_generation_quality_eval.sh` 和 `TestDesignQualityEvaluationTest`，覆盖候选唯一性、步骤预期完整性、覆盖类型顺序和敏感泄露基线。 |
| quality gate | `scripts/wp5_quality_gate.sh` 支持 `WP5_RUN_AI_EVAL=1` 运行 WP5 质量评估。 |
| 发布 sourceRef 幂等 | WP5 发布 dryRun 和正式发布已能识别同项目同 `source=AI_GENERATED`、同 `sourceRef=wp5:{candidateId}` 的 WP3 用例，并返回 `LINK_EXISTING`，避免重复创建。 |
| 发布 sourceRef 数据库兜底 | WP3 测试用例新增同项目同 AI `sourceRef` 部分唯一索引，迁移前会阻断历史重复数据；服务层通过事务级 advisory lock 先查回放，避免并发发布重复写入和事务内唯一冲突污染。 |
| 发布部分成功补偿 | 正式发布发现既有 WP3 用例时会幂等补建 requirement-case trace link；发布失败候选保留 `assetCaseId/errorMessage` 并可重试，前端发布按钮纳入失败候选的重试入口。 |
| HTTP smoke 自启动准出 | `scripts/wp5_quality_gate.sh` 已支持 `WP5_RUN_HTTP_SMOKE=1` 自启动临时 PostgreSQL 和 db profile `platform-api`，完成 SuperAdmin seed、项目创建/激活、WP3 需求准备后执行 WP5 HTTP smoke；保留 `external` 模式复用已运行环境。 |
| 前端发布工作台增强 | WP5 工作台已支持任务状态/项目/关键词筛选、候选状态/覆盖/关键词筛选、按勾选候选收敛发布范围、冲突/失败摘要，以及发布记录中的 WP3 用例追踪跳转。 |
| 前端发布冲突处理入口 | 工作台发布面板已对 `DUPLICATE_REVIEW_REQUIRED/CONFLICT` 且带候选和目标用例的记录展示“冲突处理”区，支持填写处理原因/说明、二次确认后调用 `POST /api/v1/test-design/candidates/{id}/resolve-conflict`，成功后刷新候选、质量摘要和评审历史。 |
| 后端发布冲突批量接口 | 新增 `POST /api/v1/test-design/candidates/batch-resolve-conflicts`，按候选项目 scope 校验 `testDesign:publish`，逐项携带候选版本和目标 WP3 用例 ID，返回成功/失败明细并复用单条冲突处理的需求追踪、版本保护、审计和发布记录逻辑。 |
| 应用服务职责拆分 | 移除 `TestDesignService` 总入口，将 WP5 任务查询/创建/重试/状态、生成执行、候选评审、质量摘要、发布、冲突处理、报告导出和权限 scope 拆到独立应用服务；控制器和事件处理器直接依赖职责服务，不使用 facade 代理；架构测试禁止重新引入聚合服务/facade，并将 WP5 单服务行数上限收紧到 1200。 |
| 前端发布冲突搜索与批量复用 | 工作台发布冲突面板已复用 WP3 测试用例查询接口，按任务项目和关键词搜索既有用例；冲突行可选择搜索结果或后端推荐用例作为目标，面板展示全部可处理冲突，并支持一次二次确认后调用后端批量接口复用已选/推荐用例。 |
| 发布记录候选版本快照 | `TestDesignPublishRecordResponse` 新增 `candidateStatus/candidateVersion` 白名单字段，前端可直接使用发布结果中的候选版本处理跨页冲突，不再要求先切换到候选所在页。 |
| 前端候选质量提示 | 候选编辑器已在保存前提示标题、覆盖类型、优先级、步骤数量、逐步预期、最终预期、敏感文本和同需求同覆盖标题重复问题，并用单测锁定规则。 |
| 前端候选分页效率 | 候选列表已支持 10/20/50 每页切换、上一页/下一页、页内可发布候选选择和跨页发布选择保留，并用分页单测纳入 WP5 quality gate。 |
| 前端服务端分页联动 | 工作台候选列表改为调用 `GET /api/v1/test-design/tasks/{id}/candidates?index=&size=&status=&coverageType=&keyword=`，任务轮询使用轻量 `GET /tasks/{id}/summary`，未勾选发布仍由后端按任务全量可发布候选处理。 |
| 前端批量评审入口 | 候选列表已复用后端 batch-action 支持勾选后批量确认、驳回和忽略，并展示成功/失败摘要；选择语义用单测纳入 WP5 quality gate。 |
| 前端批量字段编辑 | 工作台支持对已勾选且仍可评审的候选批量修改覆盖类型、优先级和标签追加/替换；执行前展示二次确认，执行后成功项从选择中移除、失败项保留并展示原因，规则 helper 已纳入 WP5 quality gate。 |
| 前端高风险二次确认摘要 | 批量评审、预发布和正式发布前展示操作、范围、评审意见、候选预览和风险提示；确认摘要 helper 已纳入 WP5 quality gate。 |
| 前端摘要导出 | 工作台已支持按 `testDesign:export` 权限导出当前候选页、跨页已选候选和发布结果 CSV；导出使用白名单字段和本地敏感模式脱敏，不包含描述、步骤正文、前置条件、原始 Prompt、密钥或 token，导出 helper 已纳入 WP5 quality gate。 |
| 前端质量运营摘要 | 工作台新增“质量摘要”面板，按当前候选页聚合可评审/可发布、步骤完整、预期完整、状态/覆盖/优先级分布和风险计数；摘要 helper 只输出计数与标签、不暴露描述、步骤正文或错误原文，并纳入 WP5 quality gate。 |
| 后端任务全量质量摘要 | 新增 `GET /api/v1/test-design/tasks/{id}/quality/summary`，按任务项目 scope 校验 `testDesign:read`，返回全任务候选质量计数、状态/覆盖/优先级分布和比例；前端质量面板已优先展示任务全量摘要，不再把分页摘要误当作任务级指标。 |
| 任务质量准出阈值摘要 | `quality/summary` 新增 `readiness`，按配置化默认阈值输出 `PASSED/WARNING/BLOCKED`、阻断/风险计数和逐项检查；前端质量面板展示准出状态和阈值口径，但本阶段不改变发布权限和候选状态。 |
| Prompt 版本趋势摘要 | 新增 `GET /api/v1/test-design/quality/prompt-trend`，按项目 scope 校验 `testDesign:read`，基于最近任务聚合 Prompt key/version、候选总数、确认/发布、步骤/预期完整率、低置信、错误、重复键碰撞和人工修正/驳回/忽略信号；前端工作台新增“Prompt 趋势”面板，展示版本级质量和反馈指标，接口与 helper 均不返回候选正文、评审评论、原始 Prompt、密钥或 token。 |
| Prompt 版本级准出摘要 | `prompt-trend` 版本桶新增 `readiness`，复用任务质量阈值输出 `PASSED/WARNING/BLOCKED`、阻断数、风险数和检查项；前端 Prompt 趋势面板展示每个版本的准出状态，用于运营比较但不改变发布权限或候选状态。 |
| Prompt 准出状态分布 | `prompt-trend` 顶层新增 `readinessDistribution`，按版本桶聚合准出通过、风险和阻断数量与比例；前端 Prompt 趋势顶部展示阻断版本、风险版本和分布 chip，帮助运营人员快速识别需要回滚或复盘的 Prompt 版本。 |
| 任务本域审计链摘要 | 新增 `GET /api/v1/test-design/tasks/{id}/report/audit-summary`，按任务项目 scope 校验 `testDesign:read`，基于 WP5 任务、评审记录和发布记录聚合事件总数、评审/发布/预演、失败冲突、说明覆盖和最近事件；前端工作台新增“审计链”面板，展示任务本域操作链，不返回评审评论、候选正文、发布错误原文、原始 Prompt、密钥或 token。 |
| 前端评审历史摘要 | 工作台评审历史区新增当前页摘要，聚合动作、评审人、字段变更、状态流转、版本流转和评审说明覆盖情况；摘要 helper 不暴露评论预览正文，并纳入 WP5 quality gate。 |
| 人工反馈回流摘要 | 工作台评审历史区新增“反馈回流”摘要，将人工修正、驳回和忽略聚合为 Prompt 调优信号、涉及候选数和说明覆盖率；后端任务全量报告同步输出 `feedbackLoop` 聚合行，只包含计数、比例和 tone，不导出评审评论正文。 |
| 前端任务报告摘要导出 | 工作台新增“导出报告”，生成当前任务 CSV 摘要，聚合任务元数据、当前候选页质量摘要、当前评审页审计摘要和最近发布结果；导出不包含候选正文、步骤正文、评审评论预览、原始 Prompt、密钥或 token，并纳入 WP5 quality gate。 |
| 后端任务全量报告导出 | 新增 `GET /api/v1/test-design/tasks/{id}/report/export`，按任务项目 scope 校验 `testDesign:read` + `testDesign:export`，服务端聚合全任务候选质量、评审历史、发布记录和模型观测白名单字段并写导出审计；前端“导出报告”已切换到该接口。 |
| 任务报告导出安全扫描 | 后端任务全量报告新增 `exportGovernance` 聚合行，声明 aggregate-only 和禁止导出的字段族；CSV 返回前执行最终安全扫描，命中未脱敏 secret/token/Bearer、原始 Prompt 或 request/response preview 标记时阻断导出，并将专项测试纳入 WP5 quality gate。 |
| 任务报告安全扫描策略聚合 | 后端任务全量报告新增 `safetyScanPolicy` 聚合行，固定输出 fail-closed 模式、敏感文本扫描、原始载荷标记扫描、request/response preview 标记扫描和命中详情不导出；不写扫描命中片段、违规字段名或原始 payload 片段，并将专项测试纳入 WP5 quality gate。 |
| 任务报告归档策略聚合 | 后端任务全量报告新增 `archivePolicy` 聚合行，归档保留天数按 1-3650 有界化，固定输出 `platformManaged` 存储策略、审批要求、外发开关和策略跟踪状态；不导出归档路径、归档备注、审批说明、工单 URL 或其他自由文本，并将专项测试纳入 WP5 quality gate。 |
| 任务模型观测摘要 | `TestDesignTaskResponse` 新增脱敏 `modelObservation`，从 WP2 调用日志和异步 job 读取状态、traceId、成本、延迟、token、fallback、错误码和脱敏错误摘要；前端任务诊断和报告摘要展示白名单聚合字段，不返回 request/response preview、原始 Prompt、密钥或 token。 |
| 服务端筛选导出 | 新增 `GET /api/v1/test-design/candidates/export`，按 task/project scope 校验 `testDesign:read` + `testDesign:export`，最多导出 500 条白名单字段候选摘要并写导出审计；前端“导出筛选”已切换到服务端接口。 |
| 同需求高相似冲突治理 | WP5 发布 dryRun 和正式发布已能识别同需求下高相似 WP3 用例，并返回 `DUPLICATE_REVIEW_REQUIRED/CONFLICT`，阻断静默重复创建；标题和正文相似度阈值已支持配置化，精确同标题仍始终阻断。 |
| 人工冲突链接入口 | 新增 `POST /api/v1/test-design/candidates/{id}/resolve-conflict`，按候选项目 scope 校验 `testDesign:publish`，要求候选版本号和目标 WP3 用例 ID，确认目标用例属于同项目且已关联候选需求后，复用 WP3 trace link 服务补链、将候选置为 `PUBLISHED`，并写 `MANUAL_LINK_EXISTING` 发布记录和 `RESOLVE_CONFLICT` 评审记录。 |
| 候选质量门禁 | 新增 WP5 候选 JSON Schema 资源和服务端质量门禁，生成、重试和人工编辑均校验必填字段、步骤完整性、优先级/覆盖类型、重复键、置信度和明显敏感泄露。 |
| 创建任务幂等 | `POST /api/v1/test-design/tasks` 支持 `Idempotency-Key` 请求头或请求体 `idempotencyKey`，按项目唯一回放相同请求，并用 `requestDigest` 阻断同 key 不同 payload；DB profile 对同项目同 key 使用事务级锁避免并发重复创建竞态。 |
| 前端创建任务幂等 | WP5 工作台创建任务时会按项目、标题、需求、覆盖类型和用例数生成请求签名，同一签名下复用一次性 `idempotencyKey`，成功后轮换新键，失败后保留原键便于用户重试同一请求。 |
| 前端任务诊断 | 工作台任务侧栏新增“任务诊断”面板，展示脱敏后的 prompt/version、模型、`modelInvocationId`、`inputDigest`、`idempotencyKey`、错误摘要、请求人、创建/更新时间和上下文键/数量摘要，帮助定位幂等回放、失败原因和模型调用链。 |
| 前端生成来源区分 | 工作台服务状态、任务列表、候选列表、候选编辑区和任务诊断新增统一来源判定，区分“模型输出”“模型降级模板”“规则模板”和“模型待生成”，避免规则模板或 fallback 候选被误认为真实模型输出；`testDesignGenerationSource.test.ts` 已纳入 WP5 quality gate。 |
| 上下文摘要与 inputDigest | 创建任务时从 WP3 应用服务读取需求和同需求历史用例，保留 WP4 来源字段摘要，写入脱敏截断后的 `contextSummary` 和 SHA-256 `inputDigest`；响应不暴露 `requestDigest` 或原始 Prompt。 |
| 显式上下文资产输入 | `POST /api/v1/test-design/tasks` 支持 `contextApiIds/contextPageIds/contextFlowIds`，按任务项目读取活跃 API、页面和业务流资产，写入 `contextSummary.explicitAssets` 的计数、ID 和脱敏摘要；前端生成配置可输入逗号或换行分隔 ID，幂等签名和服务端 request digest 均覆盖这些 ID。 |
| 上下文裁剪策略配置化 | `veri-agent.test-design.context-*` 控制关联资产、显式资产、历史用例和摘要字符数上限；健康接口暴露 `contextLimits`，任务 `contextSummary.limits` 与模型请求 `contextPacking` 使用同一套生效值，并纳入 request digest。 |
| 上下文策略诊断与报告 | 前端任务诊断新增上下文策略摘要；后端任务全量报告新增 `context/contextPolicy` 聚合行，仅输出需求、关联资产、显式资产、历史用例计数和裁剪上限，不导出显式资产 ID、schema、页面树、流程 JSON、需求正文、历史用例步骤或原始 Prompt。 |
| 模型输出结构校验 | 新增 WP5 模型原始输出 JSON Schema 和 `TestDesignModelOutputParser`，在未来 WP2 响应进入候选生成前校验字段白名单、必填项、枚举、步骤数量、置信度和明显敏感泄露，不持久化原始模型输出。 |
| WP2 模型生成接入 | `veri-agent.test-design.generation-mode=MODEL` 时通过 WP2 `ModelInvocationService` 调用 `wp5-test-design-v1` Prompt，解析结构化 JSON 后生成候选并保留模型调用观测；默认仍为 `RULE_TEMPLATE`。 |
| 模型失败降级 | `MODEL_WITH_FALLBACK` 或 `MODEL + model-fallback-enabled=true` 时，WP2 Prompt 缺失、供应商失败或输出非法会记录任务级降级提示并回退规则模板，避免候选被误标为纯模型生成。 |
| WP5 Prompt 种子 | 新增 `V20260529_039__wp5_model_prompt_seed.sql` 和本地内存 Prompt，DB validation 增加 `wp5.model_prompt_seeded`。 |
| 生成任务事件驱动 | `POST /api/v1/test-design/tasks` 默认返回 `QUEUED` 任务并发布 `test-design.generation.requested` 事件；消费者通过 `QUEUED -> RUNNING` 条件认领生成候选，重复事件只回放当前任务，不重复写候选。 |
| 生成事件恢复扫描 | 新增 WP5 事件恢复扫描，服务启动和定时任务会重新发布持久化 `QUEUED` 任务事件，处理权仍由消费者条件认领，避免事件投递中断后任务长期停留在排队态。 |
| 运行中超时回收 | 恢复扫描按 `WP5_EVENT_RECOVERY_RUNNING_TIMEOUT_SECONDS` 将长时间未更新的 `RUNNING` 任务批量标记为 `FAILED`，保留候选证据并走显式 retry 入口恢复，避免重复事件二次生成候选。 |
| 异步 smoke 和前端适配 | WP5 HTTP smoke 已轮询 `QUEUED/RUNNING -> SUCCEEDED` 后继续评审/发布；前端工作台展示 `QUEUED` 筛选并对排队/运行中任务自动刷新详情。 |

## 6. 里程碑拆解

| 里程碑 | 目标 | 主要交付物 | 负责人 | 验收 |
|---|---|---|---|---|
| M1 安全和契约加固 | 防止越权、补齐状态操作和质量入口 | batch scope、retry/cancel、质量评估脚本 | 服务端架构师、质量工程师 | 专项测试通过，OpenAPI 暴露契约。 |
| M2 模型与上下文 | 接入 WP2 并可控装配上下文 | 模型调用适配器、上下文 packer、JSON Schema、脱敏摘要 | 服务端架构师 | 已具备需求/来源/历史用例摘要、inputDigest、模型原始输出结构校验、事件驱动生成边界、WP2 模型调用、严格失败和 fallback 降级；真实 provider 联调与 Prompt 趋势对比仍待补齐。 |
| M3 冲突治理 | 发布前识别重复和冲突 | dryRun 计划器、sourceRef 幂等、数据库唯一兜底、相似候选检测、人工冲突链接入口 | 服务端架构师 | 重复发布不污染 WP3；人工确认冲突后可审计链接既有用例。 |
| M4 前端企业化 | 用户在页面完成主流程 | 任务工作台、候选评审、发布预览、错误态、摘要导出 | 前端工程师 | 已具备任务/候选筛选、候选服务端分页联动、服务端筛选导出、批量评审、批量字段编辑、批量/发布二次确认摘要、选择性发布、候选/发布摘要导出、后端任务全量报告导出、任务全量质量摘要、任务质量准出阈值摘要、评审历史页摘要、模型观测摘要、生成来源标签、候选质量提示、冲突摘要和 WP3 跳转；仍需运营化体验补齐。 |
| M5 准出运营 | 可持续评估和发布 | golden set、质量阈值、发布准出记录、观测字段 | 产品经理、质量工程师 | quality gate、smoke、评测报告可复现；新增异步生成专项测试、事件恢复专项测试、模型观测摘要专项测试、任务报告导出专项测试、任务全量质量摘要/准出阈值专项测试和 `QUEUED` 状态 DB validation。 |

## 7. 验收标准

1. 任务创建、重试、取消、查询、候选评审、dryRun、发布和发布记录均有后端权限与项目 scope 保护。
2. 模型输入和输出不泄露明显密钥、token、原始 Prompt 或受控上下文全文。
3. 候选落库前经过结构和质量校验，失败原因可解释。
4. 发布到 WP3 只使用 WP3 应用服务，不直连资产表。
5. 重复发布、重复候选和同需求高相似用例不会静默污染资产库。
6. 前端能覆盖 loading、empty、error、403、409、partial success、traceId 和发布结果。
7. `mvn -B -pl platform-api test`、`cd portal-web && npm test`、`cd portal-web && npm run build` 与 WP5 专项 gate 按影响面执行并记录。

## 8. 风险和回滚

| 风险 | 当前处置 | 后续动作 |
|---|---|---|
| 规则模板被误认为真实 AI | 默认仍为 `generationMode=RULE_TEMPLATE`；模型模式成功会带 `modelInvocationId/provider/model`，降级模式会保留任务级降级提示；前端已在任务、候选和诊断区展示“模型输出/模型降级模板/规则模板/模型待生成”来源标签。 | 后续继续接入真实 provider 后校准 Prompt 质量、趋势对比和人工反馈回流。 |
| 重试或重复事件导致候选重复 | 重试按 duplicateKey 跳过已有候选；异步事件消费通过 `QUEUED -> RUNNING` 条件认领，重复事件只返回当前任务；运行中超时只标记失败，不自动重放生成。 | 继续补补偿后台和多实例压测。 |
| 任务停留排队或运行态 | 任务先持久化为 `QUEUED`，提交事件后后台消费；恢复扫描会重发排队事件，并将超时 `RUNNING` 标记为可重试失败；HTTP smoke 和前端均按异步状态轮询。 | 后续补队列 lag 指标、超时告警和人工重发入口。 |
| 发布重复创建 WP3 用例 | 本次已按 `sourceRef=wp5:{candidateId}` 识别已存在用例并链接，新增 AI `sourceRef` 部分唯一索引、迁移前重复数据预检、服务层事务级锁和失败重试补链，并补同需求高相似用例检测、可配置相似度阈值和人工冲突链接入口。 | 继续补完整前端冲突运营台和异步补偿后台。 |
| 本地脱敏覆盖不足 | 本次只覆盖明显 secret/token 模式。 | 上下文 packer 接入统一敏感字段分类和 WP2 策略。 |
| 候选质量门禁过严影响人工编辑 | 编辑器已在保存前给出字段级质量提示，覆盖步骤数量、预期结果、重复标题和敏感文本；后端仍是最终准入。 | 后续将阈值逐步配置化，并把批量编辑纳入同一提示体系。 |
| 模型响应结构漂移 | 模型原始 JSON 已通过 parser 做字段白名单、必填、枚举、步骤数量和敏感文本校验，非法响应在严格模型模式下阻断落库，在 fallback 模式下回退模板并留任务提示；前端任务诊断已展示 prompt/version、`modelInvocationId`、模型观测摘要、错误摘要和上下文键摘要。 | 接入真实 provider 后继续补端到端审计链看板、Prompt 趋势对比和人工反馈回流。 |
| 幂等键被误复用 | 已对同项目同 key 存储 requestDigest，不同 payload 返回 `CONFLICT`，并用事务级锁降低并发重复提交竞态；前端按创建 payload 签名生成一次性幂等键，同一失败请求重试复用，成功后轮换。 | 后续在任务列表展示回放来源，并补多实例压测证据。 |
| 失败原因难定位 | 任务响应已从 WP2 调用日志和异步 job 读取脱敏模型观测摘要；前端任务诊断面板展示调用 ID、traceId、成本、延迟、token、fallback、错误码、脱敏错误摘要、`inputDigest`、`idempotencyKey` 和上下文键摘要；任务本域审计链摘要展示 WP5 任务、评审和发布记录的最近事件、失败冲突和说明覆盖，便于人工定位回放或失败链路。 | 后续串联 WP1 审计、WP2 调用、WP5 任务和 WP3 发布记录，形成端到端观测看板。 |
| 前端质量摘要被误解为任务全量指标 | 工作台已优先使用后端 `quality/summary` 展示任务全量质量指标和准出阈值摘要；接口只返回聚合计数、分布、比例和阈值检查，不暴露候选正文、步骤正文、错误原文或评审评论。 | 后续补 golden set 趋势、Prompt 版本对比和人工反馈回流。 |
| 评审历史摘要被误解为任务全量审计 | 面板明确标注“当前评审页 x-y / total”，摘要 helper 只聚合当前页动作、字段和评审人计数，不暴露评论预览正文。 | 后续由后端提供任务全量审计汇总、归档状态和导出留痕，前端再区分页级与任务级审计看板。 |
| 任务报告摘要被误解为全量准出报告 | “导出报告”已切换为后端全任务聚合，输出任务元数据、模型观测聚合字段、上下文规模与策略聚合字段、导出治理聚合字段、安全扫描策略聚合字段、归档策略聚合字段、全量候选质量摘要、全量评审历史摘要和发布记录摘要；不输出候选正文、步骤正文、评审评论、请求/响应 preview、trace/job 明细、受控上下文正文、安全扫描命中详情、归档路径或审批备注，并写导出审计。 | 后续补真实归档存储、审批流、审计报表模板和敏感词扫描运营记录。 |
| 模型观测摘要误泄露原文 | `modelObservation` 只返回调用状态、traceId/jobId、成本、延迟、token、路由和脱敏错误摘要；服务端测试验证不返回 WP2 request/response preview，前端报告只导出 trace/job 是否存在，不导出具体 traceId/jobId。 | 若未来增加更多 WP2 字段，必须继续走白名单 DTO 和敏感模式回归测试。 |
| 上下文摘要误含敏感信息 | 任务响应仅持久化脱敏截断摘要和 `inputDigest`；任务诊断只展示键/计数和策略数字，任务报告只导出上下文聚合行，不保存或导出原始 Prompt、完整文档正文、显式资产 ID、schema 或密钥字段。 | 后续接入 WP2 统一敏感分类和公开模型策略时扩大上下文红线测试。 |
| 发布失败产生部分成功 | 已记录逐候选 publish record；若 WP3 用例已创建但 trace link 缺失，重试会按 sourceRef 找回用例并补建链接，候选保留失败原因和资产 ID。 | 增加补偿后台、重试调度和跨 WP 发布事务边界运行手册。 |
| managed HTTP smoke 环境依赖 | `WP5_RUN_HTTP_SMOKE=1` 会启动 Docker PostgreSQL 和 Maven `platform-api`，日志保存在 `build/wp5-http-smoke/`。 | CI 或受限环境可改用 `WP5_RUN_HTTP_SMOKE=external` 指向已部署服务，失败时保留日志并阻断发布。 |
| 前端发布范围误选 | 发布面板默认覆盖后端任务全量可发布候选，勾选候选后仅发布跨页缓存中的勾选范围；服务端分页下发布前展示二次确认摘要。 | 后续增加跨页选择全部匹配候选的批量策略。 |
| 批量评审/编辑部分失败 | 前端批量评审和批量字段编辑均展示逐条失败摘要，并保留失败项选择，成功项从选择中移除；执行前展示二次确认摘要。 | 后续增加更细的冲突定位和跨页选择全部匹配候选策略。 |
| 高风险操作误触发 | 批量评审、批量字段编辑、预发布和正式发布已在前端展示二次确认摘要，确认前可取消且不触发接口。 | 后续可按权限矩阵把更多 WP5 导出和运营动作纳入统一确认组件。 |
| 导出泄露敏感信息 | 候选服务端筛选导出、评审历史导出、后端任务全量报告导出、已选候选和发布摘要导出均使用白名单字段；候选筛选导出限制 500 条并写审计，任务报告只输出聚合字段并在返回前执行安全扫描；安全扫描策略仅输出固定枚举和布尔开关，归档策略仅输出有界数字、布尔值和固定枚举，不导出安全扫描命中片段、归档路径、审批说明、描述、步骤正文、前置条件、原始 Prompt 或完整模型输入。 | 后续将真实归档存储状态和敏感词扫描记录纳入统一导出治理。 |

回滚方式：

1. 若本次后端改动异常，可回滚 WP5 retry/cancel 接口、batch scope 和脱敏逻辑；不影响已发布 WP3 测试用例。
2. 若质量评估脚本误报，可临时不设置 `WP5_RUN_AI_EVAL=1`，但必须保留单测失败证据并修复阈值或样本。
3. 若后续模型接入异常，可关闭生成能力，保留候选评审、查询和已入库资产。
