# WP9 Webhook 签名样例与 CI 接入说明

| 项目 | 内容 |
|---|---|
| 覆盖任务 | WP9 M8A 供应商 webhook 接入样例 |
| 当前路径 | `POST /api/v1/execution/webhooks/{triggerId}` |
| 签名算法 | HMAC-SHA256，hex 小写 |
| 日期 | 2026-06-14 |

## 1. 必填 Header

| Header | 示例 | 说明 |
|---|---|---|
| `X-VA-Timestamp` | `1760000000` | Unix epoch seconds；默认允许偏移由 `WP9_WEBHOOK_CLOCK_SKEW_SECONDS` 控制，当前默认 300 秒。 |
| `X-VA-Event-Id` | `github:repo:1234567890:deploy` | 外部事件唯一 ID；重试同一事件必须复用同一个 ID 和同一份 raw body。 |
| `X-VA-Signature` | `d4c3...` | HMAC-SHA256 hex 小写。 |

签名串固定为：

```text
timestamp.eventId.rawBody
```

`rawBody` 必须是 HTTP 请求实际发送的原始 JSON 字符串。签名前后不能重新格式化、排序字段、追加换行或改变空格；推荐使用 `--data-binary` 或文件方式发送。

## 2. 本地签名 helper

默认只输出可执行 curl，不发送请求：

```bash
WP9_WEBHOOK_BASE_URL='https://veri-agent.example.com' \
WP9_WEBHOOK_TRIGGER_ID='trigger-uuid' \
WP9_WEBHOOK_SECRET='replace-with-ci-secret' \
WP9_WEBHOOK_EVENT_ID='manual:smoke:001' \
WP9_WEBHOOK_PAYLOAD='{"source":"manual","status":"success"}' \
bash scripts/wp9_webhook_sign.sh
```

只输出 header：

```bash
WP9_WEBHOOK_OUTPUT=headers \
WP9_WEBHOOK_URL='https://veri-agent.example.com/api/v1/execution/webhooks/trigger-uuid' \
WP9_WEBHOOK_SECRET_FILE='./.local/wp9-webhook-secret' \
WP9_WEBHOOK_PAYLOAD_FILE='./payload.json' \
bash scripts/wp9_webhook_sign.sh
```

显式发送请求：

```bash
WP9_WEBHOOK_SEND=1 \
WP9_WEBHOOK_URL='https://veri-agent.example.com/api/v1/execution/webhooks/trigger-uuid' \
WP9_WEBHOOK_SECRET='replace-with-ci-secret' \
WP9_WEBHOOK_EVENT_ID='manual:smoke:001' \
WP9_WEBHOOK_PAYLOAD='{"source":"manual","status":"success"}' \
bash scripts/wp9_webhook_sign.sh
```

生产 CI 中不要打印 `WP9_WEBHOOK_SECRET`；如日志策略要求更严格，也不要打印一次性 `X-VA-Signature`。

## 3. GitHub Actions 样例

```yaml
name: WP9 execution trigger

on:
  workflow_dispatch:
  push:
    branches: [main]

jobs:
  trigger-wp9:
    runs-on: ubuntu-latest
    steps:
      - name: Trigger Veri Agent WP9
        env:
          WP9_WEBHOOK_URL: ${{ secrets.VERI_AGENT_WP9_WEBHOOK_URL }}
          WP9_WEBHOOK_SECRET: ${{ secrets.VERI_AGENT_WP9_WEBHOOK_SECRET }}
          EVENT_ID: github:${{ github.repository }}:${{ github.run_id }}:${{ github.run_attempt }}
        run: |
          set -euo pipefail
          timestamp="$(date +%s)"
          payload="$(jq -nc \
            --arg provider github \
            --arg repo "$GITHUB_REPOSITORY" \
            --arg ref "$GITHUB_REF_NAME" \
            --arg sha "$GITHUB_SHA" \
            --arg runUrl "$GITHUB_SERVER_URL/$GITHUB_REPOSITORY/actions/runs/$GITHUB_RUN_ID" \
            '{provider:$provider,repo:$repo,ref:$ref,sha:$sha,runUrl:$runUrl,status:"success"}')"
          signature="$(printf '%s' "$timestamp.$EVENT_ID.$payload" \
            | openssl dgst -sha256 -hmac "$WP9_WEBHOOK_SECRET" -hex \
            | awk '{print $NF}')"
          curl -fsS -X POST "$WP9_WEBHOOK_URL" \
            -H 'Content-Type: application/json' \
            -H "X-VA-Timestamp: $timestamp" \
            -H "X-VA-Event-Id: $EVENT_ID" \
            -H "X-VA-Signature: $signature" \
            --data-binary "$payload"
```

`github.run_attempt` 可让手工 re-run 生成新的外部事件；如果希望 re-run 幂等回放同一个 WP9 run，则去掉 `run_attempt` 并保持 payload 不变。

## 4. GitLab CI 样例

```yaml
trigger_wp9:
  image: alpine:3.20
  variables:
    EVENT_ID: "gitlab:${CI_PROJECT_PATH}:${CI_PIPELINE_ID}"
  before_script:
    - apk add --no-cache curl jq openssl
  script:
    - |
      set -euo pipefail
      timestamp="$(date +%s)"
      payload="$(jq -nc \
        --arg provider gitlab \
        --arg project "$CI_PROJECT_PATH" \
        --arg ref "$CI_COMMIT_REF_NAME" \
        --arg sha "$CI_COMMIT_SHA" \
        --arg pipelineUrl "$CI_PIPELINE_URL" \
        '{provider:$provider,project:$project,ref:$ref,sha:$sha,pipelineUrl:$pipelineUrl,status:"success"}')"
      signature="$(printf '%s' "$timestamp.$EVENT_ID.$payload" \
        | openssl dgst -sha256 -hmac "$VERI_AGENT_WP9_WEBHOOK_SECRET" -hex \
        | awk '{print $NF}')"
      curl -fsS -X POST "$VERI_AGENT_WP9_WEBHOOK_URL" \
        -H 'Content-Type: application/json' \
        -H "X-VA-Timestamp: $timestamp" \
        -H "X-VA-Event-Id: $EVENT_ID" \
        -H "X-VA-Signature: $signature" \
        --data-binary "$payload"
```

`VERI_AGENT_WP9_WEBHOOK_URL` 和 `VERI_AGENT_WP9_WEBHOOK_SECRET` 必须配置为 masked/protected CI variable。

## 5. Jenkins Pipeline 样例

```groovy
pipeline {
  agent any
  environment {
    WP9_WEBHOOK_URL = credentials('veri-agent-wp9-webhook-url')
    WP9_WEBHOOK_SECRET = credentials('veri-agent-wp9-webhook-secret')
  }
  stages {
    stage('Trigger WP9') {
      steps {
        sh '''
          set -euo pipefail
          timestamp="$(date +%s)"
          event_id="jenkins:${JOB_NAME}:${BUILD_NUMBER}"
          payload="$(jq -nc \
            --arg provider jenkins \
            --arg job "$JOB_NAME" \
            --arg build "$BUILD_NUMBER" \
            --arg buildUrl "$BUILD_URL" \
            --arg gitCommit "${GIT_COMMIT:-unknown}" \
            '{provider:$provider,job:$job,build:$build,buildUrl:$buildUrl,gitCommit:$gitCommit,status:"success"}')"
          signature="$(printf '%s' "$timestamp.$event_id.$payload" \
            | openssl dgst -sha256 -hmac "$WP9_WEBHOOK_SECRET" -hex \
            | awk '{print $NF}')"
          curl -fsS -X POST "$WP9_WEBHOOK_URL" \
            -H 'Content-Type: application/json' \
            -H "X-VA-Timestamp: $timestamp" \
            -H "X-VA-Event-Id: $event_id" \
            -H "X-VA-Signature: $signature" \
            --data-binary "$payload"
        '''
      }
    }
  }
}
```

Jenkins 凭据应使用 Secret Text 或受控凭据插件；不要把 secret 或 signature 写入构建产物。

## 6. 联调前置条件

1. WP9 全局 webhook 开关已启用：`WP9_WEBHOOK_ENABLED=true` 或对应配置 `veri-agent.execution.webhook-enabled=true`。
2. 执行计划状态为 `READY`，计划 `triggerPolicy.webhookEnabled=true`。
3. WEBHOOK trigger 状态为 `ENABLED`，且配置了 `secretRef`；WP1 SecretProvider 可解析 ACTIVE、未过期、用途为 `WEBHOOK_SIGNING`、作用域匹配项目的密钥。
4. CI 侧保存的是 secret 明文值，不是 `secret://` 引用；`secret://` 只在 Veri Agent 服务端内部使用。
5. `X-VA-Event-Id` 对同一外部事件稳定。失败重试时如果 payload 不变，应复用同一 eventId；如果 payload 变化，应生成新的 eventId。
6. webhook payload 只传必要摘要，例如 provider、repo/project、ref、sha、pipeline/build URL 和状态；WP9 只保存 digest 和安全摘要，不保存 raw payload。

## 7. 排错表

| 现象 | 常见原因 | 处理 |
|---|---|---|
| `EXECUTION_TRIGGER_DISABLED` | 全局 webhook 关闭、trigger 禁用或计划不允许 webhook | 检查 `/api/v1/execution/health`、计划 triggerPolicy 和 trigger 状态。 |
| `EXECUTION_TRIGGER_SIGNATURE_INVALID` | raw body 被改写、secret 错误、timestamp 过期或 canonical string 拼错 | 固定 raw body；使用 `timestamp.eventId.rawBody`；确认 hex 小写和 CI 时钟。 |
| 重复事件没有回放同一个 run | eventId 每次重试都变化，或 payload 变化导致请求 digest 不同 | 对同一次外部事件使用稳定 eventId；payload 变化时视为新事件。 |
| CI 日志出现 secret | shell 开启了 `set -x`、echo 了 secret，或凭据未设置 masked | 关闭命令回显；使用 CI secret/masked variable；轮换泄露密钥。 |
| 触发成功但 run 未执行 | scheduler 未启用或 WP6 runner/allowlist 阻断 | 使用 `scripts/wp9_scheduler_smoke.sh` 验证调度；检查 run detail 的节点错误码和 WP6 runner 配置。 |

## 8. 验收

外部 CI 接入完成的准出证据：

1. 故意改错签名返回 `EXECUTION_TRIGGER_SIGNATURE_INVALID`。
2. 正确签名首次返回 `ACCEPTED`，响应包含 `runId`。
3. 使用同一 eventId 和同一 raw body 重试返回幂等回放，不重复创建 run。
4. `/api/v1/execution/triggers/{id}/events` 可查询 accepted/rejected 事件、requestDigest 和错误码。
5. run detail 和 run export 不包含 webhook secret、secretRef 明文、signature 或 raw payload。
