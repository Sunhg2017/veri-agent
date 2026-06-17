# WP10 报告与失败诊断 - 运维 Runbook

| 项目 | 内容 |
|---|---|
| 工作包 | WP10 报告与失败诊断 |
| 文档性质 | 报告生成、证据 manifest、AI 诊断、缺陷草稿、导出脱敏、回滚和发布准出 Runbook |
| 当前口径 | WP10 当前由 `platform-api` 承载报告控制面，消费 WP9/WP8/WP3/WP5 aggregate-only 摘要，默认同步生成并可选由后台 worker 处理自身报告队列，通过 WP2 受控模型调用生成诊断建议，并由 `portal-web #reports` 提供浏览器主链路 |
| 日期 | 2026-06-17 |

## 1. 适用范围

本 Runbook 适用于 WP10 开发、预发和生产发布准出，以及报告生成失败、worker 认领失败、stale `GENERATING` 恢复、evidence manifest 异常、AI 诊断降级、模型预算阻断、缺陷草稿异常、导出阻断、前端 DOM 脱敏异常和紧急回滚处理。

WP10 是报告与失败诊断控制面，不触发 runner、不调度 execution run、不写外部缺陷系统、不归档 runner 原始产物。排障时只能围绕已持久化的 aggregate-only report summary、evidence manifest digest、latest diagnosis、defect draft preview、export manifest 和 traceId 追踪，不得要求或补采 runner stdout/stderr、请求/响应正文、webhook payload、raw prompt 或 raw response。

## 2. 开关和配置

| 配置 | 默认 | 说明 |
|---|---|---|
| `WP10_REPORTING_ENABLED` / `veri-agent.reporting.enabled` | `true` | WP10 报告控制面总开关；关闭后业务写操作应阻断，health 仍用于观测。 |
| `WP10_REPORT_GENERATE_ENABLED` / `veri-agent.reporting.generate-enabled` | `true` | 是否允许生成或重试报告。 |
| `WP10_REPORT_ASYNC_GENERATION_ENABLED` / `veri-agent.reporting.async-generation-enabled` | `false` | 是否将生成和重试请求先写入 `QUEUED`，由 worker 后台处理。 |
| `WP10_REPORT_GENERATION_WORKER_ENABLED` / `veri-agent.reporting.generation-worker-enabled` | `true` | 是否启用 managed 报告生成 worker。 |
| `WP10_REPORT_GENERATION_WORKER_INTERVAL_MS` / `veri-agent.reporting.generation-worker-interval-ms` | `5000` | worker 固定 delay。 |
| `WP10_REPORT_GENERATION_WORKER_INITIAL_DELAY_MS` / `veri-agent.reporting.generation-worker-initial-delay-ms` | `30000` | worker 初始延迟。 |
| `WP10_REPORT_GENERATION_WORKER_ID` / `veri-agent.reporting.generation-worker-id` | `wp10-report-worker` | worker 标识。 |
| `WP10_REPORT_GENERATION_WORKER_BATCH_SIZE` / `veri-agent.reporting.generation-worker-batch-size` | `4` | 单次 tick 认领上限。 |
| `WP10_REPORT_GENERATION_RUNNING_TIMEOUT_SECONDS` / `veri-agent.reporting.generation-running-timeout-seconds` | `1800` | `GENERATING` 超时恢复阈值。 |
| `WP10_REPORT_GENERATION_RECOVERY_BATCH_SIZE` / `veri-agent.reporting.generation-recovery-batch-size` | `50` | 单次 tick 恢复上限。 |
| `WP10_DIAGNOSIS_ENABLED` / `veri-agent.reporting.diagnosis-enabled` | `true` | 是否允许触发 WP2 AI 诊断；关闭后保留既有规则分类。 |
| `WP10_DEFECT_DRAFT_ENABLED` / `veri-agent.reporting.defect-draft-enabled` | `true` | 是否允许生成平台内缺陷草稿。 |
| `WP10_REPORT_EXPORT_ENABLED` / `veri-agent.reporting.export-enabled` | `true` | 是否允许 JSON/Markdown 脱敏摘要导出。 |
| `WP10_MAX_EVIDENCE_ITEMS` / `veri-agent.reporting.max-evidence-items` | `200` | 单报告 evidence manifest 上限；服务端会限制到安全范围。 |
| `WP10_MAX_DIAGNOSIS_CONTEXT_CHARS` / `veri-agent.reporting.max-diagnosis-context-chars` | `12000` | 发送 WP2 前的脱敏 bounded context 字符上限。 |
| `WP10_MAX_EXPORT_MARKDOWN_CHARS` / `veri-agent.reporting.max-export-markdown-chars` | `30000` | Markdown 摘要导出字符上限。 |
| `WP10_REPORT_SCHEMA_VERSION` / `veri-agent.reporting.schema-version` | `wp10-report-v1` | 报告快照 schema version。 |
| `WP10_REPORT_FIELD_SET_VERSION` / `veri-agent.reporting.field-set-version` | `wp10-report-export-fields-v1` | 导出字段集版本。 |

生产建议：

1. 首次发布先确认 `GET /api/v1/reports/health` 的开关、limits、schemaVersion、fieldSetVersion 和 policy 摘要符合发布计划。
2. 若发现报告生成、worker 认领或跨 WP 证据异常，优先关闭 `WP10_REPORT_GENERATE_ENABLED=false`；若只需绕开后台队列，可关闭 `WP10_REPORT_ASYNC_GENERATION_ENABLED=false` 回到同步生成。
3. 若发现模型预算、provider 或上下文安全风险，优先关闭 `WP10_DIAGNOSIS_ENABLED=false`，规则分类仍可作为 fallback。
4. 若发现导出或页面泄露风险，优先关闭 `WP10_REPORT_EXPORT_ENABLED=false` 并撤销 `report:export` 权限。
5. 若发现草稿字段或 payload preview 风险，优先关闭 `WP10_DEFECT_DRAFT_ENABLED=false`，不要删除既有草稿证据。

## 3. 日常验证

开发默认入口：

```bash
bash scripts/wp10_quality_gate.sh
```

单项验证：

```bash
bash scripts/wp10_report_smoke.sh
bash scripts/wp10_defect_draft_smoke.sh
bash scripts/wp10_export_redaction_smoke.sh
bash scripts/wp10_diagnosis_quality_eval.sh
bash scripts/wp10_diagnosis_redaction_eval.sh
bash scripts/wp10_frontend_e2e_smoke.sh
bash db/validation/run_wp1_db_validation.sh
mvn -B -pl platform-api test
cd portal-web && npm test
cd portal-web && npm run build
```

浏览器 smoke 依赖本机 Chrome 或 Playwright Chromium。CI 无系统 Chrome 时可显式安装：

```bash
WP10_FRONTEND_INSTALL_BROWSERS=1 bash scripts/wp10_frontend_e2e_smoke.sh
```

跳过项只能作为受控例外使用：

| 跳过变量 | 影响 | 准出要求 |
|---|---|---|
| `WP10_SKIP_DIAGNOSIS_EVAL=1` | 跳过离线诊断质量评测。 | 记录原因、风险、替代验证和恢复计划。 |
| `WP10_SKIP_DIAGNOSIS_REDACTION_EVAL=1` | 跳过 WP2 bounded context 脱敏专项。 | 生产发布默认不得跳过；必须有安全负责人确认。 |
| `WP10_SKIP_FRONTEND_E2E=1` | 跳过 `#reports` Playwright smoke 和 DOM 禁止字段扫描。 | 记录浏览器环境问题，并补前端 Vitest/build 和人工截图检查。 |
| `WP10_SKIP_DB_VALIDATION=1` | 跳过合并 DB validation。 | 涉及 DB、权限、审计或发布准出时不得跳过。 |

## 4. 发布准出检查点

1. `GET /api/v1/reports/health` 只展示开关、limits、schemaVersion、fieldSetVersion 和 redaction policy 摘要，不展示 provider、prompt、secret 或 evidence 明细。
2. 报告生成、列表、详情、诊断、草稿、导出和归档均必须通过 RBAC 权限和 project scope 校验。
3. 源 run 必须来自 WP9 脱敏 run export，且具备 `REPORT_HANDOFF` 摘要；WP10 不读取 WP9 原始表或 runner artifact。
4. evidence manifest 只能保存 sourceWp、sourceType、schemaVersion、summaryKeys、digest、计数、状态和 redactionFlags，不保存跨 WP 原始正文。
5. 诊断上下文只发送脱敏 bounded context，响应和数据库只保留 contextDigest、modelInvocationDigest 和 digest-only 元数据。
6. JSON/Markdown 导出只来自 WP10 已持久化快照，导出正文不落库，manifest 必须标记 `aggregateOnly=true`。
7. 缺陷草稿只保存平台内草稿和 masked payload preview，`externalSystemWriteAttempted=false`。
8. 前端 `#reports` 桌面和 390px 主链路无横向溢出，DOM 禁止字段样本扫描为 0。
9. DB validation 覆盖 WP10 表、约束、索引、权限、角色授权、基础配置、审计事件和无 `tenant_id` 回归。
10. 涉及 Java 代码的发布必须运行 `bash scripts/platform_api_java_line_guard.sh`，并完成《阿里巴巴 Java 开发手册》自查和核心逻辑注释检查。

## 5. 报告生成和 worker 失败处理

| 现象或错误码 | 常见原因 | 处理 |
|---|---|---|
| `REPORT_DISABLED` | WP10 总开关关闭。 | 确认发布计划是否允许开启 `WP10_REPORTING_ENABLED=true`；不允许时保持只读观测。 |
| `REPORT_GENERATE_DISABLED` | 生成开关关闭。 | 如为止血状态，继续保留；如为误配，开启后重跑 report smoke。 |
| 异步入队后长时间停留在 `QUEUED` | worker 关闭、初始延迟过长、tick 频率过低或批量处理被堆积占满。 | 检查 health 的 `asyncGenerationEnabled/generationWorkerEnabled/generationWorkerBatchSize` 和 worker 日志；必要时关闭 `WP10_REPORT_ASYNC_GENERATION_ENABLED=false` 回到同步生成。 |
| worker tick 跳过 queued candidate | 并发 worker 或手动 tick 已先一步条件认领。 | 观察 tick 的 `skippedCandidateCount`；通常属于正常竞争保护，不要手工改状态。 |
| stale `GENERATING` 被恢复为 FAILED | worker 进程中断、生成耗时超过阈值或跨 WP 调用长时间失败。 | 检查 `WP10_REPORT_GENERATION_RUNNING_TIMEOUT_SECONDS`、`report.generate.recovered` 审计和失败 traceId；确认源 run 仍可读后执行 retry。 |
| `REPORT_SOURCE_RUN_NOT_FOUND` | run 不存在、跨项目不可见或用户无源 run scope。 | 核对 projectId、executionRunId、用户角色和 WP9 run 归属；不要放宽为全局查询。 |
| `REPORT_SOURCE_RUN_NOT_READY` | WP9 run 未终态、缺少 `REPORT_HANDOFF`、或 handoff 摘要不满足 `rawReportStored=false`。 | 先排查 WP9 run export 和 REPORT_HANDOFF 节点，不在 WP10 侧伪造报告。 |
| `REPORT_INVALID_STATE` | 对 READY/ARCHIVED 报告执行不允许的重试、诊断或导出动作。 | 刷新详情，按状态机操作；FAILED 才能重试生成。 |
| requestKey 回放不符合预期 | 同一 executionRunId/requestKey 已生成过报告。 | 比对 reportId、sourceRunDigest 和 idempotentReplay；需要新快照时使用新的 requestKey。 |
| 列表可见但详情 404/403 | 项目 scope 或权限变化。 | 检查 `report:read` 授权、项目成员关系和后端 scope resolver。 |

排障证据至少记录：projectId、executionRunId、requestKey、reportId、status、failedCode、traceId、sourceRunDigest、操作者、接口时间和相关审计事件。不要直接删除 `report_execution_report`、`report_evidence_manifest` 或审计记录。

生成链路恢复后运行：

```bash
bash scripts/wp10_report_smoke.sh
```

worker 链路恢复后额外运行：

```bash
mvn -B -pl platform-api -Dtest=ReportGenerationWorkerServiceTest,ReportingHealthControllerTest test
```

## 6. Evidence Manifest 异常处理

| 场景 | 推荐操作 | 证据 |
|---|---|---|
| WP9 manifest 缺失 | 确认源 run export 是否包含节点摘要和 `REPORT_HANDOFF`。 | executionRunId、run export schemaVersion、traceId。 |
| WP8 manifest 缺失 | 确认 WP9 节点摘要是否包含 WP8 引用 key，如 `accountLeaseRef(s)`、`dataSetRef(s)`、`cleanupTaskRef(s)`。 | summaryKeys、sourceRefDigest、WP8 reportEvidence 调用结果。 |
| manifest 数量被裁剪 | 检查 `WP10_MAX_EVIDENCE_ITEMS` 和 report summary 的 truncated 标记。 | maxEvidenceItems、evidenceManifestCount、evidenceManifestTruncated。 |
| evidence 命中敏感样本 | 立即关闭生成和导出，保留报告与 traceId，运行 export redaction smoke 和 diagnosis redaction eval。 | reportId、manifestDigest、redactionFlags、blocked policy。 |
| WP3/WP5 证据为空 | WP9 节点摘要未输出对应白名单引用，或引用跨项目/已不可见。 | 检查 `summaryKeys`、sourceRefDigest、WP3/WP5 应用服务 aggregate-only 响应和项目 scope；不要手工直连 WP3/WP5 表补数据。 |

WP10 不直连 WP8/WP9/WP3/WP5 表。确需跨 WP 追踪时，通过对应应用服务、导出接口或明确 port 获取 aggregate-only 摘要。

## 7. AI 诊断降级和模型预算处理

| 现象或错误码 | 常见原因 | 处理 |
|---|---|---|
| `REPORT_DIAGNOSIS_DISABLED` | 诊断开关关闭。 | 若为止血或预算保护，使用 RULE_READY 规则分类继续排障。 |
| `REPORT_DIAGNOSIS_POLICY_BLOCKED` | WP2 预算、策略、敏感内容或 provider gate 阻断。 | 记录 reportId、contextDigest、errorCode、traceId；检查 WP2 预算和策略，不要求返回 raw prompt。 |
| `AI_FAILED` | WP2 调用失败或被降级。 | 保留规则分类和候选根因；确认是否需要重试诊断。 |
| `MODEL_INVOCATION_FAILED` | provider 或 WP2 invocation 异常。 | 先按 WP2 Provider Runbook 排障；WP10 不直接暴露 provider payload。 |
| context 被裁剪 | evidence 或 summary 过大。 | 检查 `maxDiagnosisContextChars`、truncated flag 和 evidenceManifestCount；必要时调大上限后重跑专项评测。 |
| 诊断结果置信度低 | 输入证据有限或规则 fallback 命中 UNKNOWN。 | 记录为人工复核，补充上游 WP9/WP8 聚合摘要质量，不补采原始日志。 |

紧急止血：

```bash
WP10_DIAGNOSIS_ENABLED=false
```

恢复前必须运行：

```bash
bash scripts/wp10_diagnosis_quality_eval.sh
bash scripts/wp10_diagnosis_redaction_eval.sh
```

## 8. 导出阻断处理

| 现象或错误码 | 常见原因 | 处理 |
|---|---|---|
| `REPORT_EXPORT_DISABLED` | 导出开关关闭。 | 确认是否处于敏感泄露止血；误配时开启后重跑 redaction smoke。 |
| `REPORT_EXPORT_REDACTION_BLOCKED` | 导出正文或 manifest 命中禁止字段。 | 不展示命中值；记录 reportId、exportType、blockReason、traceId 和 redactionPolicy。 |
| `REPORT_EXPORT_TYPE_INVALID` | exportType 非 `JSON`/`MARKDOWN`。 | 使用受支持格式，前端按钮不应产生非法值。 |
| `REPORT_INVALID_STATE` | 非 READY 报告导出。 | 等待或重试生成到 READY，再执行导出。 |
| Markdown 截断 | 摘要超出 `maxExportMarkdownChars`。 | 保留 contentDigest 和 manifest；如需调大上限，先评审泄露风险和 build/test。 |

发现导出或前端 DOM 泄露时：

1. 设置 `WP10_REPORT_EXPORT_ENABLED=false`，必要时撤销 `report:export`。
2. 保留 reportId、export manifest、contentDigest、traceId 和截图证据；截图中不要扩大传播敏感值。
3. 运行 `bash scripts/wp10_export_redaction_smoke.sh` 和 `bash scripts/wp10_frontend_e2e_smoke.sh`。
4. 修复后运行完整 `bash scripts/wp10_quality_gate.sh`，再恢复导出。

## 9. 缺陷草稿处理

| 现象或错误码 | 常见原因 | 处理 |
|---|---|---|
| `REPORT_DEFECT_DRAFT_DISABLED` | 草稿开关关闭。 | 确认是否为 payload preview 止血；误配时开启后回归草稿 smoke。 |
| `REPORT_DEFECT_DRAFT_INVALID_STATE` | 非法审阅状态流。 | 只允许 `DRAFT -> REVIEWED/DISMISSED` 和已支持的恢复流；刷新详情后重试。 |
| 草稿字段缺少证据 | latest diagnosis 或 evidence manifest 不完整。 | 先排查报告详情和规则分类，不直接写外部缺陷。 |
| payload preview 疑似泄露 | masked 策略回归或 summary 混入敏感值。 | 关闭 `WP10_DEFECT_DRAFT_ENABLED=false`，保留草稿和 traceId，回归 redaction smoke。 |

缺陷草稿不是外部缺陷系统写入结果。任何 Jira、禅道、飞书写入都属于后续专项，当前不能在 Runbook 中作为恢复手段。

## 10. 敏感泄露应急处理

命中以下任一内容时按 P0 安全事件处理：secret、token、cookie、Authorization、password、lease token、`secret://` 原文、完整 secretRef、runner stdout/stderr、请求/响应正文、webhook payload、截图、视频、源码包、raw prompt、raw response、provider payload。

应急步骤：

1. 立即关闭相关开关：导出泄露关闭 `WP10_REPORT_EXPORT_ENABLED=false`；诊断上下文风险关闭 `WP10_DIAGNOSIS_ENABLED=false`；草稿风险关闭 `WP10_DEFECT_DRAFT_ENABLED=false`；必要时关闭 `WP10_REPORT_GENERATE_ENABLED=false` 或 `WP10_REPORT_ASYNC_GENERATION_ENABLED=false`。
2. 暂停发布或回滚流量入口，保留 reportId、traceId、export manifest、diagnosis contextDigest、modelInvocationDigest 和审计事件。
3. 不在工单、聊天、release notes 或日志中复制敏感原文；只记录命中类别和 digest。
4. 运行 `bash scripts/wp10_diagnosis_redaction_eval.sh`、`bash scripts/wp10_export_redaction_smoke.sh` 和前端 DOM smoke。
5. 修复后运行完整 WP10 quality gate、DB validation 和必要的前端 build，再分阶段恢复开关。

## 11. DB、权限和审计排障

WP10 DB validation 由合并入口承载：

```bash
bash db/validation/run_wp1_db_validation.sh
```

重点检查：

1. `report_execution_report`、`report_evidence_manifest`、`report_failure_diagnosis`、`report_defect_draft`、`report_export_manifest` 表存在。
2. 状态 check、JSON check、digest check、唯一约束和查询索引存在。
3. `report:read/generate/diagnose/export/manage` 权限和默认角色授权存在。
4. `report.generated`、`report.generate.rejected`、`report.archived`、`report.diagnosis.requested`、`report.diagnosis.completed`、`report.defect_draft.created`、`report.defect_draft.reviewed`、`report.exported`、`report.export.blocked` 审计事件存在。
5. runtime role 只能按策略读写 WP10 aggregate metadata，不具备 DELETE/TRUNCATE；readonly role 只能 SELECT。
6. WP10 表没有 `tenant_id` 回归。

不要直接删除 report、diagnosis、draft、export 或 audit 数据。生产数据修复必须有工单、备份、SQL、影响范围、验证结果和前滚修复计划。

## 12. 前端 Smoke 和浏览器排障

| 现象 | 常见原因 | 处理 |
|---|---|---|
| Playwright 找不到浏览器 | CI 无系统 Chrome 且未安装 Chromium。 | 设置 `WP10_FRONTEND_INSTALL_BROWSERS=1` 后重跑 smoke。 |
| `#reports` 无权限态 | 缺少 `report:read` 或登录态失效。 | 检查用户权限和前端权限映射；不要绕过后端 RBAC。 |
| 390px 横向溢出 | digest、traceId、按钮或表格布局回归。 | 运行 `bash scripts/wp10_frontend_e2e_smoke.sh` 并修复响应式样式。 |
| DOM 禁止字段命中 | 后端响应或前端渲染泄露敏感样本。 | 立即按敏感泄露应急处理，不把命中值写入测试快照。 |
| 导出/诊断按钮置灰 | 权限不足、报告非 READY 或开关关闭。 | 对照 health、报告状态和 `report:*` 权限。 |

前端问题修复后至少运行：

```bash
cd portal-web && npm test -- --run src/api/reports.test.ts src/permissions.test.ts
cd portal-web && npm run build
bash scripts/wp10_frontend_e2e_smoke.sh
```

## 13. 回滚和止血

按影响面从小到大处理：

1. 暂停导出：设置 `WP10_REPORT_EXPORT_ENABLED=false`，必要时撤销 `report:export`。
2. 暂停 AI 诊断：设置 `WP10_DIAGNOSIS_ENABLED=false`，规则分类仍可用。
3. 暂停缺陷草稿：设置 `WP10_DEFECT_DRAFT_ENABLED=false`。
4. 暂停异步入队：设置 `WP10_REPORT_ASYNC_GENERATION_ENABLED=false`，回到同步生成路径。
5. 暂停生成和重试：设置 `WP10_REPORT_GENERATE_ENABLED=false`，保留既有报告只读。
6. 暂停 WP10 控制面：设置 `WP10_REPORTING_ENABLED=false`。
7. 回退应用版本或本次文档/代码 commit；涉及 DB migration 时按 `WP1-WP4-数据库迁移回滚与前滚策略.md` 走前滚修复，不手工回滚已执行 Flyway 版本。
8. 保留 report、evidence、diagnosis、draft、export manifest 和 audit 数据；不要直接删除证据。
9. 修复后按 `bash scripts/wp10_quality_gate.sh`、`mvn -B -pl platform-api test`、`cd portal-web && npm test`、`cd portal-web && npm run build` 和 DB validation 重跑准出。

## 14. 准出记录

发布、事故恢复、模型预算调整、导出恢复或安全事件关闭工单至少记录：

1. WP10 quality gate 命令和结果。
2. 目标环境、WP10 开关状态、schemaVersion 和 fieldSetVersion。
3. 相关 projectId、executionRunId、reportId、diagnosisId、draftId、export manifest id、traceId。
4. sourceRunDigest、manifestDigest、contextDigest、modelInvocationDigest、contentDigest 等 digest 证据。
5. 诊断评测、诊断上下文脱敏专项、导出 redaction smoke、前端 DOM smoke、DB validation 的结果。
6. 任何跳过项、风险、影响范围、回滚开关、责任人和恢复时间。
7. Java 生产代码如有变更，记录 Java 行数门禁、《阿里巴巴 Java 开发手册》自查和核心逻辑注释结论。
