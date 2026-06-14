# WP9 执行编排与任务调度 - M7B 生产 Cron Scanner 交付说明

| 项 | 内容 |
|---|---|
| 交付阶段 | M7B 生产 Cron Scanner |
| 交付日期 | 2026-06-14 |
| 交付范围 | 到期 CRON trigger 扫描、CRON run 幂等创建、trigger event 证据、health readiness、scheduler smoke 覆盖 |
| 非目标 | 供应商 webhook 插件样例、外部 webhook HTTP smoke、执行摘要导出接口、错过多次 fire 的批量补偿 |
| 涉及模块 | `platform-api` WP9 execution application/repository/mapper/test、WP9 文档 |
| 回滚方式 | 回退本次后端和文档 commit；`veri-agent.execution.cron-enabled=false` 仍可关闭 CRON 扫描触发 |

## 1. 目标与范围

M7B 目标是在既有 scheduler loop 内补齐生产级 CRON scanner 最小闭环。启用 `scheduler-enabled` 与 `cron-enabled` 后，scheduler tick 会先扫描到期 `CRON/ENABLED/nextFireAt<=now` 触发器，为每个到期时间创建或回放一个 CRON run，再进入 recovery、claim 和 dispatch 流程。

## 2. 主要变更

1. `ExecutionTriggerService` 新增 `scanDueCronTriggers`，使用 `triggerId + nextFireAt` 生成稳定 `sourceEventId` 与 requestKey，复用 `triggerExternalRun` 创建 CRON run。
2. CRON trigger 配置新增服务端校验：`config.cron` 使用 Spring `CronExpression` 解析，`timezone` 使用 `ZoneId` 校验；未传 `nextFireAt` 时自动计算下一次触发时间。
3. `ExecutionSchedulerService.runOnce()` 在 recovery/claim 前执行 CRON 扫描，并在 tick response 中返回 `cronScannedTriggerCount`、`cronTriggeredRunCount`、`cronFailedTriggerCount`。
4. `ExecutionRepository`/MyBatis/local 测试仓储新增到期 CRON trigger 查询，复用既有 `idx_execution_trigger_next_fire` 索引。
5. health policy 将 `cronScannerReady` 标为 `true`，定向测试覆盖 due CRON -> run -> event -> nextFireAt 推进。

## 3. 验收入口

```bash
mvn -B -pl platform-api -Dtest=ExecutionSchedulerServiceTest,ExecutionHealthControllerTest,ExecutionTriggerControllerTest,OpenApiContractTest test
bash scripts/wp9_scheduler_smoke.sh
WP9_GATE_MODE=release WP9_SCHEDULER_SMOKE=managed bash scripts/wp9_quality_gate.sh
```

## 4. 风险与后续

1. 本切片按每个 due `nextFireAt` 触发一次，不做错过多次 fire 的批量补偿，避免一次扫描放大运行量。
2. 扫描失败会记录 `FAILED` trigger event 并推进下一次触发，避免坏配置或不可运行计划每 tick 重复失败。
3. 供应商 webhook 插件样例、外部 webhook HTTP smoke 和执行摘要导出仍归后续 M7/M8。
