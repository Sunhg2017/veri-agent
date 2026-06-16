# WP10 报告与失败诊断 - M4C WP2 诊断调用交付说明

| 项目 | 内容 |
|---|---|
| 工作包 | WP10 报告与失败诊断 |
| 交付阶段 | M4C WP2 diagnosis invocation |
| 覆盖 Story | WP10-4.2、WP10-4.3、WP10-4.4 |
| 当前口径 | 在 M4B 诊断 API 和 bounded context 基础上接入 WP2 受控模型调用，成功返回 `AI_READY`，失败保留规则 fallback |
| 日期 | 2026-06-17 |

## 1. 需求结论

本轮 WP10 推进完成 M4C 后端切片：READY 报告触发诊断时，服务端先构造脱敏 bounded context，再通过 WP2 `ModelInvocationService` 执行受控模型调用。WP2 成功时持久化 `AI_READY`、`modelInvocationDigest`、context digest 和模型输出 digest-only 元数据；WP2 预算、策略、敏感内容或 provider 失败时持久化 `AI_FAILED/REPORT_DIAGNOSIS_POLICY_BLOCKED`，保留 M4A 规则分类、候选根因、置信度和人工复核要求。

本轮不保存 raw prompt、raw response、provider payload 或模型输出正文，不新增前端页面，不生成缺陷草稿，不实现导出摘要，不改变报告生成和查询主契约。

## 2. 范围和非目标

| 类型 | 内容 |
|---|---|
| 目标 | 将 `POST /api/v1/reports/{id}/diagnoses` 从固定策略降级升级为 WP2 invocation 成功/失败双路径。 |
| 目标 | 拆出 `ReportDiagnosisContextBuilder`，统一 bounded context、digest 和敏感 key/value 过滤。 |
| 目标 | 拆出 `ReportDiagnosisAiInvoker`，封装 WP2 command、service principal、`modelInvocationDigest` 和错误降级。 |
| 目标 | `AI_READY` 仍强制 `manualReviewRequired=true`，避免模型结论被误读为最终定论。 |
| 目标 | health 输出 `aiDiagnosisReady=true`、`aiDiagnosisFallbackReady=true`。 |
| 非目标 | 不解析并保存模型正文中的新候选根因，不导出模型响应正文。 |
| 非目标 | 不依赖 promptKey seed；WP10 当前通过 direct message + marker 与 WP2 交互。 |

## 3. 主要变更

| 模块 | 变更 |
|---|---|
| `ReportService` | 诊断触发改为 bounded context -> WP2 invocation -> `AI_READY` 或 `AI_FAILED` 持久化，并更新报告 summary 和审计。 |
| `ReportDiagnosisContextBuilder` | 新增脱敏 context builder，过滤敏感字段名和值，正文只用于 WP2 调用和 digest，不持久化不返回。 |
| `ReportDiagnosisAiInvoker` | 新增 WP2 调用适配器，生成 digest-only 模型元数据，统一预算/策略/provider/空响应降级。 |
| `LocalEchoModelProviderClient` | 增加 `WP10_FAILURE_DIAGNOSIS_V1` marker 的本地结构化响应，支持 local/db 测试路径。 |
| `ReportingHealthService` | 标记 AI 诊断接线和 fallback 均已就绪。 |
| 测试 | 控制器测试覆盖 `AI_READY`、model command 安全 payload、预算阻断 fallback、health 和 OpenAPI。 |

## 4. 验收标准

1. READY 报告触发诊断，在 WP2 成功时返回 `status=AI_READY`、`modelInvocationDigest`、`modelInvoked=true`、`aiDiagnosisReady=true`。
2. WP2 预算或策略阻断时返回 `status=AI_FAILED`、`errorCode=REPORT_DIAGNOSIS_POLICY_BLOCKED`，规则分类和候选根因仍可用。
3. 发送给 WP2 的上下文不包含 Authorization、Bearer、`secret://`、账号租借 ID 原文、账号 key/displayName、lease token 或 raw runner payload。
4. API 响应和持久化摘要不包含 raw prompt、raw response、provider payload 或模型输出正文。
5. Java 生产文件行数门禁、WP10 quality gate、后端目标测试和前端默认验证按影响面通过。

## 5. 验证入口

| 命令 | 用途 |
|---|---|
| `mvn -B -pl platform-api -Dtest=ReportControllerTest,ReportingHealthControllerTest,ReportingOpenApiContractTest test` | 验证诊断 API、AI_READY、预算 fallback、health readiness、OpenAPI 和脱敏边界。 |
| `mvn -B -pl platform-api -Dtest=DbProfileRepositoryContractTest#reportingRepositoryPersistsLatestFailureDiagnosisThroughJdbc test` | 验证真实 PostgreSQL/MyBatis 中诊断持久化契约仍可用。 |
| `bash scripts/platform_api_java_line_guard.sh` | Platform API Java 生产文件行数门禁。 |
| `bash scripts/wp10_quality_gate.sh` | 聚合 WP10 后端、DB validation、report smoke 和质量门禁。 |
| `mvn -B -pl platform-api test` | 后端全量测试。 |
| `cd portal-web && npm test`、`cd portal-web && npm run build` | 前端默认验证，确认本次后端变更未破坏现有前端构建和测试。 |

本轮涉及 Java 生产代码、诊断 API、权限、审计、模型调用和模型上下文安全边界，需参考 `doc/mvp/final/engineering/WP1-WP4-统一发布准出清单.md` 并执行与影响面匹配的最小必要验证。

## 6. 风险与回滚

| 风险 | 处置 | 回滚 |
|---|---|---|
| WP2 provider 不可用导致诊断失败 | 降级为 `AI_FAILED/REPORT_DIAGNOSIS_POLICY_BLOCKED`，保留规则分类 | 回退本次 commit 或临时关闭诊断入口 |
| 模型上下文误带敏感字段 | context builder 过滤敏感 key/value，测试捕获 WP2 command 并扫描禁止字段 | 回退本次 commit；保留 M4A/M4B 规则 fallback |
| AI_READY 被误认为最终根因 | `manualReviewRequired=true`，响应仍保留规则 evidence refs 和人工确认语义 | 前端隐藏 AI 标识或回退到 M4B 降级 |
| 模型原文误被导出 | 只保存 `outputDigest` 和 invocation digest，不保存 raw response | 回退本次 commit；清理最新诊断后重新生成报告 |

## 7. 五角色结论

| 角色 | 结论 | 说明 |
|---|---|---|
| 资深项目经理 | 通过 | M4C 范围限定为 WP2 诊断调用接线和 fallback，不扩展到草稿、导出或前端，回滚路径清晰。 |
| 资深产品经理 | 通过 | 用户可获得 `AI_READY` 或明确的策略降级，AI 结果仍要求人工确认，避免确定性误导。 |
| 资深服务端架构师 | 通过 | WP2 调用封装在独立 invoker，context builder 独立承载脱敏边界，`ReportService` 保持编排职责。 |
| 资深前端工程师 | 有条件通过 | 后端字段已可支持诊断视图；`portal-web` 诊断 UI 仍待 M6 实现。 |
| 资深质量工程师 | 通过 | 已覆盖成功、预算降级、context 安全、health 和 OpenAPI；后续可继续扩展离线诊断评测集。 |
