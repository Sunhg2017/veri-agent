# WP9 执行编排与任务调度 - M7A 质量门禁脚本交付说明

| 项 | 内容 |
|---|---|
| 交付阶段 | M7A 质量门禁脚本 |
| 交付日期 | 2026-06-14 |
| 交付范围 | WP9 聚合 quality gate、managed scheduler smoke 入口、测试策略和研发拆解同步 |
| 非目标 | 生产 cron scanner、供应商 webhook 插件样例、外部 webhook HTTP smoke、执行摘要导出接口 |
| 涉及模块 | `scripts/wp9_quality_gate.sh`、`scripts/wp9_scheduler_smoke.sh`、WP9 文档 |
| 回滚方式 | 回退本次脚本和文档 commit；已完成的 WP9 后端、前端和 Playwright smoke 不受影响 |

## 1. 目标与范围

M7A 目标是补齐 WP9 发布准出的本地聚合入口，使日常开发和 release gate 都能用同一脚本复用已有测试资产。开发模式默认聚合 WP9 后端定向测试、前端 WP9 Vitest、Playwright smoke、前端构建和 DB validation；release 模式要求显式启用 managed scheduler smoke。

## 2. 主要变更

1. 新增 `scripts/wp9_quality_gate.sh`，支持 `WP9_QUALITY_GATE_PLAN_ONLY=1` 输出计划、`WP9_SKIP_FRONTEND_E2E=1` 跳过浏览器 smoke、`WP9_SKIP_DB_VALIDATION=1` 跳过 DB validation。
2. 新增 `scripts/wp9_scheduler_smoke.sh`，复用 `ExecutionSchedulerServiceTest` 作为 managed scheduler smoke，覆盖 scheduler tick、WP6 dispatch、report handoff、disabled noop、失败脱敏和配置边界。
3. release gate 模式支持 `WP9_GATE_MODE=release` 或 `WP9_RELEASE_GATE=1`，并强制要求 `WP9_SCHEDULER_SMOKE=managed`。
4. 更新测试策略和研发拆解文档，标明 M7A 已完成和后续剩余边界。

## 3. 验收入口

```bash
bash scripts/wp9_quality_gate.sh
WP9_GATE_MODE=release WP9_SCHEDULER_SMOKE=managed bash scripts/wp9_quality_gate.sh
bash scripts/wp9_scheduler_smoke.sh
```

计划检查：

```bash
WP9_QUALITY_GATE_PLAN_ONLY=1 bash scripts/wp9_quality_gate.sh
WP9_GATE_MODE=release bash scripts/wp9_quality_gate.sh
```

第二条应返回非零并提示 release gate 必须显式启用 scheduler smoke。

## 4. 风险与后续

1. `wp9_scheduler_smoke.sh` 当前是 managed JVM/MockMvc 级 smoke，不依赖外部网络或真实 runner endpoint。
2. 生产 cron scanner、供应商 webhook 插件样例和外部 webhook HTTP smoke 尚未落地。
3. 执行摘要导出接口仍未实现，quality gate 暂不包含导出 smoke。
