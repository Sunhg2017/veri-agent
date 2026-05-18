# WP1 P0 API 契约

| 项目 | 内容 |
|---|---|
| 工作包 | WP1 平台基础底座 |
| 面向阶段 | MVP P0 |
| 文档用途 | 前后端接口评审、后端研发拆解、接口自动化与权限矩阵测试输入 |
| API 前缀 | `/api/v1` |
| 适用端 | Web 管理后台、同一 `platform-api` 内 WP 模块调用、后续外部集成 |
| 冻结范围 | 认证/初始化、部门、用户、角色授权、项目、应用、环境、配置、上下文、审计 |

> 单平台修订：根据 2026-05-17 产品决策，WP1 当前只保留“单平台、多部门、多项目、多应用、多环境”控制面。不再提供平台实例分层、平台实例管理员、平台实例切换或跨实例隔离 API。当前代码已落地 `/api/v1/management/**` P0 管理视图接口，后续 API 继续围绕部门、项目、应用、环境和角色作用域收敛。

> 当前实现基线：API JSON 字段统一使用 camelCase；分页统一使用 `index`、`size`；WP1/WP2/WP3 属于同一个 `platform-api` Java 服务内的领域模块，模块间通过 Spring 应用服务复用能力，不通过 HTTP 回调本服务。

## 1. 契约原则

1. WP1 是平台控制面底座，所有后续 WP 获取部门、项目、应用、环境、配置、权限和审计能力时，必须通过 WP1 API，不直接读写 WP1 数据库表。
2. 所有业务 API 默认从认证 Token 解析 `user_id`、角色和作用域，客户端不得通过请求体或查询参数覆盖授权上下文。
3. 所有写操作必须由服务端记录审计。前端可以展示审计提示，但不能决定是否写审计。
4. P0 权限以后端校验为准，前端菜单和按钮只作为体验优化。
5. 敏感值不得在接口响应、审计、导出、应用日志中明文出现。
6. 本文为等价 API 契约，研发实现 OpenAPI 时字段类型、枚举、示例和错误响应需与本文保持一致。

## 2. 通用协议

### 2.1 请求头

| 请求头 | 必填 | 说明 |
|---|---|---|
| `Authorization: Bearer <token>` | 除登录、初始化外必填 | 用户访问令牌或服务令牌。 |
| `Content-Type: application/json` | 写请求必填 | 请求体格式。 |
| `X-Trace-Id` | 可选 | 调用方传入链路 ID；未传时服务端生成并在响应返回。 |
| `Idempotency-Key` | 创建类接口推荐 | 幂等键，最长 128 字符，同一用户、同一路径下唯一。 |
| `X-Delegated-User-Id` | 内部 API 必填 | 模块或外部集成调用时的用户委托身份。 |
| `X-Caller-Service` | 内部 API 必填 | 调用方标识，如 `asset`、`model-access`、`wp9-execution-service`。 |

### 2.2 统一响应格式

```json
{
  "code": "OK",
  "message": "success",
  "traceId": "trc_202605160001",
  "data": {}
}
```

错误响应：

```json
{
  "code": "VALIDATION_ERROR",
  "message": "请求字段校验失败",
  "traceId": "trc_202605160001",
  "data": {
    "fieldErrors": [
      {
        "field": "code",
        "reason": "编码只能包含字母、数字、下划线和中划线"
      }
    ]
  }
}
```

### 2.3 分页格式

列表请求统一参数：

| 参数 | 说明 |
|---|---|
| `index` | 页码索引，从 0 开始，默认 0。 |
| `size` | 每页条数，默认 20，最大 100。 |
| `sort` | 排序字段，默认由接口定义；不允许传未白名单字段。 |
| `order` | `asc` 或 `desc`，默认 `desc`。 |
| `keyword` | 关键词，按接口定义匹配名称、编码或账号。 |

分页响应：

```json
{
  "code": "OK",
  "message": "success",
  "traceId": "trc_202605160001",
  "data": {
    "items": [],
    "index": 0,
    "size": 20,
    "total": 0
  }
}
```

### 2.4 错误码口径

| 错误码 | HTTP | 说明 | 审计要求 |
|---|---:|---|---|
| `OK` | 200/201 | 成功。 | 按接口审计事件记录。 |
| `BAD_REQUEST` | 400 | JSON 结构非法、参数类型错误。 | 写接口可记录失败审计。 |
| `VALIDATION_ERROR` | 400 | 必填、格式、长度、枚举、业务字段校验失败。 | 写接口记录 `FAILED`。 |
| `UNAUTHORIZED` | 401 | 未登录、Token 无效、会话过期、刷新令牌无效。 | 登录失败、会话失效需审计。 |
| `FORBIDDEN` | 403 | 权限不足或越权访问。 | 必须记录 `ACCESS_DENIED`。 |
| `NOT_FOUND` | 404 | 资源不存在或对当前用户不可见。 | 枚举探测场景可记录 `ACCESS_DENIED`。 |
| `CONFLICT` | 409 | 唯一性冲突、重复绑定、幂等键冲突。 | 写接口记录 `FAILED`。 |
| `INVALID_STATE` | 409 | 当前状态不允许该操作。 | 写接口记录 `FAILED`。 |
| `SECRET_REQUIRED` | 400 | 敏感变量缺少明文输入或密钥引用。 | 记录失败审计且脱敏请求。 |
| `SECRET_POLICY_VIOLATION` | 400 | 敏感字段试图明文返回、明文导出或以普通变量保存。 | 记录失败审计。 |
| `SECRET_PROVIDER_ERROR` | 502 | 密钥服务不可用、加密失败、密钥引用无效。 | 记录失败审计，主业务回滚。 |
| `AUDIT_WRITE_PENDING` | 202 | 内部审计写入进入补偿队列。 | 仅内部审计 API 使用。 |
| `INTERNAL_ERROR` | 500 | 系统异常。 | 写接口进入审计补偿或错误日志。 |

### 2.5 幂等规则

1. 创建类接口支持 `Idempotency-Key`。同一操作者、同一路径、同一幂等键重复提交时，若请求摘要一致，返回首次成功结果；若请求摘要不同，返回 `CONFLICT`。
2. 幂等记录建议保留 24 小时。幂等返回仍需携带当前 `traceId`，并在 `data.idempotentReplayed=true` 标识重放。
3. 未提供幂等键时，通过唯一索引防重复，例如部门编码、项目编码、应用编码、环境编码。
4. `PUT` 覆盖语义接口天然幂等；`PATCH` 状态流接口重复设置到同一状态可返回 `OK`，但需在审计中标记 `no_change=true`。
5. 批量接口任一项失败时整体失败，不做部分成功；确需部分成功的能力不进入 P0。

### 2.6 状态流规则

| 对象 | 状态 | 允许流转 | P0 限制 |
|---|---|---|---|
| 用户 | `PENDING_ACTIVATION`、`ENABLED`、`DISABLED`、`LOCKED` | 未激活可启用/停用；启用可停用/锁定；锁定可启用/停用；停用可启用 | 非启用用户不可登录，停用/锁定需撤销会话并递增 `auth_version`。 |
| 项目 | `PREPARING`、`ACTIVE`、`ARCHIVED`、`DISABLED` | 筹备中可进行中/停用；进行中可归档/停用；归档可进行中/停用；停用可筹备中/进行中 | 归档和停用项目只读，不可新增成员、应用、环境。 |
| 应用 | `ENABLED`、`DISABLED` | 启用与停用互转 | 停用后不可新增应用专属环境，不可作为新资产或执行目标。 |
| 环境 | `ENABLED`、`DISABLED` | 启用与停用互转 | 停用环境不可被后续执行选择。 |
| 角色 | `ENABLED`、`DISABLED` | 启用与停用互转 | 内置角色不可停用；停用角色不参与权限计算。 |

### 2.7 平台上下文规则

1. 用户 Token 必须包含 `user_id`、用户名、显示名称、角色、账号状态和有效期。
2. 平台级操作使用 `scope_type=PLATFORM`，部门、项目、应用、环境操作按资源作用域授权。
3. 部门、项目、应用、环境等业务对象不包含平台实例隔离字段，服务端写入时只注入操作者和资源归属关系。
4. 查询列表必须应用授权范围过滤，P0 本地样板先以登录态保护和管理 API 聚合视图落地。
5. 越权访问默认返回 `FORBIDDEN`，并记录 `ACCESS_DENIED` 或等价审计事件。
6. 同服务内 WP 模块通过 Spring 应用服务复用 WP1 能力；外部集成调用内部 API 时必须使用服务令牌，并携带 `X-Delegated-User-Id`。WP1 需同时校验调用方身份、委托用户状态和目标资源作用域。

## 3. 敏感字段与 SecretProvider 策略

### 3.1 敏感字段返回策略

| 字段类型 | 示例 | 入库策略 | 响应策略 | 审计策略 |
|---|---|---|---|---|
| 密码/Token/Cookie/API Key | `password`、`api_token`、`cookie` | 不明文入库；密码只保存哈希；密钥通过 SecretProvider | 不返回明文；只返回是否已配置、掩码或引用摘要 | 不记录明文，只记录是否变更。 |
| 个人联系方式 | `email`、`mobile` | 可按业务字段保存 | 管理视图可返回；审计、导出按规则脱敏 | 邮箱、手机号脱敏。 |
| 仓库地址/内部 URL | `repoUrl`、`webUrl`、`apiBaseUrl` | 可明文保存 | 按权限返回 | 审计可记录，URL 中疑似凭证参数必须脱敏。 |
| 普通变量 | `BASE_URL`、`PROJECT_CODE` | `value_kind=PLAIN` 时可明文保存 | 按权限返回明文 | 可记录摘要。 |
| 敏感变量 | `ADMIN_PASSWORD`、`API_TOKEN`、`COOKIE_SECRET` | `valueKind=SECRET` 或 `SECRET_REF`，不得明文保存 | 仅返回 `maskedValue`、`secretRefSummary`、`secretProvider`、`updatedAt` | 只记录引用变化、掩码、`changed=true`。 |

### 3.2 SecretProvider 引用字段策略

1. 环境变量 `value_kind` 固定为 `PLAIN`、`SECRET`、`SECRET_REF`。
2. `SECRET` 表示本次请求携带 `secret_value` 明文，由 WP1 调用 `SecretProvider` 保存后只落库 `secret_ref`、`masked_value`、`secret_provider`、`secret_version`。
3. `SECRET_REF` 表示调用方提交已有引用，WP1 只校验引用格式和资源作用域，不读取明文。
4. `secretRef` 格式：`secret://local/{scopeType}/{scopeId}/{secretId}`。未来迁移 Vault/KMS 时保持 `secretRef` 抽象不变。
5. 响应中禁止返回 `secretValue`。返回字段统一为 `secretRefSummary`，例如 `secret://local/ENVIRONMENT/{envId}/***`。
6. 审计数据库列 `before_json`、`after_json`、`diff_json` 中敏感字段只允许出现 `changed`、`maskedValue`、`secretRefSummary`、`secretProvider`、`secretVersion`。
7. 如果请求将疑似敏感变量以 `PLAIN` 保存，服务端返回 `SECRET_POLICY_VIOLATION`。

## 4. 权限点口径

P0 权限点使用 `{resource}:{action}`：

| 资源 | 权限点 |
|---|---|
| department | `department:read`、`department:create`、`department:edit`、`department:enable`、`department:disable`、`department:member_manage` |
| user | `user:read`、`user:create`、`user:edit`、`user:enable`、`user:disable`、`user:lock`、`user:assign_role` |
| role | `role:read`、`role:create`、`role:edit`、`role:bind`、`role:unbind` |
| project | `project:read`、`project:create`、`project:edit`、`project:archive`、`project:disable`、`project:member_manage` |
| application | `application:read`、`application:create`、`application:edit`、`application:disable` |
| environment | `environment:read`、`environment:create`、`environment:edit`、`environment:disable`、`environment:use` |
| config | `config:read`、`config:edit` |
| audit | `audit:read`、`audit:export`、`audit:write_internal` |
| secret | `secret:reference` |
| context | `context:read`、`context:switch`、`context:effective_read` |

## 5. P0 API 清单

### 5.0 当前代码已落地接口

当前 WP1 P0 样板已在 `platform-api` 中落地以下接口，均使用统一响应 envelope。除初始化、登录和健康检查外，管理接口必须携带 `Authorization: Bearer <token>`。

| 接口 | 状态 | 用途 | 请求字段 | 响应摘要 |
|---|---|---|---|---|
| `GET /api/v1/health` | 已实现 | 后端健康检查 | 无 | `status`、`timestamp`、`service` |
| `POST /api/v1/bootstrap/super-admin` | 已实现 | 首次初始化 `SuperAdmin` | `bootstrapToken`、`username`、`password`、`displayName`、`email` | `userId`、`role`、`mustChangePassword` |
| `POST /api/v1/auth/login` | 已实现 | 用户登录 | `username`、`password` | `accessToken`、`tokenType`、`expiresAt`、用户摘要、角色 |
| `GET /api/v1/auth/me` | 已实现 | 获取当前用户 | Bearer Token | 用户摘要、角色、账号状态 |
| `GET /api/v1/management/departments` | 已实现 | 查询部门管理视图 | Bearer Token | `name`、`parent`、`lead`、`members`、`status` |
| `POST /api/v1/management/departments` | 已实现 | 新增部门样板数据并写审计 | `name` | 新部门视图 |
| `GET /api/v1/management/departments/{key}` | 已实现 | 查询部门详情视图 | Bearer Token | `name`、`parent`、`lead`、`members`、`status` |
| `PATCH /api/v1/management/departments/{key}` | 已实现 | 编辑部门名称并写审计 | `name` | 更新后部门视图 |
| `PATCH /api/v1/management/departments/{key}/status` | 已实现 | 启用/停用部门并写审计 | `status=ENABLED/DISABLED` | 更新后部门视图 |
| `GET /api/v1/management/users` | 已实现 | 查询用户与权限管理视图 | Bearer Token | `username`、`displayName`、`email`、`role`、`department`、`status`、`lastSeen` |
| `GET /api/v1/management/users/{username}` | 已实现 | 查询用户详情视图 | Bearer Token | `username`、`displayName`、`email`、`role`、`department`、`status`、`lastSeen` |
| `PATCH /api/v1/management/users/{username}` | 已实现 | 编辑用户显示名称和邮箱并写审计 | `displayName`、`email` | 更新后用户视图 |
| `POST /api/v1/management/users` | 已实现 | 邀请用户样板数据并写审计 | `name` | 新用户视图 |
| `POST /api/v1/management/users/{username}/enable` | 已实现 | 启用用户并写审计 | Bearer Token | 用户视图 |
| `POST /api/v1/management/users/{username}/disable` | 已实现 | 停用用户、提升 `auth_version` 并写审计 | Bearer Token | 用户视图 |
| `POST /api/v1/management/users/{username}/lock` | 已实现 | 锁定用户、提升 `auth_version` 并写审计 | Bearer Token | 用户视图 |
| `POST /api/v1/management/users/{username}/unlock` | 已实现 | 解锁用户、提升 `auth_version` 并写审计 | Bearer Token | 用户视图 |
| `POST /api/v1/management/users/{username}/reset-password` | 已实现 | 重置密码、要求下次改密、提升 `authVersion` 并写审计 | `newPassword` | 用户视图 |
| `GET /api/v1/management/projects` | 已实现 | 查询项目空间管理视图 | Bearer Token | `name`、`department`、`owner`、`apps`、`status` |
| `POST /api/v1/management/projects` | 已实现 | 创建项目并写审计 | `code`、`name`、`sensitivityLevel`、`allowPublicModel` | 新项目视图 |
| `GET /api/v1/management/projects/{key}` | 已实现 | 查询项目详情视图 | 项目 `code`、`name` 或 `id` | 项目视图 |
| `PATCH /api/v1/management/projects/{key}` | 已实现 | 编辑项目基础字段并写审计 | `name`、`sensitivityLevel`、`allowPublicModel` | 项目视图 |
| `PATCH /api/v1/management/projects/{key}/status` | 已实现 | 项目状态流转并写审计 | `status=PREPARING/ACTIVE/ARCHIVED/DISABLED` | 项目视图 |
| `GET /api/v1/management/projects/{key}/members` | 已实现 | 查询项目成员和项目级角色 | 项目 `code`、`name` 或 `id` | `username`、`displayName`、`role`、`memberType`、`status` |
| `POST /api/v1/management/projects/{key}/members` | 已实现 | 添加项目成员并绑定项目级角色 | `username`、`roleCode=ProjectOwner/Tester/Developer/Auditor` | 项目成员视图 |
| `POST /api/v1/management/projects/{key}/members/{username}/remove` | 已实现 | 移除项目成员并解绑该项目级角色 | Bearer Token | 项目成员视图，`status=已移除` |
| `GET /api/v1/management/applications` | 已实现 | 查询应用管理视图 | Bearer Token | `name`、`type`、`owner`、`version`、`status` |
| `POST /api/v1/management/applications` | 已实现 | 登记应用并写审计 | `code`、`name`、`project`、`appType`、`defaultWebUrl`、`defaultApiBaseUrl`、`sensitivityLevel`、`allowPublicModel` | 新应用视图 |
| `GET /api/v1/management/applications/{key}` | 已实现 | 查询应用详情视图 | 应用 `code`、`name` 或 `id` | 应用视图 |
| `PATCH /api/v1/management/applications/{key}` | 已实现 | 编辑应用基础字段并写审计 | `name`、`appType`、`defaultWebUrl`、`defaultApiBaseUrl`、`sensitivityLevel`、`allowPublicModel` | 应用视图 |
| `PATCH /api/v1/management/applications/{key}/status` | 已实现 | 应用启停并写审计 | `status=ENABLED/DISABLED` | 应用视图 |
| `GET /api/v1/management/applications/{key}/owners` | 已实现 | 查询应用负责人和应用级角色 | 应用 `code`、`name` 或 `id` | `username`、`displayName`、`role`、`scopeType=APPLICATION`、`status` |
| `POST /api/v1/management/applications/{key}/owners` | 已实现 | 添加应用负责人并绑定应用级角色 | `username`、`roleCode=AppOwner` | 作用域用户角色视图 |
| `POST /api/v1/management/applications/{key}/owners/{username}/remove` | 已实现 | 移除应用负责人并解绑应用级 `AppOwner` | Bearer Token | 作用域用户角色视图，`status=已移除` |
| `GET /api/v1/management/environments` | 已实现 | 查询环境管理视图 | Bearer Token | `name`、`cluster`、`endpoint`、`status` |
| `POST /api/v1/management/environments` | 已实现 | 新增环境并写审计 | `code`、`name`、`project`、`application`、`scopeType`、`envType`、`webUrl`、`apiBaseUrl` | 新环境视图 |
| `GET /api/v1/management/environments/{key}` | 已实现 | 查询环境详情视图 | 环境 `code`、`name` 或 `id` | 环境视图 |
| `PATCH /api/v1/management/environments/{key}` | 已实现 | 编辑环境基础字段并写审计 | `name`、`envType`、`webUrl`、`apiBaseUrl` | 环境视图 |
| `PATCH /api/v1/management/environments/{key}/status` | 已实现 | 环境启停并写审计 | `status=ENABLED/DISABLED` | 环境视图 |
| `GET /api/v1/management/environments/{key}/users` | 已实现 | 查询环境授权用户和环境级角色 | 环境 `code`、`name` 或 `id` | `username`、`displayName`、`role`、`scopeType=ENVIRONMENT`、`status` |
| `POST /api/v1/management/environments/{key}/users` | 已实现 | 添加环境授权用户并绑定环境级角色 | `username`、`roleCode=Tester/Developer/Auditor` | 作用域用户角色视图 |
| `POST /api/v1/management/environments/{key}/users/{username}/remove` | 已实现 | 移除环境授权用户并解绑该环境级角色 | Bearer Token | 作用域用户角色视图，`status=已移除` |
| `GET /api/v1/management/integrations` | 已实现 | 查询集成配置视图 | Bearer Token | `key`、`name`、`category`、`scope`、`status` |
| `POST /api/v1/management/integrations` | 已实现 | 登记集成配置并写审计 | `code`、`name`、`category`、`scope` | 集成配置视图，状态默认 `已启用` |
| `GET /api/v1/management/integrations/{key}` | 已实现 | 查询集成配置详情 | 集成 `key` 或名称 | 集成配置视图 |
| `PATCH /api/v1/management/integrations/{key}` | 已实现 | 编辑集成配置基础字段并写审计 | `name`、`category`、`scope` | 集成配置视图 |
| `PATCH /api/v1/management/integrations/{key}/status` | 已实现 | 集成配置启停并写审计 | `status=ENABLED/DISABLED` | 集成配置视图 |
| `GET /api/v1/management/audit-logs` | 已实现 | 查询审计日志视图，支持分页、全文检索和结构化筛选 | Bearer Token | 查询参数：`index`、`size`、`search`、`actor`、`action`、`resourceType`、`result`、`startTime`、`endTime`；响应字段：`time`、`actor`、`action`、`target`、`result` |
| `GET /api/v1/management/settings` | 已实现 | 查询系统设置视图 | Bearer Token | `key`、`name`、`value`、`scope`、`status` |
| `POST /api/v1/management/settings` | 已实现 | 新增系统设置并写审计 | `key`、`name`、`value`、`scope` | 设置视图，敏感键禁止明文值 |
| `GET /api/v1/management/settings/{key}` | 已实现 | 查询系统设置详情 | 设置 `key` | 设置视图 |
| `PATCH /api/v1/management/settings/{key}` | 已实现 | 编辑系统设置并写审计 | `name`、`value`、`scope` | 设置视图，敏感键禁止明文值 |
| `PATCH /api/v1/management/settings/{key}/status` | 已实现 | 设置启停并写审计 | `status=ENABLED/DISABLED` | 设置视图 |

> 说明：旧版正式领域 API 规划骨架已归档，不再放在当前契约中作为参考。后续新增正式领域 API 必须沿用本文件当前基线：camelCase JSON 字段、`index/size` 分页、单平台资源作用域、同一 `platform-api` 内模块优先走 Spring 应用服务。

当前 `db` profile 已支持项目、应用和环境创建、详情、编辑和状态流请求中的正式字段；`name` 仍保留最小兼容写法，未传 `code` 时由服务端生成。环境未传 `application` 时默认为项目公共环境，传入 `application` 或显式 `scopeType=APPLICATION` 时创建应用专属环境，并校验应用归属同一项目。归档/停用项目不可继续新增或编辑下级资源，停用应用不可新增专属环境，停用应用和环境不可继续编辑。项目成员接口会同时维护 `base_project_member` 和 `rbac_role_binding(scope_type=PROJECT)`；应用负责人和环境授权用户直接维护 `rbac_role_binding(scope_type=APPLICATION/ENVIRONMENT)`。角色变更后递增用户 `authVersion`，旧 Token 会失效。

### 5.1 当前内部上下文与审计边界

当前保留 `/api/v1/contexts/projects/{projectId}`、`/api/v1/contexts/applications/{applicationId}` 和 `/api/v1/audit/events` 作为外部集成或未来拆分后的 HTTP 契约。WP2/WP3 在当前同一 `platform-api` 进程内不通过这些 HTTP 端点回调本服务，而是直接注入 `PlatformIntegrationService` 复用 WP1 上下文和审计能力。

内部审计事件数据库字段仍使用 snake_case 列名，例如 `trace_id`、`resource_type`、`scope_type`；API 层 DTO 和 JSON 响应保持 camelCase。

## 6. WP 模块调用边界

| 调用方 | 当前调用方式 | 可以获得 | 禁止事项 |
|---|---|---|---|
| WP2 模型接入层 | 当前同进程调用 `PlatformIntegrationService`；外部契约保留 `/contexts/projects/{projectId}`、`/contexts/applications/{applicationId}`、`/audit/events` | 项目/应用策略、`allowPublicModel`、`sensitivityLevel`、审计写入 | 不通过 HTTP 回调本服务；不直接读取 WP1 表；不在审计中写 Prompt 或密钥明文。 |
| WP3 测试资产模型 | 当前同进程调用 `PlatformIntegrationService`；外部契约保留 `/contexts/projects/{projectId}`、`/audit/events` | 项目状态、策略字段、审计写入 | 不维护 WP1 项目成员；资产表可保存 WP1 资源 ID，但创建和引用校验必须走 WP1 应用服务。 |
| 后续外部集成 | `/contexts/projects/{projectId}`、`/contexts/applications/{applicationId}`、`/audit/events` | 项目/应用上下文、策略字段、审计入口 | 不直接读写 WP1 表；不要求 WP1 返回密钥明文。 |

外部集成调用统一要求：

1. 使用服务令牌：`Authorization: Bearer <service_token>`。
2. 携带委托用户：`X-Delegated-User-Id`。
3. 携带调用服务：`X-Caller-Service`。
4. 携带链路 ID：`X-Trace-Id`。
5. WP1 对调用方身份和委托用户同时鉴权。调用方只能调用被授权的内部 API，委托用户必须对目标资源具备相应读、用或写权限。

## 7. 研发拆解提示

1. 先实现统一响应、错误码、Trace ID、用户上下文注入和鉴权拦截器，再实现领域 API。
2. 初始化、登录、会话撤销、用户停用失效是 P0 闭环门禁。
3. 角色绑定、成员关系和状态流必须同步刷新权限缓存或递增 `authVersion`。
4. 环境变量写入需要先完成 `SecretProvider` 抽象和本地加密实现。
5. 审计失败不得阻断主流程，但必须进入 outbox 补偿；密钥保存失败必须阻断主流程。
6. OpenAPI 契约测试需覆盖统一响应、分页、错误码、权限拒绝、资源作用域越权、状态流、审计事件和敏感字段脱敏。
