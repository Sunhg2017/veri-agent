# WP7 Web 管理后台 UI/E2E - Runner Runbook

| 项目 | 内容 |
|---|---|
| 工作包 | WP7 Web 管理后台 UI/E2E |
| 文档性质 | runner 开关、allowlist、artifact、排障和回滚说明 |
| 当前口径 | 已提供 disabled、managed preview 和 Playwright subprocess 三类 runner 形态，支持 WP8 凭据注入、受控 artifact 存储/下载、`HAR/JUnit XML` 扩展和登录免凭据场景视频采集 |
| 日期 | 2026-06-20 |

## 1. 适用范围

本 Runbook 适用于 WP7 手动试运行、发布前准出检查和受控排障。当前 `platform-api` 默认装配 `DisabledUiE2eRunnerAdapter`，即使创建运行也只会持久化终态 `BLOCKED` 摘要，方便审计和 requestKey 幂等回放。显式开启 `veri-agent.ui-e2e.runner-enabled=true` 后：

1. `runner-mode=managed` 或 `http-adapter` 使用 `ManagedPreviewUiE2eRunnerAdapter`，只落脱敏的 aggregate-only 预演摘要，不执行真实浏览器。
2. `runner-mode=playwright-subprocess` 或 `real-browser` 使用 `PlaywrightSubprocessUiE2eRunnerAdapter`，在本地临时目录内执行受控 Playwright 子进程。
3. 真实浏览器 runner 当前只支持 `LOGIN`、`NAVIGATE`、`ASSERT` 三类步骤；多场景批量执行、任意浏览器参数和容器化隔离不在当前范围。

所有运行都必须满足项目 scope、`APPROVED` 场景与 bundle、WP8 `accountLeaseRef` 脱敏账号契约、baseUrl allowlist、超时和 artifact 策略。控制面与导出结果不得返回密码、token、cookie、`secret://` 原文、租借 token 明文或宿主机真实路径。

## 2. 开关和配置

| 配置 | 默认 | 说明 |
|---|---|---|
| `veri-agent.ui-e2e.enabled` | `true` | WP7 控制面总开关。关闭后 `#ui-e2e` 和 API 都应视为不可用。 |
| `veri-agent.ui-e2e.runner-enabled` | `false` | 真实 runner 总开关；默认关闭。 |
| `veri-agent.ui-e2e.runner-mode` | `disabled` | `disabled`、`managed`、`http-adapter`、`playwright-subprocess`、`real-browser`。 |
| `veri-agent.ui-e2e.default-timeout-seconds` | `300` | 单次运行默认超时。 |
| `veri-agent.ui-e2e.max-timeout-seconds` | `1800` | 单次运行最大超时。 |
| `veri-agent.ui-e2e.max-scenes-per-run` | `1` | 当前只允许单场景运行。 |
| `veri-agent.ui-e2e.max-artifact-size-bytes` | `20971520` | 单个 artifact 受控落盘上限。 |
| `veri-agent.ui-e2e.max-artifact-count` | `20` | 单次运行最多保留的 artifact 数。 |
| `veri-agent.ui-e2e.max-concurrency` | `2` | 单机并发上限。 |
| `veri-agent.ui-e2e.allowlist-base-urls` | 空 | 允许执行的 baseUrl 白名单。未命中时返回 `UI_E2E_BASE_URL_NOT_ALLOWED`。 |
| `veri-agent.ui-e2e.capture-screenshot-enabled` | `true` | 允许截图落盘。 |
| `veri-agent.ui-e2e.capture-video-enabled` | `false` | 允许视频采集；含 `LOGIN` 步骤时必须阻断。 |
| `veri-agent.ui-e2e.capture-har-enabled` | `false` | 允许 `HAR` 采集，固定 `recordHar(mode=minimal, content=omit)`。 |
| `veri-agent.ui-e2e.capture-trace-enabled` | `true` | 允许 trace 落盘。 |
| `veri-agent.ui-e2e.capture-junit-xml-enabled` | `false` | 允许导出基于 step result 合成的 `JUnit XML`。 |
| `veri-agent.ui-e2e.export-enabled` | `true` | 允许导出运行脱敏摘要。 |
| `veri-agent.ui-e2e.runner-node-command` | `node` | 本地 Playwright runner 使用的 Node 命令。 |
| `veri-agent.ui-e2e.runner-node-modules-dir` | `../portal-web/node_modules` | Playwright 运行时所在目录。 |
| `veri-agent.ui-e2e.artifact-storage-dir` | `${java.io.tmpdir}/veri-agent/ui-e2e-artifacts` | 受控 artifact 本地根目录。 |

用于门禁的环境变量：

| 变量 | 说明 |
|---|---|
| `WP7_GATE_MODE` | `release/preprod/prod` 时要求显式开启 browser smoke、runner smoke 和 artifact eval。 |
| `WP7_BROWSER_SMOKE` | `managed/auto/1` 时执行 `scripts/wp7_browser_smoke.sh`。 |
| `WP7_RUNNER_SMOKE` | `managed/auto/1` 时执行 `scripts/wp7_runner_smoke.sh`。 |
| `WP7_ARTIFACT_REDACTION_EVAL` | 默认 `1`，执行 `scripts/wp7_artifact_redaction_eval.sh`。 |
| `WP7_QUALITY_GATE_PLAN_ONLY` | 仅打印质量门禁执行计划，不真正执行命令。 |

## 3. 日常验证

开发默认门禁：

```bash
bash scripts/wp7_quality_gate.sh
```

仅执行浏览器 smoke：

```bash
bash scripts/wp7_browser_smoke.sh
```

仅执行 runner smoke：

```bash
bash scripts/wp7_runner_smoke.sh
```

仅执行 artifact 脱敏评测：

```bash
bash scripts/wp7_artifact_redaction_eval.sh
```

发布模式必须显式声明本地 smoke：

```bash
WP7_GATE_MODE=release \
WP7_BROWSER_SMOKE=managed \
WP7_RUNNER_SMOKE=managed \
WP7_ARTIFACT_REDACTION_EVAL=1 \
bash scripts/wp7_quality_gate.sh
```

## 4. 准出检查点

1. `runner-enabled=false` 时，运行必须稳定落为 `BLOCKED/UI_E2E_RUNNER_DISABLED`，而不是偷偷执行浏览器。
2. `runner-mode=managed` 只能生成 aggregate-only 预演摘要，取消操作返回 `EXECUTION_RUNNER_NOT_READY`。
3. `runner-mode=playwright-subprocess` 必须只访问 allowlist 命中的 host，且 baseUrl 不能绕过环境摘要解析。
4. WP8 账号契约只允许 `accountLeaseRef`、账号摘要、`secretRefDigest` 和策略布尔值进入控制面；任何 SecretProvider 失败都不得回显明文。
5. 含 `LOGIN` 步骤的场景即使开启 `capture-video-enabled`，也必须把 `VIDEO` 记为 `BLOCKED` 并返回 `captureBlockedReason=credentialEntryWindow`。
6. `HAR` 必须使用 `recordHar(mode=minimal, content=omit)`，浏览器关闭后再次脱敏再落盘。
7. `JUNIT_XML` 只能基于 step result 合成，不引入外部 reporter，也不得落原始请求/响应或 runner stdout。
8. 受控下载只允许返回 `artifact://ui-e2e/...` 指向的本地文件；`summary://ui-e2e/...` 只表示摘要，不可下载。
9. `LocalUiE2eArtifactStorage` 必须阻断路径逃逸、缺失文件和超出 `max-artifact-size-bytes` 的对象。
10. artifact、导出、审计和前端 DOM 都不得出现 password、token、cookie、Authorization、`secret://` 原文、租借 token 或宿主机真实路径。

## 5. 排障

| 现象 | 处理 |
|---|---|
| `UI_E2E_RUNNER_DISABLED` | 先确认是否故意关闭 `runner-enabled`。需要真实执行时显式开启开关并设置 `runner-mode=playwright-subprocess`。 |
| `EXECUTION_RUNNER_NOT_READY` | 当前通常表示 `managed preview` 或未接入异步 cancel；检查 `runnerMode` 和 health。 |
| `UI_E2E_BASE_URL_NOT_ALLOWED` | 检查 `baseUrlRef` 解析结果和 `allowlist-base-urls` 是否只包含正确 host。 |
| `UI_E2E_ACCOUNT_LEASE_INVALID` | 检查 `accountLeaseRef` 是否有效、是否属于同一项目、账号状态是否仍可用。 |
| 运行详情中 `VIDEO/BLOCKED` 且 `captureBlockedReason=credentialEntryWindow` | 这是含 `LOGIN` 步骤场景的预期行为；当前不允许把凭据输入窗口写入磁盘。 |
| `HAR` 或 `VIDEO` 显示 `harNotProduced/videoNotProduced` | 检查 Playwright 是否完整关闭 browser/context、artifact 开关是否开启、工作目录是否可写。 |
| `UI_E2E_ARTIFACT_DOWNLOAD_NOT_READY` | 检查 manifest 是否为 `CAPTURED` 且 `storageRef` 为 `artifact://ui-e2e/...`，以及本地文件是否仍在受控存储中。 |
| 取消运行后仍显示同步完成 | 当前 Playwright subprocess 是同步执行，`cancel` 仅是 best-effort 控制面语义；运行会被标记为 `CANCELED`，但不保证中断已在子进程生效。 |
| artifact 脱敏评测失败 | 视为安全阻断，先关闭导出或 capture 开关，再修复 redaction 规则并重跑 `scripts/wp7_artifact_redaction_eval.sh`。 |

## 6. 回滚

发现目标误放行、凭据泄露风险、artifact 落盘异常或 runner 不稳定时，优先执行：

1. 设置 `veri-agent.ui-e2e.runner-enabled=false`。
2. 把 `veri-agent.ui-e2e.runner-mode=disabled`。
3. 视情况关闭 `capture-video-enabled`、`capture-har-enabled`、`capture-junit-xml-enabled`、`export-enabled`。
4. 清空或收紧 `allowlist-base-urls`。
5. 保留 `ui_e2e_run`、`ui_e2e_run_step_result`、`ui_e2e_artifact_manifest` 和审计摘要；如受控存储中已有不应保留的原始文件，按安全流程删除文件并保留 digest 证据。
6. 若问题来自前端入口，可临时移除相关权限或隐藏 `#ui-e2e` 菜单，但不得删除审计和运行摘要。

## 7. 当前限制

1. 真实浏览器 runner 当前只支持 `LOGIN/NAVIGATE/ASSERT`，不支持更复杂的模板包和多场景编排。
2. `cancel` 仅提供控制面 best-effort 语义，不保证真正中断同步子进程。
3. 不提供 SSE/WebSocket 实时日志推送；当前只在运行完成后查看步骤结果、audit timeline 和受控 artifact。
4. 不提供 Docker/容器化隔离执行；当前所有真实浏览器 runner 都在宿主机受控子进程内执行。
5. 不提供对象存储、CDN、签名 URL 或外部下载分享链接。
6. 不支持含 `LOGIN` 场景的视频脱敏后留存；此类视频必须阻断。
7. 不持久化 runner stdout/stderr、原始 DOM、密码、token、cookie、`secret://` 原文或租借 token 明文。
