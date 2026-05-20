# Veri Agent

AI 驱动的端到端企业级测试平台。WP1、WP2、WP3、WP4 是研发任务拆分，不是服务拆分；当前后端由同一个 `platform-api` Java 服务承载，内部按领域模块组织平台基础、模型接入、资产管理和文档输入能力。

## 当前 WP1 口径

- 单平台，不建设平台实例分层体系。
- 支持多部门、多项目、多应用、多环境。
- 后端基础包路径：`com.songhg.veri.agent`。
- P0 样板已支持初始化、登录、刷新令牌、注销、管理视图查询、部门详情/编辑/启停、用户详情/资料编辑、项目/应用/环境正式创建、详情、编辑和状态流，设置分页 CRUD/启停，项目成员、应用负责人、环境授权用户与资源级角色绑定，账号启停、锁定/解锁、重置密码和审计追踪。
- 管理 API 已接入 RBAC 权限校验，`local` 模式使用内置角色权限，`db` 模式从 `rbac_role_permission` 解析，并对项目、应用、环境、审计和设置列表执行资源作用域过滤；前端菜单和按钮权限已有规则与测试。
- OpenAPI 已声明 WP1 控制面标题、版本和 Bearer 鉴权方案，并通过契约测试保护认证、管理、账号生命周期和设置 CRUD 关键路径。
- WP2/WP3 通过同进程 Spring 应用服务复用 WP1 上下文校验和审计写入能力，不通过 HTTP 回调本服务。
- 前端管理台已接入部门、用户、项目、应用、环境、设置详情回读、基础字段编辑、状态流转、项目成员、应用负责人和环境授权用户操作面板，和资源级 API 对齐。
- `local` profile 使用内存数据，方便前端和接口样板开发。
- `db` profile 使用 PostgreSQL + Flyway，已接入部门、用户、项目、应用、环境、设置、会话、资源级角色绑定和审计的持久化路径；部门已支持详情、编辑和启停，用户已支持详情和资料编辑，项目/应用/环境已支持编码、归属、敏感级别、公有云模型开关、默认访问地址、环境作用域、详情、编辑、状态流和协作授权字段；审计已持久化失败/拒绝/变更结果和 before/after/diff 字段，敏感设置明文写入会被拒绝。

## 模块

| 路径 | 说明 |
|---|---|
| `platform-api` | Spring Boot 3.5 + Java 21 后端服务，承载 WP1/WP2/WP3/WP4 领域模块。 |
| `portal-web` | React + TypeScript + Vite Web 管理后台。 |
| `db/migration/wp1` | WP1 PostgreSQL/Flyway 迁移脚本。 |
| `db/validation` | WP1/WP2 数据库结构、种子、安全约束校验脚本。 |
| `doc/mvp/final` | WP1/WP2 PRD、架构、工程补充和交付验收文档。 |
| `doc/mvp/final/engineering/WP1-当前可持续研发底座交付说明.md` | 当前 WP1 单平台实现、准出命令、验证结论和后续研发入口。 |
| `doc/mvp/final/engineering/WP2-模型接入层-交付说明.md` | 当前 WP2 模型接入实现、API 边界、数据库交付和验证入口。 |
| `doc/mvp/final/engineering/WP3-测试资产管理-当前交付说明.md` | 当前 WP3 资产模型、API、权限、状态流、前端入口和验证命令。 |
| `doc/mvp/final/engineering/WP4-需求与文档输入-研发拆解与里程碑计划.md` | 当前 WP4 需求输入、候选确认、发布、真实文档解析和 webhook 交付口径。 |
| `doc/mvp/final/engineering/当前实现基线.md` | 当前代码与文档对齐的权威基线：单服务、无租户、camelCase、分页和验证入口。 |
| `doc/mvp/final/engineering/WP1-单平台权限矩阵与菜单矩阵.md` | 当前 WP1 单平台权限、菜单和按钮规则。 |
| `doc/mvp/final/engineering/WP1-审计事件字典.md` | 当前 WP1 P0 审计事件、字段和验收规则。 |
| `doc/mvp/final/engineering/WP1-WP4-统一发布准出清单.md` | 跨 WP 本地、CI、预发和生产发布准出索引。 |
| `doc/mvp/final/engineering/WP1-WP4-变更影响矩阵.md` | WP1 context/audit/secret、WP2 invocation、WP3 asset、WP4 import/publish 的影响矩阵。 |
| `doc/mvp/final/engineering/WP1-WP4-指标命名与看板规范.md` | 统一 metrics 命名、Grafana/告警建议和 traceId 串联口径。 |
| `doc/mvp/final/engineering/WP1-WP4-Release-Notes-模板.md` | 面向验收和生产升级的 release notes 模板。 |
| `doc/mvp/final/engineering/WP1-发布前DB权限Runbook.md` | WP1 预发/生产数据库应用角色权限检查 runbook。 |
| `doc/mvp/final/engineering/WP2-Provider接入与SecretRef轮换Runbook.md` | WP2 外部 provider 接入、探活、故障处理和密钥轮换 runbook。 |
| `doc/mvp/final/engineering/WP4-Webhook签名样例与联调说明.md` | WP4 webhook cURL/Node.js/Java 签名样例和联调排错说明。 |
| `infra/docker-compose.yml` | 本地 PostgreSQL + platform-api 研发环境。 |
| `scripts/wp1_db_profile_smoke.sh` | 针对已启动 db profile 后端的 HTTP 烟测脚本。 |
| `scripts/wp1_quality_gate.sh` | WP1 本地质量门禁入口，串联后端测试、前端测试、前端构建和数据库校验。 |
| `scripts/wp2_model_access_smoke.sh` | 针对已启动 `platform-api` 的 WP2 API 烟测脚本。 |
| `scripts/wp2_module_policy_smoke.sh` | 针对同一 `platform-api` 内 WP2 消费 WP1 策略的烟测脚本。 |
| `scripts/wp3_quality_gate.sh` | WP3 本地质量门禁入口，串联资产 API 测试、OpenAPI 契约、前端资产测试、数据库校验和可选 smoke。 |
| `scripts/wp3_asset_smoke.sh` | 针对已启动 `platform-api` 的 WP3 资产 CRUD、分页、状态流拒绝和追踪关系烟测脚本。 |
| `.github/workflows/wp3-asset-management.yml` | WP3 PR/主干 CI 入口，复用 `scripts/wp3_quality_gate.sh` 并归档 DB validation 日志。 |
| `scripts/wp4_document_input_smoke.sh` | 针对已启动 `platform-api` 的 WP4 文档输入、候选确认、发布和 webhook 烟测脚本。 |
| `scripts/wp4_binary_document_smoke.sh` | WP4 真实 Word/PDF/OCR 文本抽取的本地烟测脚本。 |
| `scripts/wp4_ai_parse_quality_eval.sh` | WP4 AI 解析质量评测集门禁脚本。 |

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
    "bootstrapToken": "local-init-token",
    "username": "admin_user",
    "password": "PlainPassword123",
    "displayName": "平台管理员",
    "email": "admin@example.com"
  }'
```

创建项目、应用和应用专属环境：

```bash
TOKEN="$(curl -s -X POST http://127.0.0.1:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin_user","password":"PlainPassword123"}' \
  | jq -r '.data.accessToken')"

curl -X POST http://127.0.0.1:8080/api/v1/management/projects \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"code":"wp1-demo","name":"WP1 Demo","sensitivityLevel":"CONFIDENTIAL","allowPublicModel":false}'

curl -X POST http://127.0.0.1:8080/api/v1/management/applications \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"code":"wp1-demo-web","name":"WP1 Demo Web","project":"wp1-demo","appType":"Web","defaultApiBaseUrl":"https://api.demo.local","sensitivityLevel":"STRICT","allowPublicModel":false}'

curl -X POST http://127.0.0.1:8080/api/v1/management/environments \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"code":"wp1-demo-stg","name":"WP1 Demo Staging","project":"wp1-demo","application":"wp1-demo-web","scopeType":"APPLICATION","envType":"STAGING","webUrl":"https://demo.local","apiBaseUrl":"https://api.demo.local"}'
```

## WP2 模型接入层

访问：

- WP2 健康：http://127.0.0.1:8080/api/v1/model-access/health
- Swagger：http://127.0.0.1:8080/swagger-ui.html

调用 WP2 API 时使用同一个 `platform-api` 的 WP2 功能域路径：

```bash
curl -X POST http://127.0.0.1:8080/api/v1/model-access/invocations \
  -H 'Authorization: Bearer local-model-access-token' \
  -H 'X-Caller-Service: wp5-test-design' \
  -H 'X-Delegated-User-Id: user-001' \
  -H 'Content-Type: application/json' \
  -d '{
    "projectId": "project-001",
    "promptKey": "test-case-design",
    "promptVariables": {"context": "登录流程"},
    "messages": [{"role": "user", "content": "生成 3 条冒烟测试点"}],
    "allowPublicModel": false,
    "sensitivityLevel": "INTERNAL"
  }'
```

调用日志支持分页、筛选、敏感级别审计和成本汇总：

```bash
curl 'http://127.0.0.1:8080/api/v1/model-access/invocations?projectId=project-001&index=0&size=20' \
  -H 'Authorization: Bearer local-model-access-token' \
  -H 'X-Caller-Service: wp5-test-design' \
  -H 'X-Delegated-User-Id: user-001'

curl 'http://127.0.0.1:8080/api/v1/model-access/invocations/summary?projectId=project-001' \
  -H 'Authorization: Bearer local-model-access-token' \
  -H 'X-Caller-Service: wp5-test-design' \
  -H 'X-Delegated-User-Id: user-001'

curl 'http://127.0.0.1:8080/api/v1/model-access/invocations/export?projectId=project-001' \
  -H 'Authorization: Bearer local-model-access-token' \
  -H 'X-Caller-Service: wp5-test-design' \
  -H 'X-Delegated-User-Id: user-001'
```

## WP3 测试资产管理

访问：

- WP3 健康：http://127.0.0.1:8080/api/v1/asset/health
- Swagger：http://127.0.0.1:8080/swagger-ui.html

WP3 资产 API 支持内部 `WP3_SERVICE_TOKEN` 和登录用户 Bearer token。内部服务调用示例：

```bash
curl -X POST http://127.0.0.1:8080/api/v1/asset/requirements \
  -H 'Authorization: Bearer local-asset-token' \
  -H 'X-Caller-Service: wp5-test-design' \
  -H 'X-Delegated-User-Id: user-001' \
  -H 'Content-Type: application/json' \
  -d '{
    "projectId": "project-001",
    "title": "登录需求",
    "description": "用户可使用账号密码登录",
    "priority": "HIGH",
    "tags": "auth,login"
  }'
```

列表统一使用 `index/size` 分页，并支持 `projectId/status/source/keyword`：

```bash
curl 'http://127.0.0.1:8080/api/v1/asset/requirements?projectId=project-001&status=DRAFT&keyword=登录&index=0&size=20' \
  -H 'Authorization: Bearer local-asset-token' \
  -H 'X-Caller-Service: wp5-test-design' \
  -H 'X-Delegated-User-Id: user-001'
```

当前资产工作台已在 `portal-web` 增加资产库入口和需求资产页面。权限收敛为 `asset:read`、`asset:manage`、`asset:review`、`asset:export`。

## WP4 文档输入

访问：

- WP4 健康：http://127.0.0.1:8080/api/v1/document-input/health
- WP4 Webhook：`POST /api/v1/document-input/webhooks/{sourceCode}`

WP4 管理、导入、候选确认、发布和事件查询使用同一个 `platform-api`，服务端调用默认令牌为 `local-document-input-token`。当前实现统一使用 `/api/v1/document-input/imports` 管理导入记录、候选和发布记录；早期文档中的 `/batches` 为历史建议路径，验收以当前实现和 OpenAPI/测试为准。受保护接口权限收敛为 `requirementInput:read`、`requirementInput:manage`、`requirementInput:import`、`requirementInput:candidate_review`、`requirementInput:publish`、`requirementInput:webhook_replay`。

候选查询支持 `status`、`sourceRef`、`keyword` 筛选；批量候选操作同时支持简单 `candidateIds` 和携带版本号的 versioned candidates，用于阻断并发脏写。`CUSTOM_API` webhook source 保存 `secretRef`、`eventVersion`、`mappingVersion` 和字段映射；事件查询支持 `sourceId`、`sourceCode`、`eventType`、`status`、`receivedFrom`、`receivedTo`。发布到 WP3 时保留 `source`、`sourceRef`、`sourceUrl` 和 `acceptanceCriteria` 追踪；同一 `externalRequirementId` 重复导入会在 dryRun 中返回 `UPDATE` 和 `diffSummary`，正式发布通过 WP3 应用服务更新既有 `IMPORT` 需求资产，不重复创建。若既有 WP3 需求已进入非 `DRAFT` 状态且存在差异，dryRun 返回 `CONFLICT_REVIEW_REQUIRED`，正式发布会失败并保留人工评审后的资产内容。

AI 文档解析 MVP 通过 WP2 `ModelAccessService` 接入，受 `WP4_MODEL_PARSE_ENABLED` 控制，默认 Prompt key 为 `wp4-document-requirement-parse`。开启后文本、Markdown、Word、PDF、OCR 和 `CUSTOM_API` 导入会先经 WP2 模型解析生成 `parseSource=MODEL` 的候选项，并保存 `modelInvocationId`、`modelProviderName`、`modelName`；WP2 策略阻断、敏感内容阻断或模型失败时回退到规则解析，候选继续进入人工确认，不会绕过 WP2 或直接发布到 WP3。

WP4 真实文档解析支持 `WORD`、`PDF`、`OCR` sourceType：Word 使用 Apache POI 抽取 doc/docx 文本，PDF 使用 PDFBox 抽取文本型 PDF，OCR 通过 `WP4_OCR_COMMAND` 命令 provider 接收 `{input}` 临时文件并返回识别文本。`/imports` 接受纯文本、raw base64 或 `data:...;base64,...` 内容；`/imports/multipart` 接受 `multipart/form-data` 的 `projectId`、`sourceType`、可选来源字段和 `file`，用于真实文件上传。导入受 `WP4_IMPORT_MAX_CONTENT_BYTES`、`WP4_DOCUMENT_BINARY_MAX_BYTES` 限制；`WP4_BINARY_MIME_VALIDATION_ENABLED` 开启后会校验声明 MIME 与实际文件魔数/内容类型，`WP4_PDF_MAX_PAGES` 和 `WP4_PDF_MAX_PARSE_MILLIS` 会限制 PDF 页数和解析耗时；OCR 额外受 `WP4_OCR_TIMEOUT_SECONDS`、`WP4_OCR_MAX_OUTPUT_CHARS`、`WP4_OCR_MAX_CONCURRENT_PROCESSES` 限流，健康接口会返回当前二进制解析、PDF 和 OCR 配置。

`CUSTOM_API` webhook 使用 `X-VA-Timestamp`、`X-VA-Event-Id`、`X-VA-Idempotency-Key`、`X-VA-Event-Version`、`X-VA-Signature`，签名串为 `timestamp.eventId.idempotencyKey.rawBody`。

Webhook 入口在签名前执行来源保护：`WP4_WEBHOOK_ALLOWED_CIDRS` 配置全局 IP/CIDR 白名单，`WP4_WEBHOOK_TRUSTED_PROXY_CIDRS` 配置可信代理后才信任 `X-Forwarded-For`；`WP4_WEBHOOK_RATE_LIMIT_MAX_REQUESTS` 和 `WP4_WEBHOOK_RATE_LIMIT_WINDOW_SECONDS` 提供按 sourceCode、remoteIp、idempotencyKey 的单实例限流。多实例生产限流仍建议接入网关或 Redis。

当前 webhook 密钥解析优先调用 WP1 `SecretProvider`，`db` profile 支持 `LOCAL_ENCRYPTED` 的 `secret_reference` + `secret_local_store` 密文解析，也支持 `VAULT`/`KMS` provider 通过 `WP1_EXTERNAL_SECRET_RESOLVE_URL` 调用外部 HTTP resolve endpoint；认证令牌只从 `WP1_EXTERNAL_SECRET_AUTH_TOKEN` 注入，不写入库表。解析会校验 ACTIVE、未过期、`WEBHOOK_SIGNING` 用途以及 `CONFIG + document_input_source.id` 作用域。`veri-agent.document-input.webhook-secrets` 配置映射、`wp4-webhook-default` 和 `secret://wp4/*` 仅作为 dev/test fallback，可通过 `WP4_LOCAL_WEBHOOK_SECRET_FALLBACK_ENABLED=false` 禁用。

针对已启动后端执行 WP4 smoke：

```bash
WP4_SMOKE_BASE_URL=http://127.0.0.1:8080 \
WP4_SERVICE_TOKEN=local-document-input-token \
WP3_SERVICE_TOKEN=local-asset-token \
WP4_WEBHOOK_SECRET=local-document-input-webhook-secret \
bash scripts/wp4_document_input_smoke.sh
```

如需在 smoke 中覆盖 AI 解析链路，启动后端时设置 `WP4_MODEL_PARSE_ENABLED=true`；脚本会额外校验 AI 候选、WP2 调用追踪字段和 `veri.agent.document_input.model_parse` 指标。

本地验证二进制解析和 AI 质量门禁：

```bash
bash scripts/wp4_binary_document_smoke.sh
bash scripts/wp4_ai_parse_quality_eval.sh
```

## 跨 WP 发布准出与治理

统一发布入口以 `doc/mvp/final/engineering/WP1-WP4-统一发布准出清单.md` 为准，覆盖本地、CI、预发和生产发布。共享契约变更先查 `doc/mvp/final/engineering/WP1-WP4-变更影响矩阵.md`，确认 WP1 context/audit/secret、WP2 invocation、WP3 asset、WP4 import/publish/webhook 的受影响测试；指标和看板按 `doc/mvp/final/engineering/WP1-WP4-指标命名与看板规范.md` 收敛；发布记录使用 `doc/mvp/final/engineering/WP1-WP4-Release-Notes-模板.md`。

预发/生产发布前的专项 runbook：

- WP1 DB 权限：`doc/mvp/final/engineering/WP1-发布前DB权限Runbook.md`
- WP2 provider 与密钥轮换：`doc/mvp/final/engineering/WP2-Provider接入与SecretRef轮换Runbook.md`
- WP4 webhook 签名联调：`doc/mvp/final/engineering/WP4-Webhook签名样例与联调说明.md`

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

`run_wp1_db_validation.sh` 会顺序执行 WP1/WP2/WP3/WP4 迁移，并额外运行 `wp_all_schema_validation.sql` 与 `wp4_document_input_validation.sql`，覆盖 WP4 文档输入表、字段、索引、状态约束、webhook 幂等唯一索引和单平台字段回归。

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
WP3_SKIP_DB_VALIDATION=1 bash scripts/wp3_quality_gate.sh
```

```bash
WP3_SERVICE_TOKEN=local-asset-token bash scripts/wp3_asset_smoke.sh
```

```bash
WP2_SERVICE_TOKEN=local-model-access-token bash scripts/wp2_model_access_smoke.sh
```

```bash
WP4_SERVICE_TOKEN=local-document-input-token \
WP3_SERVICE_TOKEN=local-asset-token \
WP4_WEBHOOK_SECRET=local-document-input-webhook-secret \
bash scripts/wp4_document_input_smoke.sh
```

```bash
bash scripts/wp4_binary_document_smoke.sh
bash scripts/wp4_ai_parse_quality_eval.sh
```

```bash
WP1_BOOTSTRAP_TOKEN=local-init-token \
WP2_SERVICE_TOKEN=local-model-access-token \
WP3_SERVICE_TOKEN=local-asset-token \
WP4_SERVICE_TOKEN=local-document-input-token \
WP4_WEBHOOK_SECRET=local-document-input-webhook-secret \
bash scripts/wp_all_integration_test.sh
```

```bash
bash scripts/wp2_quality_gate.sh
```

当前额外烟测结论：

- `db` profile 可自动执行 WP1 Flyway 迁移。
- 可完成 SuperAdmin 初始化与登录。
- 登录响应包含 `accessToken`、`refreshToken` 和 `sessionId`，刷新令牌会轮换会话，注销会撤销当前会话。
- 可在 PostgreSQL 中创建部门、用户、项目、应用、环境、集成配置和系统设置，并维护项目成员、应用负责人和环境授权用户。
- 可查询用户详情、启用、停用、锁定/解锁用户，并通过重置密码激活账号；停用、锁定/解锁、重置密码会提升 `auth_version`，db profile 会话校验会联动用户当前状态和版本，使旧访问令牌失效。
- 管理列表可回读持久化数据。
- 审计日志可显示写操作名称和结果，并支持 `actor/action/resourceType/result/search/startTime/endTime` 组合筛选；db profile smoke 已覆盖管理对象创建审计、账号锁定/解锁审计、失败登录审计、拒绝审计、资源作用域过滤、设置 CRUD 和敏感设置拒绝。
- 无权限角色访问管理写接口会返回 `FORBIDDEN`。
- `/v3/api-docs` 可生成 OpenAPI 文档，且契约测试保护认证、管理、账号生命周期和设置 CRUD 关键路径。
- WP2 `db` profile 默认种子可直接完成 local echo 调用、调用日志查询、成本汇总、成本报表、成本告警、供应商就绪检查缓存和 CSV 导出 smoke；WP2 聚合门禁可串联模型接入测试、数据库 validation，并按需执行 HTTP smoke / 模块策略 smoke。
- WP3 已补齐资产库前后端闭环：列表分页、需求/API/页面/业务流/用例/追踪链接基础 API、用户态 `asset:*` RBAC、OpenAPI 契约测试、前端资产 API normalizer、资产库导航和需求工作台。
- WP4 smoke 覆盖 Markdown 导入、候选 `status/sourceRef/keyword` 筛选、versioned 批量确认、dryRun、发布到 WP3、WP3 `source/sourceRef/sourceUrl/acceptanceCriteria` 追踪、发布记录、`CUSTOM_API` source health、`X-VA-*` 签名 webhook、幂等 replay、事件日志、无效签名拒绝和当前导入/候选/发布/webhook metrics。
- WP4 二进制文档 smoke 覆盖真实 docx、真实文本 PDF、OCR 命令 provider；AI 解析质量评测输出标题召回、优先级准确率、验收标准覆盖率并执行阈值门禁。

## WP1 1～8 项收敛状态

1. 设置已扩展为分页 CRUD、详情、编辑和启停 API，前端设置操作面板已接入。
2. RBAC 已从资源级绑定扩展到 `db` profile 资源作用域过滤、前端菜单权限和按钮权限。
3. 账号与认证审计已覆盖登录成功、失败登录、账号启停、锁定/解锁、重置密码和资料编辑。
4. 审计已扩展到成功、失败、拒绝、变更结果，并持久化 before/after/diff 字段和筛选分页。
5. 敏感设置键会拒绝明文保存，只允许掩码值或 Secret 引用类写法。
6. OpenAPI 契约测试已覆盖设置 CRUD、认证、管理和账号生命周期关键路径。
7. `db` profile smoke 已扩展到设置 CRUD、敏感设置拒绝、失败登录审计、资源作用域过滤和授权拒绝。
8. 已补充预发/生产应用数据库角色校验脚本：`scripts/wp1_release_role_validation.sh`。
