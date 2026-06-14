# WP9 执行编排与任务调度 - M8F CRON 容量策略交付说明

| 项 | 内容 |
|---|---|
| 交付阶段 | M8F CRON 容量策略 |
| 交付日期 | 2026-06-14 |
| 交付范围 | 错过多次 fire 不补偿策略、定向容量 smoke、quality gate 接入、runbook/技术文档同步 |
| 非目标 | 批量补偿历史窗口、修改 scheduler Java 主路径语义、修改数据库结构、前端页面改动、独立 worker 进程 |
| 涉及模块 | `platform-api` scheduler/trigger 测试、`scripts/wp9_cron_capacity_smoke.sh`、`scripts/wp9_quality_gate.sh`、WP9 文档 |
| 回滚方式 | 回退本次测试、脚本和文档 commit；既有 CRON scanner、scheduler loop 和 run export 不受影响 |

## 1. 目标与范围

M8F 目标不是增加新的 CRON 调度能力，而是把现有“只按每个 due `nextFireAt` 创建一次 run，不补偿历史窗口”的策略提升为可独立验收的发布准出证据。新增定向 smoke 证明：

1. 一条长时间积压的 CRON trigger 在一次 tick 中只会 materialize 一次。
2. 扫描后 `nextFireAt` 会推进到扫描时间之后，不会在下一次立即 tick 时继续回补同一历史窗口。
3. 触发事件只保留一条 accepted evidence，不会生成重复 run。

本切片只处理容量策略与可测试性，不引入历史窗口补偿、批量追赶或运维侧自动修复。

## 2. 主要变更

1. 新增 `ExecutionSchedulerServiceTest#runOnceCapsMissedCronFireAtOneRunAndAdvancesPastScanWindow`，验证 6 小时积压的 CRON 只生成 1 个 run，且下一次 `nextFireAt` 已推进到 `tickedAt` 之后。
2. 新增 `scripts/wp9_cron_capacity_smoke.sh`，仅运行 CRON 容量策略相关定向测试。
3. `scripts/wp9_quality_gate.sh` 增加 script syntax 检查和默认执行步骤，保证 M8F 进入 WP9 准出门禁。
4. 更新 `WP9-Scheduler-Trigger-Runbook.md`、技术设计、测试策略和研发拆解，明确“不做错过多次 fire 的批量补偿”是当前发布约束，而不是遗漏实现。

## 3. 验收入口

```bash
bash -n scripts/wp9_cron_capacity_smoke.sh
bash scripts/wp9_cron_capacity_smoke.sh
WP9_QUALITY_GATE_PLAN_ONLY=1 bash scripts/wp9_quality_gate.sh
git diff --check
```

## 4. 风险与后续

1. 当前策略保守，不会自动补偿历史窗口；如果业务未来要求追赶缺失窗口，需要单独定义 backfill 上限、速率控制和重放证据。
2. 新 smoke 复用 scheduler test 上下文，验证的是控制面行为，不是独立生产压测。
3. 如果后续引入更强容量保障，应把 `nextFireAt` 推进策略、节流参数和失败重试分开建模。

## 5. 五角色结论

| 角色 | 结论 | 说明 |
|---|---|---|
| 资深项目经理 | 通过 | M8F 只补容量策略准出，不扩大到补偿历史窗口，范围清晰可回滚。 |
| 资深产品经理 | 通过 | 明确告诉发布侧：当前 CRON 只保证单次 due fire，不承诺自动追赶。 |
| 资深服务端架构师 | 通过 | 不改运行时语义，只把现有策略做成可验证门禁。 |
| 资深前端工程师 | 无影响 | 本切片不改前端。 |
| 资深质量工程师 | 通过 | 新增 smoke 并纳入 quality gate，覆盖容量边界和重复触发防线。 |
