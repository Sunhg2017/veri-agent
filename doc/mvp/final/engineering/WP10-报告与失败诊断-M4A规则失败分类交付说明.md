# WP10 报告与失败诊断 - M4A 规则失败分类交付说明

| 项目 | 内容 |
|---|---|
| 工作包 | WP10 报告与失败诊断 |
| 交付阶段 | M4A Rule-based failure classifier |
| 覆盖 Story | WP10-4.1、WP10-4.4 |
| 当前口径 | 基于 WP10 已持久化 evidence manifest 生成无模型可用的 `RULE_READY` 诊断快照 |
| 日期 | 2026-06-17 |

## 1. 需求结论

本轮 WP10 推进完成 M4A 后端切片：`platform-api` 在报告生成和 FAILED 重试链路中，基于 WP9/WP8 aggregate-only evidence manifest 进行规则失败分类，持久化 `report_failure_diagnosis` 并在报告详情 `latestDiagnosis` 返回 `RULE_READY` 诊断结果。

本轮不实现 WP2 AI 诊断调用、模型上下文构造、诊断触发 API、WP3/WP5 evidence adapter、缺陷草稿、导出摘要和 `portal-web` 诊断视图。M4A 的目标是让无模型状态下的报告详情具备稳定的失败分类 fallback。

## 2. 范围和非目标

| 类型 | 内容 |
|---|---|
| 目标 | 新增 `RuleFailureClassifier`，从已持久化 evidence manifest 识别 `NO_FAILURE/TIMEOUT/DEPENDENCY_BLOCKED/ASSERTION_FAILED/TEST_DATA_ACCOUNT/RUNNER_FAILURE/UNKNOWN`。 |
| 目标 | 生成 `RULE_READY` 诊断快照，输出 `classification`、`rootCauseCandidates`、`confidence`、`evidenceRefs`、`nextActions` 和 `manualReviewRequired`。 |
| 目标 | 报告 summary 同步写入 `diagnosisStatus`、`diagnosisRuleVersion`、`diagnosisPrimaryCategory` 和 `diagnosisManualReviewRequired`。 |
| 非目标 | 不调用 WP2，不保存 raw prompt/raw response，不新增 `/diagnoses` 诊断触发 API。 |
| 非目标 | 不读取 WP8/WP9 原表，不保存源节点 ID、账号租借 ID、账号 key/displayName、secretRef 原文、lease token 或 runner 原始产物。 |

## 3. 主要变更

| 模块 | 变更 |
|---|---|
| `RuleFailureClassifier` | 基于 manifest 摘要和 digest 生成规则分类、候选根因、证据引用、置信度和下一步动作。 |
| `ReportService` | 报告生成和 FAILED 重试时生成 `RULE_READY` 诊断；详情、幂等回放和归档返回最新诊断。 |
| Repository/MyBatis | 增加 `report_failure_diagnosis` 最小读写链路，支持替换当前报告最新规则诊断。 |
| Health | `failureClassifierReady=true`，`aiDiagnosisReady=false` 保持不变。 |
| 测试 | `ReportControllerTest` 覆盖 assertion primary bucket、账号租借 secondary bucket、`NO_FAILURE` 成功报告、digest evidence refs 和敏感字段不回显。 |
| 文档 | 更新 README、WP10 技术契约、研发任务拆解、测试策略和本交付说明。 |

## 4. 验收标准

1. 生成 READY 报告后，`latestDiagnosis.status=RULE_READY`，且 summary 中包含规则诊断状态和 primary category。
2. ASSERTION_FAILED 节点输出 `primaryCategory=ASSERTION_FAILED`；账号锁定或租借异常可作为 `TEST_DATA_ACCOUNT` secondary category。
3. SUCCEEDED 报告输出 `primaryCategory=NO_FAILURE`，无 root cause candidate，`manualReviewRequired=false`。
4. `rootCauseCandidates[*].evidenceRefs` 只包含 WP9/WP8 digest 引用，不包含源 run/node/account/lease 原始 ID。
5. 响应不包含 Authorization、Bearer、`secret://`、lease token、账号 key/displayName、raw prompt 或 raw response。

## 5. 验证入口

| 命令 | 用途 |
|---|---|
| `mvn -B -pl platform-api -Dtest=ReportControllerTest,ReportingHealthControllerTest test` | 验证规则分类、详情响应、health readiness 和脱敏边界。 |
| `bash scripts/wp10_report_smoke.sh` | 覆盖 WP10 report smoke、health 和 OpenAPI。 |
| `bash scripts/wp10_quality_gate.sh` | 聚合 WP10 脚本语法、Java 行数门禁、report smoke、后端契约测试和 DB validation。 |
| `bash scripts/platform_api_java_line_guard.sh` | Platform API Java 生产文件行数门禁。 |

本轮涉及 Java 生产代码、数据库诊断表写入、报告详情契约和失败诊断安全边界，需参考 `doc/mvp/final/engineering/WP1-WP4-统一发布准出清单.md` 并执行与影响面匹配的最小必要验证。

## 6. 风险与回滚

| 风险 | 处置 | 回滚 |
|---|---|---|
| 规则分类误判 | 输出 confidence、manualReviewRequired 和 evidenceRefs，不生成确定性结论；AI 诊断后续仍可覆盖或补充 | 回退本次 commit 或隐藏诊断展示，报告生成仍可保留 |
| 诊断输出泄露敏感字段 | 分类器只消费 manifest 摘要和 digest，测试扫描响应禁止字段 | 删除对应 report 快照后回退 commit |
| 规则诊断被误认为 AI 诊断 | `classificationOnly=true`、`modelInvoked=false`、`aiDiagnosisReady=false` 明确区分 | 保持 health 中 `aiDiagnosisReady=false`，后续 UI 按字段区分 |
| 诊断写入失败影响报告生成 | 当前 M4A 与报告生成同事务，失败会阻断生成以避免报告详情缺少规则 fallback | 回退本次 commit 或临时关闭 `veri-agent.reporting.generate-enabled` |

## 7. 五角色结论

| 角色 | 结论 | 说明 |
|---|---|---|
| 资深项目经理 | 通过 | M4A 范围限定为规则 fallback，不抢跑 WP2 AI、缺陷草稿或前端；回滚路径清晰。 |
| 资深产品经理 | 通过 | 报告详情从占位诊断推进到可读的失败分类、证据引用和下一步动作，且保留人工确认提示。 |
| 资深服务端架构师 | 通过 | 分类器只消费 WP10 已脱敏 manifest，不直连跨 WP 表；诊断表读写链路与既有 schema 对齐。 |
| 资深前端工程师 | 无影响 | 本轮未改 `portal-web`；后续诊断视图可直接消费 `latestDiagnosis` 结构。 |
| 资深质量工程师 | 通过 | 已补 controller 测试覆盖失败/成功分类、secondary bucket、health readiness 和敏感字段不回显。 |
