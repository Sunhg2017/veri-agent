# Veri Agent

AI 驱动的端到端企业级测试平台。当前仓库已具备 WP1 平台基础底座样板，并开始交付 WP2 模型接入层：模型配置、Prompt 版本、统一调用、脱敏校验、预算护栏、失败降级、成本记录、调用审计和 CSV 导出。

## 当前 WP1 口径

- 单平台，不建设平台实例分层体系。
- 支持多部门、多项目、多应用、多环境。
- 后端基础包路径：`com.songhg.veri.agent`。
- P0 样板已支持初始化、登录、刷新令牌、注销、管理视图查询、部门详情/编辑/启停、用户详情/资料编辑、项目/应用/环境正式创建、详情、编辑和状态流，设置分页 CRUD/启停，项目成员、应用负责人、环境授权用户与资源级角色绑定，账号启停、锁定/解锁、重置密码和审计追踪。
- 管理 API 已接入 RBAC 权限校验，`local` 模式使用内置角色权限，`db` 模式从 `rbac_role_permission` 解析，并对项目、应用、环境、审计和设置列表执行资源作用域过滤；前端菜单和按钮权限已有规则与测试。
- OpenAPI 已声明 WP1 控制面标题、版本和 Bearer 鉴权方案，并通过契约测试保护认证、管理、账号生命周期和设置 CRUD 关键路径。
- 已提供 WP2 严格模式所需的内部 context 校验和审计写入端点，使用 `WP1_SERVICE_TOKEN` 保护。
- 前端管理台已接入部门、用户、项目、应用、环境、设置详情回读、基础字段编辑、状态流转、项目成员、应用负责人和环境授权用户操作面板，和资源级 API 对齐。
- `local` profile 使用内存数据，方便前端和接口样板开发。
- `db` profile 使用 PostgreSQL + Flyway，已接入部门、用户、项目、应用、环境、设置、会话、资源级角色绑定和审计的持久化路径；部门已支持详情、编辑和启停，用户已支持详情和资料编辑，项目/应用/环境已支持编码、归属、敏感级别、公有云模型开关、默认访问地址、环境作用域、详情、编辑、状态流和协作授权字段；审计已持久化失败/拒绝/变更结果和 before/after/diff 字段，敏感设置明文写入会被拒绝。

## 模块

| 路径 | 说明 |
|---|---|
| `platform-api` | Spring Boot 3.5 + Java 21 后端控制面服务。 |
| `model-access` | WP2 Spring Boot 模型接入服务。 |
| `portal-web` | React + TypeScript + Vite Web 管理后台。 |
| `db/migration/wp1` | WP1 PostgreSQL/Flyway 迁移脚本。 |
| `db/migration/wp2` | WP2 模型接入层迁移脚本。 |
| `db/validation` | WP1/WP2 数据库结构、种子、安全约束校验脚本。 |
| `doc/mvp/final` | WP1/WP2 PRD、架构、工程补充和交付验收文档。 |
| `doc/mvp/final/engineering/WP1-当前可持续研发底座交付说明.md` | 当前 WP1 单平台实现、准出命令、验证结论和后续研发入口。 |
| `doc/mvp/final/engineering/WP1-单平台权限矩阵与菜单矩阵.md` | 当前 WP1 单平台权限、菜单和按钮规则。 |
| `doc/mvp/final/engineering/WP1-审计事件字典.md` | 当前 WP1 P0 审计事件、字段和验收规则。 |
| `infra/docker-compose.wp1.yml` | 本地 PostgreSQL 研发环境。 |
| `scripts/wp1_db_profile_smoke.sh` | 针对已启动 db profile 后端的 HTTP 烟测脚本。 |
| `scripts/wp1_quality_gate.sh` | WP1 本地质量门禁入口，串联后端测试、前端测试、前端构建和数据库校验。 |
| `scripts/wp2_model_access_smoke.sh` | 针对已启动 WP2 服务的 HTTP 烟测脚本。 |

## 本地内存模式

```bash
WP1_BOOTSTRAP_TOKEN=local-init-token \
WP1_AUTH_TOKEN_SECRET=local-auth-secret \
mvn -pl platform-api spring-boot:run
```

```bash
cd portal-web
npm run dev -- --host 127.0.0.1
```

访问：

- 前端：http://127.0.0.1:5173
- 后端健康：http://127.0.0.1:8080/api/v1/health
- Swagger：http://127.0.0.1:8080/swagger-ui.html

## PostgreSQL 持久化模式

```bash
docker compose -f infra/docker-compose.wp1.yml up -d postgres
```

```bash
WP1_BOOTSTRAP_TOKEN=local-init-token \
WP1_AUTH_TOKEN_SECRET=local-auth-secret \
WP1_DATASOURCE_URL=jdbc:postgresql://localhost:5432/veri_agent \
WP1_DATASOURCE_USERNAME=veri_agent \
WP1_DATASOURCE_PASSWORD=veri_agent_dev \
mvn -pl platform-api spring-boot:run -Dspring-boot.run.profiles=db
```

初始化首个管理员：

```bash
curl -X POST http://127.0.0.1:8080/api/v1/bootstrap/super-admin \
  -H 'Content-Type: application/json' \
  -d '{
    "bootstrap_token": "local-init-token",
    "username": "admin_user",
    "password": "PlainPassword123",
    "display_name": "平台管理员",
    "email": "admin@example.com"
  }'
```

创建项目、应用和应用专属环境：

```bash
TOKEN="$(curl -s -X POST http://127.0.0.1:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin_user","password":"PlainPassword123"}' \
  | jq -r '.data.access_token')"

curl -X POST http://127.0.0.1:8080/api/v1/management/projects \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"code":"wp1-demo","name":"WP1 Demo","sensitivity_level":"CONFIDENTIAL","allow_public_model":false}'

curl -X POST http://127.0.0.1:8080/api/v1/management/applications \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"code":"wp1-demo-web","name":"WP1 Demo Web","project":"wp1-demo","app_type":"Web","default_api_base_url":"https://api.demo.local","sensitivity_level":"STRICT","allow_public_model":false}'

curl -X POST http://127.0.0.1:8080/api/v1/management/environments \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"code":"wp1-demo-stg","name":"WP1 Demo Staging","project":"wp1-demo","application":"wp1-demo-web","scope_type":"APPLICATION","env_type":"STAGING","web_url":"https://demo.local","api_base_url":"https://api.demo.local"}'
```

## WP2 模型接入层

```bash
WP2_SERVICE_TOKEN=local-model-access-token \
mvn -pl model-access spring-boot:run
```

PostgreSQL 持久化模式：

```bash
WP2_SERVICE_TOKEN=local-model-access-token \
WP2_DATASOURCE_URL=jdbc:postgresql://localhost:5432/veri_agent \
WP2_DATASOURCE_USERNAME=veri_agent \
WP2_DATASOURCE_PASSWORD=veri_agent_dev \
mvn -pl model-access spring-boot:run -Dspring-boot.run.profiles=db
```

启用 WP2 对 WP1 的严格上下文校验和审计写入时，保持 `WP2_PLATFORM_API_SERVICE_TOKEN` 与 WP1 的 `WP1_SERVICE_TOKEN` 一致。

访问：

- WP2 健康：http://127.0.0.1:8081/api/v1/model-access/health
- WP2 Swagger：http://127.0.0.1:8081/swagger-ui.html

调用 WP2 API 时使用服务令牌，并携带 WP1 约定的服务调用头：

```bash
curl -X POST http://127.0.0.1:8081/api/v1/model-access/invocations \
  -H 'Authorization: Bearer local-model-access-token' \
  -H 'X-Caller-Service: wp5-test-design' \
  -H 'X-Delegated-User-Id: user-001' \
  -H 'Content-Type: application/json' \
  -d '{
    "project_id": "project-001",
    "prompt_key": "test-case-design",
    "prompt_variables": {"context": "登录流程"},
    "messages": [{"role": "user", "content": "生成 3 条冒烟测试点"}],
    "allow_public_model": false,
    "sensitivity_level": "INTERNAL"
  }'
```

调用日志支持分页、筛选、敏感级别审计和成本汇总：

```bash
curl 'http://127.0.0.1:8081/api/v1/model-access/invocations?project_id=project-001&page=0&size=20' \
  -H 'Authorization: Bearer local-model-access-token' \
  -H 'X-Caller-Service: wp5-test-design' \
  -H 'X-Delegated-User-Id: user-001'

curl 'http://127.0.0.1:8081/api/v1/model-access/invocations/summary?project_id=project-001' \
  -H 'Authorization: Bearer local-model-access-token' \
  -H 'X-Caller-Service: wp5-test-design' \
  -H 'X-Delegated-User-Id: user-001'

curl 'http://127.0.0.1:8081/api/v1/model-access/invocations/export?project_id=project-001' \
  -H 'Authorization: Bearer local-model-access-token' \
  -H 'X-Caller-Service: wp5-test-design' \
  -H 'X-Delegated-User-Id: user-001'
```

## 验证

```bash
mvn test
```

```bash
cd portal-web
npm run test
```

```bash
cd portal-web
npm run build
```

```bash
bash db/validation/run_wp1_db_validation.sh
```

```bash
WP1_BOOTSTRAP_TOKEN=local-init-token bash scripts/wp1_db_profile_smoke.sh
```

```bash
bash scripts/wp1_quality_gate.sh
```

后端契约测试会导出 OpenAPI 快照到：

```text
build/openapi/wp1-v1.json
```

```bash
bash db/validation/run_wp2_db_validation.sh
```

```bash
WP2_SERVICE_TOKEN=local-model-access-token bash scripts/wp2_model_access_smoke.sh
```

```bash
bash scripts/wp2_quality_gate.sh
```

当前额外烟测结论：

- `db` profile 可自动执行 WP1 Flyway 迁移。
- 可完成 SuperAdmin 初始化与登录。
- 登录响应包含 `access_token`、`refresh_token` 和 `session_id`，刷新令牌会轮换会话，注销会撤销当前会话。
- 可在 PostgreSQL 中创建部门、用户、项目、应用、环境、集成配置和系统设置，并维护项目成员、应用负责人和环境授权用户。
- 可查询用户详情、启用、停用、锁定/解锁用户，并通过重置密码激活账号；停用、锁定/解锁、重置密码会提升 `auth_version`，db profile 会话校验会联动用户当前状态和版本，使旧访问令牌失效。
- 管理列表可回读持久化数据。
- 审计日志可显示写操作名称和结果，并支持 `actor/action/resource_type/result/search/start_time/end_time` 组合筛选；db profile smoke 已覆盖管理对象创建审计、账号锁定/解锁审计、失败登录审计、拒绝审计、资源作用域过滤、设置 CRUD 和敏感设置拒绝。
- 无权限角色访问管理写接口会返回 `FORBIDDEN`。
- `/v3/api-docs` 可生成 OpenAPI 文档，且契约测试保护认证、管理、账号生命周期和设置 CRUD 关键路径。
- WP2 `db` profile 默认种子可直接完成 local echo 调用、调用日志查询、成本汇总、成本报表、成本告警、供应商就绪检查缓存和 CSV 导出 smoke；WP2 聚合门禁可串联模型接入测试、数据库 validation，并按需执行 HTTP smoke / strict 联调 smoke。

## WP1 1～8 项收敛状态

1. 设置已扩展为分页 CRUD、详情、编辑和启停 API，前端设置操作面板已接入。
2. RBAC 已从资源级绑定扩展到 `db` profile 资源作用域过滤、前端菜单权限和按钮权限。
3. 账号与认证审计已覆盖登录成功、失败登录、账号启停、锁定/解锁、重置密码和资料编辑。
4. 审计已扩展到成功、失败、拒绝、变更结果，并持久化 before/after/diff 字段和筛选分页。
5. 敏感设置键会拒绝明文保存，只允许掩码值或 Secret 引用类写法。
6. OpenAPI 契约测试已覆盖设置 CRUD、认证、管理和账号生命周期关键路径。
7. `db` profile smoke 已扩展到设置 CRUD、敏感设置拒绝、失败登录审计、资源作用域过滤和授权拒绝。
8. 已补充预发/生产应用数据库角色校验脚本：`scripts/wp1_release_role_validation.sh`。
