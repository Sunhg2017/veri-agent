# WP9 执行编排与任务调度 - M8E WP10 交接准出交付说明

| 项 | 内容 |
|---|---|
| 交付阶段 | M8E WP10 交接准出 |
| 交付日期 | 2026-06-14 |
| 交付范围 | REPORT_HANDOFF scheduler 完成证据、run export 脱敏证据、独立 smoke 脚本、quality gate 接入和 WP9 文档同步 |
| 非目标 | 实现 WP10 报告生成、生成报告正文或文件、读取 runner 原始产物、修改 Java 运行逻辑、修改数据库结构、前端页面改动 |
| 涉及模块 | `scripts/wp9_report_handoff_smoke.sh`、`scripts/wp9_quality_gate.sh`、WP9 文档 |
| 回滚方式 | 回退本次脚本和文档 commit；既有 scheduler loop、run export、webhook、worker readiness 和主质量门禁仍可按原路径运行 |

## 1. 目标与范围

M8E 目标是把 WP9 已有 `REPORT_HANDOFF` 能力从“被 scheduler 测试顺带覆盖”推进到“发布前有独立准出证据”。本轮新增 `scripts/wp9_report_handoff_smoke.sh`，复用现有后端测试验证两件事：

1. scheduler tick 可在同一 run 中完成 `API_TEST` 和 `REPORT_HANDOFF` 节点，并把报告交接摘要标记为 `reportHandoffReady=true`、`rawReportStored=false`。
2. `GET /api/v1/execution/runs/{id}/export` 只输出脱敏 run detail、节点状态计数和 redaction policy，不包含 raw baseUrl、secret、stdout/stderr、请求响应正文或 webhook payload。

本切片不生成 WP10 报告正文，也不承接 WP10 的诊断、归档、下载或报表门户能力。WP9 的职责仍是提供可审计、可脱敏、可由 WP10 消费的执行摘要和交接状态。

## 2. 主要变更

1. 新增 `scripts/wp9_report_handoff_smoke.sh`，定向运行 `ExecutionSchedulerServiceTest#runOnceClaimsDispatchesApiTestAndCompletesReportHandoff` 和 `ExecutionRunControllerTest#exportsSanitizedRunEvidenceWithNodeStatusCounts`。
2. `scripts/wp9_quality_gate.sh` 增加 report handoff smoke 的语法检查和默认执行步骤，避免 WP9 准出漏掉 WP10 交接证据。
3. 更新 README、技术设计、测试策略和研发拆解，记录 M8E 已覆盖 report handoff 发布证据，并把 WP10 完整集成继续保留为后续工作。

## 3. 验收入口

```bash
bash -n scripts/wp9_report_handoff_smoke.sh
bash scripts/wp9_report_handoff_smoke.sh
WP9_QUALITY_GATE_PLAN_ONLY=1 bash scripts/wp9_quality_gate.sh
git diff --check
```

## 4. 风险与后续

1. smoke 复用后端测试和内存/测试替身，不启动真实 WP10 服务；WP10 真实消费接口、报告生成和端到端发布准出仍需 WP10 集成后补齐。
2. 当前 `REPORT_HANDOFF` 摘要只表达 handoff ready 和 raw report 未存储，不包含完整报告 schema；如 WP10 需要更强契约，应另行定义版本化 handoff schema。
3. `wp9_quality_gate.sh` 默认增加一个后端定向 smoke，会增加少量本地验证耗时，但能提前发现 report handoff 或 export 脱敏回归。

## 5. 五角色结论

| 角色 | 结论 | 说明 |
|---|---|---|
| 资深项目经理 | 通过 | M8E 范围限定为 WP9 交接准出证据，不扩大到 WP10 报告实现，回滚路径清晰。 |
| 资深产品经理 | 通过 | 满足发布负责人对“WP9 是否已提供可交接摘要”的验收需要，同时保留完整报告为 WP10 范围。 |
| 资深服务端架构师 | 通过 | 不改 Java 运行逻辑，复用既有 scheduler、run export 和脱敏契约，跨 WP 边界清晰。 |
| 资深前端工程师 | 无影响 | 本切片不改 `portal-web`；运行详情导出按钮仍使用既有 run export API。 |
| 资深质量工程师 | 通过 | 新增独立 smoke 并纳入 WP9 quality gate，覆盖 REPORT_HANDOFF 完成和导出脱敏证据。 |
