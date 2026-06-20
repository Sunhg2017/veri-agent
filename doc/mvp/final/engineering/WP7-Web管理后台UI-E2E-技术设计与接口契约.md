# WP7 Web 管理后台 UI/E2E - 技术设计与接口契约

| 项目 | 内容 |
|---|---|
| 工作包 | WP7 Web 管理后台 UI/E2E |
| 角色产出 | 资深服务端架构师 |
| 文档性质 | 技术设计、数据模型、接口契约和服务端质量约束 |
| 当前口径 | 在 `platform-api` 内新增 `uie2e` 领域模块，复用 WP1 权限、审计、traceId、项目/应用/环境上下文，消费 WP3/WP5/WP8/WP9/WP10 的稳定应用层契约 |
| 版本 | v0.1 |
| 日期 | 2026-06-18 |

## 1. 架构原则

1. WP7 是 UI/E2E 控制面和受控 runner 接入层，不是浏览器资源平台或容器编排平台。
2. 所有资源必须绑定 `projectId`；`applicationId`、`environmentId` 作为更细粒度的引用和授权上下文。
3. 场景、bundle、运行、artifact 和 Flaky 都必须写审计，且 payload 只保存摘要、计数、digest 和错误码。
4. WP7 不直接读取 WP3/WP5/WP8/WP9/WP10 表；跨 WP 输入必须走应用服务、导出接口或明确 port。
5. 账号凭据只以 `accountLeaseRef` 为句柄流转；控制面只接收账号摘要和 `secretRefDigest`，不持久化密码、token、cookie、`secret://` 原文或租借 token 明文。
6. 原始 artifact 不进入当前 P0 主数据面；WP7 只保存 manifest、digest、size、storageRef 和 redaction flags，并允许从受控本地存储按权限下载。
7. Runner 必须受 allowlist、超时、并发、artifact 大小、步骤数和网络策略限制；默认 disabled。

## 2. 模块划分

| 包 | 职责 |
|---|---|
| `uie2e.api` | Controller、request/response DTO、OpenAPI contract、health API。 |
| `uie2e.application` | scene、bundle、review、run、artifact、Flaky 应用服务和跨 WP adapter。 |
| `uie2e.domain` | 状态机、步骤模板、失败分类和值对象。 |
| `uie2e.infrastructure` | MyBatis mapper、runner adapter、WP8 account adapter、WP3/WP5 reference adapter、artifact storage adapter。 |
| `uie2e.config` | runner enablement、allowlist、超时、并发和 artifact policy。 |

## 3. 数据模型草案

| 表 | 关键字段 | 说明 |
|---|---|---|
| `ui_e2e_scene` | `id`、`project_id`、`application_id`、`environment_id`、`code`、`name`、`status`、`risk_level`、`source_summary_json`、`tags_json` | UI 场景主表，保存页面/流程/用例来源摘要和业务目标。 |
| `ui_e2e_scene_step` | `id`、`scene_id`、`project_id`、`step_order`、`step_type`、`action_summary_json`、`locator_strategy_json`、`assertion_summary_json`、`wait_policy_json` | 结构化步骤模板，不保存敏感 DOM 原文快照。 |
| `ui_e2e_bundle` | `id`、`scene_id`、`project_id`、`status`、`bundle_digest`、`spec_summary_json`、`fixture_summary_json`、`static_check_summary_json` | Playwright bundle 摘要与静态校验结果。 |
| `ui_e2e_bundle_review` | `id`、`bundle_id`、`project_id`、`review_status`、`review_comment`、`reviewed_by`、`reviewed_at` | bundle 审批记录。 |
| `ui_e2e_run` | `id`、`scene_id`、`bundle_id`、`project_id`、`status`、`request_key`、`runner_mode`、`base_url_digest`、`account_lease_ref`、`account_summary_json`、`failure_code`、`failure_summary`、`started_at`、`finished_at` | 单次执行记录和运行摘要。 |
| `ui_e2e_run_step_result` | `id`、`run_id`、`scene_step_id`、`step_order`、`status`、`duration_ms`、`failure_bucket`、`error_code`、`summary_json` | 步骤级结果和失败分类。 |
| `ui_e2e_artifact_manifest` | `id`、`run_id`、`artifact_type`、`storage_ref`、`artifact_digest`、`size_bytes`、`redaction_flags_json`、`capture_status` | screenshot/trace/runner log、`HAR`、`JUNIT_XML` 和受控 `VIDEO` 摘要。 |
| `ui_e2e_flaky_mark` | `id`、`project_id`、`scene_id`、`run_id`、`status`、`reason_code`、`reason_summary` | Flaky 标记与处理结果。 |

约束要求：

1. 所有生产表必须带 `created_at`、`updated_at`、`created_by`、`updated_by`，无特殊情况不得省略。
2. `code` 在项目内唯一；`request_key` 在 `project_id + scene_id` 维度内幂等。
3. `scene_step.step_order`、`run_step_result.step_order` 必须唯一且连续可排序。
4. `account_lease_ref` 只作为外部稳定引用持久化，不允许落租借 token、secretRef 原文或凭据明文。
5. 需要为 `project_id/application_id/environment_id/status`、`request_key`、`scene_id`、`bundle_id`、`run_id` 建索引。
6. 所有 JSON 摘要字段必须有结构上限和禁止字段过滤。

## 4. 配置项草案

| 配置 | 默认值 | 说明 |
|---|---|---|
| `veri-agent.ui-e2e.enabled` | `true` | WP7 控制面总开关。 |
| `veri-agent.ui-e2e.runner-enabled` | `false` | 浏览器执行总开关，默认关闭。 |
| `veri-agent.ui-e2e.runner-mode` | `disabled` | `disabled`、`managed`、`http-adapter`、`playwright-subprocess`、`real-browser`。 |
| `veri-agent.ui-e2e.default-timeout-seconds` | `300` | 单次运行默认超时。 |
| `veri-agent.ui-e2e.max-timeout-seconds` | `1800` | 单次运行最大超时。 |
| `veri-agent.ui-e2e.max-scenes-per-run` | `1` | P0 单次运行场景数上限。 |
| `veri-agent.ui-e2e.max-artifact-size-bytes` | `20971520` | 单个 artifact 摘要对应对象大小上限。 |
| `veri-agent.ui-e2e.max-artifact-count` | `20` | 单次运行最多保留的 artifact 摘要数。 |
| `veri-agent.ui-e2e.max-concurrency` | `2` | 单机 runner 并发上限。 |
| `veri-agent.ui-e2e.allowlist-base-urls` | 空 | 允许执行的 baseUrl host 白名单。 |
| `veri-agent.ui-e2e.capture-screenshot-enabled` | `true` | 是否采集截图摘要。 |
| `veri-agent.ui-e2e.capture-video-enabled` | `false` | 是否允许视频采集；含 `LOGIN` 步骤的场景即使开启也必须按 `credentialEntryWindow` 阻断。 |
| `veri-agent.ui-e2e.capture-har-enabled` | `false` | 是否采集 `HAR`；落盘前必须做脱敏和 `content=omit` 限制。 |
| `veri-agent.ui-e2e.capture-trace-enabled` | `true` | 是否采集 trace 摘要。 |
| `veri-agent.ui-e2e.capture-junit-xml-enabled` | `false` | 是否导出基于 step result 合成的 `JUnit XML`。 |
| `veri-agent.ui-e2e.export-enabled` | `true` | 是否允许导出脱敏摘要。 |
| `veri-agent.ui-e2e.artifact-storage-dir` | `${java.io.tmpdir}/veri-agent/ui-e2e-artifacts` | 受控原始 artifact 本地落盘根目录，仅通过 API 下载，不回显真实路径。 |

## 5. 跨 WP 依赖

| 工作包 | 依赖方式 |
|---|---|
| WP1 | 复用项目/应用/环境、RBAC、审计、traceId、OpenAPI 和 SecretProvider 审计能力。 |
| WP3 | 通过应用服务读取页面、业务流、追踪关系摘要，作为场景输入。 |
| WP5 | 通过应用服务读取已发布测试用例摘要和步骤结构，作为场景草稿输入。 |
| WP8 | 通过 `TestDataCrossWpReferenceService#runnerAccountContract` 获取 `accountLeaseRef`、账号摘要和 `secretRefDigest`。 |
| WP9 | 对齐 `UI_TEST` 节点输入输出契约，提供执行摘要、错误码和 artifact refs。 |
| WP10 | 提供 aggregate-only evidence manifest、失败分类、Flaky 标记和诊断输入摘要。 |

## 6. 接口契约草案

统一前缀：`/api/v1/ui-e2e`

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| `GET` | `/health` | 匿名健康检查 | 返回 enablement、runner、artifact、allowlist、credential policy 摘要。 |
| `POST` | `/scenes` | `uiE2e:manage` | 创建 UI 场景。 |
| `GET` | `/scenes` | `uiE2e:read` | 按项目、应用、环境、状态分页查询场景。 |
| `GET` | `/scenes/{id}` | `uiE2e:read` | 查询场景详情、步骤和来源摘要。 |
| `PATCH` | `/scenes/{id}` | `uiE2e:manage` | 更新名称、标签、步骤、状态和来源摘要。 |
| `POST` | `/scenes/{id}/archive` | `uiE2e:manage` | 归档场景。 |
| `POST` | `/scenes/{id}/submit-review` | `uiE2e:review` | 提交场景或 bundle 评审。 |
| `POST` | `/bundles` | `uiE2e:manage` | 创建或刷新 bundle 摘要。 |
| `GET` | `/bundles` | `uiE2e:read` | 分页查询 bundle。 |
| `GET` | `/bundles/{id}` | `uiE2e:read` | 查询 bundle 详情与静态校验摘要。 |
| `POST` | `/bundles/{id}/approve` | `uiE2e:review` | 审批通过 bundle。 |
| `POST` | `/bundles/{id}/reject` | `uiE2e:review` | 驳回 bundle。 |
| `POST` | `/runs` | `uiE2e:execute` | 发起单次运行。 |
| `GET` | `/runs` | `uiE2e:read` | 查询运行列表。 |
| `GET` | `/runs/{id}` | `uiE2e:read` | 查询运行详情、步骤结果和 artifact 摘要。 |
| `POST` | `/runs/{id}/cancel` | `uiE2e:execute` | 取消运行。 |
| `GET` | `/runs/{id}/export` | `uiE2e:export` | 导出运行脱敏摘要。 |
| `GET` | `/runs/{id}/artifacts/{artifactId}/download` | `uiE2e:export` | 下载已落入受控存储的原始 artifact 文件。 |
| `POST` | `/flaky-marks` | `uiE2e:flaky` | 创建或更新 Flaky 标记。 |
| `GET` | `/flaky-marks` | `uiE2e:read` | 查询 Flaky 标记列表。 |

## 7. 关键请求体

### 创建场景

```json
{
  "projectId": "uuid",
  "applicationId": "uuid",
  "environmentId": "uuid",
  "code": "portal-role-admin-login",
  "name": "后台管理员登录并进入首页",
  "riskLevel": "HIGH",
  "tags": ["login", "rbac", "smoke"],
  "sourceSummary": {
    "pageRefs": ["page-uuid-1"],
    "flowRefs": ["flow-uuid-1"],
    "testCaseRefs": ["case-uuid-1"]
  },
  "steps": [
    {
      "stepType": "LOGIN",
      "actionSummary": {
        "usernameField": "data-testid=username",
        "passwordField": "data-testid=password",
        "submitAction": "click"
      },
      "locatorStrategy": {
        "preferred": "testId",
        "fallback": ["role", "text"]
      },
      "assertionSummary": {
        "successSignal": "url contains /dashboard"
      }
    }
  ]
}
```

### 创建运行

```json
{
  "projectId": "uuid",
  "sceneId": "uuid",
  "bundleId": "uuid",
  "environmentId": "uuid",
  "baseUrlRef": "env:portal.base-url",
  "accountLeaseRef": "uuid",
  "requestKey": "ui-e2e-login-smoke-001",
  "reason": "manual smoke"
}
```

响应只返回摘要：

```json
{
  "id": "uuid",
  "status": "QUEUED",
  "sceneSummary": {
    "sceneId": "uuid",
    "code": "portal-role-admin-login",
    "name": "后台管理员登录并进入首页"
  },
  "accountSummary": {
    "accountLeaseRef": "uuid",
    "status": "ACTIVE",
    "accountKey": "qa-admin-01",
    "displayName": "QA Admin 01",
    "roleTags": ["ADMIN"],
    "secretRefDigest": "sha256:..."
  }
}
```

## 8. Health API 返回要求

`GET /api/v1/ui-e2e/health` 至少返回：

1. `enabled`
2. `runnerEnabled`
3. `runnerMode`
4. `defaultTimeoutSeconds`
5. `maxTimeoutSeconds`
6. `maxConcurrency`
7. `maxArtifactCount`
8. `maxArtifactSizeBytes`
9. `allowlistEnabled`
10. `credentialPolicy`
11. `artifactPolicy`
12. `supportedNodeType=UI_TEST`

其中 `artifactPolicy` 至少包含 `captureScreenshotEnabled`、`captureVideoEnabled`、`captureHarEnabled`、`captureTraceEnabled`、`captureJunitXmlEnabled`、`maxArtifactCount`、`maxArtifactSizeBytes` 和 `rawArtifactDownloadReady`。

不得返回：

1. secretRef 原文
2. baseUrl 明文完整值
3. cookie、Authorization、token、密码
4. 外部对象存储密钥

## 8.1 Artifact 下载契约

`GET /api/v1/ui-e2e/runs/{id}/artifacts/{artifactId}/download` 返回 `application/octet-stream` 或推断出的具体媒体类型，并带 `Content-Disposition: attachment`。

约束：

1. 只有 manifest `storageRef` 已指向受控存储且文件存在时才返回 200。
2. `storageRef` 仍只作为 opaque ref 出现在详情与导出中，不回显真实宿主机路径。
3. 下载失败统一返回 `UI_E2E_ARTIFACT_DOWNLOAD_NOT_READY`，不把文件系统错误暴露给调用方。
4. 本地存储 adapter 必须阻断路径逃逸和超出 `max-artifact-size-bytes` 的文件落盘。

## 9. Runner Port 草案

建议新增应用层端口：

| Port | 方法 | 说明 |
|---|---|---|
| `UiE2eRunnerPort` | `validate(UiE2eRunCommand)` | 校验 baseUrl allowlist、scene/bundle 状态、artifact policy 和 account contract。 |
| `UiE2eRunnerPort` | `run(UiE2eRunCommand)` | 执行浏览器场景并返回 aggregate-only run result。 |
| `UiE2eRunnerPort` | `cancel(UiE2eRunCancelCommand)` | 尝试取消运行并返回稳定状态。 |

默认实现：

1. `DisabledUiE2eRunnerAdapter`：总是返回 `EXECUTION_RUNNER_NOT_READY` 或 `UI_E2E_RUNNER_DISABLED`。
2. `ManagedPreviewUiE2eRunnerAdapter`：本地受控预览，读取账号契约并输出脱敏注入计划摘要。
3. `PlaywrightSubprocessUiE2eRunnerAdapter`：本地受控真实浏览器执行，运行时完成凭据注入，并把 screenshot/trace/log 复制到受控 artifact 存储。
4. 首期不开放任意 shell、自定义浏览器启动参数和未批准网络出口。

## 10. WP8 凭据注入契约

WP7 只消费 WP8 允许的 runner contract 字段白名单：

| 允许字段 | 禁止字段 |
|---|---|
| `accountLeaseRef`、租借状态、过期时间、账号 `accountKey/displayName/status/roleTags/scopeSummary`、`secretRefDigest`、凭据策略布尔值 | 密码、token、cookie、`secret://` 原文、`secretRef` 原文、租借 token 明文、健康摘要原文 |

凭据注入要求：

1. 以 `accountLeaseRef` 作为唯一句柄。
2. 注入行为发生在 runner 执行进程或受控 adapter 内部。
3. 控制面 API、审计、日志和前端 DOM 均不得返回明文凭据。
4. 任何 SecretProvider 失败只返回错误码和 digest，不返回底层 provider 原文报错。
5. 当前 `ManagedPreviewUiE2eRunnerAdapter` 已在 runner 内部把 WP8 解析结果收敛成受控注入计划，支持 `accountKey + password` 和 `wp7-login-form-v1` 结构化 payload 两类格式；对控制面仅返回 `credentialPlanReady/type/format/schema/principalSource/componentCount` 等脱敏摘要。

## 11. WP9 集成契约

WP9 `UI_TEST` 节点建议输入：

1. `sceneRef`
2. `bundleRef`
3. `environmentId`
4. `baseUrlRef`
5. `accountLease.accountPoolRef/applicationId/environmentId/roleTags/ttlSeconds/requestKey`

WP7 返回给 WP9 的字段白名单：

| 允许字段 | 禁止字段 |
|---|---|
| `runId`、`status`、`failureCode`、`failureBucket`、`startedAt`、`finishedAt`、`traceId`、步骤状态计数、artifact refs、`accountLeaseRef`、`secretRefDigest`、Flaky 标记、aggregate summary | screenshot/video/trace 原文、stdout/stderr 原文、password、token、cookie、完整 DOM、完整源码、原始请求/响应正文 |

未就绪阶段，WP9 dry-run 和 scheduler 仍应返回 `EXECUTION_RUNNER_NOT_READY`。

## 12. WP10 证据契约

WP10 只读取：

1. 运行状态
2. 失败分类
3. 步骤计数和步骤级摘要
4. artifact manifest
5. Flaky 标记
6. account lease 摘要引用

不得读取：

1. 原始 screenshot/video/trace 文件
2. DOM 原文
3. 账号凭据
4. Playwright 完整源码

## 13. 审计事件草案

| 事件 | 资源 |
|---|---|
| `ui_e2e.scene.created` | `UI_E2E_SCENE` |
| `ui_e2e.scene.updated` | `UI_E2E_SCENE` |
| `ui_e2e.scene.archived` | `UI_E2E_SCENE` |
| `ui_e2e.bundle.created` | `UI_E2E_BUNDLE` |
| `ui_e2e.bundle.reviewed` | `UI_E2E_BUNDLE` |
| `ui_e2e.run.created` | `UI_E2E_RUN` |
| `ui_e2e.run.started` | `UI_E2E_RUN` |
| `ui_e2e.run.completed` | `UI_E2E_RUN` |
| `ui_e2e.run.canceled` | `UI_E2E_RUN` |
| `ui_e2e.run.exported` | `UI_E2E_RUN` |
| `ui_e2e.flaky.marked` | `UI_E2E_FLAKY_MARK` |

## 14. 错误码草案

| 错误码 | 场景 |
|---|---|
| `UI_E2E_SCENE_NOT_READY` | 场景未审批或状态非法。 |
| `UI_E2E_BUNDLE_NOT_READY` | bundle 未审批或静态校验失败。 |
| `UI_E2E_RUNNER_DISABLED` | runner 开关关闭。 |
| `EXECUTION_RUNNER_NOT_READY` | WP7 runner 尚未进入可执行状态。 |
| `UI_E2E_ARTIFACT_DOWNLOAD_NOT_READY` | artifact 尚未落入受控存储、文件不存在或下载策略未就绪。 |
| `UI_E2E_ACCOUNT_LEASE_INVALID` | `accountLeaseRef` 不存在、越权或状态不允许。 |
| `UI_E2E_BASE_URL_NOT_ALLOWED` | baseUrl 不在 allowlist。 |
| `UI_E2E_ARTIFACT_POLICY_BLOCKED` | artifact 采集或导出命中安全阻断。 |
| `UI_E2E_STATIC_CHECK_FAILED` | 脚本包静态校验未通过。 |
| `UI_E2E_RUN_NOT_CANCELABLE` | 终态运行不能取消。 |
| `UI_E2E_EXPORT_DISABLED` | 导出被配置关闭。 |
| `UI_E2E_RESOURCE_SCOPE_DENIED` | 跨项目或无权限资源引用。 |

## 15. 安全与兼容

1. 只允许显式白名单 baseUrl，禁止任意外网访问。
2. Playwright bundle 仅允许受控依赖集合，禁止 `child_process`、未受控 `fetch` 和危险 import。
3. artifact manifest、导出结果和前端 DOM 必须执行禁止字段扫描。
4. 所有错误展示都包含 `traceId`，但不得回显底层 provider 或凭据原文。
5. 兼容当前单服务、camelCase、统一响应 envelope 和现有 RBAC 模式。

## 16. 当前实现切片建议

建议按以下顺序落地：

1. M1：权限点、DB schema、health API、domain skeleton。
2. M2：scene CRUD、step template、source summary 绑定。
3. M3：bundle 摘要、静态校验、评审流。
4. M4：run API、`UiE2eRunnerPort`、WP8 adapter、凭据注入 adapter、Playwright 子进程 runner。
5. M5：artifact manifest、受控 artifact 下载、failure classifier、Flaky API。
6. M6：WP9/WP10 契约联调和前端闭环。
