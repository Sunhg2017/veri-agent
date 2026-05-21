# WP4 本轮执行拆解 - 候选确认与 Webhook 安全

| 项目 | 内容 |
|---|---|
| 工作包 | WP4 需求与文档输入 |
| 基线文档 | `doc/mvp/final/engineering/WP4-需求与文档输入-研发拆解与里程碑计划.md` |
| 本轮聚焦 | A 候选确认与 WP3 发布；B Webhook 安全与事件重放；C AI 文档解析 MVP；D 真实 Word/PDF/OCR 解析；E 生产级 SecretProvider；F AI 解析质量评测集 |
| 当前实现路径 | `/api/v1/document-input` |
| 当前连接器枚举 | `CUSTOM_API` 表示自研 API/Webhook 输入源 |
| 文档性质 | 本轮执行拆解与验收口径，不替代最终 PRD、OpenAPI 契约和测试用例 |
| 日期 | 2026-05-18 |

## 1. 本轮目标

本轮继续保留 Confluence、飞书、钉钉、语雀为预留连接器，同时把 Word、PDF、OCR 从预留提升为 P0 文本抽取能力，优先把 WP4 MVP 中最容易阻塞验收的六条链路做实：

1. A 线：从已解析候选项进入人工确认、批量确认、忽略、发布到 WP3，并可追踪发布结果。
2. B 线：从 `CUSTOM_API` webhook 安全接收事件，完成签名、幂等、防重放、失败记录、人工重放和审计闭环。
3. C 线：通过 WP2 模型接入完成 AI 文档解析 MVP，AI 候选仍进入人工确认，并在 WP2 阻断或失败时回退规则解析。
4. D 线：真实 Word/PDF/OCR 文档解析，抽取文本进入规则解析、AI 解析、候选确认和发布闭环。
5. E 线：生产级 SecretProvider，webhook 密钥优先通过 WP1 SecretProvider 解析，本地 fallback 仅限 dev/test。
6. F 线：AI 解析质量评测集，用 golden corpus 和阈值门禁保护 Prompt/解析器质量。

完成后，WP4 应具备“真实文档进入、AI 辅助解析、候选可控发布”和“外部事件可信进入”的最小准出能力。

## 2. 角色分工

| 角色 | 负责人范围 | 本轮关键产出 |
|---|---|---|
| PM/项目经理 | 范围冻结、跨 WP 协调、里程碑推进、验收口径确认 | 本执行拆解、每日风险同步、M1/M2 准出清单 |
| 后端负责人 | `/api/v1/document-input` API、AI 解析接入、Word/PDF/OCR 抽取、SecretProvider 适配、候选状态机、WP3 发布适配、webhook 安全与重放 | 服务端接口、状态流、WP2 fallback、二进制解析、SecretProvider、幂等策略、审计与错误码 |
| 前端负责人 | AI 解析来源展示、Word/PDF/OCR 导入入口、候选确认工作台、发布结果页、webhook 事件与重放入口 | 候选列表/详情、模型调用追踪、批量操作、失败事件列表、重放交互 |
| 测试负责人 | 契约测试、权限测试、二进制解析、AI 解析与 fallback、质量评测集、端到端 smoke、异常与安全用例 | AI 解析评测集、Word/PDF/OCR 测试集、候选发布测试集、webhook 安全测试集、回归报告 |
| WP2 负责人 | Prompt、模型调用策略、敏感内容阻断、预算与调用审计 | `wp4-document-requirement-parse` Prompt、调用日志、阻断错误码 |
| WP3 负责人 | 需求资产 upsert 契约、来源追踪、发布结果返回 | WP3 最小 upsert DTO、重复发布语义、资产详情跳转 |
| 安全/运维负责人 | 签名算法、SecretProvider、OCR 命令、限流、日志脱敏、指标告警 | webhook 安全配置、重放窗口策略、OCR 资源策略、告警阈值 |

## 3. 接口契约摘要

统一基础路径为 `/api/v1/document-input`。当前实现的导入记录、候选和发布记录统一使用 `/imports` 资源；早期拆解中的 `/batches` 属于历史建议路径，验收以当前实现、OpenAPI 和 smoke 为准。所有字段使用 camelCase；分页请求使用 `index`、`size`；响应沿用平台统一 envelope。除 webhook 接收端点外，接口必须经过 WP1 登录态、项目/应用上下文和权限校验。

当前权限点已从细粒度建议收敛为：`requirementInput:read`、`requirementInput:manage`、`requirementInput:import`、`requirementInput:candidate_review`、`requirementInput:publish`、`requirementInput:webhook_replay`。

### 3.1 A 线：候选确认与 WP3 发布

| 能力 | 方法与路径 | 契约摘要 | 当前权限 |
|---|---|---|---|
| 创建导入 | `POST /imports` | 创建文本/Markdown 导入记录并生成候选；保存 sourceCode、sourceRef、sourceUrl、标题、内容和项目上下文 | `requirementInput:import` |
| 查询导入 | `GET /imports`、`GET /imports/{importId}` | 按项目、输入源、类型、状态分页查询导入记录 | `requirementInput:read` |
| 查询导入候选 | `GET /imports/{importId}/candidates` | 支持按 `status`、`sourceRef`、`keyword` 分页查询；返回候选字段、原文引用、置信度、版本、确认状态 | `requirementInput:read` |
| 更新候选 | `PUT /candidates/{candidateId}` | 编辑标题、描述、验收标准、优先级、标签、外部来源字段；必须校验版本号 | `requirementInput:candidate_review` |
| 确认候选 | `POST /candidates/{candidateId}/confirm` | 将候选置为 confirmed，记录确认人、确认时间、确认版本 | `requirementInput:candidate_review` |
| 忽略候选 | `POST /candidates/{candidateId}/ignore` | 将候选置为 ignored，必须提交 ignoreReason | `requirementInput:candidate_review` |
| 批量确认/忽略 | `POST /candidates/batch-action` | 支持 confirm、ignore；支持 `candidateIds` 或携带版本号的 versioned candidates；限制单次数量，返回逐项结果 | `requirementInput:candidate_review` |
| 发布到 WP3 | `POST /imports/{importId}/publish` | 发布已确认候选；支持 dryRun、candidateIds；返回创建、更新、关联既有资产、评审冲突、跳过、失败明细，更新项带 diffSummary | `requirementInput:publish` |
| 查询发布记录 | `GET /imports/{importId}/publish-records` | 返回 WP3 assetId/existingRequirementId、externalRequirementId、sourceFragmentId、sourceRef、发布状态、差异摘要、错误摘要 | `requirementInput:read` |

候选状态建议：`pending`、`confirmed`、`ignored`、`publishing`、`published`、`publishFailed`。发布必须通过 WP3 应用服务，不直连 WP3 表。

### 3.1.1 C 线：AI 文档解析 MVP

AI 解析只通过 WP2 `ModelAccessService`，不在 WP4 直连外部模型 SDK。`WP4_MODEL_PARSE_ENABLED` 开启后，文本、Markdown 和 `CUSTOM_API` 导入调用默认 Prompt key `wp4-document-requirement-parse`，模型结果以 `parseSource=MODEL` 保存到候选项，并返回 `modelInvocationId`、`modelProviderName`、`modelName` 供前端和审计追踪。

模型解析策略为“模型优先、规则补缺、失败兜底”：模型成功时按标题去重合并规则解析结果；WP2 敏感内容阻断、策略阻断、预算超限、供应商不可用或响应不可解析时，若规则解析已有结果，则导入仍成功并记录 fallback 审计与 `veri.agent.document_input.model_parse` 指标；若规则和模型均无结果，则导入失败并保留错误摘要。

### 3.2 B 线：Webhook 安全与事件重放

| 能力 | 方法与路径 | 契约摘要 | 当前权限 |
|---|---|---|---|
| 查询输入源与健康 | `GET /sources`、`GET /sources/{sourceId}/health` | 查询输入源配置和健康状态；健康返回签名算法、secretRef 配置状态、eventVersion 等 | `requirementInput:read` |
| 配置 `CUSTOM_API` 输入源 | `POST /sources`、`PUT /sources/{sourceId}` | connectorType 必须为 `CUSTOM_API`；保存 sourceCode、secretRef、eventVersion、mappingVersion、字段映射 | `requirementInput:manage` |
| Webhook 接收 | `POST /webhooks/{sourceCode}` | 接收外部事件；校验签名、时间戳、eventId、idempotencyKey、eventVersion、payload 大小 | 外部签名认证 |
| 查询事件 | `GET /webhook-events` | 支持按 `sourceId`、`sourceCode`、`eventType`、`status`、`receivedFrom`、`receivedTo` 分页查询；payload 默认脱敏 | `requirementInput:read` |
| 查看事件详情 | `GET /webhook-events/{eventId}` | 返回校验结果、处理阶段、错误码、重试次数、traceId、脱敏 payload | `requirementInput:read` |
| 人工重放 | `POST /webhook-events/{eventId}/replay` | 仅允许 failed、deadLetter、replayable 状态；重放沿用原 eventId 幂等保护，生成 replayTraceId | `requirementInput:webhook_replay` |

支持事件类型：`requirement.created`、`requirement.updated`、`requirement.statusChanged`、`requirement.archived`。事件源类型必须落在 `CUSTOM_API`，不得新增 `CUSTOM_WEBHOOK` 等重复枚举。

当前 webhook 密钥解析的本轮口径为优先调用 WP1 `SecretProvider` 抽象；`db` profile 下支持 `LOCAL_ENCRYPTED` 的 `secret_reference` + `secret_local_store` 密文解析，并校验 ACTIVE、未过期、`WEBHOOK_SIGNING` 用途以及 `CONFIG + document_input_source.id` 作用域。外部 Vault/KMS provider 通过 `WP1_EXTERNAL_SECRET_RESOLVE_URL` resolve，支持 timeout、短暂失败 retry、`WP1_EXTERNAL_SECRET_HEALTH_URL` 健康探测，以及可选 `WP1_EXTERNAL_SECRET_SIGNING_KEY_ID` + `WP1_EXTERNAL_SECRET_SIGNING_SECRET` HMAC-SHA256 请求签名；`/api/v1/document-input/health` 只返回脱敏健康摘要，不暴露 endpoint、token、签名密钥、secretRef 或明文。配置映射、`wp4-webhook-default` 和 `secret://wp4/*` 仅作为 dev/test fallback，可通过 `WP4_LOCAL_WEBHOOK_SECRET_FALLBACK_ENABLED=false` 禁用。

### 3.3 Webhook 安全头建议

| Header | 必填 | 用途 |
|---|---|---|
| `X-VA-Timestamp` | 是 | 秒级或毫秒级时间戳，用于时间窗口校验 |
| `X-VA-Event-Id` | 是 | 外部事件唯一 ID，用于事件幂等 |
| `X-VA-Idempotency-Key` | 是 | 业务幂等键，用于重复投递去重 |
| `X-VA-Signature` | 是 | HMAC 签名，覆盖 timestamp、eventId、idempotencyKey 和原始 body |
| `X-VA-Event-Version` | 是 | 外部事件版本，配合 mappingVersion 做兼容处理 |

签名失败、时间戳超窗、事件 ID 重复、幂等键冲突、payload 超限、sourceCode 不存在或停用，均必须拒绝处理并写入审计或安全日志。错误摘要不得输出 secret、token、cookie 或完整签名。

cURL、Node.js 和 Java 签名样例及联调排错口径见 `doc/mvp/final/engineering/WP4-Webhook签名样例与联调说明.md`。外部系统联调必须以 `timestamp.eventId.idempotencyKey.rawBody` 为唯一 canonical string，并保证签名时的 raw body 与 HTTP 实际发送内容完全一致。

## 4. 开发任务清单

### A 线：候选确认与 WP3 发布

| 编号 | 任务 | 负责人 | 优先级 | 完成口径 |
|---|---|---|---|---|
| A1 | 冻结候选状态机和状态转换规则 | 后端、测试、PM | P0 | 状态枚举、允许转换、错误码和审计事件达成一致 |
| A2 | 补齐候选查询、编辑、确认、忽略接口契约 | 后端、前端 | P0 | 前后端字段、分页、错误响应、乐观锁版本字段一致 |
| A3 | 实现批量确认/忽略的逐项结果模型 | 后端、前端 | P0 | 部分成功可返回明细，失败项不影响成功项落库；支持 versioned candidates |
| A4 | 对齐 WP3 upsert 最小 DTO | 后端、WP3 负责人 | P0 | 明确 externalRequirementId、sourceFragmentId、title、description、acceptanceCriteria、status、source、sourceRef、sourceUrl 映射 |
| A5 | 实现发布 dryRun 和正式发布 | 后端 | P0 | dryRun 只返回创建/更新/冲突预览；正式发布写入 WP3 应用服务 |
| A6 | 建立发布记录和来源追踪 | 后端、WP3 负责人 | P0 | 可从批次追溯到候选、原文片段、WP3 assetId 和外部 ID |
| A7 | 候选确认工作台页面 | 前端 | P0 | 支持筛选、编辑、确认、忽略、批量操作、原文引用查看 |
| A8 | 发布预览和发布结果页面 | 前端 | P0 | 展示 dryRun 差异、发布明细、失败原因、WP3 跳转 |
| A9 | 权限、并发和重复发布测试 | 测试 | P0 | 覆盖越权、乐观锁冲突、重复发布、部分失败、回滚或补偿语义 |

### C 线：AI 文档解析 MVP

| 编号 | 任务 | 负责人 | 优先级 | 完成口径 |
|---|---|---|---|---|
| C1 | 接入 WP2 模型解析 Prompt | 后端、WP2 负责人 | P0 | `WP4_MODEL_PARSE_ENABLED=true` 时通过 `wp4-document-requirement-parse` 调用 WP2，不直连模型 SDK |
| C2 | 保存 AI 候选追踪字段 | 后端 | P0 | 候选与导入预览返回 `parseSource`、`modelInvocationId`、`modelProviderName`、`modelName` |
| C3 | 实现模型与规则结果合并 | 后端、测试 | P0 | 模型结果优先，规则结果补缺，按标题去重；AI 候选仍为待确认状态 |
| C4 | 实现 WP2 失败 fallback | 后端、测试 | P0 | 敏感内容阻断、策略阻断、预算超限、模型不可用、响应不可解析时，规则解析可兜底且审计可追踪 |
| C5 | 前端展示 AI 来源和调用追踪 | 前端 | P0 | 候选卡片展示 parseSource、置信度、modelInvocationId、provider/model |
| C6 | AI 解析 smoke 与指标 | 测试、运维 | P0 | smoke 在模型开关开启时校验 AI 候选；指标覆盖模型解析尝试和候选数量 |

### D 线：真实 Word/PDF/OCR 解析

| 编号 | 任务 | 负责人 | 优先级 | 完成口径 |
|---|---|---|---|---|
| D1 | Word doc/docx 文本抽取 | 后端、测试 | P0 | Apache POI 抽取真实 docx/doc 文本；支持 plain text、raw base64、data URL 输入；解析后生成候选 |
| D2 | PDF 文本抽取 | 后端、测试 | P0 | PDFBox 抽取文本 PDF；扫描件缺 OCR provider 时稳定失败并返回可读错误 |
| D3 | OCR 命令 provider | 后端、运维、测试 | P0 | `WP4_OCR_COMMAND` 可接收 `{input}` 临时文件，返回 OCR 文本；超时、空输出、失败退出码均有错误 |
| D4 | 前端类型与提示 | 前端 | P0 | `WORD/PDF/OCR` 不再标记预留；导入表单提示可粘贴 base64 或 data URL |
| D5 | 二进制解析 smoke | 测试 | P0 | `scripts/wp4_binary_document_smoke.sh` 覆盖真实 docx、真实 PDF 和 OCR 命令适配 |

### E 线：生产级 SecretProvider

| 编号 | 任务 | 负责人 | 优先级 | 完成口径 |
|---|---|---|---|---|
| E1 | 平台 SecretProvider 抽象 | 架构、WP1 | P0 | 提供 `SecretProvider`、`SecretResolveContext`、`ResolvedSecret`；调用方只传 secretRef，不接触明文存储 |
| E2 | LOCAL_ENCRYPTED 实现 | WP1、后端、安全 | P0 | db profile 读取 `secret_reference`、`secret_provider`、`secret_local_store`，用 `WP1_LOCAL_SECRET_MASTER_KEY` 解密 |
| E3 | WP4 webhook resolver 接入 | 后端、安全 | P0 | resolver 优先调用 SecretProvider；配置 fallback 可关闭；用途不匹配、过期、撤销、provider 不可用均拒绝 |
| E4 | SecretProvider 测试 | 测试 | P0 | provider 优先级、fallback 关闭、未知 ref、用途校验、无明文泄露测试通过 |
| E5 | 外部 provider 健康摘要 | 后端、安全/运维 | P1 | 外部 Vault/KMS provider 支持 timeout/retry/health URL；健康响应和指标仅输出脱敏状态摘要 |

### F 线：AI 解析质量评测集

| 编号 | 任务 | 负责人 | 优先级 | 完成口径 |
|---|---|---|---|---|
| F1 | Golden corpus | PM、测试、WP2 | P0 | 建立 `wp4-ai-parse-eval/corpus.json`，记录输入、模型响应、期望标题和优先级；当前 `corpusVersion=wp4-c1-2026-05-22` 扩展到 12 个样本、28 条期望需求 |
| F2 | 质量指标 | 测试、后端 | P0 | 标题召回、优先级准确率、验收标准覆盖率均有阈值；额外校验样本 ID 唯一、版本一致、行业/难度/覆盖标签完整、六类 sourceType 各至少 2 个样本 |
| F3 | 准出脚本 | 测试、运维 | P0 | `scripts/wp4_ai_parse_quality_eval.sh` 一键运行并输出 prompt/parser 版本、分桶指标、scenario 数和期望需求数 |

### B 线：Webhook 安全与事件重放

| 编号 | 任务 | 负责人 | 优先级 | 完成口径 |
|---|---|---|---|---|
| B1 | 冻结 `CUSTOM_API` webhook 配置项 | 后端、安全/运维、PM | P0 | sourceCode、secretRef、eventVersion、mappingVersion、启停状态和字段映射稳定 |
| B2 | 实现签名校验和时间窗口校验 | 后端、安全/运维 | P0 | HMAC 覆盖原始 body；支持时钟偏移配置；失败请求不进入业务处理 |
| B3 | 实现 eventId 和 idempotencyKey 幂等 | 后端 | P0 | 重复投递返回可预期结果，不重复生成候选或发布 |
| B4 | 实现事件版本与字段映射兼容层 | 后端 | P0 | 未知字段保留在 raw payload；未知版本进入 failed 或 unsupportedVersion 状态 |
| B5 | 事件处理状态与失败记录 | 后端 | P0 | 状态覆盖 received、validated、mapped、processed、failed、deadLetter、replayed |
| B6 | 人工重放接口与重放审计 | 后端、前端 | P0 | 只允许可重放状态；记录 replayBy、replayAt、replayTraceId 和结果 |
| B7 | Webhook 事件列表和详情页 | 前端 | P0 | 可筛选失败事件、查看脱敏 payload、错误码、traceId、重放结果 |
| B8 | 限流、payload 上限和日志脱敏 | 安全/运维、后端 | P0 | 配置项可调；安全失败可观测；日志不泄露敏感信息 |
| B9 | Webhook 安全测试与重放测试 | 测试 | P0 | 覆盖签名失败、过期请求、重复事件、幂等冲突、未知版本、死信重放 |

## 5. 验收标准

### A 线验收

1. 用户可在项目空间查看解析候选，按 `status`、`sourceRef`、`keyword` 筛选，并查看原文引用、来源类型、AI/规则来源和置信度。
2. 候选可编辑、确认、忽略；忽略必须记录原因；并发编辑必须通过版本号阻断脏写。
3. 批量确认/忽略支持逐项结果返回，可携带候选版本号，部分失败时成功项不回滚。
4. 发布前 dryRun 能识别创建、更新、关联既有资产、评审冲突、跳过项；更新项返回 `diffSummary`，非 DRAFT 既有资产存在差异时返回 `CONFLICT_REVIEW_REQUIRED`。
5. 正式发布只调用 WP3 应用服务，不直接读写 WP3 表。
6. 重复发布同一批次或同一 `externalRequirementId` 不产生重复 WP3 需求资产。
7. 发布记录可追溯 importId、candidateId、sourceFragmentId、externalRequirementId、WP3 assetId、sourceRef、操作者和 traceId；WP3 需求资产可回读 `source`、`sourceRef`、`sourceUrl`、`acceptanceCriteria`。
8. 越权项目、无发布权限、候选未确认、候选已忽略等场景返回稳定错误码并写审计。

### C 线验收

1. 启用 `WP4_MODEL_PARSE_ENABLED=true` 后，文本、Markdown 和 `CUSTOM_API` 导入可生成 `parseSource=MODEL` 的候选项。
2. 每个 AI 候选返回 `modelInvocationId`、`modelProviderName`、`modelName`，可在 WP2 调用日志中按 `actorService=wp4-document-input` 追踪。
3. AI 解析结果不得绕过人工确认；发布到 WP3 仍只处理已确认候选。
4. WP2 敏感内容阻断、策略阻断、预算超限或供应商失败时，规则解析 fallback 可用，失败原因进入审计和指标。
5. `veri.agent.document_input.model_parse` 和 `veri.agent.document_input.model_parse.candidates` 可被 actuator metrics 查询。

### D 线验收

1. `WORD`、`PDF`、`OCR` sourceType 可创建为 ENABLED，health 不再按预留类型处理。
2. 真实 docx 和真实文本 PDF 可通过 `/imports` 的 base64/data URL 内容解析成候选需求。
3. `OCR` 在配置 `WP4_OCR_COMMAND` 后可将命令输出文本转为候选；未配置时明确返回不可用。
4. `/imports` 有内容大小上限，二进制解码有大小上限，超限不进入解析。
5. Word/PDF/OCR 候选仍必须人工确认后才能发布到 WP3。

### E 线验收

1. WP4 webhook 签名密钥优先通过 WP1 SecretProvider 解析，不把明文写入 source、响应或审计。
2. `LOCAL_ENCRYPTED` provider 校验 provider enabled、secret active、未过期、用途为 `WEBHOOK_SIGNING`。
3. `WP4_LOCAL_WEBHOOK_SECRET_FALLBACK_ENABLED=false` 时，未接入 SecretProvider 的 ref 必须拒绝。
4. source A/B 不同 secretRef 时，交叉签名不得通过。
5. 外部 Vault/KMS provider 不可用、健康端点未配置或返回异常时，健康摘要可观测且不泄露 endpoint、token、secretRef 或明文。

### F 线验收

1. `scripts/wp4_ai_parse_quality_eval.sh` 可一键运行并输出标题召回、优先级准确率、验收标准覆盖率。
2. 初始门禁阈值：标题召回 ≥ 0.80，优先级准确率 ≥ 0.80，验收标准覆盖率 ≥ 0.75。
3. corpus 必须保留版本、行业、难度和覆盖标签；`TEXT/MARKDOWN/WORD/PDF/OCR/CUSTOM_API` 每类至少 2 个样本，并覆盖长文档、表格需求、歧义优先级、异常格式和 OCR 低置信度场景。
4. 评测失败阻断 WP4 AI 解析相关变更准出。

### B 线验收

1. `CUSTOM_API` 输入源可配置 webhook endpoint、secretRef、eventVersion、mappingVersion 和字段映射。
2. `POST /api/v1/document-input/webhooks/{sourceCode}` 必须校验签名、时间戳、eventId、idempotencyKey 和 payload 上限。
3. 签名失败、过期请求、重复事件、停用 source、未知 source 均不会进入业务处理，并可追踪 traceId。
4. `requirement.created`、`requirement.updated`、`requirement.statusChanged`、`requirement.archived` 均能进入映射处理。
5. 重复投递不重复生成候选，不重复触发 WP3 发布。
6. 失败事件可按 `sourceId`、`sourceCode`、`eventType`、`status`、`receivedFrom`、`receivedTo` 查询，可查看脱敏详情，并可在允许状态下人工重放。
7. 重放必须记录原事件、重放人、重放时间、重放 traceId、重放结果；超过重试上限进入 deadLetter。
8. webhook 安全失败、业务处理失败、重放成功/失败均有审计或安全日志，且不泄露密钥和完整签名。

## 6. 风险与应对

| 风险 | 影响 | 触发信号 | 应对 |
|---|---|---|---|
| WP3 upsert 语义未冻结 | 发布链路阻塞或产生重复资产 | externalRequirementId/sourceFragmentId 去重规则不清 | M0 先冻结最小 DTO 和幂等规则；必要时先接 WP3 mock |
| 候选编辑与发布并发冲突 | 用户覆盖彼此修改或发布旧版本 | 同一候选多人编辑、确认后继续修改 | 候选接口强制版本号；发布读取 confirmedVersion |
| Webhook 签名覆盖范围不一致 | 合法事件被拒或伪造事件通过 | 外部平台与服务端拼签规则不一致 | 按 `WP4-Webhook签名样例与联调说明.md` 固定 canonical string，并纳入联调用例 |
| 外部事件重复或乱序 | 候选状态错误、WP3 状态回退 | 同一外部需求快速更新或重试投递 | 使用 eventTime、eventVersion、idempotencyKey 和业务版本判断是否跳过 |
| 重放绕过幂等保护 | 重复生成候选或重复发布 | 人工多次点击重放或自动重试并发 | 重放仍走原处理链路和幂等键；重放请求自身增加操作幂等 |
| 错误日志泄露 payload 敏感内容 | 合规和安全风险 | 失败事件详情展示完整 token、cookie 或隐私字段 | 统一脱敏规则；详情页默认展示脱敏 payload，原文访问需更高权限或不开放 |
| 范围再次扩展到协作文档连接器 | 本轮延期 | 要求 Confluence/飞书/钉钉/语雀真实解析 | 本轮新增 `WORD`、`PDF`、`OCR` 文本抽取，协作文档连接器保持预留状态 |

## 7. 里程碑

| 里程碑 | 建议时点 | 目标 | 准出物 |
|---|---|---|---|
| M0 契约冻结 | D1 | 冻结 A/B 两线 API、状态机、权限点、错误码、WP3 upsert DTO、webhook 签名规则 | 契约表、状态流、签名样例、测试用例清单 |
| M1 A 线服务端闭环 | D2-D3 | 候选编辑确认、批量操作、dryRun、正式发布和发布记录可用 | 服务端接口、单元/集成测试、WP3 mock 或联调结果 |
| M2 B 线服务端闭环 | D3-D4 | `CUSTOM_API` webhook 安全接收、幂等、失败记录和重放可用 | 安全校验用例、事件状态机、重放审计 |
| M3 前端工作台联调 | D4-D5 | 候选确认/发布页面、webhook 事件/重放页面完成主流程联调 | 前端 smoke、接口联调记录、权限态校验 |
| M3.5 D/E/F 线收敛 | D4-D5 | Word/PDF/OCR、SecretProvider、AI 质量评测完成可验证闭环 | 二进制文档 smoke、SecretProvider 测试、AI 评测报告 |
| M4 发布验收 | D5 | A/B/C/D/E/F 六线通过端到端验收，形成 WP4 本轮发布说明 | smoke 报告、风险清单关闭记录、验收结论 |

## 8. 本轮准出清单

1. 所有新增或变更接口统一落在 `/api/v1/document-input`。
2. 自研 webhook 输入源枚举统一使用 `CUSTOM_API`。
3. A 线至少完成“候选编辑 -> 确认 -> dryRun -> 发布 WP3 -> 查询发布记录”主链路。
4. B 线至少完成“签名校验 -> 幂等接收 -> 字段映射 -> 失败记录 -> 人工重放”主链路。
5. 权限按 `requirementInput:read/manage/import/candidate_review/publish/webhook_replay` 收敛；审计、错误码、traceId、日志脱敏均进入验收范围。
6. `WORD`、`PDF`、`OCR` 进入本轮可用范围；Confluence、飞书、钉钉、语雀继续预留。
7. 测试必须覆盖正常流、越权流、并发流、重复发布、签名失败、过期请求、停用 source、未知 source、重复事件、死信重放、Word/PDF/OCR 解析、SecretProvider 和 AI 质量评测。
