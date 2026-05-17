# WP1 单平台 P0 交付版 - PRD 与架构补充

| 项目 | 内容 |
|---|---|
| 工作包 | WP1 平台基础底座 |
| 当前口径 | 单平台、多部门、多项目、多应用、多环境 |
| 适用阶段 | MVP P0 可持续研发底座 |
| 更新时间 | 2026-05-17 |

## 1. 产品边界

WP1 不建设平台实例分层体系，不提供实例管理、实例管理员、跨实例隔离、实例级配置和实例级审计。本阶段目标是先交付 Web 管理后台的基础控制面，让后续 WP 可以复用初始化、认证、组织、项目、应用、环境、集成、审计和系统设置能力。

P0 必须能完成以下闭环：

1. 初始化首个 `SuperAdmin`。
2. 使用初始化账号登录 Web 管理后台。
3. 刷新登录会话、注销并撤销当前会话。
4. 查看平台健康状态和当前用户。
5. 查看部门、用户、项目、应用、环境、集成、审计、设置管理视图。
6. 创建部门、用户、项目、应用、环境样板数据。
7. 启用、停用用户，重置用户密码。
8. 写操作能在审计视图中追踪。
9. 未登录用户不能访问管理 API。

## 2. 角色与权限口径

P0 使用轻量角色口径，先满足样板后台准入和后续 RBAC 扩展：

| 角色 | 用途 |
|---|---|
| `SuperAdmin` | 首个初始化管理员，具备平台全局管理入口。 |
| `PlatformAdmin` | 平台管理员，后续承接日常配置和成员治理。 |
| `DepartmentManager` | 部门管理者，后续管理部门成员和部门范围授权。 |
| `ProjectOwner` | 项目负责人，后续管理项目、成员、应用和环境。 |
| `AppOwner` | 应用负责人，后续管理应用接入和应用环境。 |
| `Tester` | 测试执行和资产使用者。 |
| `Developer` | 研发协作角色。 |
| `Auditor` | 审计查看者。 |

当前代码阶段管理 API 已以 Bearer Token 和 RBAC 权限点双重保护。`local` profile 使用内置角色权限表，`db` profile 从 `rbac_role_permission` 查询角色权限，并对项目、应用、环境、审计和设置列表执行资源作用域过滤。前端已接入菜单权限和按钮权限规则，所有接口仍以后端鉴权为准。

## 3. 当前已实现 API

| 能力 | 接口 |
|---|---|
| 健康检查 | `GET /api/v1/health` |
| 初始化 | `POST /api/v1/bootstrap/super-admin` |
| 登录 | `POST /api/v1/auth/login` |
| 刷新令牌 | `POST /api/v1/auth/refresh` |
| 注销 | `POST /api/v1/auth/logout` |
| 当前用户 | `GET /api/v1/auth/me` |
| 部门 | `GET/POST /api/v1/management/departments` |
| 用户 | `GET/POST /api/v1/management/users` |
| 用户启用 | `POST /api/v1/management/users/{username}/enable` |
| 用户停用 | `POST /api/v1/management/users/{username}/disable` |
| 用户重置密码 | `POST /api/v1/management/users/{username}/reset-password` |
| 项目 | `GET/POST /api/v1/management/projects` |
| 应用 | `GET/POST /api/v1/management/applications` |
| 环境 | `GET/POST /api/v1/management/environments` |
| 集成 | `GET/POST/PATCH /api/v1/management/integrations`、`PATCH /api/v1/management/integrations/{key}/status` |
| 审计 | `GET /api/v1/management/audit-logs`，支持 `search`、`actor`、`action`、`resource_type`、`result`、`start_time`、`end_time` 筛选 |
| 设置 | `GET/POST/PATCH /api/v1/management/settings`、`PATCH /api/v1/management/settings/{key}/status` |
| OpenAPI | `GET /v3/api-docs`、`GET /swagger-ui.html` |

所有响应统一使用：

```json
{
  "code": "OK",
  "message": "success",
  "trace_id": "trc_xxx",
  "data": {}
}
```

## 4. 架构现状

后端模块：

| 模块 | 说明 |
|---|---|
| `common` | 统一响应、错误码、Trace ID、全局异常处理。 |
| `bootstrap` | 首个 `SuperAdmin` 初始化。 |
| `auth` | 登录、刷新令牌、注销、当前用户、Bearer Token 签发和校验、会话存储。 |
| `authorization` | RBAC 权限解析和管理 API 权限校验；支持 local 内置权限和 db 持久化权限。 |
| `management` | P0 管理视图聚合、样板创建动作和账号生命周期；`local` profile 使用内存实现，`db` profile 使用 PostgreSQL 持久化实现。 |
| `security` | Spring Security 入口保护和公开接口白名单。 |
| `common.openapi` | OpenAPI 标题、版本、Bearer 安全方案和契约测试准入。 |

前端模块：

| 模块 | 说明 |
|---|---|
| `portal-web` | React + TypeScript + Vite 管理后台。 |
| `api/*` | 健康检查、初始化、认证、管理接口客户端。 |
| `App.tsx` | 左侧导航、顶部登录、概览、管理页面和快速创建。 |

数据库：

| 内容 | 说明 |
|---|---|
| 迁移脚本 | `db/migration/wp1/V20260516_001~003`、`db/migration/wp1/V20260517_004~005`。 |
| 校验脚本 | `db/validation/run_wp1_db_validation.sh`。 |
| 单平台校验 | 校验无旧实例隔离表、无业务实例隔离字段、无实例级权限点。 |

运行模式：

| 模式 | 用途 | 数据来源 |
|---|---|---|
| `local` | 快速开发、前端联调、无数据库演示 | 内存样板数据 |
| `db` | 可持续研发、真实迁移和持久化烟测 | PostgreSQL + Flyway |

## 5. 准出标准

P0 当前准出以以下命令和浏览器验收为准：

```bash
mvn -pl platform-api test
cd portal-web && npm run test
cd portal-web && npm run build
bash db/validation/run_wp1_db_validation.sh
WP1_BOOTSTRAP_TOKEN=local-init-token bash scripts/wp1_db_profile_smoke.sh
```

浏览器验收：

1. 打开 `http://127.0.0.1:5173/`。
2. 登录 `admin_user / PlainPassword123`。
3. 概览页能显示管理数据统计。
4. 进入组织部门页，创建 `WP1验收部门`。
5. 表格新增记录显示负责人为当前用户。
6. 进入审计日志页，能看到“创建部门 / WP1验收部门 / 成功”。
7. 使用缺少目标权限的角色访问管理写接口时，后端返回 `FORBIDDEN`。

db profile HTTP 烟测：

1. `docker compose -f infra/docker-compose.wp1.yml up -d postgres`。
2. 使用 `-Dspring-boot.run.profiles=db` 启动后端。
3. Flyway 自动执行 `V20260516_001~003` 和 `V20260517_004~005`。
4. 初始化 `SuperAdmin` 并登录。
5. 登录响应包含 `access_token`、`refresh_token` 和 `session_id`。
6. 刷新令牌后旧访问令牌失效，注销后当前访问令牌失效。
7. 创建部门和用户。
8. 启用、停用用户，重置密码后可使用新密码登录。
9. 部门、用户列表可回读 PostgreSQL 数据。
10. 审计日志显示资源名称和操作结果，并支持按 actor/action/resource_type/result/start_time/end_time 组合筛选。
11. `/v3/api-docs` 正常生成，且包含认证、用户生命周期和管理 API 关键路径。

## 6. 1～8 项收敛结果

以下事项已在本轮 WP1 收敛中完成，不再作为当前剩余项：

1. 设置分页 CRUD、详情、编辑和启停。
2. RBAC 资源作用域过滤、菜单权限和按钮权限。
3. 登录成功/失败、账号生命周期和资料编辑审计。
4. 审计成功、失败、拒绝、变更结果和 before/after/diff 字段。
5. 敏感设置明文拒绝策略。
6. 设置 CRUD 等关键路径 OpenAPI 契约断言。
7. db profile smoke 对设置、敏感配置、失败登录、资源作用域过滤和授权拒绝的覆盖。
8. 预发/生产应用数据库角色校验脚本。

后续 WP1 迭代主要剩余在更深的产品化能力，例如角色定义管理、审计导出任务、Redis 会话清理、数据保留策略和更复杂的审批/组织同步。
