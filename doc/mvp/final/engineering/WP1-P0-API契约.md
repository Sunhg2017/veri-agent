# WP1 P0 API 契约

| 项目 | 内容 |
|---|---|
| 工作包 | WP1 平台基础底座 |
| 面向阶段 | MVP P0 |
| 文档用途 | 前后端接口评审、后端研发拆解、接口自动化与权限矩阵测试输入 |
| API 前缀 | `/api/v1` |
| 适用端 | Web 管理后台、后续 WP 服务间调用 |
| 冻结范围 | 认证/初始化、部门、用户、角色授权、项目、应用、环境、配置、上下文、审计 |

> 单平台修订：根据 2026-05-17 产品决策，WP1 当前只保留“单平台、多部门、多项目、多应用、多环境”控制面。不再提供平台实例分层、平台实例管理员、平台实例切换或跨实例隔离 API。当前代码已落地 `/api/v1/management/**` P0 管理视图接口，后续 API 继续围绕部门、项目、应用、环境和角色作用域收敛。

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
| `X-Delegated-User-Id` | 内部 API 必填 | 服务间调用时的用户委托身份。 |
| `X-Caller-Service` | 内部 API 必填 | 服务名，如 `wp3-asset-service`、`wp9-execution-service`。 |

### 2.2 统一响应格式

```json
{
  "code": "OK",
  "message": "success",
  "trace_id": "trc_202605160001",
  "data": {}
}
```

错误响应：

```json
{
  "code": "VALIDATION_ERROR",
  "message": "请求字段校验失败",
  "trace_id": "trc_202605160001",
  "data": {
    "field_errors": [
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
| `page` | 页码，从 1 开始，默认 1。 |
| `page_size` | 每页条数，默认 20，最大 100。 |
| `sort` | 排序字段，默认由接口定义；不允许传未白名单字段。 |
| `order` | `asc` 或 `desc`，默认 `desc`。 |
| `keyword` | 关键词，按接口定义匹配名称、编码或账号。 |

分页响应：

```json
{
  "code": "OK",
  "message": "success",
  "trace_id": "trc_202605160001",
  "data": {
    "items": [],
    "page": 1,
    "page_size": 20,
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
2. 幂等记录建议保留 24 小时。幂等返回仍需携带当前 `trace_id`，并在 `data.idempotent_replayed=true` 标识重放。
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
6. 服务间调用必须使用服务令牌，并携带 `X-Delegated-User-Id`。WP1 需同时校验服务身份、委托用户状态和目标资源作用域。

## 3. 敏感字段与 SecretProvider 策略

### 3.1 敏感字段返回策略

| 字段类型 | 示例 | 入库策略 | 响应策略 | 审计策略 |
|---|---|---|---|---|
| 密码/Token/Cookie/API Key | `password`、`api_token`、`cookie` | 不明文入库；密码只保存哈希；密钥通过 SecretProvider | 不返回明文；只返回是否已配置、掩码或引用摘要 | 不记录明文，只记录是否变更。 |
| 个人联系方式 | `email`、`mobile` | 可按业务字段保存 | 管理视图可返回；审计、导出按规则脱敏 | 邮箱、手机号脱敏。 |
| 仓库地址/内部 URL | `repo_url`、`web_url`、`api_base_url` | 可明文保存 | 按权限返回 | 审计可记录，URL 中疑似凭证参数必须脱敏。 |
| 普通变量 | `BASE_URL`、`PROJECT_CODE` | `value_kind=PLAIN` 时可明文保存 | 按权限返回明文 | 可记录摘要。 |
| 敏感变量 | `ADMIN_PASSWORD`、`API_TOKEN`、`COOKIE_SECRET` | `value_kind=SECRET` 或 `SECRET_REF`，不得明文保存 | 仅返回 `masked_value`、`secret_ref_summary`、`secret_provider`、`updated_at` | 只记录引用变化、掩码、`changed=true`。 |

### 3.2 SecretProvider 引用字段策略

1. 环境变量 `value_kind` 固定为 `PLAIN`、`SECRET`、`SECRET_REF`。
2. `SECRET` 表示本次请求携带 `secret_value` 明文，由 WP1 调用 `SecretProvider` 保存后只落库 `secret_ref`、`masked_value`、`secret_provider`、`secret_version`。
3. `SECRET_REF` 表示调用方提交已有引用，WP1 只校验引用格式和资源作用域，不读取明文。
4. `secret_ref` 格式：`secret://local/{scope_type}/{scope_id}/{secret_id}`。未来迁移 Vault/KMS 时保持 `secret_ref` 抽象不变。
5. 响应中禁止返回 `secret_value`。返回字段统一为 `secret_ref_summary`，例如 `secret://local/ENVIRONMENT/{env_id}/***`。
6. 审计 `before_json`、`after_json`、`diff_json` 中敏感字段只允许出现 `changed`、`masked_value`、`secret_ref_summary`、`secret_provider`、`secret_version`。
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
| `POST /api/v1/bootstrap/super-admin` | 已实现 | 首次初始化 `SuperAdmin` | `bootstrap_token`、`username`、`password`、`display_name`、`email` | `user_id`、`role`、`must_change_password` |
| `POST /api/v1/auth/login` | 已实现 | 用户登录 | `username`、`password` | `access_token`、`token_type`、`expires_at`、用户摘要、角色 |
| `GET /api/v1/auth/me` | 已实现 | 获取当前用户 | Bearer Token | 用户摘要、角色、账号状态 |
| `GET /api/v1/management/departments` | 已实现 | 查询部门管理视图 | Bearer Token | `name`、`parent`、`lead`、`members`、`status` |
| `POST /api/v1/management/departments` | 已实现 | 新增部门样板数据并写审计 | `name` | 新部门视图 |
| `GET /api/v1/management/departments/{key}` | 已实现 | 查询部门详情视图 | Bearer Token | `name`、`parent`、`lead`、`members`、`status` |
| `PATCH /api/v1/management/departments/{key}` | 已实现 | 编辑部门名称并写审计 | `name` | 更新后部门视图 |
| `PATCH /api/v1/management/departments/{key}/status` | 已实现 | 启用/停用部门并写审计 | `status=ENABLED/DISABLED` | 更新后部门视图 |
| `GET /api/v1/management/users` | 已实现 | 查询用户与权限管理视图 | Bearer Token | `username`、`display_name`、`email`、`role`、`department`、`status`、`last_seen` |
| `GET /api/v1/management/users/{username}` | 已实现 | 查询用户详情视图 | Bearer Token | `username`、`display_name`、`email`、`role`、`department`、`status`、`last_seen` |
| `PATCH /api/v1/management/users/{username}` | 已实现 | 编辑用户显示名称和邮箱并写审计 | `display_name`、`email` | 更新后用户视图 |
| `POST /api/v1/management/users` | 已实现 | 邀请用户样板数据并写审计 | `name` | 新用户视图 |
| `POST /api/v1/management/users/{username}/enable` | 已实现 | 启用用户并写审计 | Bearer Token | 用户视图 |
| `POST /api/v1/management/users/{username}/disable` | 已实现 | 停用用户、提升 `auth_version` 并写审计 | Bearer Token | 用户视图 |
| `POST /api/v1/management/users/{username}/lock` | 已实现 | 锁定用户、提升 `auth_version` 并写审计 | Bearer Token | 用户视图 |
| `POST /api/v1/management/users/{username}/unlock` | 已实现 | 解锁用户、提升 `auth_version` 并写审计 | Bearer Token | 用户视图 |
| `POST /api/v1/management/users/{username}/reset-password` | 已实现 | 重置密码、要求下次改密、提升 `auth_version` 并写审计 | `new_password` | 用户视图 |
| `GET /api/v1/management/projects` | 已实现 | 查询项目空间管理视图 | Bearer Token | `name`、`department`、`owner`、`apps`、`status` |
| `POST /api/v1/management/projects` | 已实现 | 创建项目并写审计 | `code`、`name`、`sensitivity_level`、`allow_public_model` | 新项目视图 |
| `GET /api/v1/management/projects/{key}` | 已实现 | 查询项目详情视图 | 项目 `code`、`name` 或 `id` | 项目视图 |
| `PATCH /api/v1/management/projects/{key}` | 已实现 | 编辑项目基础字段并写审计 | `name`、`sensitivity_level`、`allow_public_model` | 项目视图 |
| `PATCH /api/v1/management/projects/{key}/status` | 已实现 | 项目状态流转并写审计 | `status=PREPARING/ACTIVE/ARCHIVED/DISABLED` | 项目视图 |
| `GET /api/v1/management/projects/{key}/members` | 已实现 | 查询项目成员和项目级角色 | 项目 `code`、`name` 或 `id` | `username`、`display_name`、`role`、`member_type`、`status` |
| `POST /api/v1/management/projects/{key}/members` | 已实现 | 添加项目成员并绑定项目级角色 | `username`、`role_code=ProjectOwner/Tester/Developer/Auditor` | 项目成员视图 |
| `POST /api/v1/management/projects/{key}/members/{username}/remove` | 已实现 | 移除项目成员并解绑该项目级角色 | Bearer Token | 项目成员视图，`status=已移除` |
| `GET /api/v1/management/applications` | 已实现 | 查询应用管理视图 | Bearer Token | `name`、`type`、`owner`、`version`、`status` |
| `POST /api/v1/management/applications` | 已实现 | 登记应用并写审计 | `code`、`name`、`project`、`app_type`、`default_web_url`、`default_api_base_url`、`sensitivity_level`、`allow_public_model` | 新应用视图 |
| `GET /api/v1/management/applications/{key}` | 已实现 | 查询应用详情视图 | 应用 `code`、`name` 或 `id` | 应用视图 |
| `PATCH /api/v1/management/applications/{key}` | 已实现 | 编辑应用基础字段并写审计 | `name`、`app_type`、`default_web_url`、`default_api_base_url`、`sensitivity_level`、`allow_public_model` | 应用视图 |
| `PATCH /api/v1/management/applications/{key}/status` | 已实现 | 应用启停并写审计 | `status=ENABLED/DISABLED` | 应用视图 |
| `GET /api/v1/management/applications/{key}/owners` | 已实现 | 查询应用负责人和应用级角色 | 应用 `code`、`name` 或 `id` | `username`、`display_name`、`role`、`scope_type=APPLICATION`、`status` |
| `POST /api/v1/management/applications/{key}/owners` | 已实现 | 添加应用负责人并绑定应用级角色 | `username`、`role_code=AppOwner` | 作用域用户角色视图 |
| `POST /api/v1/management/applications/{key}/owners/{username}/remove` | 已实现 | 移除应用负责人并解绑应用级 `AppOwner` | Bearer Token | 作用域用户角色视图，`status=已移除` |
| `GET /api/v1/management/environments` | 已实现 | 查询环境管理视图 | Bearer Token | `name`、`cluster`、`endpoint`、`status` |
| `POST /api/v1/management/environments` | 已实现 | 新增环境并写审计 | `code`、`name`、`project`、`application`、`scope_type`、`env_type`、`web_url`、`api_base_url` | 新环境视图 |
| `GET /api/v1/management/environments/{key}` | 已实现 | 查询环境详情视图 | 环境 `code`、`name` 或 `id` | 环境视图 |
| `PATCH /api/v1/management/environments/{key}` | 已实现 | 编辑环境基础字段并写审计 | `name`、`env_type`、`web_url`、`api_base_url` | 环境视图 |
| `PATCH /api/v1/management/environments/{key}/status` | 已实现 | 环境启停并写审计 | `status=ENABLED/DISABLED` | 环境视图 |
| `GET /api/v1/management/environments/{key}/users` | 已实现 | 查询环境授权用户和环境级角色 | 环境 `code`、`name` 或 `id` | `username`、`display_name`、`role`、`scope_type=ENVIRONMENT`、`status` |
| `POST /api/v1/management/environments/{key}/users` | 已实现 | 添加环境授权用户并绑定环境级角色 | `username`、`role_code=Tester/Developer/Auditor` | 作用域用户角色视图 |
| `POST /api/v1/management/environments/{key}/users/{username}/remove` | 已实现 | 移除环境授权用户并解绑该环境级角色 | Bearer Token | 作用域用户角色视图，`status=已移除` |
| `GET /api/v1/management/integrations` | 已实现 | 查询集成配置视图 | Bearer Token | `key`、`name`、`category`、`scope`、`status` |
| `POST /api/v1/management/integrations` | 已实现 | 登记集成配置并写审计 | `code`、`name`、`category`、`scope` | 集成配置视图，状态默认 `已启用` |
| `GET /api/v1/management/integrations/{key}` | 已实现 | 查询集成配置详情 | 集成 `key` 或名称 | 集成配置视图 |
| `PATCH /api/v1/management/integrations/{key}` | 已实现 | 编辑集成配置基础字段并写审计 | `name`、`category`、`scope` | 集成配置视图 |
| `PATCH /api/v1/management/integrations/{key}/status` | 已实现 | 集成配置启停并写审计 | `status=ENABLED/DISABLED` | 集成配置视图 |
| `GET /api/v1/management/audit-logs` | 已实现 | 查询审计日志视图，支持分页、全文检索和结构化筛选 | Bearer Token | 查询参数：`page`、`page_size`、`search`、`actor`、`action`、`resource_type`、`result`、`start_time`、`end_time`；响应字段：`time`、`actor`、`action`、`target`、`result` |
| `GET /api/v1/management/settings` | 已实现 | 查询系统设置视图 | Bearer Token | `key`、`name`、`value`、`scope`、`status` |
| `POST /api/v1/management/settings` | 已实现 | 新增系统设置并写审计 | `key`、`name`、`value`、`scope` | 设置视图，敏感键禁止明文值 |
| `GET /api/v1/management/settings/{key}` | 已实现 | 查询系统设置详情 | 设置 `key` | 设置视图 |
| `PATCH /api/v1/management/settings/{key}` | 已实现 | 编辑系统设置并写审计 | `name`、`value`、`scope` | 设置视图，敏感键禁止明文值 |
| `PATCH /api/v1/management/settings/{key}/status` | 已实现 | 设置启停并写审计 | `status=ENABLED/DISABLED` | 设置视图 |

> 说明：5.1 之后保留的是后续正式领域 API 的规划骨架，仍需继续按单平台口径拆分和替换，不作为本轮已实现接口声明。

当前 `db` profile 已支持项目、应用和环境创建、详情、编辑和状态流请求中的正式字段；`name` 仍保留最小兼容写法，未传 `code` 时由服务端生成。环境未传 `application` 时默认为项目公共环境，传入 `application` 或显式 `scope_type=APPLICATION` 时创建应用专属环境，并校验应用归属同一项目。归档/停用项目不可继续新增或编辑下级资源，停用应用不可新增专属环境，停用应用和环境不可继续编辑。项目成员接口会同时维护 `base_project_member` 和 `rbac_role_binding(scope_type=PROJECT)`；应用负责人和环境授权用户直接维护 `rbac_role_binding(scope_type=APPLICATION/ENVIRONMENT)`。角色变更后递增用户 `auth_version`，旧 Token 会失效。

### 5.1 初始化与认证

| 接口 | 用途 | 权限点 | 审计事件 | 关键请求字段 | 关键响应字段 | 主要错误码 |
|---|---|---|---|---|---|---|
| `POST /bootstrap/super-admin` | 初始化首个超级管理员。 | 初始化令牌 | `USER_CREATE`、`SUPER_ADMIN_INIT` | `bootstrap_token`、`username`、`password`、`display_name`、`email` | `user_id`、`role=SuperAdmin`、`must_change_password` | `VALIDATION_ERROR`、`CONFLICT`、`FORBIDDEN` |
| `POST /auth/login` | 用户登录并建立会话。 | 无 | `AUTH_LOGIN_SUCCESS`、`AUTH_LOGIN_FAILED` | `username`、`password` | `access_token`、`refresh_token`、`expires_in`、`user`、`roles`、`menus` | `UNAUTHORIZED`、`INVALID_STATE` |
| `POST /auth/logout` | 当前用户登出并撤销会话。 | 登录用户 | `AUTH_LOGOUT`、`AUTH_SESSION_REVOKE` | `refresh_token` 可选 | `revoked`、`session_id` | `UNAUTHORIZED` |
| `POST /auth/refresh` | 刷新访问令牌。 | 登录用户 | `AUTH_TOKEN_REFRESH` | `refresh_token` | `access_token`、`expires_in`、`auth_version` | `UNAUTHORIZED`、`INVALID_STATE` |
| `GET /auth/me` | 获取当前用户、权限和默认上下文。 | 登录用户 | 无 | 无 | `user`、`roles`、`permissions`、`menus`、`default_project_id`、`auth_version` | `UNAUTHORIZED` |
| `GET /sessions` | 查询本人或授权用户会话。 | `user:read` 或本人 | `AUDIT_QUERY` | `user_id`、分页 | `items[].session_id`、`created_at`、`expires_at`、`revoked_at`、`client_ip` | `FORBIDDEN` |
| `DELETE /sessions/{sessionId}` | 撤销指定会话。 | `user:disable` 或本人 | `AUTH_SESSION_REVOKE` | `reason` | `revoked`、`revoked_at` | `FORBIDDEN`、`NOT_FOUND` |

### 5.2 平台边界

WP1 P0 平台边界统一为单平台，不提供平台实例创建、实例管理员、实例切换或跨实例隔离能力。数据归属和权限作用域由部门、项目、应用、环境和角色绑定表达。

### 5.3 部门

| 接口 | 用途 | 权限点 | 审计事件 | 关键请求字段 | 关键响应字段 | 主要错误码 |
|---|---|---|---|---|---|---|
| `GET /departments/tree` | 查询授权范围内部门树。 | `department:read` | 无 | `status`、`include_members_count` | 部门树、负责人摘要、成员数 | `FORBIDDEN` |
| `POST /departments` | 创建部门。 | `department:create` | `DEPARTMENT_CREATE` | `parent_id`、`code`、`name`、`sort_order` | `dept_id`、`path`、`status` | `VALIDATION_ERROR`、`CONFLICT` |
| `GET /departments/{deptId}` | 查看部门详情。 | `department:read` | 无 | `include=members,managers` | 部门详情、父子关系、负责人、成员摘要 | `FORBIDDEN`、`NOT_FOUND` |
| `PATCH /departments/{deptId}` | 编辑部门信息或调整父部门。 | `department:edit` | `DEPARTMENT_UPDATE` | `name`、`parent_id`、`sort_order` | 更新后部门、`path` | `VALIDATION_ERROR`、`CONFLICT`、`INVALID_STATE` |
| `PATCH /departments/{deptId}/status` | 启停部门。 | `department:enable` 或 `department:disable` | `DEPARTMENT_ENABLE`、`DEPARTMENT_DISABLE` | `status`、`reason` | `status` | `INVALID_STATE`、`FORBIDDEN` |
| `PUT /departments/{deptId}/managers` | 覆盖部门负责人。 | `department:member_manage`、`role:bind` | `DEPARTMENT_MANAGER_ADD`、`DEPARTMENT_MANAGER_REMOVE`、`ROLE_BIND`、`ROLE_UNBIND` | `user_ids[]` | `managers[]`、`role_bindings[]` | `VALIDATION_ERROR`、`FORBIDDEN` |
| `GET /departments/{deptId}/members` | 查询部门成员。 | `department:read` | 无 | `status`、`keyword`、分页 | 成员分页、主部门标识、岗位 | `FORBIDDEN` |
| `POST /departments/{deptId}/members` | 添加部门成员。 | `department:member_manage` | `DEPARTMENT_MEMBER_ADD` | `user_id`、`is_primary`、`position` | `member_id`、`is_primary` | `CONFLICT`、`INVALID_STATE` |
| `DELETE /departments/{deptId}/members/{userId}` | 移除部门成员。 | `department:member_manage` | `DEPARTMENT_MEMBER_REMOVE` | `replacement_primary_dept_id` 可选、`reason` | `removed` | `INVALID_STATE`、`FORBIDDEN` |

### 5.4 用户

| 接口 | 用途 | 权限点 | 审计事件 | 关键请求字段 | 关键响应字段 | 主要错误码 |
|---|---|---|---|---|---|---|
| `GET /users` | 查询用户列表。 | `user:read` | 无 | `dept_id`、`status`、`keyword`、分页 | 用户分页、部门摘要、角色摘要、最近登录时间 | `FORBIDDEN` |
| `POST /users` | 创建或邀请用户。 | `user:create` | `USER_CREATE`、`USER_INVITE` | `username`、`display_name`、`email`、`mobile`、`dept_ids`、`primary_dept_id`、`invite`、`role_bindings` | `user_id`、`status`、`invite_sent` | `VALIDATION_ERROR`、`CONFLICT` |
| `GET /users/{userId}` | 查看用户详情。 | `user:read` 或本人 | 无 | 无 | 用户详情、部门、角色绑定、状态 | `FORBIDDEN`、`NOT_FOUND` |
| `PATCH /users/{userId}` | 编辑用户基础信息和部门关系。 | `user:edit` 或本人受限编辑 | `USER_UPDATE` | `display_name`、`email`、`mobile`、`dept_ids`、`primary_dept_id`、`external_id` | 更新后用户 | `VALIDATION_ERROR`、`FORBIDDEN`、`INVALID_STATE` |
| `PATCH /users/{userId}/status` | 启用、停用、锁定用户。 | `user:enable`、`user:disable` 或 `user:lock` | `USER_ENABLE`、`USER_DISABLE`、`USER_LOCK`、`AUTH_SESSION_REVOKE` | `status`、`reason` | `status`、`revoked_sessions`、`auth_version` | `INVALID_STATE`、`FORBIDDEN` |
| `GET /users/{userId}/permissions` | 查看用户有效权限。 | `user:read` 或本人 | 无 | `scope_type`、`scope_id` 可选 | `roles`、`role_bindings`、`effective_permissions`、`menus` | `FORBIDDEN` |

### 5.5 角色授权

| 接口 | 用途 | 权限点 | 审计事件 | 关键请求字段 | 关键响应字段 | 主要错误码 |
|---|---|---|---|---|---|---|
| `GET /permissions` | 查询 P0 权限点字典。 | `role:read` | 无 | `resource`、`scope` 可选 | `permissions[]` | `FORBIDDEN` |
| `GET /roles` | 查询角色列表。 | `role:read` | 无 | `scope`、`builtin`、`status` | 角色列表、权限摘要、是否内置 | `FORBIDDEN` |
| `POST /roles` | 创建自定义角色。 | `role:create` | `ROLE_CREATE` | `code`、`name`、`scope`、`permission_codes[]`、`description` | `role_id`、`status` | `VALIDATION_ERROR`、`CONFLICT`、`FORBIDDEN` |
| `GET /roles/{roleId}/permissions` | 查看角色权限。 | `role:read` | 无 | 无 | `role`、`permissions[]` | `FORBIDDEN`、`NOT_FOUND` |
| `PUT /roles/{roleId}/permissions` | 覆盖角色权限点。 | `role:edit` | `ROLE_PERMISSION_UPDATE` | `permission_codes[]` | `permissions[]`、`auth_version_incremented` | `FORBIDDEN`、`INVALID_STATE` |
| `PATCH /roles/{roleId}/status` | 启停角色。 | `role:edit` | `ROLE_UPDATE` | `status`、`reason` | `status` | `INVALID_STATE`、`FORBIDDEN` |
| `GET /role-bindings` | 查询授权绑定。 | `role:read` | 无 | `subject_type`、`subject_id`、`scope_type`、`scope_id`、分页 | 绑定分页、角色、主体、作用域 | `FORBIDDEN` |
| `POST /role-bindings` | 授予角色。 | `role:bind` | `ROLE_BIND` | `subject_type`、`subject_id`、`role_code` 或 `role_id`、`scope_type`、`scope_id` | `binding_id`、`effective` | `VALIDATION_ERROR`、`FORBIDDEN`、`CONFLICT` |
| `DELETE /role-bindings/{bindingId}` | 回收角色。 | `role:unbind` | `ROLE_UNBIND` | `reason` | `removed`、`auth_version_incremented` | `FORBIDDEN`、`NOT_FOUND` |

### 5.6 项目

| 接口 | 用途 | 权限点 | 审计事件 | 关键请求字段 | 关键响应字段 | 主要错误码 |
|---|---|---|---|---|---|---|
| `GET /projects` | 查询授权范围内项目。 | `project:read` | 无 | `status`、`dept_id`、`owner_user_id`、`keyword`、分页 | 项目分页、负责人、关联部门、敏感级别 | `FORBIDDEN` |
| `POST /projects` | 创建项目并绑定部门和负责人。 | `project:create`、`role:bind` | `PROJECT_CREATE`、`PROJECT_DEPARTMENT_UPDATE`、`PROJECT_MEMBER_ADD`、`ROLE_BIND` | `code`、`name`、`dept_ids`、`primary_dept_id`、`owner_user_ids`、`description`、`sensitivity_level`、`allow_public_model` | `project_id`、`status`、`owners`、`departments` | `VALIDATION_ERROR`、`CONFLICT`、`FORBIDDEN` |
| `GET /projects/{projectId}` | 查看项目详情。 | `project:read` | 无 | `include=members,applications,environments` | 项目详情、成员、应用数、环境数、配置摘要 | `FORBIDDEN`、`NOT_FOUND` |
| `PATCH /projects/{projectId}` | 编辑项目基础信息。 | `project:edit` | `PROJECT_UPDATE` | `name`、`description`、`sensitivity_level`、`allow_public_model` | 更新后项目 | `VALIDATION_ERROR`、`INVALID_STATE` |
| `PUT /projects/{projectId}/departments` | 覆盖项目关联部门和主责部门。 | `project:edit` | `PROJECT_DEPARTMENT_UPDATE` | `dept_ids[]`、`primary_dept_id` | `departments[]` | `VALIDATION_ERROR`、`FORBIDDEN` |
| `PATCH /projects/{projectId}/status` | 项目状态流转。 | `project:archive`、`project:disable` 或 `project:edit` | `PROJECT_ARCHIVE`、`PROJECT_DISABLE`、`PROJECT_ENABLE` | `status`、`reason` | `status` | `INVALID_STATE`、`FORBIDDEN` |
| `GET /projects/{projectId}/members` | 查询项目成员。 | `project:read` | 无 | `role_code`、`status`、`keyword`、分页 | 成员分页、角色绑定、成员类型 | `FORBIDDEN` |
| `POST /projects/{projectId}/members` | 添加项目成员并授予项目内角色。 | `project:member_manage`、`role:bind` | `PROJECT_MEMBER_ADD`、`ROLE_BIND` | `user_id`、`role_codes[]`、`member_type` | `member_id`、`role_bindings[]` | `FORBIDDEN`、`CONFLICT`、`INVALID_STATE` |
| `PUT /projects/{projectId}/members/{userId}/roles` | 覆盖项目成员角色。 | `project:member_manage`、`role:bind`、`role:unbind` | `ROLE_BIND`、`ROLE_UNBIND` | `role_codes[]` | `role_bindings[]` | `FORBIDDEN`、`INVALID_STATE` |
| `DELETE /projects/{projectId}/members/{userId}` | 移除项目成员并回收项目角色。 | `project:member_manage`、`role:unbind` | `PROJECT_MEMBER_REMOVE`、`ROLE_UNBIND` | `reason` | `removed` | `INVALID_STATE`、`FORBIDDEN` |

### 5.7 应用

| 接口 | 用途 | 权限点 | 审计事件 | 关键请求字段 | 关键响应字段 | 主要错误码 |
|---|---|---|---|---|---|---|
| `GET /projects/{projectId}/applications` | 查询项目下应用。 | `application:read` | 无 | `status`、`app_type`、`keyword`、分页 | 应用分页、负责人、敏感级别、默认地址 | `FORBIDDEN` |
| `POST /projects/{projectId}/applications` | 创建被测应用并绑定负责人。 | `application:create`、`role:bind` | `APPLICATION_CREATE`、`APPLICATION_OWNER_BIND`、`ROLE_BIND` | `code`、`name`、`app_type`、`owner_user_ids`、`default_web_url`、`default_api_base_url`、`repo_url`、`sensitivity_level`、`allow_public_model` | `app_id`、`owners`、`status` | `VALIDATION_ERROR`、`CONFLICT`、`FORBIDDEN` |
| `GET /applications/{appId}` | 查看应用详情。 | `application:read` | 无 | `include=environments,owners` | 应用详情、负责人、环境数、配置摘要 | `FORBIDDEN`、`NOT_FOUND` |
| `PATCH /applications/{appId}` | 编辑应用基础信息和负责人。 | `application:edit`、`role:bind` 可选 | `APPLICATION_UPDATE`、`APPLICATION_OWNER_BIND`、`APPLICATION_OWNER_UNBIND` | `name`、`app_type`、`owner_user_ids`、`default_web_url`、`default_api_base_url`、`repo_url`、`sensitivity_level`、`allow_public_model` | 更新后应用、`owners` | `VALIDATION_ERROR`、`FORBIDDEN`、`INVALID_STATE` |
| `PATCH /applications/{appId}/status` | 启停应用。 | `application:disable` 或 `application:edit` | `APPLICATION_ENABLE`、`APPLICATION_DISABLE` | `status`、`reason` | `status` | `INVALID_STATE`、`FORBIDDEN` |

### 5.8 环境

| 接口 | 用途 | 权限点 | 审计事件 | 关键请求字段 | 关键响应字段 | 主要错误码 |
|---|---|---|---|---|---|---|
| `GET /projects/{projectId}/environments` | 查询项目公共环境和应用专属环境。 | `environment:read` | 无 | `scope_type`、`app_id`、`env_type`、`status`、分页 | 环境分页、`scope_type`、应用摘要、执行开关 | `FORBIDDEN` |
| `POST /projects/{projectId}/environments` | 创建项目公共环境。 | `environment:create` on Project | `ENVIRONMENT_CREATE` | `scope_type=PROJECT`、`code`、`name`、`env_type`、`web_url`、`api_base_url`、`execution_policy`、`health_check` | `env_id`、`scope_type`、`status` | `VALIDATION_ERROR`、`CONFLICT`、`FORBIDDEN` |
| `GET /applications/{appId}/environments` | 查询应用专属环境。 | `environment:read` | 无 | `env_type`、`status`、分页 | 环境分页、应用摘要、执行开关 | `FORBIDDEN` |
| `POST /applications/{appId}/environments` | 创建应用专属环境。 | `environment:create` on Application | `ENVIRONMENT_CREATE` | `scope_type=APPLICATION`、`code`、`name`、`env_type`、`web_url`、`api_base_url`、`execution_policy`、`health_check` | `env_id`、`scope_type`、`app_id`、`status` | `VALIDATION_ERROR`、`CONFLICT`、`FORBIDDEN` |
| `GET /environments/{envId}` | 查看环境详情。 | `environment:read` | 无 | `include_variables`、`include_config` | 环境详情、变量脱敏列表、有效配置摘要 | `FORBIDDEN`、`NOT_FOUND` |
| `PATCH /environments/{envId}` | 编辑环境基础配置。 | `environment:edit` | `ENVIRONMENT_UPDATE`、`ENV_AUTH_CONFIG_UPDATE` | `name`、`env_type`、`web_url`、`api_base_url`、`execution_policy`、`health_check`、`auth_config` | 更新后环境 | `VALIDATION_ERROR`、`INVALID_STATE`、`SECRET_POLICY_VIOLATION` |
| `PATCH /environments/{envId}/status` | 启停环境。 | `environment:disable` 或 `environment:edit` | `ENVIRONMENT_ENABLE`、`ENVIRONMENT_DISABLE` | `status`、`reason` | `status` | `INVALID_STATE`、`FORBIDDEN` |
| `PUT /environments/{envId}/variables` | 批量替换或合并环境变量。 | `environment:edit`、敏感变量需 `secret:reference` | `ENV_VARIABLE_UPDATE` | `mode=REPLACE/MERGE`、`variables[].key`、`variables[].value_kind`、`variables[].plain_value`、`variables[].secret_value`、`variables[].secret_ref`、`variables[].deleted` | 变量列表、敏感变量掩码、`secret_ref_summary` | `VALIDATION_ERROR`、`SECRET_REQUIRED`、`SECRET_POLICY_VIOLATION`、`SECRET_PROVIDER_ERROR` |
| `POST /environments/{envId}/use-check` | 后续执行前校验环境可用性。 | `environment:use` | 可选 `CONTEXT_READ` | `caller_type`、`purpose` | `usable`、`reason`、`environment_status`、`execution_policy` | `FORBIDDEN`、`INVALID_STATE` |

变量更新规则：

1. `REPLACE`：请求中未出现的变量视为删除。
2. `MERGE`：只新增或覆盖请求中的变量；`deleted=true` 表示删除指定变量。
3. 任一变量校验、权限校验或密钥保存失败时整体失败。
4. `PROD` 环境默认关闭 API/UI/E2E 自动执行开关；如需开启，必须具备项目级环境编辑权限并记录原因。

### 5.9 配置

配置继承优先级固定为：

```text
Environment > Application > Project > Platform Default
```

| 接口 | 用途 | 权限点 | 审计事件 | 关键请求字段 | 关键响应字段 | 主要错误码 |
|---|---|---|---|---|---|---|
| `GET /configs/effective` | 获取目标作用域有效配置。 | `config:read` + 目标作用域读权限 | 无 | `project_id`、`app_id`、`env_id`、`keys[]`、`include_sources` | `items[].key`、`value`、`masked`、`source_scope_type`、`source_scope_id`、`overridden` | `FORBIDDEN`、`NOT_FOUND` |
| `GET /configs` | 查询指定作用域显式配置。 | `config:read` + 目标作用域读权限 | 无 | `scope_type`、`scope_id`、`keys[]` | `items[]`、`version` | `FORBIDDEN` |
| `PUT /configs` | 覆盖或合并指定作用域配置。 | `config:edit` + 目标作用域编辑权限 | `CONFIG_UPDATE` | `scope_type`、`scope_id`、`mode=REPLACE/MERGE`、`configs[].key`、`configs[].value`、`configs[].sensitive` | `version`、`items[]`、脱敏策略 | `VALIDATION_ERROR`、`FORBIDDEN`、`SECRET_POLICY_VIOLATION` |
| `DELETE /configs` | 删除指定作用域配置项，恢复低优先级配置。 | `config:edit` + 目标作用域编辑权限 | `CONFIG_UPDATE` | `scope_type`、`scope_id`、`keys[]`、`reason` | `deleted_keys[]`、`effective_items[]` | `FORBIDDEN`、`NOT_FOUND` |

P0 配置键建议：

| 配置键 | 说明 | 敏感 |
|---|---|---|
| `allow_public_model` | 是否允许使用公有云模型。WP1 只保存、读取、审计。 | 否 |
| `sensitivity_level` | `PUBLIC`、`INTERNAL`、`CONFIDENTIAL`、`STRICT`。 | 否 |
| `default_resource_pool` | 默认执行资源池标识。WP1 不调度资源。 | 否 |
| `execution.api_enabled`、`execution.ui_enabled`、`execution.e2e_enabled` | 执行开关。WP1 只保存策略字段。 | 否 |
| `notification.default_channel` | 默认通知渠道标识。 | 否 |
| `auth.secret_ref` | 环境鉴权凭证引用。 | 是 |

### 5.10 上下文

| 接口 | 用途 | 权限点 | 审计事件 | 关键请求字段 | 关键响应字段 | 主要错误码 |
|---|---|---|---|---|---|---|
| `GET /contexts/current` | 获取当前用户全局上下文。 | 登录用户 | 无 | `include=projects,menus,permissions` | 用户、部门、角色、菜单、授权项目摘要 | `UNAUTHORIZED` |
| `GET /contexts/projects/{projectId}` | 获取项目上下文，供前端和后续 WP 使用。 | 服务身份或 `project:read` | 可选 `CONTEXT_READ` | `include=apps,environments,members,permissions,configs` | 项目、部门、成员、应用、环境、有效配置、调用方权限 | `FORBIDDEN`、`NOT_FOUND` |
| `GET /contexts/applications/{appId}` | 获取应用上下文。 | 服务身份或 `application:read` | 可选 `CONTEXT_READ` | `include=environments,owners,configs,permissions` | 应用、项目、负责人、应用环境、策略字段、有效配置 | `FORBIDDEN`、`NOT_FOUND` |
| `GET /contexts/environments/{envId}` | 获取环境运行上下文。 | 服务身份或 `environment:read` | 可选 `CONTEXT_READ` | `caller_type=FRONTEND/EXECUTOR/AGENT`、`include_variables`、`include_configs` | 环境、项目、应用、执行开关、变量脱敏列表、SecretProvider 引用策略 | `FORBIDDEN`、`NOT_FOUND`、`INVALID_STATE` |
| `POST /contexts/permission-check` | 服务间批量权限校验。 | 服务身份 | `ACCESS_DENIED` 仅拒绝时 | `checks[].user_id`、`checks[].permission`、`checks[].scope_type`、`checks[].scope_id` | `results[].allowed`、`reason`、`matched_bindings` | `FORBIDDEN`、`VALIDATION_ERROR` |

上下文 API 边界：

1. 上下文 API 只返回 WP1 权威对象、权限摘要、策略字段和脱敏配置，不返回后续 WP 的资产、执行、报告数据。
2. 后续 WP 可以缓存上下文，但权限、用户状态、角色绑定变更后必须支持失效。P0 建议缓存不超过 5 分钟，并在 `auth_version`、`context_version` 变化时主动刷新。
3. `contexts/environments` 不返回敏感明文。执行服务如需真实凭证，应由具备权限的后续 SecretProvider 读取通道处理，WP1 P0 只返回引用策略。

### 5.11 审计

| 接口 | 用途 | 权限点 | 审计事件 | 关键请求字段 | 关键响应字段 | 主要错误码 |
|---|---|---|---|---|---|---|
| `POST /audit/events` | 后续 WP 内部写入审计事件。 | 服务身份 + `audit:write_internal` | `INTERNAL_AUDIT_WRITE` | `action`、`resource_type`、`resource_id`、`scope_type`、`scope_id`、`result`、`before_json`、`after_json`、`diff_json`、`reason` | `event_id`、`status` | `FORBIDDEN`、`VALIDATION_ERROR`、`AUDIT_WRITE_PENDING` |
| `GET /audit/events` | 查询授权范围内审计日志。 | `audit:read` | 大范围查询记 `AUDIT_QUERY` | `start_time`、`end_time`、`actor_user_id`、`action`、`resource_type`、`resource_id`、`scope_type`、`scope_id`、`result`、分页 | 审计分页、脱敏摘要 | `FORBIDDEN`、`VALIDATION_ERROR` |
| `GET /audit/events/{eventId}` | 查看审计详情。 | `audit:read` | `AUDIT_QUERY` | 无 | 审计详情、脱敏 before/after/diff、trace 信息 | `FORBIDDEN`、`NOT_FOUND` |
| `POST /audit/events/export` | 创建审计导出任务。 | `audit:export` | `AUDIT_EXPORT` | 查询筛选条件、`format=CSV`、`reason` | `export_task_id`、`status` | `FORBIDDEN`、`VALIDATION_ERROR` |
| `GET /audit/events/export/{taskId}` | 查询审计导出任务。 | `audit:export` | 无 | 无 | `task_id`、`status`、`download_url`、`expired_at` | `FORBIDDEN`、`NOT_FOUND` |

P0 审计事件编码：

| 分类 | 事件 |
|---|---|
| 认证会话 | `AUTH_LOGIN_SUCCESS`、`AUTH_LOGIN_FAILED`、`AUTH_LOGOUT`、`AUTH_TOKEN_REFRESH`、`AUTH_SESSION_REVOKE`、`ACCESS_DENIED` |
| 部门 | `DEPARTMENT_CREATE`、`DEPARTMENT_UPDATE`、`DEPARTMENT_ENABLE`、`DEPARTMENT_DISABLE`、`DEPARTMENT_MEMBER_ADD`、`DEPARTMENT_MEMBER_REMOVE`、`DEPARTMENT_MANAGER_ADD`、`DEPARTMENT_MANAGER_REMOVE` |
| 用户角色 | `USER_CREATE`、`USER_INVITE`、`USER_UPDATE`、`USER_ENABLE`、`USER_DISABLE`、`USER_LOCK`、`ROLE_CREATE`、`ROLE_UPDATE`、`ROLE_PERMISSION_UPDATE`、`ROLE_BIND`、`ROLE_UNBIND` |
| 项目应用 | `PROJECT_CREATE`、`PROJECT_UPDATE`、`PROJECT_ARCHIVE`、`PROJECT_DISABLE`、`PROJECT_ENABLE`、`PROJECT_DEPARTMENT_UPDATE`、`PROJECT_MEMBER_ADD`、`PROJECT_MEMBER_REMOVE`、`APPLICATION_CREATE`、`APPLICATION_UPDATE`、`APPLICATION_ENABLE`、`APPLICATION_DISABLE`、`APPLICATION_OWNER_BIND`、`APPLICATION_OWNER_UNBIND` |
| 环境配置 | `ENVIRONMENT_CREATE`、`ENVIRONMENT_UPDATE`、`ENVIRONMENT_ENABLE`、`ENVIRONMENT_DISABLE`、`ENV_VARIABLE_UPDATE`、`ENV_AUTH_CONFIG_UPDATE`、`CONFIG_UPDATE` |
| 审计 | `AUDIT_QUERY`、`AUDIT_EXPORT`、`INTERNAL_AUDIT_WRITE` |

审计事件结构字段：

| 字段 | 说明 |
|---|---|
| `trace_id` | 链路 ID。 |
| `actor_type` | `USER`、`SERVICE`、`SYSTEM`。 |
| `actor_user_id` | 操作人；系统任务可为空。 |
| `actor_service` | 服务身份。 |
| `actor_ip`、`user_agent` | 来源信息。 |
| `action` | 审计事件编码。 |
| `resource_type`、`resource_id` | 资源类型和资源 ID。 |
| `scope_type`、`scope_id` | 作用域。 |
| `result` | `SUCCESS`、`FAILED`、`DENIED`。 |
| `before_json`、`after_json`、`diff_json` | 变更摘要，敏感字段脱敏。 |
| `reason` | 失败、拒绝或人工操作原因。 |
| `created_at` | 事件时间。 |

## 6. 后续 WP 调用边界

| 调用方 | 可以调用的 WP1 API | 可以获得 | 禁止事项 |
|---|---|---|---|
| WP2 模型接入层 | `/contexts/projects/{projectId}`、`/contexts/applications/{appId}`、`/configs/effective`、`/audit/events` | 项目/应用/环境作用域、`allow_public_model`、`sensitivity_level`、审计写入 | WP2 在 strict 模式消费上下文策略并取更严格路由策略；不绕过 WP1 权限；不直接读取配置表；不在审计中写 Prompt 或密钥明文。 |
| WP3 测试资产模型 | `/contexts/current`、`/contexts/projects/{projectId}`、`/contexts/permission-check`、`/audit/events` | 项目、应用、环境、成员、权限上下文 | 不直接维护 WP1 项目成员；资产表可保存 WP1 资源 ID，但校验必须走 WP1。 |
| WP4/WP11 输入与企业连接器 | `/contexts/projects/{projectId}`、`/configs/effective`、`/audit/events` | 第三方凭证引用、权限映射基础、审计写入 | 不要求 WP1 返回第三方凭证明文；连接器业务配置不落 WP1 通用配置以外字段。 |
| WP8 测试数据与账号池 | `/contexts/environments/{envId}`、`/configs/effective`、`/audit/events` | 应用环境、凭证引用、项目隔离、账号使用审计入口 | 不把账号租借、数据清理任务写入 WP1；不通过环境详情读取账号密码明文。 |
| WP9 执行编排与任务调度 | `/contexts/environments/{envId}`、`/environments/{envId}/use-check`、`/configs/effective`、`/audit/events` | 环境可用性、执行开关、资源池标识、审计入口 | 不由 WP1 发起调度；不忽略停用环境和 PROD 默认禁用策略。 |
| WP10 报告与失败诊断 | `/contexts/projects/{projectId}`、`/contexts/environments/{envId}`、`/contexts/permission-check`、`/audit/events` | 报告查看权限、用户/项目/环境上下文、审计写入 | 不建立独立权限体系绕过 WP1。 |

内部调用统一要求：

1. 使用服务令牌：`Authorization: Bearer <service_token>`。
2. 携带委托用户：`X-Delegated-User-Id`。
3. 携带调用服务：`X-Caller-Service`。
4. 携带链路 ID：`X-Trace-Id`。
5. WP1 对服务身份和委托用户同时鉴权。服务只能调用被授权的内部 API，委托用户必须对目标资源具备相应读、用或写权限。

## 7. 研发拆解提示

1. 先实现统一响应、错误码、Trace ID、用户上下文注入和鉴权拦截器，再实现领域 API。
2. 初始化、登录、会话撤销、用户停用失效是 P0 闭环门禁。
3. 角色绑定、成员关系和状态流必须同步刷新权限缓存或递增 `auth_version`。
4. 环境变量写入需要先完成 `SecretProvider` 抽象和本地加密实现。
5. 审计失败不得阻断主流程，但必须进入 outbox 补偿；密钥保存失败必须阻断主流程。
6. OpenAPI 契约测试需覆盖统一响应、分页、错误码、权限拒绝、资源作用域越权、状态流、审计事件和敏感字段脱敏。
