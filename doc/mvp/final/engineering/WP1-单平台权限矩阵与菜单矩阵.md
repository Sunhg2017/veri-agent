# WP1 单平台权限矩阵与菜单矩阵

| 项目 | 内容 |
|---|---|
| 工作包 | WP1 平台基础底座 |
| 当前口径 | 单平台、多部门、多项目、多应用、多环境 |
| 依据代码 | `BuiltinPermissionCatalog`、`ManagementController`、`portal-web/src/permissions.ts` |
| 版本 | v0.1 |
| 日期 | 2026-05-21 |

## 1. 当前冻结角色

| 角色 | 定位 |
|---|---|
| `SuperAdmin` | 平台初始化与最高管理角色，具备 WP1 P0 全量管理权限。 |
| `PlatformAdmin` | 平台日常管理角色，管理组织、用户、项目、应用、环境、配置、审计和授权。 |
| `DepartmentManager` | 部门负责人，管理部门信息和部门成员，查看关联项目、应用、环境、配置和审计。 |
| `ProjectOwner` | 项目负责人，管理绑定项目、项目成员、应用、环境、配置、审计和项目范围授权。 |
| `AppOwner` | 应用负责人，管理绑定应用、应用专属环境、配置、审计和应用范围授权。 |
| `Tester` | 测试人员，查看授权项目、应用、环境和配置，可使用授权环境。 |
| `Developer` | 研发人员，查看授权项目、应用、环境和配置，不默认具备环境使用权。 |
| `Auditor` | 审计角色，只读查看授权范围内资源摘要、角色摘要和审计日志。 |

## 2. P0 权限点

| 资源 | 权限点 |
|---|---|
| department | `department:read`、`department:create`、`department:edit`、`department:enable`、`department:disable`、`department:member_manage` |
| user | `user:read`、`user:create`、`user:edit`、`user:enable`、`user:disable`、`user:lock`、`user:assign_role`、`user:reset_password` |
| role | `role:read`、`role:create`、`role:edit`、`role:bind`、`role:unbind` |
| project | `project:read`、`project:create`、`project:edit`、`project:archive`、`project:disable`、`project:member_manage` |
| application | `application:read`、`application:create`、`application:edit`、`application:disable`、`application:owner_manage` |
| environment | `environment:read`、`environment:create`、`environment:edit`、`environment:disable`、`environment:use`、`environment:user_manage` |
| config | `config:read`、`config:edit` |
| audit | `audit:read`、`audit:export`、`audit:write_internal` |
| context | `context:read`、`context:switch`、`context:effective_read` |
| secret | `secret:reference`、`secret:read`、`secret:manage`、`secret:rotate`、`secret:disable` |

说明：实例级权限点已从当前 P0 口径移除。旧实例边界 API、实例管理员角色、业务实例隔离字段和实例隔离表均不得重新进入 WP1 单平台模型。

## 3. 角色默认权限

| 角色 | 默认权限摘要 |
|---|---|
| `SuperAdmin` | WP1 P0 管理权限，含角色创建/编辑、审计内部写入、全部基础资源创建、状态变更、资源授权和 Secret 引用管理。 |
| `PlatformAdmin` | 组织、用户、角色绑定、项目、应用、环境、配置、审计、上下文、资源授权和 Secret 引用管理；不可创建或编辑内置角色定义。 |
| `DepartmentManager` | 部门读写和成员管理；用户基础读写；项目、应用、环境、配置、审计和上下文只读/摘要能力。 |
| `ProjectOwner` | 项目读写、归档、停用和成员管理；项目下应用、环境、配置、审计、角色绑定、资源授权、Secret 引用和上下文能力。 |
| `AppOwner` | 应用读写和停用；应用负责人维护、应用专属环境授权、配置、审计、角色绑定、Secret 引用和上下文能力。 |
| `Tester` | 项目、应用、环境、配置和上下文读取；可使用授权环境。 |
| `Developer` | 项目、应用、环境、配置和上下文读取；不可默认使用环境。 |
| `Auditor` | 部门、用户、项目、应用、环境、配置、角色、审计和上下文读取；可导出审计。 |

## 4. 菜单可见性

| 页面 | 读权限 | 当前前端路由 |
|---|---|---|
| 系统概览 | 登录前后均可见 | `overview` |
| 组织部门 | `department:read` | `organizations` |
| 用户与权限 | `user:read` | `users` |
| 项目空间 | `project:read` | `projects` |
| 应用管理 | `application:read` | `applications` |
| 环境管理 | `environment:read` | `environments` |
| 集成配置 | `config:read` | `integrations` |
| 审计日志 | `audit:read` | `audit` |
| 系统设置 | `config:read`；Secret 侧栏另需 `secret:read` | `settings` |

前端菜单隐藏只用于体验优化，所有接口必须以后端鉴权结果为准。当前 `portal-web/src/permissions.ts` 是菜单权限和按钮权限的唯一前端规则来源。

## 5. 当前按钮权限

| 页面 | 操作 | 权限 |
|---|---|---|
| 组织部门 | 新增部门 | `department:create` |
| 用户与权限 | 邀请用户 | `user:create` |
| 用户与权限 | 启用账号 | `user:enable` |
| 用户与权限 | 停用账号 | `user:disable` |
| 用户与权限 | 重置密码 | `user:reset_password` |
| 用户与权限 | 分配角色 | `user:assign_role` + `role:bind` |
| 用户与权限 | 解绑角色 | `user:assign_role` + `role:unbind` |
| 项目空间 | 创建项目 | `project:create` |
| 应用管理 | 登记应用 | `application:create` |
| 环境管理 | 新增环境 | `environment:create` |
| 系统设置 | Secret 引用摘要 | `secret:read` |
| 系统设置 | 创建 Secret 引用 | `secret:manage` |
| 系统设置 | 轮换 Secret 引用 | `secret:rotate` |
| 系统设置 | 撤销 Secret 引用 | `secret:disable` |

## 6. P0 自动化验收要求

1. `Tester` 访问 `GET /api/v1/management/users` 必须返回 403，并产生 `权限校验` / `DENIED` 审计。
2. 缺少页面读权限时，前端不发起对应管理列表请求。
3. 缺少按钮权限时，前端不展示对应操作控件。
4. 角色绑定和解绑必须同时校验 `user:assign_role` 与 `role:bind`/`role:unbind`。
5. 权限变更、停用用户、重置密码必须提升 `auth_version` 或撤销会话，使旧权限不会继续生效。
6. 自定义角色创建和编辑必须校验 `role:create`/`role:edit`，内置角色不可编辑或停用，且权限集合不得超过操作者自身已拥有的权限点。
