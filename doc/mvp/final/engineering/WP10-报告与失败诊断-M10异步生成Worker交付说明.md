# WP10 报告与失败诊断 - M10 异步生成 Worker 交付说明

| 项目 | 内容 |
|---|---|
| 工作包 | WP10 报告与失败诊断 |
| 切片 | M10 异步生成 worker |
| 文档性质 | 运行时代码、测试、文档和准出记录 |
| 日期 | 2026-06-17 |

## 1. 需求结论

本轮补齐 WP10 自身 `report_execution_report` 队列的后台执行器。报告生成保持默认同步兼容；开启 `veri-agent.reporting.async-generation-enabled=true` 后，生成和重试请求先进入 `QUEUED`，worker 通过条件认领将 `QUEUED -> GENERATING`，并收敛到 `READY/FAILED`。`GENERATING` 超过阈值时会被恢复为 `FAILED/REPORT_GENERATION_TIMEOUT`。

worker 只处理 WP10 自身队列，不触发 WP9 execution run，不修改跨 WP 表，不引入数据库迁移或前端页面变更。

非目标：

1. 不建设外部调度器，不认领 WP9 队列。
2. 不变更 WP3/WP5/WP8/WP9 aggregate-only 证据契约。
3. 不建设真实 provider 质量看板、外部缺陷写入、PDF/Word 完整报告或趋势/BI。

## 2. 主要变更

服务端：

1. 新增 `ReportGenerationWorkerService` 和 `ReportGenerationWorkerTickResponse`。
2. `ReportService` 增加 `queuedReports`、`processQueuedReport`、`recoverStaleGeneratingReports`、`queueRetry` 和异步重试收敛逻辑。
3. `ReportingProperties` 与 `application-reporting.yml` 增加 worker / async 配置项。
4. `ReportingRepository`、JDBC / in-memory 仓储和 MyBatis XML 增加 queued scan、stale scan 和条件状态更新。
5. `ReportingHealthService` / `ReportingHealthResponse` 增加 worker readiness 字段。

测试：

1. 新增 `ReportGenerationWorkerServiceTest`，覆盖队列入队、worker 认领、失败脱敏、stale 恢复和异步 retry。
2. 更新 `ReportingHealthControllerTest`，覆盖 worker health 字段。
3. 更新 `DbProfileRepositoryContractTest`，覆盖 JDBC 条件认领和 stale 扫描。
4. 修正 `ReportDiagnosisContextRedactionEvaluationTest` 的 `ReportingProperties` 构造参数。

文档：

1. 更新 PRD、技术设计、测试策略、Runbook、发布准出、剩余工作盘点、README 和当前实现基线。
2. 新增本交付说明，作为 M10 准出证据。

## 3. 验证记录

已执行并通过：

```bash
mvn -B -pl platform-api -Dtest=ReportGenerationWorkerServiceTest test
mvn -B -pl platform-api -Dtest=ReportGenerationWorkerServiceTest,ReportingHealthControllerTest,DbProfileRepositoryContractTest#reportingRepositoryClaimsQueuedReportsAndScansStaleGeneratingThroughJdbc test
bash scripts/platform_api_java_line_guard.sh
bash scripts/wp10_quality_gate.sh
mvn -B -pl platform-api test
git diff --check
```

结果：

1. `ReportGenerationWorkerServiceTest` 通过，4 tests，0 failures，0 errors，0 skipped。
2. 定向 health / DB contract 通过，覆盖 `QUEUED -> GENERATING -> READY/FAILED`、失败摘要脱敏、异步 retry 清理旧失败字段、stale `GENERATING` 恢复和 JDBC 条件认领。
3. `bash scripts/platform_api_java_line_guard.sh` 通过，Platform API 生产 Java 文件均不超过 1200 行。
4. `bash scripts/wp10_quality_gate.sh` 通过，包含脚本语法、Java 行数门禁、WP10 smoke、诊断质量评测、诊断上下文脱敏评测、后端/OpenAPI 测试、前端 Vitest、Playwright smoke、前端 build 和合并 DB validation。
5. `mvn -B -pl platform-api test` 通过，664 tests，0 failures，0 errors，0 skipped，`BUILD SUCCESS`。
6. `git diff --check` 通过，无空白错误。

## 4. 风险和回滚

| 风险 | 处置 | 回滚 |
|---|---|---|
| worker 停滞在 `QUEUED` | 通过 health、worker 日志和 tick 结果定位 | 关闭 `veri-agent.reporting.async-generation-enabled`，回到同步生成 |
| worker 重复认领 | `updateReportIfStatus` 条件更新防并发 | 保持条件认领，不需要手工改状态 |
| stale `GENERATING` 误判 | `generation-running-timeout-seconds` 限制可调 | 调整阈值或关闭 async generation |
| 失败摘要泄露 | `SensitiveTextSanitizer` 和失败摘要测试兜底 | 修复后重跑 worker 测试和 redaction gate |

## 5. 五角色结论

| 角色 | 结论 | 说明 |
|---|---|---|
| 资深项目经理 | 通过 | M10 只补 WP10 自身 worker，不扩张到 WP9 调度或新模块。 |
| 资深产品经理 | 通过 | 异步生成仍保持同步兼容，用户可感知的状态收敛清晰。 |
| 资深服务端架构师 | 通过 | 条件认领、stale 恢复和异步 retry 的状态机语义清楚。 |
| 资深前端工程师 | 无影响 | 本切片不修改前端运行时代码。 |
| 资深质量工程师 | 通过 | 定向 worker / health / DB contract、Java 行数门禁、WP10 quality gate、后端全量和 `git diff --check` 均已通过。 |
