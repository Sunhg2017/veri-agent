# WP2 Provider 接入与 SecretRef 轮换 Runbook

| 项目 | 内容 |
|---|---|
| 覆盖任务 | WP2-B3 外部 provider runbook、WP2-B4 SecretRef 轮换流程 |
| 当前 API | `/api/v1/model-access` |
| 当前 provider 类型 | `LOCAL_ECHO`、`OPENAI_COMPATIBLE`、`MOCK_FAILURE` |
| 日期 | 2026-05-20 |

## 1. 当前口径

1. WP2 当前运行在同一个 `platform-api` 内，不是独立服务。
2. OpenAI-compatible provider 必须配置 `baseUrl`，平台会调用 `${baseUrl}/v1/chat/completions`。
3. 当前实现的外部 provider 密钥字段为 `apiKeyRef`，仅接受 `env:VARIABLE_NAME`；环境变量由部署系统或外部密钥系统注入，不在库表、日志、审计或 release notes 中保存明文。
4. 后续如果 WP2 直接接入 WP1 SecretProvider，仍沿用本文的双引用、灰度检查、切换、旧引用失效流程。

## 2. 接入前检查

| 项 | 要求 |
|---|---|
| 网络 | `platform-api` 运行环境能访问 provider 或代理网关 |
| 协议 | 兼容 OpenAI Chat Completions，路径为 `/v1/chat/completions` |
| TLS | 生产必须优先使用 `https`，私有网关需要证书和 egress 策略 |
| 密钥 | 通过环境变量注入，例如 `MODEL_API_KEY_PRIMARY`；WP2 中只保存 `env:MODEL_API_KEY_PRIMARY` |
| 策略 | 确认项目 `allowPublicModel` 和 `sensitivityLevel` 允许外部 provider |
| 预算 | 配置或确认 `WP2_DAILY_PLATFORM_COST_LIMIT`、`WP2_DAILY_PROJECT_COST_LIMIT`、成本单价 |
| 观测 | 打开 `veri.agent.model_access.*` 指标、调用日志、provider check 输出 |

## 3. 新 provider 接入步骤

1. 在部署系统中注入密钥环境变量，不重启前不要删除旧变量：

```bash
export MODEL_API_KEY_PRIMARY='***'
```

2. 创建 provider：

```bash
curl -X POST http://127.0.0.1:8080/api/v1/model-access/providers \
  -H 'Authorization: Bearer local-model-access-token' \
  -H 'X-Caller-Service: wp2-provider-runbook' \
  -H 'X-Delegated-User-Id: release-owner' \
  -H 'Content-Type: application/json' \
  -d '{
    "name": "openai-compatible-primary",
    "providerType": "OPENAI_COMPATIBLE",
    "baseUrl": "https://model-gateway.example.com",
    "apiKeyRef": "env:MODEL_API_KEY_PRIMARY",
    "priority": 20,
    "timeoutMs": 5000,
    "inputCostPer1kTokens": 0.001,
    "outputCostPer1kTokens": 0.002
  }'
```

3. 对 provider 执行就绪检查：

```bash
curl -X POST http://127.0.0.1:8080/api/v1/model-access/providers/{providerId}/check \
  -H 'Authorization: Bearer local-model-access-token' \
  -H 'X-Caller-Service: wp2-provider-runbook' \
  -H 'X-Delegated-User-Id: release-owner' \
  -H 'Content-Type: application/json' \
  -d '{}'
```

期望：`data.status` 为 `UP`，`errorCode` 为空。重复检查可能返回 `cached=true`，缓存 TTL 由 `WP2_PROVIDER_CHECK_CACHE_TTL_MS` 控制。

4. 用低风险项目执行最小调用：

```bash
curl -X POST http://127.0.0.1:8080/api/v1/model-access/invocations \
  -H 'Authorization: Bearer local-model-access-token' \
  -H 'X-Caller-Service: wp2-provider-runbook' \
  -H 'X-Delegated-User-Id: release-owner' \
  -H 'X-Trace-Id: provider-runbook-smoke-001' \
  -H 'Content-Type: application/json' \
  -d '{
    "projectId": "project-001",
    "promptKey": "test-case-design",
    "messages": [{"role": "user", "content": "返回一条健康检查文本"}],
    "allowPublicModel": true,
    "sensitivityLevel": "INTERNAL"
  }'
```

5. 查询调用日志和指标：

```bash
curl 'http://127.0.0.1:8080/api/v1/model-access/invocations?projectId=project-001&index=0&size=20' \
  -H 'Authorization: Bearer local-model-access-token' \
  -H 'X-Caller-Service: wp2-provider-runbook' \
  -H 'X-Delegated-User-Id: release-owner'
```

```bash
curl http://127.0.0.1:8080/actuator/metrics/veri.agent.model_access.invocations
```

## 4. 私有模型和代理网关

私有模型建议通过内部网关适配 OpenAI-compatible 协议，而不是在 WP2 中新增私有 SDK：

1. 网关对外暴露 `/v1/chat/completions`。
2. 网关负责私有模型认证、模型名映射、区域路由、重试和供应商细节。
3. WP2 只配置网关 `baseUrl`、`apiKeyRef`、timeout、priority 和成本。
4. 高敏项目必须确认 `allowPublicModel=false` 时不会路由到公开 provider；必要时使用 `LOCAL_*` 或私有网关 provider。

## 5. SecretRef/apiKeyRef 轮换流程

### 5.1 零中断轮换

| 步骤 | 操作 | 验证 |
|---|---|---|
| 1 | 在外部密钥系统中创建新密钥，并注入新环境变量 `MODEL_API_KEY_NEXT` | `platform-api` 运行环境能读取新变量 |
| 2 | 保留旧变量 `MODEL_API_KEY_PRIMARY`，部署带双变量的版本 | 旧 provider 调用仍成功 |
| 3 | 新建 shadow provider，`apiKeyRef=env:MODEL_API_KEY_NEXT`，priority 暂不高于主 provider | `/providers/{id}/check` 返回 `UP` |
| 4 | 用指定 `providerId` 或低风险项目执行最小 invocation | 调用日志 `status=SUCCEEDED`，成本和 token 正常 |
| 5 | 将新 provider priority 调整为更小数字，或把旧 provider 调整为备用 | `veri.agent.model_access.invocations` 中新 provider 开始有成功调用 |
| 6 | 观察一个发布窗口后停用旧 provider | `/providers/{oldId}/disable` 后主链路仍成功 |
| 7 | 删除旧环境变量或在外部密钥系统中吊销旧 secret | provider check 和 smoke 均通过 |

### 5.2 原地更新轮换

只有在 provider 不支持双活或供应商强制吊销旧 key 时使用：

```bash
curl -X PUT http://127.0.0.1:8080/api/v1/model-access/providers/{providerId} \
  -H 'Authorization: Bearer local-model-access-token' \
  -H 'X-Caller-Service: wp2-provider-runbook' \
  -H 'X-Delegated-User-Id: release-owner' \
  -H 'Content-Type: application/json' \
  -d '{"apiKeyRef":"env:MODEL_API_KEY_NEXT"}'
```

更新后立即执行 provider check 和最小 invocation。失败时把 `apiKeyRef` 改回旧变量，或启用备用 provider。

## 6. 故障排查

| 现象 | 可能原因 | 处理 |
|---|---|---|
| 创建 provider 返回 `BAD_REQUEST` | `baseUrl` 不是 http/https，或 `apiKeyRef` 不是 `env:` | 修正字段；不要传密钥明文 |
| provider check `DOWN` 且认证失败 | 环境变量缺失、key 错误、网关 401/403 | 确认部署环境变量和 provider 权限 |
| provider check `DOWN` 且限流 | provider 429 | 降低调用量，调整预算或切备用 provider |
| invocation `MODEL_POLICY_VIOLATION` | WP1 项目不允许公开模型或敏感级别过高 | 检查项目 `allowPublicModel` 和 `sensitivityLevel` |
| invocation `BUDGET_EXCEEDED` | 平台或项目日预算不足 | 检查预算配置和成本日报 |
| invocation fallback 增多 | 主 provider 失败、熔断或超时 | 查看 `WP2_PROVIDER_CIRCUIT_FAILURE_THRESHOLD`、`WP2_PROVIDER_CIRCUIT_OPEN_MS`、provider check |
| 成本异常 | 单价配置错误或 token 估算异常 | 校验 `inputCostPer1kTokens`、`outputCostPer1kTokens` 和调用日志 |

## 7. 发布准出

外部 provider 或密钥轮换相关变更，release notes 必须记录：

1. provider name、provider type、脱敏 `baseUrl`、`apiKeyRef`。
2. provider check 结果和最小 invocation 的 `traceId`。
3. 新旧 key 的轮换窗口、旧 key 禁用时间和回滚动作。
4. 成本和预算配置变更。
5. 是否影响 WP4 AI 解析；若影响，必须执行 `bash scripts/wp4_ai_parse_quality_eval.sh`。
