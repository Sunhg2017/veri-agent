# WP7 Web 管理后台 UI/E2E - 发布准出说明

| 项目 | 内容 |
|---|---|
| 工作包 | WP7 Web 管理后台 UI/E2E |
| 文档性质 | 发布准出、验证记录、风险、回滚和五角色结论 |
| 当前分支 | `codex/wp10-planning` |
| 远端 | `origin/codex/wp10-planning` |
| 日期 | 2026-06-20 |

## 1. 准出结论

WP7 当前承诺范围已经形成可验收闭环。`platform-api` 已提供 scene/bundle/review/run/flaky 控制面、`/api/v1/ui-e2e/health`、Playwright 子进程真实浏览器 runner、WP8 `runnerAccountContract` 脱敏账号契约与凭据注入、受控 artifact 本地存储/下载、`HAR/JUnit XML` 真实采集、登录免凭据场景视频采集、运行脱敏导出、失败分类、权限、审计、DB validation 和 `scripts/wp7_quality_gate.sh`；`portal-web` 已提供 `#ui-e2e` 工作台，覆盖场景编辑、bundle 评审、运行详情、artifact 下载、Flaky 标记、DOM 禁止字段扫描和 390px 浏览器 smoke。

当前准出口径不包含 SSE/WebSocket 实时日志推送、Docker/容器化隔离执行、预签名 URL/CDN/外部分享链路、含 `LOGIN` 场景的视频脱敏留存、多场景批量运行、浏览器池或第三方登录复杂流程。这些均作为后续专项记录，不构成本轮 WP7 发布阻断。按当前基线，WP7 artifact 存储已可复用平台级对象存储抽象，但不改变本轮 WP7 对外准出边界。

## 2. 范围和非目标

本次准出范围：

1. WP7 后端控制面：health、scene CRUD、bundle 生成与评审、run create/detail/list/cancel/export、artifact download、flaky mark、权限、审计和 traceId。
2. WP7 runner：`disabled`、`managed preview`、`playwright-subprocess` 三类模式，以及 allowlist、超时、artifact 数量/大小、WP8 凭据注入和受控本地 artifact 存储。
3. WP7 前端工作台：`#ui-e2e` 入口权限、健康摘要、场景编辑、bundle 评审、运行详情、artifact 下载、Flaky 标记、loading/empty/error/403/409 和移动端 smoke。
4. WP7 验证脚本：browser smoke、runner smoke、artifact redaction eval、quality gate、前端测试与构建、后端测试、DB validation 和 Java 行数门禁。
5. WP7 交付文档：启动准备、PRD、技术设计、前端页面设计、测试策略、研发拆解、M5 artifact 下载交付说明、Runner Runbook、本发布准出说明和剩余工作盘点。

非目标：

1. 不提供实时运行日志推送、会话级流式控制台或外部 runner 回调通道。
2. 不提供 Docker/容器沙箱、浏览器池、远程宿主机执行，或预签名 URL/CDN/外部分享型下载链路。
3. 不对含 `LOGIN` 的场景保留视频；此类场景只能返回 `VIDEO/BLOCKED`。
4. 不开放任意 shell、自定义浏览器启动参数、未批准网络出口或任意文件下载。
5. 不把原始 DOM、runner stdout/stderr、密码、token、cookie、`secret://` 原文或租借 token 明文暴露给控制面、导出或前端。

## 3. 验证记录

本轮 WP7 运行时代码收口已执行并通过：

```bash
bash scripts/platform_api_java_line_guard.sh
mvn -B -pl platform-api -Dtest=UiE2eRunServiceTest,UiE2eHealthControllerTest,ManagedPreviewUiE2eRunnerAdapterTest,PlaywrightSubprocessUiE2eRunnerAdapterTest,UiE2eRunnerConfigurationTest,LocalUiE2eArtifactStorageTest,UiE2eSceneServiceTest test
bash scripts/wp7_runner_smoke.sh
bash scripts/wp7_artifact_redaction_eval.sh
mvn -B -pl platform-api test
bash scripts/wp7_quality_gate.sh
cd portal-web && npm test
cd portal-web && npm run build
```

结果：

1. `bash scripts/platform_api_java_line_guard.sh` 通过，Platform API 生产 Java 文件均不超过 1200 行。
2. WP7 定向后端测试通过，覆盖 health、preview runner、真实浏览器 runner、artifact storage、scene service 和运行服务。
3. `bash scripts/wp7_runner_smoke.sh` 通过，覆盖 preview/real-browser 分支、dispatch 契约和 runner 配置。
4. `bash scripts/wp7_artifact_redaction_eval.sh` 通过，覆盖 server/export/frontend DOM 禁止字段样本。
5. `mvn -B -pl platform-api test` 通过，`764` tests，`0` failures，`0` errors，`0` skipped。
6. `bash scripts/wp7_quality_gate.sh` 通过，包含脚本语法、Java 行数门禁、后端/OpenAPI 测试、前端 Vitest、browser smoke、build、合并 DB validation、runner smoke 和 artifact eval。
7. `cd portal-web && npm test` 通过，`30` files / `233` tests。
8. `cd portal-web && npm run build` 通过；仅保留既有 Vite chunk size 和 dynamic/static import warning。

本轮文档收口已额外执行并通过：

```bash
rg -n "WP7-Web管理后台UI-E2E-(Runner-Runbook|发布准出说明|剩余工作盘点)" README.md doc/mvp/final/engineering/WP7-* doc/mvp/final/engineering/当前实现基线.md
git diff --check
cd portal-web && npm test -- --run src/api/uiE2e.test.ts src/uiE2eWorkbenchState.test.ts src/permissions.test.ts
cd portal-web && npm run build
```

涉及 WP7 真实浏览器 runner 的 Java 生产代码变更已经完成《阿里巴巴 Java 开发手册》人工自查，并在 `PlaywrightSubprocessUiE2eRunnerAdapter` 中补充了视频安全边界、浏览器关闭后再落盘 `HAR/VIDEO` 等核心逻辑注释。

## 4. 跳过项和原因

未执行外部真实业务站点 smoke。原因是当前 WP7 release gate 以本地 managed/browser smoke 为准，外部站点执行需要额外评审 allowlist、测试账号、数据污染边界和破坏性动作风险。

未执行容器化、远程 runner 或预签名 URL/CDN 外部分发验证。原因是这些能力不在当前 WP7 准出口径内；按当前基线，平台级对象存储抽象已由后续专题接入，但本轮准出仍按受权下载端点与受控存储边界验收。

未执行含 `LOGIN` 场景的视频留存专项。原因是当前产品和安全边界明确要求这类场景强制阻断视频落盘，后续若要引入脱敏留存，需要单独立项并重新准出。

## 5. 发布风险

| 风险 | 准出控制 | 处置 |
|---|---|---|
| baseUrl 误放行导致外部目标被执行 | allowlist 校验、runner validate、health 摘要和 runner smoke | 立即关闭 `runner-enabled`，清空 allowlist，保留 run/audit 摘要 |
| 凭据或敏感 artifact 泄露 | WP8 白名单账号契约、artifact redaction eval、DOM 禁止字段扫描、受控下载 | 关闭 `export-enabled` 和相关 capture 开关，清理受控存储中的敏感文件，保留 digest 证据 |
| 登录场景视频被误留存 | `credentialEntryWindow` 强制阻断、真实浏览器测试覆盖 | 视为安全阻断，立即关闭视频采集并修复策略 |
| 同步 subprocess 无法真正中断 | cancel best-effort 语义、状态机和审计记录清晰 | 暂停新运行，等待当前子进程自然收敛后再切换开关 |
| 本地 artifact 存储异常或磁盘压力 | 大小门禁、数量门禁、本地受控目录和下载就绪检查 | 切换或清空 `artifact-storage-dir`，必要时关闭 download/export |
| 前端 UI 与权限/状态边界漂移 | `uiE2e.test.ts`、`uiE2eWorkbenchState.test.ts`、browser smoke 和 `permissions.test.ts` | 暂停发布，修复前端状态判断后重跑 build 和 smoke |

## 6. 回滚方式

1. 暂停真实执行：设置 `veri-agent.ui-e2e.runner-enabled=false`。
2. 降级到 preview 或彻底关闭：把 `runner-mode` 改为 `managed` 或 `disabled`。
3. 关闭受影响的 artifact：按需关闭 `capture-video-enabled`、`capture-har-enabled`、`capture-junit-xml-enabled`、`export-enabled`。
4. 收紧执行边界：清空或缩小 `allowlist-base-urls`。
5. 如问题来自 artifact 存储，切换 `artifact-storage-dir`，并按安全流程处理既有文件；`storageRef`、digest 和审计摘要必须保留。
6. 如需临时隐藏入口，可撤销 `uiE2e:*` 权限或隐藏 `#ui-e2e` 菜单，但不得删除历史 run、manifest 和 audit 证据。
7. 若回滚涉及 Java 生产代码，必须重新执行 `bash scripts/platform_api_java_line_guard.sh`，并记录《阿里巴巴 Java 开发手册》自查和核心注释结论。

## 7. 发布记录要求

目标环境发布、事故恢复或安全事件关闭工单至少记录：

1. `bash scripts/wp7_quality_gate.sh`、`mvn -B -pl platform-api test`、`cd portal-web && npm test`、`cd portal-web && npm run build` 和 `git diff --check` 结果。
2. 目标环境、WP7 开关状态、allowlist 审批结果、artifact 策略开关、当前 commit 和远端分支。
3. 相关 `projectId`、`sceneId`、`bundleId`、`runId`、`accountLeaseRef`、`traceId`。
4. `baseUrlRefDigest`、`secretRefDigest`、`artifactDigest` 等摘要证据。
5. 跳过项、风险、影响范围、回滚开关、责任人和恢复时间。
6. 若使用外部真实目标 smoke，必须额外记录测试账号、允许 host、破坏性动作边界和审批人。

## 8. 五角色准出结论

| 角色 | 结论 | 说明 |
|---|---|---|
| 资深项目经理 | 通过 | 当前范围、非目标、验证证据、风险和回滚路径已收口，后续专项不阻断本轮准出。 |
| 资深产品经理 | 通过 | 用户可通过 `#ui-e2e` 完成场景、bundle、运行、artifact 下载和 Flaky 主链路，视频与实时日志边界清晰。 |
| 资深服务端架构师 | 通过 | 服务端控制面、WP8 凭据注入、allowlist、受控 artifact 存储、`HAR/JUnit XML` 策略和安全边界均已具备准出证据。 |
| 资深前端工程师 | 通过 | 前端工作台、权限显隐、错误态、下载入口和响应式 smoke 已与当前实现对齐。 |
| 资深质量工程师 | 通过 | Java 行数门禁、后端全量、WP7 quality gate、runner smoke、artifact 脱敏评测、前端测试和 build 均已通过。 |
