# WP9 执行编排与任务调度 - M8A 供应商 Webhook 接入样例交付说明

| 项 | 内容 |
|---|---|
| 交付阶段 | M8A 供应商 Webhook 接入样例 |
| 交付日期 | 2026-06-14 |
| 交付范围 | WP9 webhook 签名 helper、GitHub Actions/GitLab CI/Jenkins 接入样例、联调前置条件、排错和验收说明 |
| 非目标 | 新增服务端接口、供应商 marketplace 插件、前端页面改动、生产 secret 托管策略调整 |
| 涉及模块 | `scripts/wp9_webhook_sign.sh`、WP9 文档 |
| 回滚方式 | 回退本次脚本和文档 commit；M7D webhook HTTP smoke、release gate 和 WP9 后端控制面不受影响 |

## 1. 目标与范围

M8A 目标是在 M7D 已验证真实 HTTP webhook 入口后，补齐供应商 CI/CD 接入样例。外部系统只需要生成 `X-VA-Timestamp`、稳定 `X-VA-Event-Id` 和 `X-VA-Signature`，按 `timestamp.eventId.rawBody` 做 HMAC-SHA256 小写 hex 签名，即可调用 `POST /api/v1/execution/webhooks/{triggerId}`。

本切片不修改 Java 服务端逻辑，不新增实际 GitHub/GitLab/Jenkins 插件包，也不改变 WP1 SecretProvider 或生产密钥轮换策略。

## 2. 主要变更

1. 新增 `scripts/wp9_webhook_sign.sh`，支持 `WP9_WEBHOOK_URL` 或 `WP9_WEBHOOK_BASE_URL + WP9_WEBHOOK_TRIGGER_ID`，可从 `WP9_WEBHOOK_PAYLOAD` 或 `WP9_WEBHOOK_PAYLOAD_FILE` 签名。
2. helper 默认输出 curl 样例，不发送请求；只有设置 `WP9_WEBHOOK_SEND=1` 才会真实调用 webhook，降低误触发风险。
3. 新增 `WP9-Webhook签名样例与CI接入说明.md`，提供 cURL/helper、GitHub Actions、GitLab CI 和 Jenkins Pipeline 示例。
4. 文档明确 CI 侧保存 secret 明文值，服务端保存 `secretRef`；`secret://` 引用不得传给外部 CI。
5. 文档补齐稳定 eventId、raw body 不改写、signature 不落日志、trigger event 查询和 run export 脱敏验收要求。

## 3. 验收入口

```bash
bash -n scripts/wp9_webhook_sign.sh
WP9_WEBHOOK_SECRET='secret' WP9_WEBHOOK_TRIGGER_ID='trigger-1' WP9_WEBHOOK_TIMESTAMP='100' WP9_WEBHOOK_EVENT_ID='evt-1' WP9_WEBHOOK_PAYLOAD='{"ok":true}' bash scripts/wp9_webhook_sign.sh
WP9_WEBHOOK_OUTPUT=signature WP9_WEBHOOK_SECRET='secret' WP9_WEBHOOK_TRIGGER_ID='trigger-1' WP9_WEBHOOK_TIMESTAMP='100' WP9_WEBHOOK_EVENT_ID='evt-1' WP9_WEBHOOK_PAYLOAD='{"ok":true}' bash scripts/wp9_webhook_sign.sh
git diff --check
```

## 4. 风险与后续

1. CI 示例依赖 `curl`、`openssl`、`awk`，GitHub/GitLab 示例还依赖 `jq`；缺少工具时应在 runner image 或 before_script 中安装。
2. helper 支持真实发送，生产使用必须显式设置 `WP9_WEBHOOK_SEND=1`，并确认 target URL 指向正确环境。
3. 供应商 marketplace 插件包、OAuth/App 安装流程和 UI 化配置仍归后续切片；本轮只交付可执行接入样例。
4. cron scanner 运维 runbook 和错过多次 fire 容量策略仍按后续 WP9-8.x 推进。

## 5. 五角色结论

| 角色 | 结论 | 说明 |
|---|---|---|
| 资深项目经理 | 通过 | 范围限定为 M8A 接入样例和签名 helper，回滚路径清晰。 |
| 资深产品经理 | 通过 | 覆盖 DevOps/CI 管理员接入 GitHub/GitLab/Jenkins 的最小可用路径和验收证据。 |
| 资深服务端架构师 | 通过 | 不改服务端契约，样例严格复用 M5/M7D 已定义的签名、幂等和脱敏边界。 |
| 资深前端工程师 | 无影响 | 本切片不改 `portal-web`，页面仍展示既有 trigger 摘要和事件证据。 |
| 资深质量工程师 | 通过 | helper 具备语法和确定性签名验证入口，文档列出错误签名、幂等和脱敏验收项。 |
