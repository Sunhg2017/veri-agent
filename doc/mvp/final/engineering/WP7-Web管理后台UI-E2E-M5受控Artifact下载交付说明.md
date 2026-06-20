# WP7 Web 管理后台 UI/E2E - M5 受控 Artifact 下载交付说明

| 项目 | 内容 |
|---|---|
| 工作包 | WP7 Web 管理后台 UI/E2E |
| 里程碑 | M5 证据与 Flaky |
| 交付主题 | 受控 artifact 存储与下载 |
| 日期 | 2026-06-20 |

## 1. 目标

补齐 WP7 “运行产物存储/下载”缺口，让 Playwright 子进程 runner 产出的 screenshot、trace、runner log、HAR、JUnit XML 以及登录免凭据场景下的视频，在临时工作目录销毁前复制到受控本地存储，并通过受权 API 下载，不暴露宿主机真实路径、存储凭据或未脱敏错误。

## 2. 本轮交付

1. 新增 `UiE2eArtifactStorage` port 和 `LocalUiE2eArtifactStorage` 本地适配器。
2. 新增配置 `veri-agent.ui-e2e.artifact-storage-dir`，默认指向 `${java.io.tmpdir}/veri-agent/ui-e2e-artifacts`。
3. 新增配置 `veri-agent.ui-e2e.capture-har-enabled` 和 `veri-agent.ui-e2e.capture-junit-xml-enabled`，并把 `artifactPolicy` 健康摘要同步扩展到 `HAR/JUnit XML` 开关。
4. `PlaywrightSubprocessUiE2eRunnerAdapter` 在 workspace 清理前把 screenshot、trace、runner log、HAR、JUnit XML 复制到受控存储，并把 `rawArtifactStored/rawArtifactDownloadReady` 标记写入 artifact manifest。
5. `PlaywrightSubprocessUiE2eRunnerAdapter` 对含 `LOGIN` 步骤的场景强制阻断视频落盘，返回 `captureBlockedReason=credentialEntryWindow`；无 `LOGIN` 场景允许真实采集视频并纳入受控下载。
6. `UiE2eRunService` 新增 `downloadArtifact` 应用服务，统一校验 run、artifact manifest 和下载就绪状态。
7. `UiE2eRunController` 新增 `GET /api/v1/ui-e2e/runs/{id}/artifacts/{artifactId}/download`，要求 `uiE2e:export` 权限。
8. `UiE2eHealthService` 与 health API 同步暴露 `artifactPolicy.rawArtifactDownloadReady=true`、`captureHarEnabled` 和 `captureJunitXmlEnabled`。
9. `portal-web` 的 `#ui-e2e` 工作台在运行详情中新增 artifact 下载按钮、就绪提示和失败反馈，直接消费受控下载端点。

## 3. 安全边界

1. API 仅返回 opaque `storageRef`，不回显真实文件路径。
2. 下载失败统一折叠为 `UI_E2E_ARTIFACT_DOWNLOAD_NOT_READY`。
3. 本地存储适配器阻断路径逃逸和超出 `maxArtifactSizeBytes` 的文件落盘。
4. 含 `LOGIN` 步骤的场景即使开启 `capture-video-enabled`，也只能返回 `VIDEO/BLOCKED`，不得把凭据输入窗口写入磁盘。
5. HAR 使用 `recordHar(mode=minimal, content=omit)`，关闭浏览器后再次脱敏落盘，避免正文和目标地址原样外泄。
6. 下载权限复用 `uiE2e:export`，避免读权限用户直接获取原始文件。
7. manifest、导出和 health 仍保持 aggregate-only 摘要，不在控制面返回存储凭据。

## 4. 验证

已执行：

```bash
bash scripts/platform_api_java_line_guard.sh
mvn -B -pl platform-api -Dtest=UiE2eRunServiceTest,UiE2eHealthControllerTest,ManagedPreviewUiE2eRunnerAdapterTest,PlaywrightSubprocessUiE2eRunnerAdapterTest,UiE2eRunnerConfigurationTest,LocalUiE2eArtifactStorageTest,UiE2eSceneServiceTest test
bash scripts/wp7_runner_smoke.sh
bash scripts/wp7_artifact_redaction_eval.sh
```

验证要点：

1. `LocalUiE2eArtifactStorageTest` 覆盖正常存取、路径逃逸阻断和大小门禁。
2. `ManagedPreviewUiE2eRunnerAdapterTest` 覆盖 preview 模式下 `HAR/JUnit XML` 占位阻断。
3. `UiE2eRunServiceTest` 覆盖真实浏览器 runner 的 `HAR/JUnit XML` 真实采集、登录场景 `VIDEO` 阻断和无登录场景 `VIDEO` 真实采集。
4. `UiE2eHealthControllerTest` 覆盖 `artifactPolicy.captureHarEnabled/captureJunitXmlEnabled` 返回。
5. `scripts/wp7_runner_smoke.sh` 和 `scripts/wp7_artifact_redaction_eval.sh` 复跑通过，确保 runner 与脱敏边界未回退。

## 5. 非目标

1. 不引入对象存储、CDN、签名 URL 或外部下载分享链接。
2. 不支持对含凭据输入窗口的视频做脱敏后留存；此类场景仍必须阻断视频落盘。
3. 不修改 WP9/WP10 对 WP7 evidence 的 aggregate-only 消费边界。

## 6. 五角色结论

| 角色 | 结论 | 说明 |
|---|---|---|
| 资深项目经理 | 通过 | 变更范围聚焦 WP7 artifact 下载缺口，未扩散到跨 WP 调度。 |
| 资深产品经理 | 通过 | 满足“可下载受控产物但不泄露宿主机路径”的产品边界。 |
| 资深服务端架构师 | 通过 | 通过 port + local adapter 落地，保持权限、脱敏和错误码边界稳定。 |
| 资深前端工程师 | 通过 | 工作台已接入 artifact 下载按钮、状态提示和受权失败反馈，沿用既有 run 详情交互。 |
| 资深质量工程师 | 通过 | 后端、前端、构建和完整 WP7 quality gate 已通过，可作为本轮验收证据。 |
