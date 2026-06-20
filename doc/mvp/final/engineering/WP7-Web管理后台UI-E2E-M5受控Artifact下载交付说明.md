# WP7 Web 管理后台 UI/E2E - M5 受控 Artifact 下载交付说明

| 项目 | 内容 |
|---|---|
| 工作包 | WP7 Web 管理后台 UI/E2E |
| 里程碑 | M5 证据与 Flaky |
| 交付主题 | 受控 artifact 存储与下载 |
| 日期 | 2026-06-20 |

## 1. 目标

补齐 WP7 “运行产物存储/下载”缺口，让 Playwright 子进程 runner 产出的 screenshot、trace、runner log 在临时工作目录销毁前复制到受控本地存储，并通过受权 API 下载，不暴露宿主机真实路径、存储凭据或未脱敏错误。

## 2. 本轮交付

1. 新增 `UiE2eArtifactStorage` port 和 `LocalUiE2eArtifactStorage` 本地适配器。
2. 新增配置 `veri-agent.ui-e2e.artifact-storage-dir`，默认指向 `${java.io.tmpdir}/veri-agent/ui-e2e-artifacts`。
3. `PlaywrightSubprocessUiE2eRunnerAdapter` 在 workspace 清理前把 screenshot、trace、runner log 复制到受控存储，并把 `rawArtifactStored/rawArtifactDownloadReady` 标记写入 artifact manifest。
4. `UiE2eRunService` 新增 `downloadArtifact` 应用服务，统一校验 run、artifact manifest 和下载就绪状态。
5. `UiE2eRunController` 新增 `GET /api/v1/ui-e2e/runs/{id}/artifacts/{artifactId}/download`，要求 `uiE2e:export` 权限。
6. `UiE2eHealthService` 与 health API 同步暴露 `artifactPolicy.rawArtifactDownloadReady=true`。

## 3. 安全边界

1. API 仅返回 opaque `storageRef`，不回显真实文件路径。
2. 下载失败统一折叠为 `UI_E2E_ARTIFACT_DOWNLOAD_NOT_READY`。
3. 本地存储适配器阻断路径逃逸和超出 `maxArtifactSizeBytes` 的文件落盘。
4. 下载权限复用 `uiE2e:export`，避免读权限用户直接获取原始文件。
5. manifest、导出和 health 仍保持 aggregate-only 摘要，不在控制面返回存储凭据。

## 4. 验证

已执行：

```bash
mvn -B -pl platform-api -Dtest=LocalUiE2eArtifactStorageTest,UiE2eRunnerConfigurationTest,UiE2eRunServiceTest,UiE2eRunControllerTest,PlaywrightSubprocessUiE2eRunnerAdapterTest,UiE2eHealthControllerTest,UiE2eOpenApiContractTest test
```

验证要点：

1. `LocalUiE2eArtifactStorageTest` 覆盖正常存取、路径逃逸阻断和大小门禁。
2. `UiE2eRunControllerTest` 覆盖受权下载返回 200、附件文件名和文件内容。
3. `UiE2eRunServiceTest` 覆盖真实浏览器 runner 成功时 `rawArtifactDownloadReady=true`。
4. `UiE2eOpenApiContractTest` 覆盖下载端点已出现在 OpenAPI。

## 5. 非目标

1. 不引入对象存储、CDN、签名 URL 或外部下载分享链接。
2. 不扩展视频、HAR、JUnit XML 真实捕获实现；这些类型仍以后续 runner 扩展为准。
3. 不修改 WP9/WP10 对 WP7 evidence 的 aggregate-only 消费边界。

## 6. 五角色结论

| 角色 | 结论 | 说明 |
|---|---|---|
| 资深项目经理 | 通过 | 变更范围聚焦 WP7 artifact 下载缺口，未扩散到跨 WP 调度。 |
| 资深产品经理 | 通过 | 满足“可下载受控产物但不泄露宿主机路径”的产品边界。 |
| 资深服务端架构师 | 通过 | 通过 port + local adapter 落地，保持权限、脱敏和错误码边界稳定。 |
| 资深前端工程师 | 无影响 | 本轮未改前端运行时代码，后续可按下载端点补 UI 按钮。 |
| 资深质量工程师 | 有条件通过 | 后端定点验证已通过；完整 WP7 quality gate 仍需与前端/DB 一起执行。 |
