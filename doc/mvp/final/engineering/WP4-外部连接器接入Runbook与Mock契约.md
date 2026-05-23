# WP4 外部连接器接入 Runbook 与 Mock 契约

| 项目 | 内容 |
|---|---|
| 覆盖任务 | WP4-D1 Confluence、WP4-D2 飞书、WP4-D3 钉钉、WP4-D4 语雀真实连接器的共同前置 |
| 当前状态 | 连接器类型、配置入口和健康检查已预留；真实外部 API 拉取仍待沙箱凭证和厂商 API 策略 |
| 适用范围 | Confluence、飞书文档、钉钉文档、语雀文档的只读同步接入设计、mock、联调和准出 |
| 准出清单 | 参考 `doc/mvp/final/engineering/WP1-WP4-统一发布准出清单.md`，真实接入后必须追加对应连接器 smoke |
| 日期 | 2026-05-23 |

## 1. 目标与边界

本 runbook 的目标是先冻结 WP4-D 真实连接器的公共契约，让后续拿到外部平台沙箱凭证后可以按同一模型接入，而不破坏当前 WP4 MVP 的文本、Markdown、Word、PDF、OCR 和 CUSTOM_API 链路。

本轮只完成共同前置，不宣称 D1-D4 真实连接器完成：

1. 定义四类协作文档连接器的最小配置 schema、secretRef 口径和资源标识。
2. 定义连接测试、分页拉取、版本游标、错误码、同步任务状态和 traceId 的 mock 契约。
3. 冻结安全、审计、权限继承、删除/归档、重试、限流和验收口径。
4. 明确无沙箱凭证时保持 `RESERVED/PLANNED` 状态，不触发真实外部请求。

非目标：

1. 不实现 OAuth、厂商 SDK、真实 API endpoint、评论同步、双向回写或机器人通知。
2. 不绕过 WP4 候选确认、WP2 模型策略、WP3 资产写入服务和 WP1 审计。
3. 不在库表、响应、日志、审计或前端状态中保存第三方密钥明文。

## 2. 当前实现约束

当前 WP4 已经为 `CONFLUENCE`、`FEISHU`、`DINGTALK`、`YUQUE` 保留 source type。健康检查对未启用真实同步的类型返回不可用说明，前端展示为预留类型。

真实连接器启用前必须满足：

1. 有对应平台的沙箱空间、测试文档、只读凭证和 API 调用额度。
2. 确认厂商 API 的鉴权方式、分页模型、版本字段、限流响应和删除/归档语义。
3. 在 dev/test 环境完成 mock 契约测试，再在 preprod 用真实沙箱做 smoke。
4. 将开关默认设为关闭，生产只对指定 source 或项目灰度启用。

## 3. 统一配置模型

所有外部连接器 source 都沿用 WP4 输入源模型，只在 `sourceType` 与 `config` 中区分平台。建议的公共字段如下：

| 字段 | 必填 | 说明 |
|---|---|---|
| `sourceType` | 是 | `CONFLUENCE`、`FEISHU`、`DINGTALK`、`YUQUE` |
| `sourceCode` | 是 | WP4 内部 source code，参与 webhook 或同步任务定位 |
| `projectId` | 是 | 写入候选和 WP3 资产的项目上下文 |
| `applicationId` | 否 | 可选应用归属 |
| `secretRef` | 是 | WP1 SecretProvider 引用，仅保存引用或 digest，不保存密钥明文 |
| `configVersion` | 是 | 连接器配置 schema 版本，首版建议 `v1` |
| `externalWorkspaceId` | 否 | 外部站点、租户、团队、知识库或组织标识 |
| `resourceRef` | 是 | 根页面、文档、知识库或目录的内部稳定引用 |
| `syncMode` | 是 | `MANUAL`、`SCHEDULED`、`WEBHOOK_HINTED`，首版建议只启用 `MANUAL` |
| `cursor` | 否 | 上次成功同步的分页或版本游标 |
| `permissionMode` | 是 | `SNAPSHOT`、`INHERIT_REFERENCE`、`IGNORE_EXTERNAL` |
| `deletePolicy` | 是 | `ARCHIVE_CANDIDATE`、`MARK_DELETED`、`IGNORE` |
| `contentMode` | 是 | `PLAIN_TEXT`、`MARKDOWN`、`HTML_TO_MARKDOWN` |

响应和审计只允许暴露 `secretRefConfigured=true/false`、`secretRefDigest`、`provider`、`version`、`scope` 等脱敏信息。

## 4. 平台配置建议

以下字段是 Veri Agent 内部 schema 建议，不替代厂商最新 API 文档。真实接入时以厂商当前文档和沙箱返回为准，但不得改变 WP4 对外响应中的稳定字段名。

| 类型 | 最小配置 | 资源标识 | 版本和分页 | 权限与删除语义 |
|---|---|---|---|---|
| `CONFLUENCE` | `siteBaseUrl`、`spaceKey`、`rootPageId/pageId`、`contentFormat` | 优先使用 page id；空间级同步必须限制 root page | 保存 `versionNumber`、`updatedAt`、`nextCursor` | 权限首版做快照摘要；删除或归档映射为 WP4 候选归档 |
| `FEISHU` | `tenantKey`、`wikiToken/docToken/nodeToken`、`locale`、`contentFormat` | 优先使用文档 token 或 wiki node token | 保存 `revision`、`updatedAt`、`pageToken` | 外部权限只保存摘要，不复制成员；删除映射为归档 |
| `DINGTALK` | `corpRef`、`workspaceId`、`docId/nodeId`、`contentFormat` | 优先使用文档或节点 id | 保存 `revision`、`updatedAt`、`nextToken` | 仅记录可读范围摘要；无权限或已删除返回稳定错误 |
| `YUQUE` | `namespace/bookSlug`、`docSlug/docId`、`contentFormat` | 优先使用 repo/book + doc slug 或 doc id | 保存 `revision/updatedAt`、`offset/page` | 私有知识库必须走 secretRef；删除或移入回收站映射为归档 |

共同约束：

1. URL 只作为可点击来源展示，不作为幂等主键；幂等主键使用厂商稳定 ID。
2. 内容转换统一输出 `plainText` 或 `markdown`，再进入 WP4 现有解析、候选确认和发布链路。
3. 版本字段缺失时必须使用 `contentHash` 作为降级版本，不得每次同步重复生成候选。
4. 真实 API 响应中的用户、权限、附件、评论等扩展字段先进入 `rawMetadataDigest` 或脱敏摘要，不进入候选正文。

## 5. Mock 契约

真实连接器落地前，后端、前端和测试可以使用 mock provider 固定契约。mock 不访问外部网络，只模拟连接测试、分页拉取、错误和同步状态。

### 5.1 连接测试请求

```json
{
  "sourceId": 1001,
  "sourceType": "CONFLUENCE",
  "projectId": "project-001",
  "traceId": "wp4-d-mock-connect-001",
  "secretRef": "secret://wp4/confluence/sandbox",
  "config": {
    "configVersion": "v1",
    "resourceRef": "space:QA,page:12345",
    "contentMode": "MARKDOWN"
  }
}
```

### 5.2 连接测试响应

```json
{
  "status": "UP",
  "ready": true,
  "dataFlowSupported": true,
  "sourceType": "CONFLUENCE",
  "externalIdentity": "sandbox-space",
  "capabilities": ["CONNECT", "PULL_DOCUMENT", "PAGINATION", "VERSION_CURSOR"],
  "secretRefConfigured": true,
  "secretRefDigest": "sha256:***",
  "message": "connector mock ready",
  "traceId": "wp4-d-mock-connect-001"
}
```

无真实凭证或 feature flag 未开启时必须返回：

```json
{
  "status": "RESERVED",
  "ready": false,
  "dataFlowSupported": false,
  "sourceType": "CONFLUENCE",
  "message": "CONFLUENCE connector is reserved for later enablement",
  "traceId": "wp4-d-reserved-001"
}
```

### 5.3 拉取请求

```json
{
  "sourceId": 1001,
  "sourceType": "CONFLUENCE",
  "syncJobId": "sync-20260523-0001",
  "traceId": "wp4-d-mock-pull-001",
  "cursor": "cursor-from-last-success",
  "limit": 50,
  "dryRun": true
}
```

### 5.4 拉取响应

```json
{
  "items": [
    {
      "externalId": "page-12345",
      "externalVersion": "42",
      "title": "登录审计需求",
      "sourceUrl": "https://docs.example.test/page-12345",
      "contentMode": "MARKDOWN",
      "content": "## 登录审计需求\n用户登录失败需要记录审计。",
      "contentHash": "sha256:content-digest",
      "updatedAt": "2026-05-23T10:00:00Z",
      "authorDigest": "sha256:author-digest",
      "permissionSnapshotDigest": "sha256:permission-digest",
      "archived": false,
      "deleted": false,
      "rawMetadataDigest": "sha256:metadata-digest"
    }
  ],
  "nextCursor": "cursor-next-page",
  "hasMore": true,
  "traceId": "wp4-d-mock-pull-001"
}
```

`content` 进入 WP4 解析链路前必须经过大小限制、敏感信息检查和错误摘要脱敏；`sourceUrl`、`externalId`、`externalVersion`、`contentHash` 用于幂等更新和 WP3 来源追踪。

## 6. 同步任务状态

真实连接器应复用 WP4 导入与候选状态，不新增绕过候选确认的写入路径。建议同步任务状态如下：

| 状态 | 含义 | 可重试 |
|---|---|---|
| `PLANNED` | 类型、schema 或 source 已预留，但未启用真实同步 | 否 |
| `RESERVED` | 当前环境未配置凭证或 feature flag 未开启 | 否 |
| `READY` | 连接测试通过，等待人工或计划同步 | 是 |
| `SYNCING` | 正在分页拉取外部文档 | 否 |
| `SUCCEEDED` | 本次分页或全量同步完成 | 是 |
| `FAILED` | 非限流类失败，保留 `lastError` 和 `traceId` | 是 |
| `RETRYING` | 已进入指数退避或计划重试 | 否 |
| `RATE_LIMITED` | 外部平台返回限流或额度不足 | 是 |
| `DISABLED` | source 停用或 connector feature flag 关闭 | 否 |

同步任务最小字段：

| 字段 | 说明 |
|---|---|
| `syncJobId` | WP4 内部任务 ID |
| `sourceId/sourceType` | 输入源定位 |
| `status` | 上表状态 |
| `attempt` | 当前尝试次数 |
| `cursorBefore/cursorAfter` | 同步游标，失败时保留进入前游标 |
| `pulledCount/changedCount/skippedCount/errorCount` | 本次处理数量 |
| `lastError.code/message` | 脱敏错误摘要 |
| `nextRetryAt` | 下次重试时间 |
| `traceId` | 贯穿连接器、导入、候选和审计 |

重试策略：

1. 认证失败、权限不足、配置错误不自动重试，进入 `FAILED`。
2. 429、5xx、网络超时可按指数退避重试，需设置最大次数和最大间隔。
3. 每页拉取成功后才能推进 `cursorAfter`；半页失败不得覆盖 last success cursor。
4. 同一 `externalId + externalVersion/contentHash` 必须幂等，重复同步只更新已有候选或跳过。

## 7. 错误码和脱敏

| 错误码 | 场景 | 处理 |
|---|---|---|
| `CONNECTOR_RESERVED` | 类型未启用真实同步 | 前端展示预留说明，不显示同步按钮 |
| `CONNECTOR_SECRET_UNRESOLVED` | `secretRef` 缺失、撤销、过期或用途不匹配 | 提示管理员检查 SecretProvider，不回显 secretRef 明文 |
| `CONNECTOR_AUTH_FAILED` | 外部平台认证失败 | 停止自动重试，记录脱敏 provider/status |
| `CONNECTOR_FORBIDDEN` | 凭证无文档读取权限 | 停止自动重试，提示检查外部授权 |
| `CONNECTOR_NOT_FOUND` | 资源 ID、页面或知识库不存在 | 标记配置错误或资源已删除 |
| `CONNECTOR_RATE_LIMITED` | 外部平台限流 | 进入 `RATE_LIMITED` 并设置 `nextRetryAt` |
| `CONNECTOR_TIMEOUT` | 外部 API 超时 | 可重试，保留 traceId |
| `CONNECTOR_PAYLOAD_TOO_LARGE` | 单文档或响应超过上限 | 跳过该文档或失败，提示拆分文档 |
| `CONNECTOR_UNSUPPORTED_CONTENT` | 内容格式无法转换 | 失败并保留外部版本摘要 |
| `CONNECTOR_VERSION_CONFLICT` | 游标、版本或幂等冲突 | 停止覆盖，进入人工排查 |

错误响应不得包含 access token、refresh token、cookie、完整 secretRef、完整外部响应体、内部 endpoint、原始异常堆栈或用户个人敏感信息。

## 8. 权限、删除和归档语义

首版真实连接器只做只读导入，不做外部权限到 WP1 角色的自动映射。

| 语义 | 首版处理 |
|---|---|
| 外部文档可读权限 | 只保存脱敏快照摘要，用于排查来源，不授予 WP1 权限 |
| 外部文档不可读 | 返回 `CONNECTOR_FORBIDDEN`，不保留原文 |
| 外部文档删除 | 根据 `deletePolicy` 生成归档候选或标记来源失效，不直接删除 WP3 资产 |
| 外部文档归档 | 生成状态变化候选，仍需人工确认后同步到 WP3 |
| 外部评论、批注、附件 | 首版不进入正文；可保留 metadata digest 供后续专项 |

WP4 发布仍必须走候选确认和 WP3 应用服务。任何连接器不得直接更新 WP3 表、不得自动覆盖非 DRAFT 的人工资产。

## 9. 安全与审计

1. 第三方凭证只允许通过 WP1 SecretProvider 解析，`secretRef` 用途建议为 `DOCUMENT_CONNECTOR_READ`，作用域绑定 `CONFIG + document_input_source.id`。
2. 日志、指标、审计、响应和前端状态只记录 digest、provider、状态、用途、sourceId、sourceType 和 traceId。
3. 外部 API 原始响应必须做字段白名单提取，未知字段只允许以 digest 或脱敏摘要保存。
4. 所有外部请求必须设置 connect/read timeout、最大响应大小、最大页大小和 User-Agent。
5. 生产环境启用前必须确认 egress 域名、TLS、代理、限流和告警。
6. 每次连接测试、同步开始、同步成功、同步失败、人工重试和配置变更都应写 WP1 审计。

## 10. 前端验收口径

预留阶段：

1. Confluence、飞书、钉钉、语雀展示为“类型已预留，待后续启用”。
2. 不展示真实同步按钮，或按钮禁用并给出不可用原因。
3. 健康检查展示 `ready=false`、`dataFlowSupported=false`、message 和 traceId。

真实接入阶段：

1. 配置表单只接收资源标识和 `secretRef`，不接收密钥明文。
2. 连接测试有 loading、success、reserved、failed、rate limited 状态。
3. 同步任务列表展示 status、lastSyncAt、lastError、nextRetryAt、traceId。
4. 失败错误必须可读，并引导检查 secretRef、权限、资源标识、限流或厂商状态。
5. 未授权用户不能看到配置、连接测试或同步按钮。

## 11. 分阶段准出

### 11.1 Mock 准出

1. mock provider 覆盖四类 sourceType 的连接测试成功、预留、认证失败、限流和分页拉取。
2. `externalId + externalVersion/contentHash` 幂等测试通过。
3. 前端禁用预留连接器真实同步入口，并展示 traceId 和不可用原因。
4. 默认验证入口通过：

```bash
mvn -B -pl platform-api test
cd portal-web && npm test
cd portal-web && npm run build
```

### 11.2 真实沙箱准出

每个平台独立准出，不允许用一个平台的 smoke 替代另一个平台：

1. 沙箱 source 创建成功，`secretRef` 由 WP1 SecretProvider 解析，响应不泄露明文。
2. 连接测试返回 `UP`，错误路径覆盖认证失败、无权限、资源不存在和限流。
3. 至少一篇真实文档进入 WP4 导入记录，生成候选，人工确认后 dryRun 能看到 WP3 写入动作。
4. 重复同步同一版本不重复创建候选；文档更新后进入 UPDATE 或新版本候选。
5. 删除或归档语义按 `deletePolicy` 生成可审查结果。
6. 对应 connector smoke、`mvn -B -pl platform-api test`、`cd portal-web && npm test`、`cd portal-web && npm run build` 均通过。

## 12. 推荐落地顺序

1. 先落 mock provider 和统一 connector port，保持真实实现默认关闭。
2. 优先接入 API 面较简单、沙箱可控的平台，例如 Confluence 或语雀。
3. 再接飞书和钉钉，重点验证开放平台应用权限、文档 token、租户边界和限流。
4. 每接入一个真实平台，都新增独立 smoke 脚本和 release notes，不把 D1-D4 一次性混合准出。

## 13. 回滚

真实连接器出现故障时的回滚顺序：

1. 关闭对应 source 的同步开关或停用 source。
2. 关闭对应环境的 connector feature flag，让健康检查回到 `RESERVED`。
3. 保留已生成导入记录、候选、审计和 traceId，不删除历史证据。
4. 若错误候选尚未发布，批量忽略或标记为需人工复核。
5. 若已经发布到 WP3，按 WP3 资产版本和状态流回滚，不直接删除 WP3 表数据。
