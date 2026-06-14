# WP9 执行编排与任务调度 - M7D 外部 Webhook HTTP Smoke 交付说明

| 项 | 内容 |
|---|---|
| 交付阶段 | M7D 外部 Webhook HTTP Smoke |
| 交付日期 | 2026-06-14 |
| 交付范围 | 真实 HTTP webhook smoke、managed/external 运行模式、WP9 release gate webhook smoke 要求、测试策略和技术设计同步 |
| 非目标 | GitHub/GitLab/Jenkins 供应商 webhook 插件样例、生产 secret 托管变更、cron scanner 运行策略调整、前端页面改动 |
| 涉及模块 | `scripts/wp9_webhook_http_smoke.sh`、`scripts/wp9_quality_gate.sh`、WP9 文档 |
| 回滚方式 | 回退本次脚本和文档 commit；后端 webhook/cron 控制面、scheduler smoke、run export 和前端工作台不受影响 |

## 1. 目标与范围

M7D 目标是补齐 WP9 P0 “外部 webhook HTTP smoke”准出能力。新增脚本通过真实 `/api/v1/execution/webhooks/{id}` HTTP 入口验证 HMAC 签名、错误签名拒绝、有效签名创建 run、重复 `sourceEventId` 幂等回放、trigger event 证据和 run export 脱敏。

本切片只建设 smoke 和 release gate 编排，不新增供应商插件、不修改 Java 服务端逻辑、不改变生产 secret 存储策略。

## 2. 主要变更

1. 新增 `scripts/wp9_webhook_http_smoke.sh`，支持 `WP9_WEBHOOK_SMOKE_MODE=managed|external|auto`。
2. managed 模式启动临时 Postgres 与 platform-api，设置 `WP9_WEBHOOK_ENABLED=true`，seed SuperAdmin 后自动创建项目、webhook signing secret、WP6 approved bundle、WP9 READY plan 和 enabled WEBHOOK trigger。
3. external 模式通过 `WP9_WEBHOOK_SMOKE_BASE_URL` 指向已运行服务，复用同一组 HTTP 验证步骤。
4. smoke 使用 `timestamp.eventId.rawPayload` 的 HMAC-SHA256 小写 hex 签名串，验证错误签名返回 `EXECUTION_TRIGGER_SIGNATURE_INVALID`。
5. smoke 验证有效签名首次 ACCEPTED、重复 `sourceEventId` DUPLICATE/幂等回放，并确认 trigger events、run detail 和 run export 不包含 payload token、secret 值或 secretRef 明文。
6. `scripts/wp9_quality_gate.sh` release 模式新增 webhook HTTP smoke 强制要求，必须显式设置 `WP9_WEBHOOK_HTTP_SMOKE=managed` 或 `external`；本地 release gate 推荐 managed。

## 3. 验收入口

```bash
bash -n scripts/wp9_quality_gate.sh scripts/wp9_webhook_http_smoke.sh
WP9_WEBHOOK_SMOKE_MODE=managed bash scripts/wp9_webhook_http_smoke.sh
WP9_QUALITY_GATE_PLAN_ONLY=1 WP9_GATE_MODE=release WP9_SCHEDULER_SMOKE=managed WP9_WEBHOOK_HTTP_SMOKE=managed bash scripts/wp9_quality_gate.sh
WP9_GATE_MODE=release WP9_SCHEDULER_SMOKE=managed WP9_WEBHOOK_HTTP_SMOKE=managed bash scripts/wp9_quality_gate.sh
```

## 4. 风险与后续

1. managed smoke 依赖本机 Docker、Maven、curl、jq 和 openssl；无这些工具时应改用 external 模式指向已启动服务。
2. external 模式要求目标服务已启用 webhook、具备可登录 SuperAdmin，并允许脚本创建临时项目和 WP6/WP9 测试数据。
3. 脚本会生成临时 PostgreSQL 容器，默认结束后清理；如需保留现场可设置 `WP9_KEEP_WEBHOOK_SMOKE_RUNTIME=1`。
4. 供应商 webhook 插件样例、CI/CD 平台接入说明和更细的 runbook 仍归后续 M8。

## 5. 五角色结论

| 角色 | 结论 | 说明 |
|---|---|---|
| 资深项目经理 | 通过 | 范围限定为 M7D smoke 与 gate 编排，回滚路径为撤回脚本和文档 commit。 |
| 资深产品经理 | 通过 | 覆盖 DevOps/CI 管理员从外部 webhook 触发执行并读取脱敏结果的核心验收。 |
| 资深服务端架构师 | 通过 | 不改服务端代码，脚本复用既有 WP1/WP6/WP9 API 契约验证真实 HTTP 链路。 |
| 资深前端工程师 | 无影响 | 本切片不改 `portal-web`，前端触发配置和运行详情已由既有 M6/M7C 覆盖。 |
| 资深质量工程师 | 通过 | managed smoke 已覆盖签名、幂等、事件证据、run detail/export 脱敏，并纳入 release gate 要求。 |
