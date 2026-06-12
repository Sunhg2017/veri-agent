# WP6 Runner Runbook

| 项目 | 内容 |
|---|---|
| 工作包 | WP6 OpenAPI 接口自动化 |
| 文档性质 | runner smoke、开关、allowlist、排障和回滚说明 |
| 当前口径 | 已提供基础 Managed HTTP adapter、service contract smoke 和 loopback runner smoke；Pytest 子进程型 runner 后续接入 |
| 日期 | 2026-06-12 |

## 1. 适用范围

本 Runbook 适用于 WP6 手动试运行和发布准出检查。当前 `platform-api` 默认使用 `DisabledApiAutomationRunnerAdapter`，不会访问外部网络；显式 `runner-enabled=true` 时装配基础 `ManagedHttpApiAutomationRunnerAdapter`，仅执行无请求体、丢弃响应体的受控 HTTP 状态码探测。runner smoke 通过 `ApiAutomationRunnerSmokeTest`、`ManagedHttpApiAutomationRunnerAdapterTest` 和配置测试验证执行分支、allowlist、timeout、loopback HTTP 执行和脱敏规则。

## 2. 开关和配置

| 配置 | 默认 | 说明 |
|---|---|---|
| `veri-agent.api-automation.runner-enabled` | `false` | 默认关闭外部执行。开启后仍需 allowlist 命中。 |
| `veri-agent.api-automation.runner-timeout-seconds` | `120` | 单次运行超时上限会被服务端归一化。 |
| `veri-agent.api-automation.runner-max-cases` | `100` | 单次运行用例上限。 |
| `veri-agent.api-automation.runner-allowed-base-url-patterns` | 空 | 允许访问的 host 或 `*.domain` 模式，不在 health 中展示明细。 |
| `WP6_RUNNER_SMOKE` | `0` | `managed/auto/external/1/true` 时执行 runner smoke。 |
| `WP6_RUNNER_BASE_URL` | `https://api.wp6-smoke.example.test/service` | `external` 模式必须显式配置并人工评审。 |
| `WP6_RUNNER_ALLOWED_HOST` | 从 baseUrl 派生 | smoke 测试使用的 allowlist host；通常不需要手工设置。 |

## 3. 日常验证

开发默认门禁不启 runner：

```bash
bash scripts/wp6_quality_gate.sh
```

仅执行 runner smoke：

```bash
WP6_RUNNER_SMOKE=managed bash scripts/wp6_runner_smoke.sh
```

发布模式必须显式启 runner smoke：

```bash
WP6_GATE_MODE=release WP6_RUNNER_SMOKE=managed bash scripts/wp6_quality_gate.sh
```

使用外部已评审 baseUrl 做 runner smoke：

```bash
WP6_RUNNER_SMOKE=external \
WP6_RUNNER_BASE_URL=https://api.example.test/service \
bash scripts/wp6_runner_smoke.sh
```

## 4. 准出检查点

1. 未配置 runner smoke 的 release/preprod/prod gate 必须阻断。
2. baseUrl 必须是 http/https，不能包含 userInfo、query 或 fragment。
3. localhost、私网 IPv4、metadata host、`.local` 和未命中 allowlist 的 host 必须返回 `RUNNER_TARGET_BLOCKED`。
4. runner 返回的 run/case 错误摘要必须脱敏，不得包含明文 baseUrl、token、cookie、Authorization、password、secret 或完整请求响应正文。
5. timeout 必须归一化为 run `TIMEOUT`、case result `TIMEOUT` 和 `RUNNER_TIMEOUT`。
6. 导出结果只能包含 baseUrl host/digest、状态、耗时、错误码和聚合断言摘要。

## 5. 排障

| 现象 | 处理 |
|---|---|
| release gate 提示必须配置 runner smoke | 设置 `WP6_RUNNER_SMOKE=managed`，或在外部目标已评审后设置 `WP6_RUNNER_SMOKE=external` 和 `WP6_RUNNER_BASE_URL`。 |
| `RUNNER_TARGET_BLOCKED` | 检查 baseUrl 是否为 localhost/私网/metadata/.local，或 allowlist 是否只配置了错误 host。 |
| `RUNNER_DISABLED` | 开发环境符合预期；真实试运行需显式开启 `runner-enabled` 并配置 allowlist。 |
| smoke 中出现 secret 明文 | 视为安全阻断，先回滚 runner adapter 或禁用 runner，再修复脱敏后重跑。 |
| timeout 未归一化 | 检查 runner adapter 返回状态和 errorCode 是否符合契约，服务端应持久化 `TIMEOUT/RUNNER_TIMEOUT`。 |

## 6. 回滚

发现执行器异常、目标误放行或结果泄露时，立即执行：

1. 将 `veri-agent.api-automation.runner-enabled=false`。
2. 清空或收紧 `runner-allowed-base-url-patterns`。
3. 保留 run/run_result 摘要用于审计；如产物引用包含敏感内容，按安全流程删除外部产物并保留 digest 证据。
4. release gate 改回只允许 `WP6_RUNNER_SMOKE=managed` smoke，真实目标修复后再恢复外部联调。

## 7. 当前限制

当前 smoke 验证 runner port 契约、控制面脱敏和基础 Managed HTTP loopback 执行，不执行真实 Pytest/httpx 脚本、不启动 WP9 调度、不验证异步 cancel，也不提供 Allure 风格报告。后续接入 Pytest 子进程型 runner 时，需要补充网络隔离、secretRef 注入、产物大小限制和 cancel smoke。
