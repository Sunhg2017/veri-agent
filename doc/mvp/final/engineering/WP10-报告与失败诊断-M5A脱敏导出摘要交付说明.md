# WP10 报告与失败诊断 - M5A 脱敏导出摘要交付说明

| 项目 | 内容 |
|---|---|
| 里程碑 | M5A 脱敏导出摘要 |
| 当前口径 | 完成报告 JSON/Markdown 脱敏摘要导出 API、export manifest 持久化、redaction smoke 和 DB contract；不生成缺陷草稿，不写外部缺陷系统，不实现前端工作台 |
| 日期 | 2026-06-17 |

## 1. 目标和范围

M5A 目标是让 READY 报告可通过 `GET /api/v1/reports/{id}/export?exportType=JSON|MARKDOWN` 生成可审计、可复制的脱敏报告摘要。服务端即时返回导出正文，同时只持久化 `report_export_manifest` 中的 schema、fieldSet、redaction policy、content digest、导出人和导出时间。

本轮范围：

1. `ReportExportService` 基于报告快照、evidence manifest 和最新诊断生成 aggregate-only 导出内容。
2. `ReportController` 新增 `GET /api/v1/reports/{id}/export`，权限为 `report:export`，项目 scope 复用报告资源 scope。
3. `ReportingRepository`、JDBC mapper 和 local repository 支持 export manifest insert、latest 和 count。
4. 报告 summary 在成功或阻断导出后更新 `exportManifestCount`。
5. health policy 将 `exportSummaryReady` 标记为 true。
6. `scripts/wp10_export_redaction_smoke.sh` 接入 `scripts/wp10_quality_gate.sh`。
7. 文档同步 M5A 当前状态。

## 2. 非目标范围

1. 不实现缺陷草稿生成、审阅、dismiss 或外部缺陷 payload preview。
2. 不写 Jira、禅道、飞书或其他外部缺陷系统。
3. 本切片交付时不落库导出正文，不提供 PDF/Word/真实文件下载；按当前基线，这些能力已由后续 M11/M12 里程碑承接交付，但不改变 M5A 当时“仅 JSON/Markdown 摘要导出”的验收边界。
4. 不新增前端 `#reports` 工作台和 DOM redaction smoke；DOM 扫描随 M6 前端落地。
5. 本切片交付时不新增 WP3/WP5 evidence adapter；按当前基线，该能力已由后续 M9 里程碑承接交付。

## 3. 安全和数据边界

导出内容只来自 WP10 已持久化的 aggregate-only 快照，不回读 WP8/WP9/WP3/WP5 原表。导出前过滤高风险 key 并扫描正文；命中禁止文本时写入 `BLOCKED/REPORT_EXPORT_REDACTION_BLOCKED` manifest 并返回空 content。

禁止字段包括 secret、token、cookie、Authorization、password、lease token、`secret://` 原文、raw prompt、raw response、provider payload、runner stdout/stderr、请求响应正文、webhook payload、账号 key/displayName 和跨 WP 原始引用清单。

## 4. 主要变更

| 模块 | 变更 |
|---|---|
| reporting application | 新增 `ReportExportService`，生成 JSON/Markdown 脱敏导出摘要，持久化 digest-only manifest。 |
| reporting API | `GET /api/v1/reports/{id}/export` 支持 `exportType=JSON|MARKDOWN`。 |
| reporting repository | 新增 `ReportExportManifest` domain、MyBatis resultMap/insert/latest/count 和 local repository 支持。 |
| health | `exportSummaryReady=true`，`defectDraftReady=false` 保持不变。 |
| scripts | 新增 `scripts/wp10_export_redaction_smoke.sh` 并纳入 `scripts/wp10_quality_gate.sh`。 |
| tests | Controller 覆盖 JSON/Markdown 导出、敏感字段不回显、非法 exportType；DB profile 覆盖 manifest 持久化。 |

## 5. 风险和回滚

| 风险 | 缓解 |
|---|---|
| 导出正文误带敏感字段 | 白名单快照、unsafe key 过滤、正文扫描、redaction smoke 和禁止正文落库。 |
| 用户误以为导出代表外部缺陷已创建 | redaction policy 固定 `externalDefectWriteAttempted=false`，M5A 不提供外部发送动作。 |
| BLOCKED manifest 计数影响列表理解 | `exportManifestCount` 只表示导出尝试/manifest 数，后续前端需展示 status。 |
| Markdown 内容过长 | `maxExportMarkdownChars` 限制并标记 `markdownTruncated`。 |

回滚方式：回退本次 M5A commit；既有报告生成、证据、诊断 API 不依赖导出 API，回滚不会影响 M1-M4C 主链路。

## 6. 验证记录

已执行：

1. `mvn -B -pl platform-api -Dtest=ReportControllerTest,ReportingHealthControllerTest,ReportingOpenApiContractTest test`
2. `bash scripts/wp10_export_redaction_smoke.sh`
3. `mvn -B -pl platform-api -Dtest=DbProfileRepositoryContractTest#reportingRepositoryPersistsExportManifestThroughJdbc test`
4. `bash -n scripts/wp10_quality_gate.sh scripts/wp10_report_smoke.sh scripts/wp10_export_redaction_smoke.sh`

后续完整准出仍需按本次交付命令结果补充 `scripts/wp10_quality_gate.sh`、`mvn -B -pl platform-api test`、`portal-web` test/build 和 Java 行数门禁。

## 7. 五角色结论

| 角色 | 结论 | 说明 |
|---|---|---|
| 资深项目经理 | 通过 | M5A 切片范围清晰，回滚简单，不混入缺陷草稿和前端工作台。 |
| 资深产品经理 | 通过 | 用户可获得 JSON/Markdown 脱敏摘要和 manifest；外部缺陷写入边界清晰。 |
| 资深服务端架构师 | 通过 | 导出服务独立于 `ReportService`，只保存 digest manifest，不直连跨 WP 表。 |
| 资深前端工程师 | 有条件通过 | 后端契约已可供前端接入；条件是 M6 实现 `#reports` 导出面板和 DOM redaction smoke。 |
| 资深质量工程师 | 通过 | 已补控制器、OpenAPI、DB contract 和 redaction smoke；完整准出需继续运行聚合门禁。 |
