# WP9 执行编排与任务调度 - M8B 调度触发 Runbook 交付说明

| 项 | 内容 |
|---|---|
| 交付阶段 | M8B 调度触发 Runbook |
| 交付日期 | 2026-06-14 |
| 交付范围 | scheduler/cron/webhook 开关、发布准出、恢复重放、webhook secret 轮换、CRON 运维、排障和回滚说明 |
| 非目标 | 新增服务端接口、修改 scheduler/trigger 运行逻辑、供应商 marketplace/App 插件包、前端页面改动 |
| 涉及模块 | WP9 文档 |
| 回滚方式 | 回退本次文档 commit；M7/M8A 已有脚本、quality gate、后端和前端能力不受影响 |

## 1. 目标与范围

M8B 目标是补齐 WP9-8.2 Scheduler Runbook。运维、QA 和发布负责人可按 Runbook 完成 scheduler、webhook、cron 的开关确认、准出验证、恢复/重放、webhook secret 轮换、CRON 排障和回滚。

本切片不改 Java 服务端逻辑、不新增接口、不改 `portal-web` 页面，也不引入新的发布脚本。

## 2. 主要变更

1. 新增 `WP9-Scheduler-Trigger-Runbook.md`，覆盖配置开关、日常验证和 release gate 要求。
2. 明确恢复和重放策略：claim 过期 recovery、手动 requestKey 回放、webhook sourceEventId 回放、cron 扫描和失败节点 retry。
3. 明确 webhook secret 轮换流程，区分服务端 `secretRef` 和 CI 侧 secret 明文值。
4. 明确 CRON 运维策略：dryRun、暂停、变更频率、错过多次 fire 不批量补偿、容量保护和证据留存。
5. 补齐排障表、回滚步骤和发布准出记录清单。

## 3. 验收入口

```bash
git diff --check
rg -n "WP9-Scheduler|M8B|Scheduler Runbook|cron scanner 运维 runbook" doc/mvp/final/engineering/WP9-*
```

## 4. 风险与后续

1. 本轮只交付 Runbook，不自动执行真实环境恢复或 secret 轮换。
2. 供应商 marketplace/App 插件包和安装配置向导仍归后续切片。
3. WP10 报告 handoff 的端到端发布准出仍需后续 WP10 集成后补齐。

## 5. 五角色结论

| 角色 | 结论 | 说明 |
|---|---|---|
| 资深项目经理 | 通过 | M8B 范围限定为运维 Runbook，回滚路径为撤回文档 commit。 |
| 资深产品经理 | 通过 | 覆盖发布负责人、QA 和运维对调度/触发可控、可恢复、可排障的使用诉求。 |
| 资深服务端架构师 | 通过 | Runbook 复用既有 scheduler、webhook、cron 契约，不引入新运行时行为。 |
| 资深前端工程师 | 无影响 | 本切片不改 `portal-web`，前端仍展示既有调度开关、trigger 摘要和事件证据。 |
| 资深质量工程师 | 通过 | Runbook 明确 release gate、smoke、恢复重放、脱敏检查和回滚证据要求。 |
