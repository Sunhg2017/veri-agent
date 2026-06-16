# WP10 报告与失败诊断 - M5B 缺陷草稿交付说明

| 项目 | 内容 |
|---|---|
| 里程碑 | M5B 缺陷草稿 |
| 当前口径 | 完成平台内缺陷草稿生成、审阅状态、masked external payload preview、持久化、OpenAPI、smoke 和 DB contract；不写外部缺陷系统，不实现前端工作台 |
| 日期 | 2026-06-17 |

## 1. 目标和范围

M5B 目标是在 READY 报告基础上生成可人工确认的缺陷草稿。草稿只使用 WP10 已持久化的 aggregate-only report summary、latest diagnosis 和 evidence manifest digest，输出标题、复现摘要、影响范围、优先级建议、证据引用和 masked payload preview。

本轮范围：

1. `POST /api/v1/reports/{id}/defect-drafts` 创建平台内 `DRAFT` 草稿。
2. `PATCH /api/v1/reports/{id}/defect-drafts/{draftId}` 支持 `DRAFT -> REVIEWED/DISMISSED`，拒绝非法状态流。
3. `ReportDefectDraftService` 独立承载草稿生成、状态流、payload preview 和审计。
4. `ReportingRepository`、MyBatis/JDBC mapper 和 local repository 支持 `report_defect_draft` insert/update/query/count。
5. 报告详情返回 `defectDrafts`，报告 summary 更新 `defectDraftCount`。
6. health policy 将 `defectDraftReady` 标记为 true。
7. `scripts/wp10_defect_draft_smoke.sh` 接入 `scripts/wp10_quality_gate.sh`。

## 2. 非目标范围

1. 不写 Jira、禅道、飞书或任何外部缺陷系统。
2. 不提供外部发送、同步、回写或附件上传动作。
3. 不保存 raw evidence、runner stdout/stderr、请求响应正文、raw prompt、raw response 或 provider payload。
4. 不新增前端 `#reports` 草稿视图；M6 继续承接前端交互和 DOM redaction smoke。
5. 不新增 WP3/WP5 evidence adapter。

## 3. 安全和数据边界

`payloadPreview` 固定为人工复制预览：`schemaVersion=wp10-defect-preview-v1`、`masked=true`、`aggregateOnly=true`、`externalSystemWriteAttempted=false`。预览 redaction policy 明确 `rawEvidenceIncluded=false`、`rawPromptStored=false`、`rawResponseStored=false`、`credentialPlaintextStored=false` 和 `externalWebhookUrlStored=false`。

草稿 evidence refs 只引用 digest 形式，例如 `wp9:execution_node:<digest>` 或 `wp8:account_lease:<digest>`；不包含源节点 ID、账号租借 ID、账号 key/displayName、secretRef 明文、lease token、Authorization、cookie、password 或 `secret://` 原文。

## 4. 主要变更

| 模块 | 变更 |
|---|---|
| reporting application | 新增 `ReportDefectDraftService`，生成草稿、masked payload preview 和审阅状态流。 |
| reporting API | 新增 `POST /defect-drafts` 和 `PATCH /defect-drafts/{draftId}`。 |
| reporting repository | 新增 `ReportDefectDraft` domain、MyBatis resultMap/insert/update/query/count 和 local repository 支持。 |
| health | `defectDraftReady=true`。 |
| DB migration | 新增 `V20260616_067__wp10_defect_draft_review_audit.sql` 补齐审阅审计事件字典。 |
| scripts | 新增 `scripts/wp10_defect_draft_smoke.sh` 并纳入 `scripts/wp10_quality_gate.sh`。 |
| tests | Controller 覆盖草稿生成、审阅、非法状态流、详情回显和敏感字段不回显；DB profile 覆盖草稿持久化。 |

## 5. 风险和回滚

| 风险 | 缓解 |
|---|---|
| 用户误以为草稿已提交外部系统 | API 只返回 `MANUAL_COPY_ONLY` preview，字段固定 `externalSystemWriteAttempted=false`。 |
| 草稿误带敏感字段 | 只使用 aggregate-only 快照、digest 引用和安全文本脱敏；Controller 测试扫描禁止文本。 |
| 状态流被绕过 | 服务端限制 `DRAFT -> REVIEWED/DISMISSED` 和 `DISMISSED -> DRAFT`，非法流返回 `REPORT_DEFECT_DRAFT_INVALID_STATE`。 |
| `ReportService` 行数膨胀 | 草稿逻辑拆到独立 service，`ReportService` 仍低于 1200 行门禁。 |

回滚方式：回退本次 M5B commit；`report_defect_draft` 表已由 M1 建立，本次新增代码和审计事件字典前滚迁移不影响 M1-M5A 主链路。若需运行期止血，可关闭 `WP10_DEFECT_DRAFT_ENABLED=false`。

## 6. 验证记录

已执行：

1. `mvn -B -pl platform-api -Dtest=ReportControllerTest,ReportingHealthControllerTest,ReportingOpenApiContractTest test`
2. `mvn -B -pl platform-api -Dtest=DbProfileRepositoryContractTest#reportingRepositoryPersistsDefectDraftThroughJdbc test`

后续完整准出仍需执行 `bash scripts/platform_api_java_line_guard.sh`、`bash scripts/wp10_quality_gate.sh`、`mvn -B -pl platform-api test`、`cd portal-web && npm test` 和 `cd portal-web && npm run build`。

## 7. 五角色结论

| 角色 | 结论 | 说明 |
|---|---|---|
| 资深项目经理 | 通过 | M5B 切片范围限定在平台内草稿和审阅状态，回滚和开关止血路径清晰。 |
| 资深产品经理 | 通过 | 草稿字段满足标题、复现、影响、优先级和证据引用要求，外部写入边界清晰。 |
| 资深服务端架构师 | 通过 | 草稿服务独立，持久化契约复用 WP10 repository，不直连跨 WP 表。 |
| 资深前端工程师 | 有条件通过 | 后端契约已可供 M6 草稿视图接入；前端工作台仍待后续实现。 |
| 资深质量工程师 | 通过 | 已覆盖 Controller、OpenAPI、DB contract 和敏感字段不回显；完整准出继续执行聚合门禁。 |
