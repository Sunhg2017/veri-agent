# WP9 Webhook Marketplace 接入包

| 项 | 内容 |
|---|---|
| packageId | `veri-agent.wp9.webhook.ci-generic` |
| 覆盖平台 | GitHub Actions、GitLab CI、Jenkins |
| 运行入口 | `POST /api/v1/execution/webhooks/{triggerId}` |
| 验证脚本 | `bash scripts/wp9_marketplace_package_smoke.sh` |

## 1. 包内容

本目录提供可复制到供应商 marketplace/App 或企业内部模板库的最小接入包：

1. `manifest.json`：声明签名算法、Header、安装变量、模板路径、payload 示例和验证入口。
2. `templates/github-actions.yml`：GitHub Actions workflow 模板。
3. `templates/gitlab-ci.yml`：GitLab CI job 模板。
4. `templates/Jenkinsfile`：Jenkins Pipeline 模板。
5. `payloads/*.json`：供应商 payload 的固定示例，便于联调时核对 raw body。

本接入包不实现供应商 OAuth App、不申请 marketplace 上架、不托管 secret；它提供上架前可审查的配置资产和离线验收入口。

## 2. 安装前置

1. Veri Agent 中已存在 `READY` WP9 执行计划，且 `triggerPolicy.webhookEnabled=true`。
2. WP9 全局 webhook 开关已开启：`WP9_WEBHOOK_ENABLED=true` 或 `veri-agent.execution.webhook-enabled=true`。
3. 已为目标计划创建 `ENABLED` WEBHOOK trigger，并配置服务端 `secretRef`。
4. CI 侧保存的是 webhook secret 明文值，不是 `secret://` 引用。
5. 目标 runner 已安装 `curl`、`openssl`、`awk` 和 `jq`。

## 3. 安装变量

| 变量 | 存放位置 | 是否敏感 | 说明 |
|---|---|---|---|
| `VERI_AGENT_WP9_WEBHOOK_URL` | CI secret/variable | 否，可按企业策略加密 | 完整 webhook URL，形如 `https://example.com/api/v1/execution/webhooks/{triggerId}`。 |
| `VERI_AGENT_WP9_WEBHOOK_SECRET` | CI secret/credential | 是 | CI 侧用于 HMAC 签名的 secret 明文。 |
| `VERI_AGENT_WP9_EVENT_PREFIX` | CI variable | 否 | 可选，用于多流水线共享 trigger 时区分 eventId。 |

## 4. 事件幂等策略

同一次外部流水线事件重试时必须复用同一个 `X-VA-Event-Id` 和同一份 raw body。若 payload 内容变化，应生成新的 eventId。建议 eventId 组成：

| 平台 | 建议 |
|---|---|
| GitHub Actions | `github:{repo}:{run_id}:{run_attempt}`；如果手动 re-run 需要回放同一 run，则去掉 `run_attempt` 并保持 payload 不变。 |
| GitLab CI | `gitlab:{project_path}:{pipeline_id}`。 |
| Jenkins | `jenkins:{job_name}:{build_number}`。 |

## 5. 安装步骤

1. 在 Veri Agent 中创建或确认 WP9 WEBHOOK trigger，记录完整 webhook URL。
2. 将 webhook secret 明文保存到供应商 secret store；不要把服务端 `secretRef` 复制到外部 CI。
3. 将对应模板复制到目标仓库，并按企业命名规范调整 job 名称、触发条件和分支过滤。
4. 首次联调前运行离线校验：

```bash
bash scripts/wp9_marketplace_package_smoke.sh
```

5. 联调时先故意改错签名，确认服务端返回 `EXECUTION_TRIGGER_SIGNATURE_INVALID`；再恢复正确签名，确认首次 `ACCEPTED`、重复同 eventId 回放既有 run。
6. 验收 run detail、trigger event 和 run export，确认不包含 secret、signature、secretRef 明文或 raw payload。

## 6. 上架检查

供应商 marketplace 或企业内部插件库上架前至少保留：

1. `manifest.json` 校验结果。
2. 模板版本和目标仓库 commit。
3. `scripts/wp9_marketplace_package_smoke.sh` 输出。
4. `scripts/wp9_webhook_http_smoke.sh` managed 或 external 结果。
5. 触发器 ID、项目范围、secretRef digest 和回滚开关。

真实 OAuth/App 上架时，需要在对应平台补充权限申请、回调 URL、安装授权、卸载回收和密钥轮换策略。本包仅覆盖 signed webhook 模板层。
