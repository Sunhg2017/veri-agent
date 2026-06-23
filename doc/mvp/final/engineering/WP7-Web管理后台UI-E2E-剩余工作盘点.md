# WP7 Web 管理后台 UI/E2E - 剩余工作盘点

| 项目 | 内容 |
|---|---|
| 工作包 | WP7 Web 管理后台 UI/E2E |
| 文档性质 | 当前范围剩余项审计、后续专项边界 |
| 日期 | 2026-06-20 |

## 1. 结论

截至当前收口，WP7 当前承诺范围无剩余 P0 功能开发项。`Runner Runbook`、`发布准出说明` 和 `剩余工作盘点` 已补齐，当前只剩目标环境发布前按对应环境 release gate 执行验证、填写发布记录，以及后续专项另行立项。

`WP7-8.5 Runner Runbook` 已由 `WP7-Web管理后台UI-E2E-Runner-Runbook.md` 补齐；`WP7-8.6 发布准出说明` 已由 `WP7-Web管理后台UI-E2E-发布准出说明.md` 补齐；`WP7-8.7 剩余工作盘点` 由本文补齐。

## 2. 当前范围已完成项

| 领域 | 完成证据 |
|---|---|
| 基础控制面 | `GET /api/v1/ui-e2e/health`、权限 seed、审计事件、DB schema、配置属性和 OpenAPI 契约已完成。 |
| Scene/Bundle | 已支持 scene CRUD、步骤模板、来源摘要绑定、bundle 生成、静态校验、submit-review/approve/reject 和状态保护。 |
| 真实浏览器 runner | 已提供 disabled、managed preview、Playwright subprocess 三类模式；真实执行支持 `LOGIN/NAVIGATE/ASSERT`。 |
| WP8 凭据注入 | 已通过 `runnerAccountContract` 和 SecretProvider 链完成运行时凭据注入，控制面只返回脱敏账号摘要和 `secretRefDigest`。 |
| Run 控制面 | 已支持 create/list/detail/cancel/export、requestKey 幂等、allowlist 校验、失败摘要和步骤结果聚合。 |
| Artifact manifest | 已支持 screenshot、trace、runner log、`HAR`、`JUNIT_XML` 和登录免凭据场景 `VIDEO` 的 manifest、digest、size、redaction flags。 |
| 浏览器矩阵与截图 Diff | 已支持单场景多浏览器 fan-out 聚合、截图 `ACTUAL/BASELINE/DIFF` 角色标记、基线自动回溯和阈值判定。 |
| 受控下载 | 已支持本地受控 artifact 存储与 `GET /api/v1/ui-e2e/runs/{id}/artifacts/{artifactId}/download`。 |
| 视频安全边界 | 含 `LOGIN` 步骤的场景强制返回 `VIDEO/BLOCKED` 且 `captureBlockedReason=credentialEntryWindow`。 |
| Flaky 治理 | 已支持 `NONE/FLAKY_CANDIDATE/CONFIRMED_FLAKY/WAIVED` 标记与审计。 |
| 前端工作台 | `#ui-e2e` 已覆盖健康摘要、场景编辑、bundle 评审、运行详情、artifact 下载、Flaky 标记、响应式布局和主要错误态。 |
| Quality gate | 已提供 `scripts/wp7_browser_smoke.sh`、`scripts/wp7_runner_smoke.sh`、`scripts/wp7_artifact_redaction_eval.sh` 和 `scripts/wp7_quality_gate.sh`。 |
| 文档交付 | 已补齐启动准备、PRD、技术设计、前端页面设计、测试策略、研发拆解、M5 artifact 下载交付说明、Runner Runbook、发布准出说明和剩余工作盘点。 |

## 3. 后续专项

| 后续项 | 当前判断 | 不阻断原因 |
|---|---|---|
| SSE/WebSocket 实时运行日志推送 | 后续前后端协同专项 | 当前运行详情和 artifact 下载已经满足 P0 控制面闭环，实时推送不影响现有执行与审计。 |
| Docker/容器化隔离执行 | 后续平台化专项 | 当前 Playwright subprocess 已在本地受控边界内可用，但不承诺隔离运行环境。 |
| 含 `LOGIN` 场景的视频脱敏留存 | 后续安全专项 | 当前明确禁止凭据输入窗口落盘，阻断策略是安全优先的预期行为。 |
| 预签名 URL/CDN/外部分享下载 | 后续存储分发专项 | 按当前基线，WP7 已接入平台级统一存储抽象并支持受权下载；剩余不在于“是否可存储/下载”，而在于预签名链接、CDN 分发和外部分享链路。 |
| 实际进程级 cancel / 外部 runner 回送 | 后续异步执行专项 | 当前 cancel 已有稳定控制面语义，但同步 subprocess 只能 best-effort。 |
| 多场景批量运行与浏览器池 | 后续规模化专项 | 当前 `maxScenesPerRun=1`，先保证单场景受控执行和审计边界稳定。 |
| 第三方登录、SSO、复杂步骤模板 | 后续产品增强专项 | 当前真实浏览器 runner 只承诺 `LOGIN/NAVIGATE/ASSERT` 三类最小闭环。 |
| 大规模视觉回归平台、浏览器池、移动端真实执行 | 后续质量增强专项 | 当前已支持单场景多浏览器矩阵和基础截图 Diff，但不承诺基线图库、人工验图体系或大规模容量调度。 |

## 4. 发布前必做

目标环境发布前建议执行：

```bash
bash scripts/wp7_quality_gate.sh
WP7_GATE_MODE=release WP7_BROWSER_SMOKE=managed WP7_RUNNER_SMOKE=managed WP7_ARTIFACT_REDACTION_EVAL=1 bash scripts/wp7_quality_gate.sh
mvn -B -pl platform-api test
cd portal-web && npm test
cd portal-web && npm run build
git diff --check
```

若需要使用外部真实站点执行 smoke，必须额外评审：

1. allowlist host 是否最小化；
2. 测试账号和测试数据是否可回收；
3. 运行不会触发破坏性业务动作；
4. artifact 落盘不会包含受限合规内容。

## 5. 范围变更触发条件

后续如出现以下任一变化，应重新打开 WP7 需求、技术设计、测试策略和发布准出说明：

1. runner 从本地 subprocess 升级为 Docker/远程 worker/浏览器池；
2. artifact 下载从受权下载端点升级为预签名 URL、CDN 或外部分发；
3. `LOGIN` 场景视频策略从“强制阻断”改为“脱敏后留存”；
4. 新增 SSE/WebSocket 实时日志、流式控制台或 runner 事件推送；
5. 支持 `LOGIN/NAVIGATE/ASSERT` 以外的大量复杂步骤模板或多场景批量执行；
6. 引入第三方登录、SSO、验证码、大规模视觉回归平台或跨浏览器容量承诺。

## 6. 五角色盘点结论

| 角色 | 结论 | 说明 |
|---|---|---|
| 资深项目经理 | 通过 | 当前范围、后续专项、发布前必做和回滚边界已明确，WP7 不再存在 P0 主链路缺口。 |
| 资深产品经理 | 通过 | 用户主链路已闭环，后续增强项没有被误标为当前完成。 |
| 资深服务端架构师 | 通过 | runner、WP8 契约、artifact 和下载边界已经稳定收口，后续专项边界清楚。 |
| 资深前端工程师 | 通过 | `#ui-e2e` 当前体验与后续前端增强边界已区分，未承诺未实现 UI。 |
| 资深质量工程师 | 通过 | 当前必跑 release gate、artifact 安全评测和运行时变更加严验证规则已经明确。 |
