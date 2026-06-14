# WP9 执行编排与任务调度 - M8G CRON 积压批次准出交付说明

| 项 | 内容 |
|---|---|
| 交付阶段 | M8G CRON 积压批次准出 |
| 交付日期 | 2026-06-14 |
| 交付范围 | CRON backlog 扫描批次上限、定向 smoke、quality gate 接入、runbook/技术文档同步 |
| 非目标 | 真实生产压测、历史 backfill、修改 scheduler Java 主路径语义、修改数据库结构、前端页面改动、独立 worker 进程 |
| 涉及模块 | `platform-api` scheduler/trigger 测试、`scripts/wp9_cron_backlog_smoke.sh`、`scripts/wp9_quality_gate.sh`、WP9 文档 |
| 回滚方式 | 回退本次测试、脚本和文档 commit；既有 CRON scanner、scheduler loop、run export 和 M8F capacity smoke 不受影响 |

## 1. 目标与范围

M8G 目标是把 CRON 积压场景的批次边界变成可重复的本地准出证据。当前 scheduler tick 使用 `schedulerTickBatchSize` 同时限制 CRON due trigger 扫描和队列认领规模，本轮不改变该语义，只补充验证：

1. 当多个 CRON trigger 同时 due 且数量超过 tick batch size 时，单次 tick 只扫描 batch 上限内的 trigger。
2. 未进入本次扫描的 due trigger 不会被错误标记、不会生成 trigger event，也不会创建 run。
3. 下一次 tick 会继续处理剩余 due trigger，形成可控的积压消化节奏。

本切片不是压力测试报告，也不承诺生产容量数值；它只冻结当前控制面在 backlog 下的节流行为，为后续真实生产压测和容量指标设计提供回归基线。

## 2. 主要变更

1. 新增 `ExecutionSchedulerServiceTest#runOnceRespectsCronBacklogBatchLimitAndLeavesRemainingDueTriggerForNextTick`，验证 3 条 due CRON trigger 在 `schedulerTickBatchSize=2` 时首轮只处理 2 条，第三条保留到下一轮。
2. 新增 `scripts/wp9_cron_backlog_smoke.sh`，定向运行 CRON backlog/batch 相关测试。
3. `scripts/wp9_quality_gate.sh` 增加 backlog smoke 的脚本语法检查和默认执行步骤，避免发布准出漏掉积压节流边界。
4. 更新 README、`WP9-Scheduler-Trigger-Runbook.md`、技术设计、测试策略和研发拆解，明确 M8G 已覆盖本地 backlog batch 准出；真实生产压测仍作为后续专项。

## 3. 验收入口

```bash
bash -n scripts/wp9_cron_backlog_smoke.sh
bash scripts/wp9_cron_backlog_smoke.sh
WP9_QUALITY_GATE_PLAN_ONLY=1 bash scripts/wp9_quality_gate.sh
git diff --check
```

## 4. 风险与后续

1. smoke 使用本地测试上下文，不代表生产吞吐或数据库锁竞争结论；真实容量仍需独立压测环境和观测指标。
2. 当前 batch 上限复用 scheduler tick batch size，后续如需独立 CRON scan limit，应新增配置并补充兼容性迁移。
3. 未扫描的 due trigger 会留到后续 tick 处理，极端积压下需要通过 worker 扩容、频率治理或未来 backfill 限额专项处理。

## 5. 五角色结论

| 角色 | 结论 | 说明 |
|---|---|---|
| 资深项目经理 | 通过 | M8G 将 CRON 积压批次边界纳入准出，不扩大到生产压测或 backfill，实现范围清晰可回滚。 |
| 资深产品经理 | 通过 | 明确发布口径：当前可证明 backlog 被限批处理，但不承诺自动追赶历史窗口或生产吞吐指标。 |
| 资深服务端架构师 | 通过 | 不改运行时语义，只补足 scheduler tick batch 对 CRON scan 的契约测试。 |
| 资深前端工程师 | 无影响 | 本切片不改 `portal-web`，无 UI、路由或权限入口影响。 |
| 资深质量工程师 | 通过 | 新增独立 smoke 并纳入 WP9 quality gate，覆盖多 due trigger 积压时的限批与接续处理。 |
