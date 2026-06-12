# WP6 Runner Runbook

| 项目 | 内容 |
|---|---|
| 工作包 | WP6 OpenAPI 接口自动化 |
| 文档性质 | runner smoke、开关、allowlist、排障和回滚说明 |
| 当前口径 | 已提供基础 Managed HTTP adapter、显式 Pytest 子进程 adapter 契约、service contract smoke、loopback runner smoke 和 Pytest runtime secret header 映射 |
| 日期 | 2026-06-12 |

## 1. 适用范围

本 Runbook 适用于 WP6 手动试运行和发布准出检查。当前 `platform-api` 默认使用 `DisabledApiAutomationRunnerAdapter`，不会访问外部网络；显式 `runner-enabled=true` 时默认装配基础 `ManagedHttpApiAutomationRunnerAdapter`，仅执行无请求体、丢弃响应体的受控 HTTP 状态码探测；显式 `runner-mode=pytest-subprocess` 时装配 `PytestSubprocessApiAutomationRunnerAdapter`，在临时目录重建已审批脚本包的最小 Pytest/httpx 文件树并执行本地子进程。runner smoke 通过 `ApiAutomationRunnerSmokeTest`、`ManagedHttpApiAutomationRunnerAdapterTest`、`PytestSubprocessApiAutomationRunnerAdapterTest` 和配置测试验证执行分支、allowlist、timeout、loopback HTTP 执行、Pytest 子进程契约、artifact size 准入和脱敏规则。

## 2. 开关和配置

| 配置 | 默认 | 说明 |
|---|---|---|
| `veri-agent.api-automation.runner-enabled` | `false` | 默认关闭外部执行。开启后仍需 allowlist 命中。 |
| `veri-agent.api-automation.runner-timeout-seconds` | `120` | 单次运行超时上限会被服务端归一化。 |
| `veri-agent.api-automation.runner-max-cases` | `100` | 单次运行用例上限。 |
| `veri-agent.api-automation.runner-allowed-base-url-patterns` | 空 | 允许访问的 host 或 `*.domain` 模式，不在 health 中展示明细。 |
| `veri-agent.api-automation.runner-artifact-max-bytes` | `1048576` | runner case 级断言/产物摘要最大字节数；超限折叠为聚合证据。 |
| `veri-agent.api-automation.runner-mode` | `managed-http` | `managed-http` 或 `pytest-subprocess`；Pytest 子进程必须显式启用。 |
| `veri-agent.api-automation.runner-pytest-command` | `python3 -m pytest` | Pytest 子进程命令，不经 shell 执行；运行环境需预装 pytest/httpx。 |
| `WP6_RUNNER_SMOKE` | `0` | `managed/pytest/auto/external/1/true` 时执行 runner smoke。 |
| `WP6_RUNNER_BASE_URL` | `https://api.wp6-smoke.example.test/service` | `external` 模式必须显式配置并人工评审。 |
| `WP6_RUNNER_ALLOWED_HOST` | 从 baseUrl 派生 | smoke 测试使用的 allowlist host；通常不需要手工设置。 |
| `WP6_RUNNER_SECRET_HEADERS_JSON` | `[]` | Pytest 子进程 runner 注入的受控 header 映射，只允许 `X-VA-WP6-Secret-N`。 |
| `WP6_RUNNER_SECRET_VALUE_N` | 空 | Pytest 子进程 runner 注入的第 N 个 secret 明文值；只存在于运行期进程环境，不得落库、审计或导出。 |

## 3. 日常验证

开发默认门禁不启 runner：

```bash
bash scripts/wp6_quality_gate.sh
```

仅执行 runner smoke：

```bash
WP6_RUNNER_SMOKE=managed bash scripts/wp6_runner_smoke.sh
```

仅执行 Pytest 子进程 adapter 契约 smoke：

```bash
WP6_RUNNER_SMOKE=pytest bash scripts/wp6_runner_smoke.sh
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
6. runner case 级 artifact 超过 `runner-artifact-max-bytes` 时必须归一化为 run `FAILED`、case result `ERROR` 和 `RUNNER_ARTIFACT_TOO_LARGE`，只保存 `artifactBytes/artifactMaxBytes` 等聚合证据。
7. 导出结果只能包含 baseUrl host/digest、状态、耗时、错误码和聚合断言摘要。
8. Pytest 模板的运行期 secret header 映射必须来自 `WP6_RUNNER_SECRET_HEADERS_JSON`，值只能来自对应的 `WP6_RUNNER_SECRET_VALUE_N`；脚本包摘要只保存环境变量名、header pattern 和脱敏策略，不保存 `secret://` 引用或 secret 明文。

## 5. 排障

| 现象 | 处理 |
|---|---|
| release gate 提示必须配置 runner smoke | 设置 `WP6_RUNNER_SMOKE=managed` 或 `pytest`，或在外部目标已评审后设置 `WP6_RUNNER_SMOKE=external` 和 `WP6_RUNNER_BASE_URL`。 |
| `RUNNER_TARGET_BLOCKED` | 检查 baseUrl 是否为 localhost/私网/metadata/.local，或 allowlist 是否只配置了错误 host。 |
| `RUNNER_DISABLED` | 开发环境符合预期；真实试运行需显式开启 `runner-enabled` 并配置 allowlist。 |
| smoke 中出现 secret 明文 | 视为安全阻断，先回滚 runner adapter 或禁用 runner，再修复脱敏后重跑。 |
| Pytest 模板提示 `invalid WP6 runner header mapping` | 检查 `WP6_RUNNER_SECRET_HEADERS_JSON` 是否为数组，元素是否只包含 `X-VA-WP6-Secret-N` 和对应 `WP6_RUNNER_SECRET_VALUE_N`。 |
| `RUNNER_ARTIFACT_TOO_LARGE` | 检查 runner 是否返回了完整 stdout/stderr、请求/响应正文或过大断言详情；默认应缩减为聚合断言摘要，不建议直接调高上限。 |
| timeout 未归一化 | 检查 runner adapter 返回状态和 errorCode 是否符合契约，服务端应持久化 `TIMEOUT/RUNNER_TIMEOUT`。 |

## 6. 回滚

发现执行器异常、目标误放行或结果泄露时，立即执行：

1. 将 `veri-agent.api-automation.runner-enabled=false`。
2. 清空或收紧 `runner-allowed-base-url-patterns`。
3. 保留 run/run_result 摘要用于审计；如产物引用包含敏感内容，按安全流程删除外部产物并保留 digest 证据。
4. release gate 改回只允许 `WP6_RUNNER_SMOKE=managed` smoke，真实目标修复后再恢复外部联调。

## 7. 当前限制

当前 smoke 验证 runner port 契约、控制面脱敏、secretRef 引用 digest、SecretProvider 解析、Managed HTTP 受控 header 注入、Pytest subprocess 命令/env/JUnit XML 解析契约、Pytest runtime secret header 映射、runner artifact size 准入与导出脱敏、取消 API 幂等语义和基础 Managed HTTP loopback 执行；`POST /api/v1/api-automation/runs/{id}/cancel` 已作为控制面尽力取消入口，当前同步 runner 通常只能对终态 run 幂等返回，后续异步 runner 才能中断运行中任务。运行请求支持 `secretRefs`，完整引用只用于运行期 SecretProvider resolve，审计、落库和导出只保留 `sha256:<digest>`；Managed HTTP 和 Pytest subprocess runner 都只允许注入服务端生成的 `X-VA-WP6-Secret-N` header，不接受任意 Authorization/Cookie header 覆盖。Pytest subprocess adapter 不持久化生成源码、stdout/stderr 原文、请求/响应正文或 secret；真实执行环境需预装 Python、pytest 和 httpx。不启动 WP9 调度、不验证异步 cancel smoke，也不提供 Allure 风格报告。后续需要补充异步 cancel smoke 和复杂页面 Playwright smoke。
