# WP4 需求与文档输入 - 研发拆解与里程碑计划

| 项目 | 内容 |
|---|---|
| 工作包 | WP4 需求与文档输入 |
| 面向阶段 | MVP 研发排期、任务拆分、迭代管理、验收准出 |
| 当前口径 | WP1/WP2/WP3 由单个 `platform-api` Java 服务承载；WP4 依赖 WP1 平台基础、WP2 模型接入和 WP3 资产管理 |
| 计划目标 | 跑通文本、Markdown、真实 Word/PDF/OCR、自研需求平台 webhook 输入闭环；Confluence、飞书、钉钉、语雀继续预留连接器类型 |
| 当前实现路径 | `/api/v1/document-input`；导入、候选和发布记录使用 `/imports` 资源，历史 `/batches` 建议路径以当前实现为准 |
| 当前权限口径 | `requirementInput:read`、`requirementInput:manage`、`requirementInput:import`、`requirementInput:candidate_review`、`requirementInput:publish`、`requirementInput:webhook_replay` |
| 文档性质 | 工程计划文档，不替代最终 PRD、OpenAPI 契约和测试用例 |
| 版本 | v0.1 |
| 日期 | 2026-05-19 |

## 0. 本轮新增敞口拆解

按 PM、架构、前端、测试和安全分工，本轮将剩余敞口收敛为三条 P0 线：

| 线 | 范围 | 负责人 | 完成口径 |
|---|---|---|---|
| D 线：真实 Word/PDF/OCR 解析 | `WORD` 支持 doc/docx 文本抽取；`PDF` 支持文本 PDF 抽取；`OCR` 支持配置命令式 OCR provider；三者均进入现有规则解析、WP2 AI 解析、候选确认和 WP3 发布链路 | 后端、前端、测试、运维 | 真实 docx、真实 PDF、OCR 命令适配均有自动测试；`WORD/PDF/OCR` 不再作为预留类型；扫描 PDF 无 OCR provider 时明确失败 |
| E 线：生产级 SecretProvider | WP4 webhook secret resolver 优先调用 WP1 `SecretProvider` 抽象，支持 `LOCAL_ENCRYPTED` 密文解密；本地配置 fallback 仅用于 dev/test 并可关闭 | 架构、WP1、安全、后端 | 解析 ACTIVE、未过期、用途匹配的密钥；密钥 provider 优先于配置；生产可禁用本地 fallback；响应和审计不泄露明文 |
| F 线：AI 解析质量评测集 | 建立最小 golden corpus 和质量门禁脚本，度量标题召回、优先级准确率、验收标准覆盖率 | PM、WP2、测试、后端 | `scripts/wp4_ai_parse_quality_eval.sh` 可一键运行；低于阈值失败；评测集随 Prompt 和解析器迭代扩展 |

## 1. MVP 范围

WP4 MVP 的目标是把外部需求内容稳定进入平台资产体系，形成“输入源配置 -> 文档/事件接收 -> 内容解析 -> 人工确认 -> 写入 WP3 需求资产 -> 审计追踪”的最小闭环。

MVP 范围如下：

| 范围项 | 交付口径 | 验收关注点 |
|---|---|---|
| 输入源类型 | 支持 `TEXT`、`MARKDOWN`、`WORD`、`PDF`、`OCR`、`CUSTOM_API` 六类可用输入源，其中 `CUSTOM_API` 覆盖自研 API/Webhook 推送 | 可以创建、启停、测试连通性、查看同步状态和最近错误 |
| 连接器类型预留 | 预留 `CONFLUENCE`、`FEISHU`、`DINGTALK`、`YUQUE` 类型 | 类型枚举、配置 schema、任务状态和 UI 占位存在，但不承诺真实拉取和解析 |
| 文本/Markdown 导入 | 支持手工粘贴文本、上传或提交 Markdown 内容，保留标题、章节、段落、列表、表格的基本结构 | 导入后可追踪原文片段、章节路径、版本号和操作者 |
| Word/PDF/OCR 解析 | 支持 Word doc/docx、文本型 PDF 和命令式 OCR 文本抽取，抽取文本进入统一解析链路 | 真实文件可解析；扫描件缺 OCR provider 时明确失败，并提示配置 OCR、换文本型文件或联系管理员；解析结果必须人工确认 |
| 自研 webhook | 支持自研需求平台通过 API/Webhook 推送需求创建、更新、状态变化和删除/归档事件 | 支持签名校验、幂等键、事件版本、字段映射和失败重放；签名失败提示可定位到 header、签名串、时间窗口或 secretRef，但不泄露密钥或完整签名 |
| 解析归一 | 将原始输入归一成需求候选项、业务规则、验收标准、约束、接口/页面线索和原文引用 | 解析结果可人工编辑，必须保留来源、置信度和原文定位 |
| 模型辅助解析 | 通过 WP2 模型接入层调用受控 Prompt，辅助提取结构化需求 | 必须继承 WP1/WP2 敏感级别和公开模型策略，记录模型调用审计 |
| WP3 写入 | 将已确认候选项写入或更新 WP3 需求资产，并建立来源追踪关系 | 写入需通过 WP3 应用服务，不直连 WP3 表；重复导入可幂等更新 |
| 前端工作台 | 提供输入源管理、导入批次、解析结果确认、差异预览、写入记录和错误重试入口 | 项目成员按权限访问；失败原因可读，可从批次追溯到原文 |
| 审计与观测 | 对输入源配置、导入、解析、确认、写入、webhook 接收和失败重试写审计 | 审计含项目、应用、输入源、批次、操作者、traceId 和结果 |

## 2. 非范围

以下内容不进入 WP4 MVP 的完成口径：

| 非范围项 | 说明 | 后续承接方向 |
|---|---|---|
| Word/PDF/OCR 高保真版式还原 | 不承诺复杂版式、图片语义、页眉页脚、批注、附件抽取、表格结构高保真还原 | P0 只做文本抽取和 OCR 文本回传，复杂版式后续引入异步 worker 或专业解析组件 |
| Confluence/飞书/钉钉/语雀真实连接器 | 不实现 OAuth、API 拉取、评论同步、权限继承、文档双向回写 | 预留连接器类型、凭证引用、配置 schema 和同步任务状态 |
| 企业协作通知和审批 | 不做机器人通知、审批卡片、评审流转、文档回写 | 由 WP11 企业协作连接器承接 |
| 原型输入 | 不接 Figma、蓝湖、Axure，不生成页面控件模型 | 由后续原型输入工作包承接 |
| 用例生成与执行 | 不直接生成测试用例、不触发执行、不生成报告 | WP5/WP9/WP10 依赖 WP4 输出的已确认需求资产 |
| 多源冲突自动裁决 | 不自动合并不同系统的语义冲突 | MVP 只做候选差异展示和人工确认 |
| 复杂知识库检索 | 不建设全文搜索、向量索引和长期知识库 | 可在后续智能检索或知识索引工作包中扩展 |
| 第三方密文明文管理 | WP4 不读取或保存第三方密文明文 | 仅保存 WP1 提供的 `secretRef` 或配置引用 |

## 3. 工程边界

1. WP4 MVP 默认作为 `platform-api` 内的需求输入领域模块实施，复用现有 Spring 应用服务、统一响应、分页、错误码、审计和上下文机制。
2. WP4 依赖 WP1 校验项目、应用、环境、权限、敏感级别、公开模型策略和审计写入，不直接读写 WP1 表。
3. WP4 依赖 WP2 做模型辅助解析，不直接调用外部模型 SDK，不绕过 WP2 的预算、敏感信息阻断、供应商策略和调用审计。
4. WP4 依赖 WP3 写入需求资产、来源追踪和后续资产关系，不直接读写 WP3 表。
5. Webhook 属于外部系统进入平台的边界接口，必须支持签名校验、幂等、限流、事件版本兼容、失败可观测和审计；生产密钥解析优先走 WP1 SecretProvider。签名样例和外部联调排错以 `doc/mvp/final/engineering/WP4-Webhook签名样例与联调说明.md` 为准。
6. Word/PDF/OCR 解析只负责文本抽取和 OCR 文本回传，不绕过候选确认；Confluence/飞书/钉钉/语雀连接器类型和配置 schema 继续前置冻结，避免后续接入破坏数据模型和 UI 路由。

## 4. Epic/Story 拆解

优先级说明：P0 为 MVP 必须交付；P1 为 MVP 后增强但需预留；P2 为后续连接器或智能化增强。

### Epic 1：输入源与连接器基础

| Story | 优先级 | 服务端任务 | 前端任务 | 测试任务 | 运维配置 |
|---|---|---|---|---|---|
| 1.1 输入源模型与连接器枚举 | P0 | 建立输入源、连接器类型、配置 schema、状态、最近同步结果、项目/应用归属模型；冻结 `TEXT`、`MARKDOWN`、`CUSTOM_API`、`WORD`、`PDF`、`OCR`、`CONFLUENCE`、`FEISHU`、`DINGTALK`、`YUQUE` 枚举 | 输入源列表、详情、创建/编辑表单；未实现类型展示为“预留”状态 | 枚举兼容、必填校验、项目越权、停用状态测试 | 配置默认启用的 MVP 类型；非 MVP 类型默认不可启用真实同步 |
| 1.2 输入源权限与审计 | P0 | 接入 WP1 项目/应用上下文校验；权限收敛为 `requirementInput:read/manage/import/candidate_review/publish/webhook_replay`；写入配置变更和启停审计 | 按权限展示创建、编辑、停用、测试按钮；无权限态处理 | 权限矩阵、跨项目访问、审计字段完整性测试 | 审计事件编码、日志字段和 traceId 规范 |
| 1.3 连接器健康检查 | P0 | 为文本/Markdown 返回本地可用状态；为 webhook 返回 URL、签名状态和最近事件状态；预留外部连接器探测接口 | 列表展示健康状态、最近错误和最近同步时间；详情页支持测试连接 | 健康检查匿名拒绝、失败摘要脱敏、状态刷新测试 | 连接器探测超时、错误摘要长度、健康缓存 TTL |

### Epic 2：文本与 Markdown 导入

| Story | 优先级 | 服务端任务 | 前端任务 | 测试任务 | 运维配置 |
|---|---|---|---|---|---|
| 2.1 文本粘贴导入 | P0 | 提供创建导入批次 API，接收标题、正文、来源备注、项目/应用上下文；生成原文快照和批次号 | 导入抽屉或页面，支持粘贴文本、选择项目/应用、提交后进入批次详情 | 空内容、超长内容、非法项目、重复提交测试 | 单次文本大小上限、批次保留天数、请求限流 |
| 2.2 Markdown 导入 | P0 | 解析 Markdown 标题层级、列表、表格、代码块和段落，保存结构化片段和原文引用 | 支持上传或粘贴 Markdown；预览章节树和片段列表 | Markdown 边界格式、表格、代码块、中文标题、异常格式测试 | Markdown 文件大小上限、允许扩展名和 MIME 校验 |
| 2.3 导入批次管理 | P0 | 提供批次列表、详情、状态流、取消、重试、错误摘要 API | 批次列表、状态筛选、详情页、重试按钮、错误展示 | 状态流、重试幂等、分页、错误脱敏测试 | 批次清理任务、失败重试次数、异步队列开关 |

### Epic 2B：真实 Word/PDF/OCR 解析

| Story | 优先级 | 服务端任务 | 前端任务 | 测试任务 | 运维配置 |
|---|---|---|---|---|---|
| 2B.1 Word 文本抽取 | P0 | 使用 Apache POI 支持 docx/doc 文本抽取；接受 plain text、raw base64、data URL 三种输入 | `WORD` 从预留改为可提交；提示可粘贴 base64 或 data URL | 真实 docx 导入、损坏文件、空文档、超限内容测试 | `WP4_IMPORT_MAX_CONTENT_BYTES`、`WP4_DOCUMENT_BINARY_MAX_BYTES` |
| 2B.2 PDF 文本抽取 | P0 | 使用 PDFBox 支持文本型 PDF；无文本且未配置 OCR 时明确失败并给出下一步 | `PDF` 从预留改为可提交；失败原因可读，包含 OCR/换文件/联系管理员建议 | 真实文本 PDF 导入、图片 PDF 无 OCR 失败、错误码测试 | PDF 文件大小、页数和处理超时后续纳入 worker |
| 2B.3 OCR provider | P0 | 支持 `WP4_OCR_COMMAND` 命令式 OCR provider，并支持 `WP4_OCR_WORKER_MODE=HTTP_WORKER` 调用外部隔离 OCR worker | `OCR` 从预留改为可提交；健康检查展示本地命令、远端 worker 和 fallback 状态 | OCR 命令成功、HTTP worker 成功、超时、空输出、失败退出码和 fallback 禁用测试 | `WP4_OCR_COMMAND`、`WP4_OCR_WORKER_URL`、`WP4_OCR_WORKER_TOKEN`、`WP4_OCR_LOCAL_COMMAND_FALLBACK_ENABLED`、`WP4_OCR_TIMEOUT_SECONDS`、`WP4_OCR_MAX_OUTPUT_CHARS` |
| 2B.4 输入安全闸门 | P0 | `/imports` 增加内容大小上限；二进制解析限制大小；临时 OCR 文件执行后清理；`WP4_MALWARE_SCAN_COMMAND` 可接入命令式文件安全扫描；生产可关闭本地 OCR fallback 以强制走隔离 worker | 错误态展示 traceId 和可读原因 | 超大 base64、伪造 MIME、OCR provider 不可用、文件扫描拒绝测试 | 生产环境建议使用 HTTP 隔离 OCR worker、限流和杀毒组件 |

### Epic 3：自研需求平台 Webhook

| Story | 优先级 | 服务端任务 | 前端任务 | 测试任务 | 运维配置 |
|---|---|---|---|---|---|
| 3.1 Webhook 接入配置 | P0 | 为 `CUSTOM_API` 输入源生成 endpoint、secretRef、eventVersion、mappingVersion、字段映射配置 | 展示 webhook URL、签名算法说明、字段映射编辑、复制入口 | 配置保存、密钥脱敏、字段映射校验测试 | webhook baseUrl、签名算法、时钟偏移容忍、密钥轮换策略 |
| 3.2 Webhook 事件接收 | P0 | 接收 `requirement.created`、`requirement.updated`、`requirement.statusChanged`、`requirement.archived`；校验签名、时间戳、事件版本和幂等键；签名失败按 `INVALID/EXPIRED/MISSING` 给出排错建议 | 事件列表、事件详情、原始 payload 脱敏查看；失败事件展示错误码、Trace ID 和下一步建议 | 签名失败、重放攻击、重复事件、未知版本、未知字段测试 | webhook 限流、payload 大小上限、允许 IP/CIDR 白名单 |
| 3.3 Webhook 失败重放 | P0 | 保存失败事件、错误码、重试次数；支持人工重放和自动有限重试 | 失败事件筛选、重放按钮、重放结果反馈 | 重放幂等、重试上限、死信状态测试 | 重试间隔、最大重试次数、死信保留时间 |

### Epic 4：解析归一与模型辅助

| Story | 优先级 | 服务端任务 | 前端任务 | 测试任务 | 运维配置 |
|---|---|---|---|---|---|
| 4.1 规则解析器 | P0 | 基于章节、列表、关键词和字段映射生成需求候选项、业务规则、验收标准、原文定位和置信度 | 解析结果按章节展示，支持展开原文引用 | 标题层级、规则编号、验收标准提取、原文定位测试 | 解析任务并发、超时、失败重试配置 |
| 4.2 模型辅助解析 | P0 | 已接入 WP2 `ModelAccessService`；`WP4_MODEL_PARSE_ENABLED` 开启后调用 `wp4-document-requirement-parse` Prompt 生成候选，并保存 `parseSource`、`modelInvocationId`、`modelProviderName`、`modelName` | 标注“模型辅助”来源，展示置信度和待确认状态 | 覆盖 WP2 成功、敏感内容阻断、fallback 测试；预算超额沿 WP2 错误码处理 | Prompt key、模型调用开关、最大内容长度、敏感级别、公有模型开关 |
| 4.3 解析结果合并 | P0 | 已实现模型解析优先、规则解析补缺；模型失败或阻断时规则解析兜底；候选仍必须人工确认 | 展示来源、置信度、模型调用追踪；发布 dryRun 展示既有 WP3 资产差异 | 合并幂等、模型失败 fallback、重复标题去重、重复来源更新测试 | 合并策略默认值、相似度阈值配置 |
| 4.4 AI 解析质量评测集 | P0 | 建立 golden corpus，按标题召回、优先级准确率、验收标准覆盖率设置阈值；提供 `scripts/wp4_ai_parse_quality_eval.sh`；当前 `corpusVersion=wp4-c1-2026-05-22` 覆盖 12 个样本、28 条期望需求，按 `TEXT/MARKDOWN/WORD/PDF/OCR/CUSTOM_API` 分桶并绑定 `promptKey/promptVersion` 与解析器版本 | 展示候选来源和模型调用追踪，为后续评测结果入口预留字段 | 低于阈值失败；Prompt/解析器变更必须跑评测；任一文档类型低于阈值可定位；缺少行业、难度、覆盖标签、每类至少 2 个样本或长文档/表格/歧义优先级/异常格式/OCR 低置信度覆盖时阻断 | 评测阈值、样本集版本、sourceType 分布和模型开关 |

### Epic 5：人工确认与 WP3 写入

| Story | 优先级 | 服务端任务 | 前端任务 | 测试任务 | 运维配置 |
|---|---|---|---|---|---|
| 5.1 候选项编辑确认 | P0 | 提供候选项查询、编辑、忽略、确认 API；查询支持 status/sourceRef/keyword；维护确认人、确认时间和版本 | 候选项表格、详情编辑、批量确认、忽略原因 | 字段校验、批量操作、versioned candidates、权限、并发编辑测试 | 乐观锁、批量操作上限 |
| 5.2 写入 WP3 需求资产 | P0 | 调用 WP3 应用服务创建/更新需求资产；建立 source/sourceRef/sourceUrl/acceptanceCriteria、sourceFragment、externalRequirementId 追踪关系 | 写入预览、写入结果、跳转 WP3 需求详情 | 幂等 upsert、重复导入、归档同步、失败回滚测试 | 写入批大小、失败补偿、WP3 调用超时 |
| 5.3 来源追踪与影响分析预留 | P1 | 保存来源版本、原文片段、外部 ID、字段映射版本，为后续变更影响分析预留 | 在资产详情显示来源链接和批次入口 | 来源链路完整性、版本查询测试 | 来源快照保留策略、归档策略 |

### Epic 6：前端工作台

| Story | 优先级 | 服务端任务 | 前端任务 | 测试任务 | 运维配置 |
|---|---|---|---|---|---|
| 6.1 需求输入导航与路由 | P0 | 提供菜单权限、统计摘要和工作台数据 API | 在项目空间内增加“需求输入”入口；包含输入源、导入批次、待确认、事件日志视图 | 菜单权限、路由守卫、空态测试 | 菜单开关、功能开关 |
| 6.2 批次详情与确认体验 | P0 | 支持片段、候选项、错误、写入记录聚合查询 | 批次详情页支持章节树、原文引用、候选项编辑、写入动作 | 大批次渲染、状态刷新、编辑保存测试 | 前端分页大小、轮询间隔 |
| 6.3 连接器预留 UI | P1 | 返回预留类型的配置 schema 和不可用原因 | Confluence/飞书/钉钉/语雀显示为“类型已预留，待后续启用”；Word/PDF/OCR 显示为可提交解析 | 非 MVP 类型不可误提交真实同步测试 | 连接器可用性配置 |

### Epic 7：测试与质量门禁

| Story | 优先级 | 服务端任务 | 前端任务 | 测试任务 | 运维配置 |
|---|---|---|---|---|---|
| 7.1 服务端单元与集成测试 | P0 | 覆盖 parser、mapping、webhook、batch、WP1/WP2/WP3 集成适配 | 无 | `mvn -B -pl platform-api test` 纳入 WP4 用例；覆盖错误码和 envelope | CI 增加 WP4 标签或测试分组 |
| 7.2 API 契约与权限测试 | P0 | 补充 OpenAPI 或等价契约；冻结请求、响应、错误码、分页和枚举 | 基于契约对齐字段命名和状态枚举 | 契约测试、权限矩阵、审计断言 | 契约 diff 阻断规则 |
| 7.3 前端交互测试 | P0 | 提供稳定 mock 或 local profile 测试数据 | 已提供 Playwright 浏览器 smoke，覆盖文档输入页真实文件上传、候选编辑/确认、发布 dryRun 预览和 webhook 事件重放主流程 | `bash scripts/wp4_frontend_e2e_smoke.sh`；本地无托管 Chromium 时可自动使用系统 Chrome，或设置 `WP4_FRONTEND_INSTALL_BROWSERS=1` 安装 | 前端 mock 数据在 `portal-web/e2e/wp4-document-input.smoke.playwright.ts` 内收敛 |
| 7.4 发布前 smoke | P0 | 提供脚本覆盖文本导入、Markdown 导入、webhook 推送、解析确认、写入 WP3、metrics | 无 | 一键 smoke 失败可定位到导入记录和 traceId | `WP4_WEBHOOK_SECRET`、`WP4_SMOKE_PROJECT_ID` 等参数 |

### Epic 8：运维配置与可观测性

| Story | 优先级 | 服务端任务 | 前端任务 | 测试任务 | 运维配置 |
|---|---|---|---|---|---|
| 8.1 配置项与 feature flag | P0 | 支持按环境开启文本、Markdown、webhook、模型辅助、预留连接器展示 | 根据 feature flag 控制入口可见性 | 默认配置、关闭开关、灰度开关测试 | `WP4_INPUT_ENABLED`、`WP4_MODEL_PARSE_ENABLED`、`WP4_WEBHOOK_ENABLED` |
| 8.2 指标与告警 | P0 | 当前输出导入、候选操作、发布、webhook、模型解析和外部 SecretProvider 健康 metrics；解析耗时、模型调用失败率在模型辅助解析启用后补齐 | 工作台展示最近失败和处理队列状态 | 指标存在性、标签维度、错误脱敏测试 | 告警阈值、仪表盘、日志采样 |
| 8.3 数据保留与清理 | P1 | 已提供导入记录/候选和 webhook 事件保留天数配置、定时清理入口、清理前归档表 `document_input_retention_archive`、清理指标和 `RETENTION_CLEANUP` 审计 | 当前仅在接口状态和运维材料展示保留配置；归档明细恢复由 DBA/运维受控处理，不开放前端明文查看入口 | 清理任务覆盖不会删除保留窗口内记录，过期 import/candidate/webhook event 先归档再移出在线查询；后续补资产来源链专项回归 | `WP4_RETENTION_CLEANUP_ENABLED`、`WP4_IMPORT_RETENTION_DAYS`、`WP4_WEBHOOK_EVENT_RETENTION_DAYS`、`veri-agent.document-input.retention-cleanup-cron` |
| 8.4 SecretProvider | P0 | WP4 webhook resolver 优先走 WP1 `SecretProvider`，支持 `LOCAL_ENCRYPTED`；外部 Vault/KMS resolve 支持 timeout、短暂失败 retry、独立 health endpoint 和可选 HMAC-SHA256 请求签名；resolve 成功/失败写入 WP1 审计并记录 provider、用途、调用方、作用域、版本和 `secretRefDigest`；校验用途、状态、过期时间和 `CONFIG + document_input_source.id` 作用域；本地 fallback 仅用于 dev/test 且可关闭；SecretProvider 成功结果按短 TTL 缓存，source 创建/更新主动失效，轮换重叠窗口可配置 | 仅展示 secretRef 引用状态、脱敏 provider 健康摘要、缓存状态和审计 digest，不展示 endpoint、token、签名密钥、完整 secretRef 或明文 | provider 优先级、fallback 关闭、用途不匹配、作用域不匹配、过期/撤销密钥、外部 provider UP/DOWN/UNKNOWN、签名头、认证失败脱敏、缓存 TTL/失效和 resolve 审计脱敏测试 | `WP1_LOCAL_SECRET_MASTER_KEY`、`WP1_LOCAL_SECRET_MASTER_KEY_VERSION`、`WP1_EXTERNAL_SECRET_RESOLVE_URL`、`WP1_EXTERNAL_SECRET_HEALTH_URL`、`WP1_EXTERNAL_SECRET_TIMEOUT_SECONDS`、`WP1_EXTERNAL_SECRET_MAX_RETRIES`、`WP1_EXTERNAL_SECRET_SIGNING_KEY_ID`、`WP1_EXTERNAL_SECRET_SIGNING_SECRET`、`WP4_LOCAL_WEBHOOK_SECRET_FALLBACK_ENABLED`、`WP4_WEBHOOK_SECRET_CACHE_TTL_SECONDS`、`WP4_WEBHOOK_SECRET_ROTATION_OVERLAP_SECONDS` |

## 5. 建议 API 边界

当前基础路径为 `/api/v1/document-input`，具体契约以当前实现、OpenAPI 和测试为准。早期拆解中出现的 `/api/v1/requirement-input` 和 `/batches` 属于历史建议路径，不再作为验收口径。MVP 需要冻结以下接口族：

| 接口族 | 示例路径 | MVP 口径 |
|---|---|---|
| 输入源 | `GET/POST /sources`、`PUT /sources/{id}` | 管理文本、Markdown、webhook 输入源；保存 `CUSTOM_API` 的 sourceCode、secretRef、eventVersion、mappingVersion 和字段映射；预留非 MVP 类型 |
| 健康检查 | `GET /sources/{id}/health` | 返回连接器状态、签名算法、secretRef 配置状态、eventVersion、最近错误和预留类型不可用原因 |
| 导入记录 | `POST /imports`、`GET /imports`、`GET /imports/{id}` | 创建文本、Markdown、Word、PDF、OCR 导入记录，查询状态和错误 |
| 解析结果 | `GET /imports/{id}/candidates?status=&sourceRef=&keyword=`、`PUT /candidates/{id}`、`POST /candidates/{id}/confirm`、`POST /candidates/{id}/ignore`、`POST /candidates/batch-action` | 人工编辑、确认、忽略候选项；批量操作支持 `candidateIds` 和携带版本号的 versioned candidates |
| 写入资产 | `POST /imports/{id}/publish`、`GET /imports/{id}/publish-records` | 将已确认候选项写入 WP3 需求资产，记录 source/sourceRef/sourceUrl/acceptanceCriteria 和发布结果 |
| Webhook | `POST /webhooks/{sourceCode}` | 接收自研需求平台事件，执行签名、幂等和字段映射 |
| Webhook 事件 | `GET /webhook-events?sourceId=&sourceCode=&eventType=&status=&receivedFrom=&receivedTo=`、`GET /webhook-events/{id}`、`POST /webhook-events/{id}/replay` | 查询、排错、重放失败事件 |

统一要求：

1. JSON 字段使用 camelCase；分页请求使用 `index`、`size`，响应使用 `items`、`index`、`size`、`total`。
2. 除公开 webhook endpoint 外，API 必须通过 WP1 登录态和项目权限校验。
3. Webhook endpoint 使用来源签名、时间戳、事件 ID 和幂等键校验，不使用普通用户登录态。
4. 错误响应使用统一 envelope；webhook 可按外部系统兼容要求返回简化成功/失败状态，但内部必须记录 traceId。
5. 文件、payload、原文片段和错误摘要均不得输出密钥、token、cookie、身份证号等敏感内容。
6. 受保护接口权限以 `requirementInput:read/manage/import/candidate_review/publish/webhook_replay` 六类为准，不再拆分候选 view/edit/confirm 或 webhook view/replay 细粒度建议。

## 6. 里程碑与验收标准

### M0：契约与数据模型冻结

| 项 | 内容 |
|---|---|
| 目标 | 冻结 WP4 MVP 的输入源模型、连接器枚举、状态机、错误码、审计事件、权限点和 API 草案 |
| 主要交付 | OpenAPI 或等价契约；数据表/实体设计；连接器类型清单；权限点和审计事件字典增量 |
| 验收标准 | 前端、后端、测试确认字段和状态无歧义；`/imports`、当前权限点和历史建议路径说明已对齐；预留连接器类型不可被误认为已实现；WP1/WP2/WP3 依赖点有明确 owner |

### M1：文本/Markdown 导入闭环

| 项 | 内容 |
|---|---|
| 目标 | 支持用户在项目内提交文本/Markdown，并生成可追踪导入批次和结构化片段 |
| 主要交付 | 输入源管理、文本导入、Markdown 导入、批次列表、批次详情、基础解析器 |
| 验收标准 | 文本和 Markdown 可成功导入；Word/PDF/OCR 可经文本抽取进入同一导入链路；章节、段落、列表和表格可追踪到原文；失败批次可查看错误并重试；越权项目不可导入 |

### M2：解析确认与 WP3 写入

| 项 | 内容 |
|---|---|
| 目标 | 将规则解析和 WP2 模型辅助解析结果变成可人工确认的需求候选项，并写入 WP3 |
| 主要交付 | 候选项编辑确认、模型辅助解析、差异合并、发布到 WP3、来源追踪 |
| 验收标准 | 至少一份文本和一份 Markdown 可生成需求候选项；开启 `WP4_MODEL_PARSE_ENABLED=true` 后候选可标记 `parseSource=MODEL` 并带 WP2 invocation 追踪；WP2 策略或敏感内容阻断时规则解析 fallback 可追踪；候选支持 status/sourceRef/keyword 筛选和携带版本号批量操作；人工确认后写入 WP3 需求资产并保留 source/sourceRef/sourceUrl/acceptanceCriteria；重复发布不产生重复资产；重复导入同一 externalRequirementId 时 dryRun 能识别 UPDATE 并给出 diffSummary，正式发布更新既有 DRAFT WP3 资产；既有资产非 DRAFT 且存在差异时返回 CONFLICT_REVIEW_REQUIRED 并阻断自动覆盖 |

### M3：自研 Webhook 增量同步

| 项 | 内容 |
|---|---|
| 目标 | 支持自研需求平台通过 webhook 推送增量事件并映射为需求候选项或 WP3 更新 |
| 主要交付 | webhook 配置、签名校验、事件接收、字段映射、幂等处理、失败重放 |
| 验收标准 | `created`、`updated`、`statusChanged`、`archived` 事件均可处理；source 配置保留 secretRef/eventVersion/mappingVersion；事件可按 sourceId/sourceCode/eventType/status/receivedFrom/receivedTo 查询；重复事件不重复写入；签名失败和过期事件被拒绝并审计；失败事件可人工重放 |

### M4：MVP 准出

| 项 | 内容 |
|---|---|
| 目标 | 完成端到端联调、质量门禁、运维配置和验收材料 |
| 主要交付 | 服务端测试、前端 smoke、webhook smoke、配置说明、指标和告警、验收报告 |
| 验收标准 | `platform-api` 测试通过；文本、Markdown、自研 webhook 三条主链路通过 smoke；metrics 和 smoke 覆盖本轮候选筛选、发布、webhook、WP3 追踪字段；审计和指标可查；非 MVP 连接器仅作为预留类型展示；无直接读写 WP1/WP2/WP3 表的实现 |

## 7. 连接器类型策略

| 类型 | MVP 状态 | 说明 |
|---|---|---|
| `TEXT` | 必做 | 手工粘贴或 API 提交纯文本需求 |
| `MARKDOWN` | 必做 | 支持 Markdown 文档结构解析和原文引用 |
| `CUSTOM_API` | 必做 | 自研需求平台 API/Webhook 增量同步 |
| `WORD` | 必做 | 支持 doc/docx 文本抽取，抽取内容进入规则解析、WP2 AI 解析和候选确认 |
| `PDF` | 必做 | 支持文本型 PDF 抽取；扫描件缺 OCR provider 时返回明确失败 |
| `OCR` | 必做 | 支持命令式 OCR provider，适配扫描件/图片文本抽取，低质量和空输出明确失败 |
| `CONFLUENCE` | 预留 | 预留空间、页面、版本、凭证配置，不做 API 拉取 |
| `FEISHU` | 预留 | 预留文档 token、空间、版本配置，不做飞书开放平台接入 |
| `DINGTALK` | 预留 | 预留文档标识和凭证配置，不做钉钉文档 API 接入 |
| `YUQUE` | 预留 | 预留知识库、文档、版本配置，不做语雀 API 接入 |

预留类型的验收口径是“数据模型、枚举、配置 schema、任务状态和前端入口不阻碍后续接入”，不是“连接器可用”。产品演示和验收材料必须明确：Confluence/飞书/钉钉/语雀仍是预留；Word/PDF/OCR 已进入 P0 可用范围，但只承诺文本抽取和 OCR 文本回传，不承诺高保真版式还原。

## 8. 与 WP1/WP2/WP3 的依赖关系

| 依赖方 | WP4 需要的能力 | 依赖风险 | 约束与缓解 |
|---|---|---|---|
| WP1 平台基础 | 项目、应用、环境上下文；用户权限；敏感级别；公开模型策略；审计写入；SecretProvider/Secret 引用；统一 API 规范 | 权限点、审计事件、SecretProvider 或上下文字段未冻结会影响前后端契约 | M0 阶段冻结 `requirementInput:read/manage/import/candidate_review/publish/webhook_replay` 权限点和审计事件；WP4 只通过 WP1 应用服务使用上下文与审计，通过 SecretProvider 解析生产密钥 |
| WP2 模型接入 | Prompt 版本、模型调用、敏感内容阻断、预算护栏、供应商 fallback、调用日志 | 模型策略阻断或预算超限会导致解析不可用；Prompt 不稳定会影响解析质量 | 规则解析器作为保底；模型辅助解析可配置关闭；Prompt key 和版本纳入验收 |
| WP3 资产管理 | 需求资产创建/更新、外部来源追踪、状态同步、资产详情跳转、重复识别 | WP3 需求模型或 upsert 契约未稳定会阻塞 WP4 发布到资产库 | M0 与 WP3 冻结最小 upsert DTO；M2 前提供 WP3 应用服务或 mock；WP4 不直连 WP3 表 |
| 前端平台框架 | 项目空间路由、菜单权限、统一表格、表单、错误态和权限态 | 其他角色并行改动前端路由可能造成入口冲突 | 仅新增 WP4 菜单和页面命名空间；遵循现有路由和权限约定 |
| 运维/CI | `platform-api` 测试、配置注入、日志、指标、smoke 脚本 | 缺少队列、对象存储或外部连接器环境会放大实施复杂度 | MVP 使用数据库任务和本地解析；对象存储、外部连接器和 worker 仅预留 |

## 9. 关键风险与应对

| 风险 | 影响 | 触发信号 | 应对策略 |
|---|---|---|---|
| MVP 范围膨胀到完整协作文档连接器 | 工期失控，测试矩阵过大 | Confluence/飞书/钉钉/语雀被要求进入可用范围 | 本轮只新增 Word/PDF/OCR 文本抽取；协作文档连接器仍预留 |
| OCR/二进制解析资源消耗或恶意文件风险 | CPU、内存、磁盘和安全风险 | 超大 base64、图片 PDF、OCR 超时、压缩炸弹 | 设置导入内容和二进制大小上限；OCR 命令/HTTP worker 超时；`WP4_MALWARE_SCAN_COMMAND` 在解析前执行文件扫描；生产通过 `WP4_OCR_WORKER_MODE=HTTP_WORKER` 和关闭 fallback 隔离 worker、杀毒和限流 |
| 自研 webhook 事件版本变化 | 增量同步失败或字段丢失 | 外部 payload 新增/重命名字段，事件语义不兼容 | 使用 eventVersion、mappingVersion 和兼容层；未知字段保留在 raw payload |
| 模型解析结果不稳定 | 候选需求质量波动，用户信任下降 | 相同输入多次解析结果差异明显 | 规则解析保底；模型结果必须人工确认；保存 prompt 版本和置信度 |
| WP3 upsert 语义不清 | 重复需求或错误覆盖 | 重复导入产生多条资产，或更新覆盖人工编辑内容 | 已使用 externalRequirementId/sourceRef 做 WP3 幂等，发布前 dryRun 提供 diffSummary；正式发布仅自动更新 DRAFT IMPORT 资产，非 DRAFT 且有差异时返回冲突并保留候选历史 |
| 敏感信息进入模型或日志 | 合规风险 | 原文包含密钥、token、客户隐私或生产数据 | 复用 WP2 敏感内容阻断；日志和错误摘要脱敏；高敏项目默认禁用公开模型 |
| Webhook 被伪造或重放 | 数据污染和安全风险 | 签名失败、时间戳过期、事件 ID 重复 | 强制签名、时间窗口、幂等键、限流、白名单和审计；生产密钥优先通过 WP1 SecretProvider 解析，本地 fallback 可关闭 |
| 与并行开发冲突 | 前后端路由、权限点或实体命名冲突 | 同名菜单、权限点、表名、DTO 发生变更 | WP4 使用独立命名空间；变更前对齐当前基线；只通过公开应用服务依赖 WP1/WP2/WP3 |

## 10. MVP 准出清单

WP4 MVP 进入验收时需同时满足以下条件：

1. 文本、Markdown、Word、PDF、OCR、自研 webhook 六类输入链路均可在项目空间内跑通。
2. Confluence、飞书、钉钉、语雀仅作为预留连接器类型出现，不在验收中承诺真实同步。
3. 所有 WP4 API 使用统一 envelope、camelCase 字段、统一分页和稳定错误码。
4. 所有受保护接口完成 WP1 权限和项目/应用上下文校验。
5. 模型辅助解析仅通过 WP2 调用，默认 Prompt key 为 `wp4-document-requirement-parse`，并保存 `parseSource`、`modelInvocationId`、`modelProviderName`、`modelName`；WP2 策略阻断、预算超限、敏感内容阻断和模型失败均不得绕过 WP2，当前实现支持规则解析 fallback。
6. 当前导入、候选和发布接口以 `/imports` 为准，历史 `/batches` 建议路径不作为验收要求。
7. 候选支持 `status`、`sourceRef`、`keyword` 筛选，批量操作支持 versioned candidates。
8. 已确认候选项只能通过 WP3 应用服务写入需求资产，且重复导入具备幂等保护；dryRun 能返回 `CREATE`/`UPDATE`/`LINK_EXISTING`/`CONFLICT_REVIEW_REQUIRED`/跳过明细和 `diffSummary`，并保留 `source`、`sourceRef`、`sourceUrl`、`acceptanceCriteria` 追踪。
9. Webhook source 支持 `secretRef`、`eventVersion`、`mappingVersion` 和字段映射；事件支持 `sourceId`、`sourceCode`、`eventType`、`status`、`receivedFrom`、`receivedTo` 查询。
10. Webhook 支持签名校验、幂等、事件版本、字段映射、失败重放和审计；密钥解析优先调用 WP1 SecretProvider，配置映射、`wp4-webhook-default` 和 `secret://wp4/*` 仅作为 dev/test fallback，生产可通过 `WP4_LOCAL_WEBHOOK_SECRET_FALLBACK_ENABLED=false` 禁用；SecretProvider 成功解析结果按 `WP4_WEBHOOK_SECRET_CACHE_TTL_SECONDS` 本地缓存，`WP4_WEBHOOK_SECRET_CACHE_TTL_SECONDS=0` 可关闭，source 创建/更新会主动失效，配置/default fallback 不缓存；`WP4_WEBHOOK_SECRET_ROTATION_OVERLAP_SECONDS` 定义轮换重叠窗口，旧密钥至少保留 `max(TTL, rotationOverlap)`；外部 Vault/KMS provider 的健康摘要通过 `/api/v1/document-input/health` 的 `externalSecretProvider` 字段暴露，缓存状态通过 `webhookSecretCache*` 字段暴露；外部 provider 可通过 `WP1_EXTERNAL_SECRET_SIGNING_KEY_ID` 和 `WP1_EXTERNAL_SECRET_SIGNING_SECRET` 为 resolve/health 请求添加 HMAC-SHA256 签名；SecretProvider resolve 成功/失败审计只记录 `secretRefDigest` 和 provider/用途/作用域元数据，不记录完整 secretRef 或明文。
11. 审计覆盖输入源配置、导入、解析、确认、发布、webhook 接收和失败重试。
12. 当前指标覆盖导入批次、候选操作、发布、webhook、模型解析和外部 SecretProvider 健康；模型解析指标为 `veri.agent.document_input.model_parse` 与 `veri.agent.document_input.model_parse.candidates`，外部 SecretProvider 健康指标为 `veri.agent.document_input.secret_provider.health`。
13. 数据保留清理默认关闭，可通过 `WP4_RETENTION_CLEANUP_ENABLED=true` 开启；导入/候选和 webhook 事件保留天数分别由 `WP4_IMPORT_RETENTION_DAYS`、`WP4_WEBHOOK_EVENT_RETENTION_DAYS` 控制；清理前写入 `document_input_retention_archive`，清理计数输出 `veri.agent.document_input.retention.cleanup{target,result}`，并写入 `RETENTION_CLEANUP` 审计。
14. 服务端测试、前端 smoke、webhook smoke、二进制文档 smoke、AI 解析质量评测、metrics 覆盖和发布前端到端 smoke 均通过。
