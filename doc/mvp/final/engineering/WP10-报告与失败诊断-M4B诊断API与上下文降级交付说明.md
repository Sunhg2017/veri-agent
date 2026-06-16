# WP10 报告与失败诊断 - M4B 诊断 API 与上下文降级交付说明

| 项目 | 内容 |
|---|---|
| 工作包 | WP10 报告与失败诊断 |
| 交付阶段 | M4B Diagnosis API and bounded context fallback |
| 覆盖 Story | WP10-4.2、WP10-4.3、WP10-4.4 |
| 当前口径 | 提供诊断触发/查询 API，生成脱敏 bounded context digest，并在 WP2 未接入前以 `AI_FAILED` 策略降级保留规则分类 |
| 日期 | 2026-06-17 |

## 1. 需求结论

本轮 WP10 推进完成 M4B 后端切片：`platform-api` 新增 `POST /api/v1/reports/{id}/diagnoses` 和 `GET /api/v1/reports/{id}/diagnoses/latest`，用户可在 READY 报告上触发诊断并查询最新诊断结果。当前仍不调用 WP2 模型；服务端基于 M4A `RULE_READY` 规则诊断构造脱敏 bounded context，仅持久化 context digest、裁剪标记和安全策略，并以 `AI_FAILED/REPORT_DIAGNOSIS_POLICY_BLOCKED` 降级返回，确保规则分类 fallback 不丢失。

本轮不实现真实 WP2 model invocation、不保存 raw prompt/raw response、不生成 AI_READY 建议、不做缺陷草稿、导出摘要和 `portal-web` 诊断视图。M4B 的目标是先稳定诊断 API、权限、审计、上下文安全边界和持久化形态，为后续 M4C 接入 WP2 留出可替换槽位。

## 2. 范围和非目标

| 类型 | 内容 |
|---|---|
| 目标 | 新增诊断触发 API，要求 `report:diagnose` 且报告状态必须为 `READY`。 |
| 目标 | 新增最新诊断查询 API，要求 `report:read`，按报告项目 scope 鉴权。 |
| 目标 | 构造不持久化正文的 bounded diagnosis context，输出 `contextDigest/contextStored=false/truncated/maxChars`。 |
| 目标 | `AI_FAILED` 降级诊断继承规则分类、候选根因、置信度和人工复核标记，并更新报告 summary。 |
| 目标 | 写入 `report.diagnosis.requested` 和 `report.diagnosis.completed` 审计事件，payload 只含摘要、digest 和状态。 |
| 非目标 | 不调用 WP2，不保存 provider payload，不生成 `modelInvocationDigest` 或 `AI_READY`。 |
| 非目标 | 不读取 WP8/WP9 原表，不把 evidence 原始值、源引用原文、账号凭据或 runner 原始产物放入模型上下文。 |

## 3. 主要变更

| 模块 | 变更 |
|---|---|
| `ReportController` | 新增 `POST /{id}/diagnoses` 和 `GET /{id}/diagnoses/latest`。 |
| `ReportService` | 增加诊断开关、READY 状态校验、context digest 构造、`AI_FAILED` 降级、summary 更新和审计。 |
| `ReportDiagnosisResponse` | 固化诊断 API 响应，区分 `classificationOnly`、`modelInvoked`、`aiDiagnosisReady` 和 `diagnosisContext`。 |
| `ReportResponseMapper` | 复用诊断响应映射，报告详情继续返回 `latestDiagnosis`。 |
| Health | 增加 `diagnosisApiReady=true`，`aiDiagnosisReady=false` 保持真实模型未接入状态。 |
| 测试 | `ReportControllerTest` 覆盖触发诊断、查询最新诊断、summary 更新、context digest 和敏感字段不回显；OpenAPI contract 覆盖新路径；DB contract 覆盖 `AI_FAILED` 持久化。 |

## 4. 验收标准

1. READY 报告可触发诊断，返回 `status=AI_FAILED`、`errorCode=REPORT_DIAGNOSIS_POLICY_BLOCKED`，且保留规则 `classification` 和 `rootCauseCandidates`。
2. `diagnosisContext` 只返回 digest、bounded/truncated 标记和安全 flag，不返回上下文正文。
3. 触发诊断后，报告列表 summary 更新为最新 `diagnosisStatus=AI_FAILED`，primary category 不变。
4. `GET /diagnoses/latest` 可读取最新诊断；无报告权限仍被项目 scope 拦截。
5. 响应不包含 Authorization、Bearer、`secret://`、lease token、账号 key/displayName、source ref 原文、raw prompt 或 raw response。

## 5. 验证入口

| 命令 | 用途 |
|---|---|
| `mvn -B -pl platform-api -Dtest=ReportControllerTest,ReportingHealthControllerTest,ReportingOpenApiContractTest test` | 验证诊断 API、health readiness、OpenAPI 和脱敏边界。 |
| `mvn -B -pl platform-api -Dtest=DbProfileRepositoryContractTest#reportingRepositoryPersistsLatestFailureDiagnosisThroughJdbc test` | 验证真实 PostgreSQL/MyBatis 中 `AI_FAILED` 诊断和 context digest 持久化。 |
| `bash scripts/platform_api_java_line_guard.sh` | Platform API Java 生产文件行数门禁。 |
| `bash scripts/wp10_quality_gate.sh` | 聚合 WP10 后端、DB validation、report smoke 和质量门禁。 |

本轮涉及 Java 生产代码、诊断 API、权限、审计、数据库读写和模型上下文安全边界，需参考 `doc/mvp/final/engineering/WP1-WP4-统一发布准出清单.md` 并执行与影响面匹配的最小必要验证。

## 6. 风险与回滚

| 风险 | 处置 | 回滚 |
|---|---|---|
| 用户误以为已完成 AI 诊断 | 响应固定 `aiDiagnosisReady=false`、`modelInvoked=false`、`classificationOnly=true`，health 保持 `aiDiagnosisReady=false` | 回退本次 commit 或隐藏诊断按钮 |
| context digest 构造误带敏感字段 | context 正文不持久化，字段名和值均按白名单/过滤处理，测试扫描禁止字段 | 回退本次 commit；保留 M4A 规则分类 |
| 降级诊断覆盖规则快照 | 降级结果继承规则 classification 和 candidates；报告详情仍可读 | 回退本次 commit 后重新生成报告恢复 `RULE_READY` |
| 后续 WP2 接入需要改契约 | M4B 已保留 `modelInvocationDigest`、`AI_READY/AI_FAILED` 状态和 context digest 字段 | 在 M4C 替换降级分支，不改路由 |

## 7. 五角色结论

| 角色 | 结论 | 说明 |
|---|---|---|
| 资深项目经理 | 通过 | M4B 范围限定为诊断 API 和安全降级，不抢跑真实 WP2 调用；回滚路径清晰。 |
| 资深产品经理 | 通过 | 用户可触发诊断并看到明确的策略降级、规则分类和人工复核提示，不会把降级误读为 AI 结论。 |
| 资深服务端架构师 | 通过 | 诊断 API、项目 scope、审计、summary 更新和持久化复用既有 reporting 架构，模型上下文只落 digest。 |
| 资深前端工程师 | 有条件通过 | 后端契约已可供诊断视图消费；`portal-web` 页面仍待 M6 实现。 |
| 资深质量工程师 | 通过 | 已补 controller、health、OpenAPI 和 DB contract 覆盖，后续 M4C 需继续补 WP2 成功/预算阻断/敏感拦截测试。 |
