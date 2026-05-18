# WP1 审计事件字典

| 项目 | 内容 |
|---|---|
| 工作包 | WP1 平台基础底座 |
| 当前口径 | 单平台、多部门、多项目、多应用、多环境 |
| 面向阶段 | P0 研发收口、接口自动化、审计验收、后续 WP 接入 |
| 版本 | v0.1 |
| 日期 | 2026-05-17 |

## 1. 目标

WP1 审计用于回答三个问题：

1. 谁在什么时间做了什么管理动作。
2. 动作影响了哪个资源，结果是成功、失败还是拒绝。
3. 后续 WP2/WP3/WP8/WP11 写入审计时是否能复用同一套字段和结果语义。

P0 阶段先保证初始化、认证、账号生命周期、角色绑定、基础管理对象创建、权限拒绝和内部服务写入可追踪。审计事件不承担业务报告、问题诊断、执行证据归档，这些归属后续 WP。

## 2. 字段规范

| 字段 | 必填 | 说明 |
|---|---|---|
| `traceId` / `trace_id` | 是 | API 响应使用 `traceId`；数据库审计列使用 `trace_id`，用于串联 API 日志和审计记录。 |
| `actor_type` | 是 | `USER`、`SYSTEM`、`SERVICE`。 |
| `actor_user_id` | 否 | 用户操作时填写；系统或服务身份可为空。 |
| `action` | 是 | 当前代码展示中文动作；后续可增加 `event_code` 保存冻结编码。 |
| `resource_type` | 是 | `user`、`department`、`project`、`application`、`environment`、`rbac_role_binding`、`permission`、`platform_context` 等。 |
| `resource_id` | 是 | 资源 ID、用户名、权限点或外部业务 ID。 |
| `scope_type` | 是 | P0 固定为 `PLATFORM`，后续项目/应用/环境细粒度授权可扩展。 |
| `scope_id` | 否 | P0 可为空。 |
| `result` | 是 | `SUCCESS`、`FAILED`、`DENIED`。管理台可展示为成功、失败、拒绝。 |
| `reason` | 否 | 失败或拒绝原因，不得包含密码、Token、Secret 明文。 |
| `before_json` | 否 | 变更前摘要，P0 可为空。 |
| `after_json` | 否 | 变更后摘要，只保存名称、状态、角色等非敏感字段。 |

## 3. P0 事件清单

| 建议事件编码 | 当前动作 | 资源类型 | 结果 | 触发点 | 验收要求 |
|---|---|---|---|---|---|
| `SUPER_ADMIN_INIT` | `SUPER_ADMIN_INIT` | `rbac_role_binding` | `SUCCESS` | 首个 SuperAdmin 初始化成功 | 重复初始化被拒绝；成功记录可按用户回查。 |
| `USER_CREATE` | `USER_CREATE` / `邀请用户` | `iam_user` / `user` | `SUCCESS` | 初始化用户或管理台创建用户 | 不记录初始密码；用户状态、角色摘要可回查。 |
| `AUTH_PASSWORD_CHANGE` | `修改密码` | `user` | `SUCCESS` | 当前用户修改密码 | 成功后当前会话失效；审计能按用户名搜索。 |
| `USER_ENABLE` | `启用用户` | `user` | `SUCCESS` | 管理员启用用户 | 用户状态变更后列表可回读。 |
| `USER_DISABLE` | `停用用户` | `user` | `SUCCESS` | 管理员停用用户 | 不能停用当前账号；成功后目标账号旧会话失效。 |
| `USER_PASSWORD_RESET` | `重置密码` | `user` | `SUCCESS` | 管理员重置用户密码 | 不记录新密码；目标账号需重新登录或修改密码。 |
| `ROLE_BIND` | `分配角色` | `rbac_role_binding` | `SUCCESS` | 给用户绑定角色 | 记录用户和角色编码；权限变更后立即生效。 |
| `ROLE_UNBIND` | `解绑角色` | `rbac_role_binding` | `SUCCESS` | 给用户解绑角色 | 记录用户和角色编码；权限变更后立即生效。 |
| `DEPARTMENT_CREATE` | `创建部门` | `department` | `SUCCESS` | 创建部门 | 记录部门名称，不记录无关人员敏感信息。 |
| `PROJECT_CREATE` | `创建项目` | `project` | `SUCCESS` | 创建项目 | 记录项目名称。 |
| `APPLICATION_CREATE` | `登记应用` | `application` | `SUCCESS` | 登记应用 | 记录应用名称。 |
| `ENVIRONMENT_CREATE` | `新增环境` | `environment` | `SUCCESS` | 创建环境 | 记录环境名称；敏感变量不在 WP1 当前动作中写入。 |
| `AUTHZ_DENIED` | `权限校验` | `permission` | `DENIED` | 后端权限校验失败 | 必须记录被拒绝权限点，例如 `user:read`。 |
| `INTERNAL_AUDIT_WRITE` | 外部提交动作 | 调用方传入 | 调用方传入 | `/api/v1/audit/events` 内部审计写入 | 必须由服务令牌保护；结果值归一化为 `SUCCESS/FAILED/DENIED`。 |

## 4. P0 验收规则

1. 所有 P0 写操作成功后必须产生 `SUCCESS` 审计。
2. 所有后端权限拒绝必须产生 `DENIED` 审计。
3. 密码、Token、Cookie、Secret、Access Key 不得进入 `reason`、`before_json`、`after_json`、应用日志和导出文件。
4. 管理台审计列表必须支持按动作、资源、原因或操作者搜索。
5. WP2/WP3 在同一 `platform-api` 内通过 Spring 应用服务写入审计；外部集成或未来拆分服务不得直接写 WP1 数据表，必须通过 `/api/v1/audit/events` 或等价内部入口写入审计。
6. 当前 P0 可继续使用中文动作展示；进入产品化迭代时建议新增 `event_code` 字段，中文展示由前端或字典转换。

## 5. 后续增强

| 能力 | 优先级 | 说明 |
|---|---|---|
| `event_code` 字段 | P1 | 将当前 `action` 展示文案和机器可读编码拆开。 |
| 审计详情页 | P1 | 展示资源摘要、变更 diff、Trace ID、失败原因。 |
| 审计导出 | P1 | 按权限范围导出，强制脱敏并记录导出审计。 |
| Outbox 补偿 | P1 | 对业务成功但审计写入失败的场景做补偿和告警。 |
| 审计归档 | P2 | 按保留周期归档或分区，避免长期查询退化。 |
