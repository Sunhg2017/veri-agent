# WP9 执行编排与任务调度 - M8C 供应商 Marketplace 接入包交付说明

| 项 | 内容 |
|---|---|
| 交付阶段 | M8C 供应商 Marketplace 接入包 |
| 交付日期 | 2026-06-14 |
| 交付范围 | WP9 webhook marketplace manifest、GitHub/GitLab/Jenkins 模板、payload 示例、安装说明、离线验收脚本和 quality gate 接入 |
| 非目标 | 新增服务端接口、真实 OAuth/App 上架、供应商 API 调用、前端页面改动、生产 secret 托管策略调整 |
| 涉及模块 | `integrations/wp9-webhook-marketplace/`、`scripts/wp9_marketplace_package_smoke.sh`、`scripts/wp9_quality_gate.sh`、WP9 文档 |
| 回滚方式 | 回退本次接入包、脚本和文档 commit；M8A 签名 helper、M8B Runbook、M7D webhook HTTP smoke 和运行时能力不受影响 |

## 1. 目标与范围

M8C 目标是在 M8A 可执行签名样例和 M8B 调度触发 Runbook 之后，补齐可复制到供应商 marketplace/App 或企业内部模板库的安装资产。DevOps 管理员可以基于 `manifest.json`、供应商模板和 payload 示例完成 GitHub Actions、GitLab CI、Jenkins 的 signed webhook 接入准备，并通过离线 smoke 校验包完整性和签名 helper 行为。

本切片不申请真实供应商 marketplace 上架，不实现 OAuth App 安装授权，不改变 `POST /api/v1/execution/webhooks/{triggerId}` 契约，也不修改 Java 运行时代码。

## 2. 主要变更

1. 新增 `integrations/wp9-webhook-marketplace/manifest.json`，声明 packageId、provider、安装变量、签名算法、Header、幂等规则、模板路径、payload 示例和验证入口。
2. 新增 `integrations/wp9-webhook-marketplace/README.md`，覆盖安装前置、安装变量、eventId 幂等策略、安装步骤、上架检查和真实 OAuth/App 后续边界。
3. 新增 GitHub Actions、GitLab CI、Jenkins 模板，统一使用 `timestamp.eventId.rawBody` HMAC-SHA256 小写 hex 签名，并通过 `--data-binary` 保持 raw body 不被改写。
4. 新增 `payloads/github.json`、`payloads/gitlab.json`、`payloads/jenkins.json`，用于离线核对供应商 payload 摘要字段。
5. 新增 `scripts/wp9_marketplace_package_smoke.sh`，离线校验 manifest、模板必需变量、secretRef 不外泄、payload JSON、README 锚点和确定性签名。
6. `scripts/wp9_quality_gate.sh` 增加 marketplace package smoke，确保 WP9 开发和发布门禁覆盖接入包漂移。

## 3. 验收入口

```bash
bash -n scripts/wp9_marketplace_package_smoke.sh
bash scripts/wp9_marketplace_package_smoke.sh
WP9_QUALITY_GATE_PLAN_ONLY=1 bash scripts/wp9_quality_gate.sh
git diff --check
```

## 4. 风险与后续

1. 模板依赖 CI runner 提供 `curl`、`jq`、`openssl` 和 `awk`；真实安装前需确认 runner image 或 agent 环境。
2. 本包只覆盖 signed webhook 模板层，不包含供应商 OAuth 权限申请、安装授权、卸载回收和 marketplace 审核材料。
3. 生产联调仍必须执行 `scripts/wp9_webhook_http_smoke.sh` managed 或 external，并检查 trigger event、run detail 和 run export 脱敏证据。
4. 错过多次 CRON fire 的容量策略和生产压测仍按后续 WP9 运维增强推进。

## 5. 五角色结论

| 角色 | 结论 | 说明 |
|---|---|---|
| 资深项目经理 | 通过 | 范围限定为 M8C 接入包和离线验收，回滚路径为撤回包、脚本和文档。 |
| 资深产品经理 | 通过 | 覆盖 DevOps/CI 管理员对 marketplace 模板、安装变量、幂等策略和上架检查的使用诉求。 |
| 资深服务端架构师 | 通过 | 复用既有 webhook 契约，不新增服务端接口，不把 `secretRef` 暴露给外部 CI。 |
| 资深前端工程师 | 无影响 | 本切片不改 `portal-web`；供应商模板安装不改变现有执行工作台交互。 |
| 资深质量工程师 | 通过 | 新增离线 smoke，并纳入 WP9 quality gate，覆盖 manifest、模板、payload、签名和脱敏约束。 |
