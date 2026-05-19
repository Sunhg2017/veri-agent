# WP4 本轮执行拆解 - 候选确认与 Webhook 安全

| 项目 | 内容 |
|---|---|
| 工作包 | WP4 需求与文档输入 |
| 基线文档 | `doc/mvp/final/engineering/WP4-需求与文档输入-研发拆解与里程碑计划.md` |
| 本轮聚焦 | A 候选确认与 WP3 发布；B Webhook 安全与事件重放 |
| 当前实现路径 | `/api/v1/document-input` |
| 当前连接器枚举 | `CUSTOM_API` 表示自研 API/Webhook 输入源 |
| 文档性质 | 本轮执行拆解与验收口径，不替代最终 PRD、OpenAPI 契约和测试用例 |
| 日期 | 2026-05-18 |

## 1. 本轮目标

本轮不扩展 Word、PDF、Confluence、飞书、钉钉、语雀等预留连接器能力，优先把 WP4 MVP 中最容易阻塞验收的两条链路做实：

1. A 线：从已解析候选项进入人工确认、批量确认、忽略、发布到 WP3，并可追踪发布结果。
2. B 线：从 `CUSTOM_API` webhook 安全接收事件，完成签名、幂等、防重放、失败记录、人工重放和审计闭环。

完成后，WP4 应具备“候选可控发布”和“外部事件可信进入”的最小准出能力。

## 2. 角色分工

| 角色 | 负责人范围 | 本轮关键产出 |
|---|---|---|
| PM/项目经理 | 范围冻结、跨 WP 协调、里程碑推进、验收口径确认 | 本执行拆解、每日风险同步、M1/M2 准出清单 |
| 后端负责人 | `/api/v1/document-input` API、候选状态机、WP3 发布适配、webhook 安全与重放 | 服务端接口、状态流、幂等策略、审计与错误码 |
| 前端负责人 | 候选确认工作台、发布结果页、webhook 事件与重放入口 | 候选列表/详情、批量操作、失败事件列表、重放交互 |
| 测试负责人 | 契约测试、权限测试、端到端 smoke、异常与安全用例 | 候选发布测试集、webhook 安全测试集、回归报告 |
| WP3 负责人 | 需求资产 upsert 契约、来源追踪、发布结果返回 | WP3 最小 upsert DTO、重复发布语义、资产详情跳转 |
| 安全/运维负责人 | 签名算法、密钥引用、限流、日志脱敏、指标告警 | webhook 安全配置、重放窗口策略、告警阈值 |

## 3. 接口契约摘要

统一基础路径为 `/api/v1/document-input`。所有字段使用 camelCase；分页请求使用 `index`、`size`；响应沿用平台统一 envelope。除 webhook 接收端点外，接口必须经过 WP1 登录态、项目/应用上下文和权限校验。

### 3.1 A 线：候选确认与 WP3 发布

| 能力 | 方法与路径 | 契约摘要 | 权限建议 |
|---|---|---|---|
| 查询批次候选 | `GET /batches/{batchId}/candidates` | 支持按状态、来源、关键字分页查询；返回候选字段、原文引用、置信度、版本、确认状态 | `requirementInput:candidate:view` |
| 更新候选 | `PUT /candidates/{candidateId}` | 编辑标题、描述、验收标准、优先级、标签、外部来源字段；必须校验版本号 | `requirementInput:candidate:edit` |
| 确认候选 | `POST /candidates/{candidateId}/confirm` | 将候选置为 confirmed，记录确认人、确认时间、确认版本 | `requirementInput:candidate:confirm` |
| 忽略候选 | `POST /candidates/{candidateId}/ignore` | 将候选置为 ignored，必须提交 ignoreReason | `requirementInput:candidate:confirm` |
| 批量确认/忽略 | `POST /candidates/batch-action` | 支持 confirm、ignore；限制单次数量，返回逐项结果 | `requirementInput:candidate:confirm` |
| 发布到 WP3 | `POST /batches/{batchId}/publish` | 发布已确认候选；支持 dryRun、candidateIds、publishMode；返回创建/更新/跳过/失败明细 | `requirementInput:publish` |
| 查询发布记录 | `GET /batches/{batchId}/publish-records` | 返回 WP3 assetId、externalRequirementId、sourceFragmentId、发布状态、错误摘要 | `requirementInput:publish:view` |

候选状态建议：`pending`、`confirmed`、`ignored`、`publishing`、`published`、`publishFailed`。发布必须通过 WP3 应用服务，不直连 WP3 表。

### 3.2 B 线：Webhook 安全与事件重放

| 能力 | 方法与路径 | 契约摘要 | 权限建议 |
|---|---|---|---|
| 配置 `CUSTOM_API` 输入源 | `POST /sources`、`PUT /sources/{sourceId}` | connectorType 必须为 `CUSTOM_API`；保存 sourceCode、secretRef、eventVersion、mappingVersion、字段映射 | `requirementInput:source:manage` |
| Webhook 接收 | `POST /webhooks/{sourceCode}` | 接收外部事件；校验签名、时间戳、eventId、idempotencyKey、eventVersion、payload 大小 | 外部签名认证 |
| 查询事件 | `GET /webhook-events` | 支持按 sourceId、eventType、status、时间范围分页查询；payload 默认脱敏 | `requirementInput:webhook:view` |
| 查看事件详情 | `GET /webhook-events/{eventId}` | 返回校验结果、处理阶段、错误码、重试次数、traceId、脱敏 payload | `requirementInput:webhook:view` |
| 人工重放 | `POST /webhook-events/{eventId}/replay` | 仅允许 failed、deadLetter、replayable 状态；重放沿用原 eventId 幂等保护，生成 replayTraceId | `requirementInput:webhook:replay` |

支持事件类型：`requirement.created`、`requirement.updated`、`requirement.statusChanged`、`requirement.archived`。事件源类型必须落在 `CUSTOM_API`，不得新增 `CUSTOM_WEBHOOK` 等重复枚举。

### 3.3 Webhook 安全头建议

| Header | 必填 | 用途 |
|---|---|---|
| `X-VA-Timestamp` | 是 | 秒级或毫秒级时间戳，用于时间窗口校验 |
| `X-VA-Event-Id` | 是 | 外部事件唯一 ID，用于事件幂等 |
| `X-VA-Idempotency-Key` | 是 | 业务幂等键，用于重复投递去重 |
| `X-VA-Signature` | 是 | HMAC 签名，覆盖 timestamp、eventId、idempotencyKey 和原始 body |
| `X-VA-Event-Version` | 是 | 外部事件版本，配合 mappingVersion 做兼容处理 |

签名失败、时间戳超窗、事件 ID 重复、幂等键冲突、payload 超限、sourceCode 不存在或停用，均必须拒绝处理并写入审计或安全日志。错误摘要不得输出 secret、token、cookie 或完整签名。

## 4. 开发任务清单

### A 线：候选确认与 WP3 发布

| 编号 | 任务 | 负责人 | 优先级 | 完成口径 |
|---|---|---|---|---|
| A1 | 冻结候选状态机和状态转换规则 | 后端、测试、PM | P0 | 状态枚举、允许转换、错误码和审计事件达成一致 |
| A2 | 补齐候选查询、编辑、确认、忽略接口契约 | 后端、前端 | P0 | 前后端字段、分页、错误响应、乐观锁版本字段一致 |
| A3 | 实现批量确认/忽略的逐项结果模型 | 后端、前端 | P0 | 部分成功可返回明细，失败项不影响成功项落库 |
| A4 | 对齐 WP3 upsert 最小 DTO | 后端、WP3 负责人 | P0 | 明确 externalRequirementId、sourceFragmentId、title、description、acceptanceCriteria、status 映射 |
| A5 | 实现发布 dryRun 和正式发布 | 后端 | P0 | dryRun 只返回创建/更新/冲突预览；正式发布写入 WP3 应用服务 |
| A6 | 建立发布记录和来源追踪 | 后端、WP3 负责人 | P0 | 可从批次追溯到候选、原文片段、WP3 assetId 和外部 ID |
| A7 | 候选确认工作台页面 | 前端 | P0 | 支持筛选、编辑、确认、忽略、批量操作、原文引用查看 |
| A8 | 发布预览和发布结果页面 | 前端 | P0 | 展示 dryRun 差异、发布明细、失败原因、WP3 跳转 |
| A9 | 权限、并发和重复发布测试 | 测试 | P0 | 覆盖越权、乐观锁冲突、重复发布、部分失败、回滚或补偿语义 |

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

1. 用户可在项目空间查看解析候选，按状态筛选，并查看原文引用、来源类型和置信度。
2. 候选可编辑、确认、忽略；忽略必须记录原因；并发编辑必须通过版本号阻断脏写。
3. 批量确认/忽略支持逐项结果返回，部分失败时成功项不回滚。
4. 发布前 dryRun 能识别创建、更新、跳过和冲突项。
5. 正式发布只调用 WP3 应用服务，不直接读写 WP3 表。
6. 重复发布同一批次或同一 `externalRequirementId` 不产生重复 WP3 需求资产。
7. 发布记录可追溯 batchId、candidateId、sourceFragmentId、externalRequirementId、WP3 assetId、操作者和 traceId。
8. 越权项目、无发布权限、候选未确认、候选已忽略等场景返回稳定错误码并写审计。

### B 线验收

1. `CUSTOM_API` 输入源可配置 webhook endpoint、secretRef、eventVersion、mappingVersion 和字段映射。
2. `POST /api/v1/document-input/webhooks/{sourceCode}` 必须校验签名、时间戳、eventId、idempotencyKey 和 payload 上限。
3. 签名失败、过期请求、重复事件、停用 source、未知 source 均不会进入业务处理，并可追踪 traceId。
4. `requirement.created`、`requirement.updated`、`requirement.statusChanged`、`requirement.archived` 均能进入映射处理。
5. 重复投递不重复生成候选，不重复触发 WP3 发布。
6. 失败事件可查询、可查看脱敏详情，并可在允许状态下人工重放。
7. 重放必须记录原事件、重放人、重放时间、重放 traceId、重放结果；超过重试上限进入 deadLetter。
8. webhook 安全失败、业务处理失败、重放成功/失败均有审计或安全日志，且不泄露密钥和完整签名。

## 6. 风险与应对

| 风险 | 影响 | 触发信号 | 应对 |
|---|---|---|---|
| WP3 upsert 语义未冻结 | 发布链路阻塞或产生重复资产 | externalRequirementId/sourceFragmentId 去重规则不清 | M0 先冻结最小 DTO 和幂等规则；必要时先接 WP3 mock |
| 候选编辑与发布并发冲突 | 用户覆盖彼此修改或发布旧版本 | 同一候选多人编辑、确认后继续修改 | 候选接口强制版本号；发布读取 confirmedVersion |
| Webhook 签名覆盖范围不一致 | 合法事件被拒或伪造事件通过 | 外部平台与服务端拼签规则不一致 | 提供签名样例、固定 canonical string，并纳入联调用例 |
| 外部事件重复或乱序 | 候选状态错误、WP3 状态回退 | 同一外部需求快速更新或重试投递 | 使用 eventTime、eventVersion、idempotencyKey 和业务版本判断是否跳过 |
| 重放绕过幂等保护 | 重复生成候选或重复发布 | 人工多次点击重放或自动重试并发 | 重放仍走原处理链路和幂等键；重放请求自身增加操作幂等 |
| 错误日志泄露 payload 敏感内容 | 合规和安全风险 | 失败事件详情展示完整 token、cookie 或隐私字段 | 统一脱敏规则；详情页默认展示脱敏 payload，原文访问需更高权限或不开放 |
| 范围再次扩展到非 MVP 连接器 | 本轮延期 | 要求 Word/PDF/协作文档真实解析 | 本轮只接受 `TEXT`、`MARKDOWN`、`CUSTOM_API`，其他类型仅保持预留状态 |

## 7. 里程碑

| 里程碑 | 建议时点 | 目标 | 准出物 |
|---|---|---|---|
| M0 契约冻结 | D1 | 冻结 A/B 两线 API、状态机、权限点、错误码、WP3 upsert DTO、webhook 签名规则 | 契约表、状态流、签名样例、测试用例清单 |
| M1 A 线服务端闭环 | D2-D3 | 候选编辑确认、批量操作、dryRun、正式发布和发布记录可用 | 服务端接口、单元/集成测试、WP3 mock 或联调结果 |
| M2 B 线服务端闭环 | D3-D4 | `CUSTOM_API` webhook 安全接收、幂等、失败记录和重放可用 | 安全校验用例、事件状态机、重放审计 |
| M3 前端工作台联调 | D4-D5 | 候选确认/发布页面、webhook 事件/重放页面完成主流程联调 | 前端 smoke、接口联调记录、权限态校验 |
| M4 发布验收 | D5 | A/B 两线通过端到端验收，形成 WP4 本轮发布说明 | smoke 报告、风险清单关闭记录、验收结论 |

## 8. 本轮准出清单

1. 所有新增或变更接口统一落在 `/api/v1/document-input`。
2. 自研 webhook 输入源枚举统一使用 `CUSTOM_API`。
3. A 线至少完成“候选编辑 -> 确认 -> dryRun -> 发布 WP3 -> 查询发布记录”主链路。
4. B 线至少完成“签名校验 -> 幂等接收 -> 字段映射 -> 失败记录 -> 人工重放”主链路。
5. 权限、审计、错误码、traceId、日志脱敏均进入验收范围。
6. 文本、Markdown 和 `CUSTOM_API` 之外的连接器不进入本轮可用范围。
7. 测试必须覆盖正常流、越权流、并发流、重复发布、签名失败、重放攻击、重复事件和死信重放。
