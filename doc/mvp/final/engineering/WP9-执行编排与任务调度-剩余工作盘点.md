# WP9 执行编排与任务调度 - 剩余工作盘点

| 项目 | 内容 |
|---|---|
| 工作包 | WP9 执行编排与任务调度 |
| 文档性质 | 当前范围剩余项审计、后续专项边界 |
| 日期 | 2026-06-15 |

## 1. 结论

截至当前收口，WP9 当前承诺范围内没有剩余功能开发项。剩余工作只剩发布前按目标环境执行 release gate、填写发布记录、以及后续专项另行立项。

`WP9-8.3 发布准出说明` 已由 `WP9-执行编排与任务调度-发布准出说明.md` 补齐；`WP9-8.4 前端操作说明` 已由 `WP9-执行编排与任务调度-前端操作说明.md` 补齐。

## 2. 当前范围已完成项

| 领域 | 完成证据 |
|---|---|
| Plan/DAG | plan CRUD、状态保护、DAG validator、dryRun、跨项目 WP6 bundle 拒绝和 secretRef 脱敏。 |
| Run/Queue | 手动触发、requestKey 幂等、run/node run 查询、cancel、retry、queue claim、heartbeat、recovery、状态聚合和 WP8 账号租借自动申请/释放。 |
| WP6 dispatch | `API_TEST` 只通过 WP6 应用服务创建 run，保留 allowlist、secretRef 和脱敏边界。 |
| WP10 handoff | `REPORT_HANDOFF` 输出 handoff 摘要，run export 提供脱敏证据，不生成完整报告。 |
| Trigger | WEBHOOK/CRON trigger 管理、dryRun、签名校验、sourceEventId 幂等、trigger event 和 CRON scanner。 |
| CRON 容量 | missed-fire 不补偿、单次 materialize、`nextFireAt` 推进、backlog batch 限批和后续 tick 接续处理。 |
| Frontend | `#execution` 工作台覆盖计划、DAG、运行、取消、重试、导出、触发配置和移动端 smoke。 |
| Quality gate | `scripts/wp9_quality_gate.sh` 聚合后端、前端、Playwright、build、DB validation、scheduler/webhook release smoke 和专项 smoke。 |
| 运维交付 | Scheduler/Trigger Runbook、webhook 签名样例、marketplace package、worker hosting readiness、前端操作说明和发布准出说明。 |

## 3. 后续专项

| 后续项 | 当前判断 | 不阻断原因 |
|---|---|---|
| 真实 cleanup worker / 破坏性 adapter | 后续安全专项 | 当前默认 `cleanup-enabled=false`，只记录任务，避免误删业务数据。 |
| WP7/WP10 跨 WP 集成深化 | 已由对应 WP 接入，后续仅保留专项增强 | WP9 已支持 `UI_TEST` 通过 WP7 应用服务创建真实 run，并为 WP10 提供 `REPORT_HANDOFF` 摘要和脱敏 run export；剩余不在于“是否接入”，而在于外部 worker、容量和更深的端到端专项。 |
| 真实供应商 OAuth/App 上架 | 后续供应商平台专项 | WP9 已提供 signed webhook helper、CI 样例和 marketplace 模板包。 |
| 独立外部 worker 二进制、多活 leader election、分布式锁 | 后续平台化增强 | 当前可用同一 `platform-api` 镜像通过 env 区分 web/scheduler-active/scheduler-standby。 |
| CRON 真实生产压测、容量看板、历史 backfill | 后续运维/容量专项 | 当前已通过 smoke 冻结不补偿、限批和接续处理语义，不承诺吞吐数值。 |

## 4. 发布前必做

```bash
bash scripts/wp9_quality_gate.sh
WP9_GATE_MODE=release WP9_SCHEDULER_SMOKE=managed WP9_WEBHOOK_HTTP_SMOKE=managed bash scripts/wp9_quality_gate.sh
git diff --check
```

发布目标若使用 external webhook smoke，必须先评审 `WP9_WEBHOOK_SMOKE_BASE_URL`、测试项目、webhook signing secret、CI eventId 策略和破坏性写入边界。
