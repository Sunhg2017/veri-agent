# WP10 报告与失败诊断 - M9 WP3/WP5 证据 Adapter 交付说明

| 项目 | 内容 |
|---|---|
| 工作包 | WP10 报告与失败诊断 |
| 切片 | M9 WP3/WP5 evidence adapter |
| 文档性质 | 运行时代码、测试、文档和准出记录 |
| 日期 | 2026-06-17 |

## 1. 需求结论

本轮推进 WP10 证据聚合从 WP9/WP8 扩展到 WP3/WP5。WP10 仍只从 WP9 脱敏 run export 的节点 `resultSummary` 白名单字段识别稳定 UUID 引用，并通过 WP3/WP5 应用服务获取 aggregate-only 证据；不直连 WP3/WP5 表，不复制资产正文、候选正文、Prompt 原文、模型载荷、trace 明细 ID 清单或审计明细。

目标：

1. WP3 requirement/API/page/businessFlow/testCase 引用可生成 `sourceWp=WP3` evidence manifest。
2. WP5 task/candidate 引用可生成 `sourceWp=WP5` evidence manifest。
3. health policy 将 `wp3EvidenceManifestReady`、`wp5EvidenceManifestReady` 和 `evidenceAggregationReady` 标记为 true。
4. 报告 summary 暴露 WP3/WP5 引用计数、manifest 计数和截断标记，便于前端与导出复用。

非目标：

1. 不新增数据库表或字段，继续复用 `report_evidence_manifest`。
2. 不新增前端页面或按钮，现有 `#reports` 详情按 evidence manifest 通用结构展示。
3. 不读取 runner 原始产物、资产正文、候选正文、Prompt、模型响应、provider payload、账号凭据、secret、token、cookie 或 Authorization。
4. 不实现真实 provider 质量评测看板、外部缺陷系统写入、PDF/Word 完整报告、原始 artifact 归档、趋势报表或生产容量 SLA。

## 2. 主要变更

服务端：

1. 新增 WP3 `AssetCrossWpReportEvidenceService`、`AssetReportEvidenceQuery` 和 `AssetReportEvidenceResponse`，按项目 scope 校验资产引用，返回状态、生命周期、优先级、版本、tag/step/trace link/关联资产计数和更新时间。
2. 新增 WP5 `TestDesignCrossWpReportEvidenceService`、`TestDesignReportEvidenceQuery` 和 `TestDesignReportEvidenceResponse`，按项目 scope 校验任务/候选引用，返回任务计数、候选状态分布、aggregate report manifest 计数与 digest、候选生命周期和关联 refs。
3. 新增 `ReportEvidenceAssembler`，从 `ReportService` 拆出 WP9/WP8/WP3/WP5 evidence refs 解析、manifest 构造、sourceRefDigest、summary key 过滤和 redaction flags。
4. `ReportService` 生成/重试报告时调用 assembler，报告 summary 增加 `wp3EvidenceReferenceCount`、`wp3EvidenceManifestCount`、`wp3EvidenceReferenceTruncated`、`wp5EvidenceReferenceCount`、`wp5EvidenceManifestCount`、`wp5EvidenceReferenceTruncated`，并将 `evidenceManifestTruncated` 扩展为 WP8/WP3/WP5 统一口径。
5. `ReportingHealthService` 将 WP3/WP5 evidence manifest 和整体 evidence aggregation readiness 标记为 ready。

测试与文档：

1. 新增 WP3/WP5 adapter 单测，覆盖 aggregate-only 输出、项目越权拒绝、敏感正文不出现在响应字符串和 WP3 同项目 trace link 计数。
2. 扩展 report controller/health controller 测试，覆盖 WP3/WP5 manifest、redaction flags、summary 计数和跨 WP service query 参数。
3. 更新启动准备、PRD、技术设计、测试策略、发布准出、剩余工作盘点、研发拆解、README 和当前实现基线，移除 WP3/WP5 adapter 后续专项口径。

## 3. 验收标准

1. WP10 只经 WP3/WP5 应用服务取证据，不直连跨 WP 表。
2. WP3/WP5 manifest 只保存 digest、summary keys、状态、计数、redaction flags 和必要时间戳。
3. WP3/WP5 原始资产正文、候选正文、Prompt 原文、模型载荷、trace/audit 明细 ID 清单不进入报告、导出或测试断言样本。
4. Java 生产文件均低于 1200 行，核心跨 WP 证据逻辑具备必要 JavaDoc/注释。
5. 影响面匹配的后端、WP10 quality gate、前端测试/build 和 `git diff --check` 通过。

## 4. 验证记录

本轮已执行并通过：

```bash
mvn -B -pl platform-api -Dtest=AssetCrossWpReportEvidenceServiceTest,TestDesignCrossWpReportEvidenceServiceTest test
bash scripts/platform_api_java_line_guard.sh
mvn -B -pl platform-api -Dtest=AssetCrossWpReportEvidenceServiceTest,TestDesignCrossWpReportEvidenceServiceTest,ReportControllerTest,ReportingHealthControllerTest,ReportingOpenApiContractTest test
bash scripts/wp10_quality_gate.sh
mvn -B -pl platform-api test
cd portal-web && npm test
cd portal-web && npm run build
git diff --check
```

结果：

1. 定向 WP3/WP5 adapter 单测通过，4 tests，0 failures，0 errors，0 skipped。
2. Java 行数门禁通过，`platform-api/src/main/java` 生产 Java 文件均不超过 1200 行。
3. WP10 定向后端/OpenAPI 测试通过，14 tests，0 failures，0 errors，0 skipped。
4. `scripts/wp10_quality_gate.sh` 通过，包含脚本语法、Java 行数门禁、WP10 smoke、诊断质量评测、诊断上下文脱敏评测、后端/OpenAPI 测试、前端 Vitest、Playwright smoke、前端 build 和合并 DB validation。
5. `mvn -B -pl platform-api test` 通过，659 tests，0 failures，0 errors，0 skipped。
6. `cd portal-web && npm test` 通过，27 files / 199 tests。
7. `cd portal-web && npm run build` 通过；仅保留既有 Vite dynamic/static import 和 chunk size warning。
8. `git diff --check` 通过。

## 5. 风险和回滚

| 风险 | 控制 | 回滚 |
|---|---|---|
| WP3/WP5 证据泄露正文 | 服务响应只返回 aggregate-only 字段，WP10 manifest 再次 digest 化并过滤 summary keys | 回退 M9 代码，保留 WP9/WP8 manifest 主链路 |
| 跨项目引用被计入证据 | WP3/WP5 服务按项目 scope 校验引用；WP3 trace link 计数限定在同项目资产集合 | 禁用报告生成或回退 adapter |
| manifest 数量超过上限 | `maxEvidenceItems` 统一截断，summary 写 truncated 标记；跨 WP 引用仍先校验 | 调小上限或回退 adapter |
| Java 文件膨胀影响门禁 | 从 `ReportService` 拆出 `ReportEvidenceAssembler`，运行 Java 行数门禁 | 继续拆分 assembler 内部职责 |

## 6. 五角色结论

| 角色 | 结论 | 说明 |
|---|---|---|
| 资深项目经理 | 通过 | M9 范围限定在 WP3/WP5 aggregate-only evidence adapter，无 DB/前端范围扩张，回滚方式清晰。 |
| 资深产品经理 | 通过 | 报告详情补齐资产和测试设计聚合证据，仍不承诺完整报告、外部缺陷写入或趋势看板。 |
| 资深服务端架构师 | 通过 | 跨 WP 取证通过应用服务，manifest digest-only，`ReportService` 已拆分避免职责和行数继续膨胀。 |
| 资深前端工程师 | 无影响 | 前端通用 evidence manifest 结构可复用，本轮不新增路由、控件或响应式行为。 |
| 资深质量工程师 | 通过 | 定向 adapter 单测、Java 行数门禁、WP10 quality gate、后端全量、前端测试/build 和 `git diff --check` 均已通过。 |
