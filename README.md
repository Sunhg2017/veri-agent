# Veri Agent

AI 驱动的端到端企业级测试平台。WP1、WP2、WP3、WP4 是研发任务拆分，不是服务拆分；当前后端由同一个 `platform-api` Java 服务承载，内部按领域模块组织平台基础、模型接入、资产管理和文档输入能力。

仓库级智能体协作、验收、自动提交和推送强制规则见 [AGENTS.md](AGENTS.md)。

## 当前 WP1 口径

- 单平台，不建设平台实例分层体系。
- 支持多部门、多项目、多应用、多环境。
- 后端基础包路径：`com.songhg.veri.agent`。
- P0 样板已支持初始化、登录、刷新令牌、注销、管理视图查询、部门详情/编辑/启停、用户详情/资料编辑、项目/应用/环境正式创建、详情、编辑和状态流，设置分页 CRUD/启停，项目成员、应用负责人、环境授权用户与资源级角色绑定，账号启停、锁定/解锁、重置密码和审计追踪。
- 管理 API 已接入 RBAC 权限校验，`local` 模式使用内置角色权限，`db` 模式从 `rbac_role_permission` 解析，并对项目、应用、环境、审计和设置列表执行资源作用域过滤；`db,redis` 模式会用 Redisson 对会话和权限解析做短 TTL 缓存。前端菜单和按钮权限已有规则与测试。
- OpenAPI 已声明 WP1 控制面标题、版本和 Bearer 鉴权方案，并通过契约测试保护认证、管理、账号生命周期和设置 CRUD 关键路径。
- WP2/WP3 通过同进程 Spring 应用服务复用 WP1 上下文校验和审计写入能力，不通过 HTTP 回调本服务。
- 前端管理台已接入部门、用户、项目、应用、环境、设置详情回读、基础字段编辑、状态流转、项目成员、应用负责人和环境授权用户操作面板，和资源级 API 对齐。
- `local` profile 使用内存数据，方便前端和接口样板开发。
- `db` profile 使用 PostgreSQL + Flyway，已接入部门、用户、项目、应用、环境、设置、会话、资源级角色绑定和审计的持久化路径；部门已支持详情、编辑和启停，用户已支持详情和资料编辑，项目/应用/环境已支持编码、归属、敏感级别、公有云模型开关、默认访问地址、环境作用域、详情、编辑、状态流和协作授权字段；审计已持久化失败/拒绝/变更结果和 before/after/diff 字段，`db,kafka` 模式会把审计记录先发布为 trace-aware 事件再异步落库，敏感设置明文写入会被拒绝。

## 模块

| 路径 | 说明 |
|---|---|
| `AGENTS.md` | 仓库级强制规则，定义五角色协作、验收、commit message、自动提交和推送流程。 |
| `platform-api` | Spring Boot 3.5 + Java 21 后端服务，承载 WP1/WP2/WP3/WP4 领域模块。 |
| `portal-web` | React + TypeScript + Vite Web 管理后台。 |
| `db/migration/wp1` | WP1 PostgreSQL/Flyway 迁移脚本。 |
| `db/validation` | WP1/WP2 数据库结构、种子、安全约束校验脚本。 |
| `doc/mvp/final` | WP1/WP2 PRD、架构、工程补充和交付验收文档。 |
| `doc/mvp/final/engineering/WP1-当前可持续研发底座交付说明.md` | 当前 WP1 单平台实现、准出命令、验证结论和后续研发入口。 |
| `doc/mvp/final/engineering/WP1-企业身份与审批预留方案.md` | WP1 M4 企业身份源、外部账号/部门同步、审批对象与角色绑定关系的预留方案。 |
| `doc/mvp/final/engineering/WP2-模型接入层-交付说明.md` | 当前 WP2 模型接入实现、API 边界、数据库交付和验证入口。 |
| `doc/mvp/final/engineering/WP2-Redisson-Kafka事件驱动架构设计与测试策略.md` | WP2 Redisson、Kafka 事件驱动异步调用、traceId 串联和测试策略。 |
| `doc/mvp/final/engineering/WP3-测试资产管理-当前交付说明.md` | 当前 WP3 资产模型、API、权限、状态流、前端入口和验证命令。 |
| `doc/mvp/final/engineering/WP4-需求与文档输入-研发拆解与里程碑计划.md` | 当前 WP4 需求输入、候选确认、发布、真实文档解析和 webhook 交付口径。 |
| `doc/mvp/final/engineering/WP5-AI用例生成与评审-研发任务拆解.md` | WP5 AI 用例生成与评审的研发任务拆解、里程碑、范围边界、风险和回滚口径。 |
| `doc/mvp/final/engineering/WP5-AI用例生成与评审-需求文档与PRD.md` | WP5 需求文档和产品 PRD，定义用户场景、功能范围、业务规则和产品验收标准。 |
| `doc/mvp/final/engineering/WP5-AI用例生成与评审-技术设计与接口契约.md` | WP5 服务端技术设计、数据模型、状态机、权限、审计、配置和 API 契约草案。 |
| `doc/mvp/final/engineering/WP5-AI用例生成与评审-前端页面设计.md` | WP5 前端页面、路由、权限、表单校验、状态展示和可测性设计。 |
| `doc/mvp/final/engineering/WP5-AI用例生成与评审-测试策略与用例脚本.md` | WP5 测试策略、功能/安全/前端用例、质量评测指标和脚本入口建议。 |
| `doc/mvp/final/engineering/WP6-Runner-Runbook.md` | WP6 runner smoke、开关、allowlist、排障和回滚说明。 |
| `doc/mvp/final/engineering/当前实现基线.md` | 当前代码与文档对齐的权威基线：单服务、无租户、camelCase、分页和验证入口。 |
| `doc/mvp/final/engineering/WP1-单平台权限矩阵与菜单矩阵.md` | 当前 WP1 单平台权限、菜单和按钮规则。 |
| `doc/mvp/final/engineering/WP1-审计事件字典.md` | 当前 WP1 P0 审计事件、字段和验收规则。 |
| `doc/mvp/final/engineering/WP1-WP4-统一发布准出清单.md` | 跨 WP 本地、CI、预发和生产发布准出索引。 |
| `doc/mvp/final/engineering/WP1-WP4-数据库迁移回滚与前滚策略.md` | WP1-WP4 统一 Flyway 迁移发布计划、备份恢复和前滚修复 Runbook。 |
| `doc/mvp/final/engineering/WP1-WP4-变更影响矩阵.md` | WP1 context/audit/secret、WP2 invocation、WP3 asset、WP4 import/publish 的影响矩阵。 |
| `doc/mvp/final/engineering/WP1-WP4-指标命名与看板规范.md` | 统一 metrics 命名、Grafana/告警建议和 traceId 串联口径。 |
| `doc/mvp/final/engineering/WP1-WP4-Release-Notes-模板.md` | 面向验收和生产升级的 release notes 模板。 |
| `doc/mvp/final/engineering/WP1-发布前DB权限Runbook.md` | WP1 预发/生产数据库应用角色权限检查 runbook。 |
| `doc/mvp/final/engineering/WP2-Provider接入与SecretRef轮换Runbook.md` | WP2 外部 provider 接入、探活、故障处理和密钥轮换 runbook。 |
| `doc/mvp/final/engineering/WP4-Webhook签名样例与联调说明.md` | WP4 webhook cURL/Node.js/Java 签名样例和联调排错说明。 |
| `doc/mvp/final/engineering/WP4-外部连接器接入Runbook与Mock契约.md` | WP4 Confluence、飞书、钉钉、语雀真实连接器的 schema、mock 契约、安全和准出口径。 |
| `doc/mvp/final/engineering/WP4-高保真解析专项评估.md` | WP4 表格结构、图片语义、页眉页脚、批注/修订和附件抽取的高保真解析专项评估。 |
| `infra/docker-compose.yml` | 本地 PostgreSQL + Redis + Kafka + platform-api + portal-web 集成研发环境。 |
| `scripts/wp1_db_profile_smoke.sh` | 针对已启动 db profile 后端的 HTTP 烟测脚本。 |
| `scripts/wp1_quality_gate.sh` | WP1 本地质量门禁入口，串联后端测试、前端测试、前端构建和数据库校验。 |
| `scripts/wp1_migration_release_plan.sh` | WP1-WP4 统一 Flyway 迁移发布计划脚本，生成待发布 migration manifest 和回滚/前滚证据。 |
| `scripts/wp2_model_access_smoke.sh` | 针对已启动 `platform-api` 的 WP2 API 烟测脚本。 |
| `scripts/wp2_module_policy_smoke.sh` | 针对同一 `platform-api` 内 WP2 消费 WP1 策略的烟测脚本。 |
| `scripts/wp2_model_quality_eval.sh` | WP2 通用模型评测集入口，支持按 `WP2_MODEL_EVAL_TASK` 跑任务类型评测。 |
| `scripts/wp3_quality_gate.sh` | WP3 本地质量门禁入口，串联资产 API 测试、OpenAPI 契约、前端资产测试、数据库校验和可选 smoke。 |
| `scripts/wp3_asset_smoke.sh` | 针对已启动 `platform-api` 的 WP3 资产 CRUD、分页、状态流拒绝和追踪关系烟测脚本。 |
| `.github/workflows/wp3-asset-management.yml` | WP3 PR/主干 CI 入口，复用 `scripts/wp3_quality_gate.sh` 并归档 DB validation 日志。 |
| `scripts/wp4_document_input_smoke.sh` | 针对已启动 `platform-api` 的 WP4 文档输入、候选确认、发布和 webhook 烟测脚本。 |
| `scripts/wp4_frontend_e2e_smoke.sh` | WP4 文档输入前端浏览器 smoke，覆盖文件上传、候选编辑、发布预览和事件重放。 |
| `scripts/wp4_binary_document_smoke.sh` | WP4 真实 Word/PDF/OCR 文本抽取的本地烟测脚本。 |
| `scripts/wp4_ai_parse_quality_eval.sh` | WP4 AI 解析质量评测集门禁脚本。 |
| `scripts/wp6_openapi_fixture_smoke.sh` | WP6 OpenAPI fixture 解析、脱敏和错误路径烟测脚本。 |
| `scripts/wp6_runner_smoke.sh` | WP6 runner contract smoke，覆盖执行分支、allowlist、timeout 和脱敏回归。 |
| `scripts/wp6_quality_gate.sh` | WP6 本地质量门禁入口，串联 OpenAPI fixture、后端、前端、构建、DB validation 和可选 runner smoke。 |

## 本地内存模式

`WP1_AUTH_TOKEN_SECRET` 必须使用至少 32 字节的随机值；下面仅为本地示例。

```bash
WP1_AUTH_TOKEN_SECRET=local-auth-secret-32-byte-minimum! \
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
WP1_AUTH_TOKEN_SECRET=local-auth-secret-32-byte-minimum! \
WP1_DATASOURCE_URL=jdbc:postgresql://localhost:5432/veri_agent \
WP1_DATASOURCE_USERNAME=veri_agent \
WP1_DATASOURCE_PASSWORD=veri_agent_dev \
mvn -pl platform-api spring-boot:run -Dspring-boot.run.profiles=db
```

## Redis + Kafka 事件驱动模式

WP2 异步模型调用、WP1 会话/权限缓存和审计异步落库可启用 Redis/Redisson 与 Kafka 事件总线，本地联调用：

```bash
docker compose -f infra/docker-compose.yml up -d postgres redis kafka
```

```bash
WP1_AUTH_TOKEN_SECRET=local-auth-secret-32-byte-minimum! \
WP1_DATASOURCE_URL=jdbc:postgresql://localhost:5432/veri_agent \
WP1_DATASOURCE_USERNAME=veri_agent \
WP1_DATASOURCE_PASSWORD=veri_agent_dev \
PLATFORM_REDIS_ADDRESS=redis://localhost:6379 \
PLATFORM_KAFKA_BOOTSTRAP_SERVERS=localhost:9092 \
mvn -pl platform-api spring-boot:run -Dspring-boot.run.profiles=db,redis,kafka
```

启用 `db,redis,kafka` 后，Redisson 承载会话短缓存、权限短缓存、provider 熔断/限流/并发状态；Kafka 承载模型调用任务、审计写入事件，以及 WP4 导入解析、发布写入和 webhook accepted 三条事件链路，事件 envelope 与日志继续使用同一 `traceId`。不启用 `redis,kafka` profile 时，服务仍使用本地事件总线、JDBC 会话/权限路径和进程内 resilience state，便于单元测试和轻量开发。

初始化首个管理员：

```bash
WP1_DATASOURCE_URL=jdbc:postgresql://localhost:5432/veri_agent \
WP1_DATASOURCE_USERNAME=veri_agent \
WP1_DATASOURCE_PASSWORD=veri_agent_dev \
WP1_SUPER_ADMIN_USERNAME=admin_user \
WP1_SUPER_ADMIN_PASSWORD=PlainPassword123 \
WP1_SUPER_ADMIN_DISPLAY_NAME=SuperAdmin \
WP1_SUPER_ADMIN_EMAIL=admin@example.com \
bash scripts/wp1_seed_super_admin.sh
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
    "sensitivityLevel": "INTERNAL",
    "capability": "CHAT"
  }'
```

调用日志支持分页、筛选、敏感级别审计、路由结果审计和成本汇总；高级路由可通过 provider `routingGroup/capabilities` 与 `veri-agent.model-access.routing-rules` 按项目、调用服务、敏感级别、能力和供应商组选择 provider。预算策略支持平台、项目和调用服务日预算，可通过 `WP2_DAILY_PLATFORM_COST_LIMIT`、`WP2_DAILY_PROJECT_COST_LIMIT`、`WP2_DAILY_CALLER_SERVICE_COST_LIMIT`、`WP2_COST_ALERT_WARNING_RATIO` 和 `WP2_BUDGET_OVERRUN_ACTION=BLOCK|FALLBACK` 控制告警、阻断或低成本 provider 降级。Prompt 版本管理支持 `highRisk=true` 高风险标记，高风险版本需先调用 `/prompts/{id}/approve` 审批通过后才能激活：

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

curl 'http://127.0.0.1:8080/api/v1/model-access/cost/alerts?actorService=wp5-test-design' \
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

当前资产工作台已在 `portal-web` 增加资产库入口、需求资产页面、API 资产页面、页面资产页面、业务流资产页面、测试用例页面和追踪矩阵页面；API 页支持列表、详情、创建、编辑、方法/路径筛选和 schema 展示，页面/业务流页支持列表、详情、创建、编辑、筛选、结构化 JSON 预览/校验和标准化原型同步，用例页支持基础信息维护、关联需求/API 展示、步骤顺序编辑和历史回滚，需求页支持历史回滚，统一导入导出面板支持需求/API/测试用例 CSV/JSON 与 API OpenAPI 预检、导入和导出下载。追踪能力已扩展到需求-API-页面-业务流-用例关系，后端 `/api/v1/asset/impact` 提供聚合影响分析。权限收敛为 `asset:read`、`asset:manage`、`asset:review`、`asset:export`。

## WP4 文档输入

访问：

- WP4 健康：http://127.0.0.1:8080/api/v1/document-input/health
- WP4 Webhook：`POST /api/v1/document-input/webhooks/{sourceCode}`

WP4 管理、导入、候选确认、发布和事件查询使用同一个 `platform-api`，服务端调用默认令牌为 `local-document-input-token`。当前实现统一使用 `/api/v1/document-input/imports` 管理导入记录、候选和发布记录；早期文档中的 `/batches` 为历史建议路径，验收以当前实现和 OpenAPI/测试为准。受保护接口权限收敛为 `requirementInput:read`、`requirementInput:manage`、`requirementInput:import`、`requirementInput:candidate_review`、`requirementInput:publish`、`requirementInput:webhook_replay`。

WP4 的三条耗时链路已改为事件驱动：文本/Markdown/Word/PDF/OCR/`CUSTOM_API` 导入先返回 `MODEL_PARSE_QUEUED`，由 `document-input.import.requested` 事件后台解析；非 dryRun 发布先返回 `PUBLISH_QUEUED`，由 `document-input.publish.requested` 事件写入 WP3；webhook 入口完成签名、幂等、限流和事件落库后返回 `ACCEPTED` 回执并发布 `document-input.webhook.accepted`，消费者再创建导入批次并解析 payload。本地 profile 使用进程内事件总线，`db,kafka` profile 使用 Kafka topic；事件 envelope、Kafka header 和消费者日志均保留同一 `traceId`，前端通过导入、候选、发布记录和 webhook 事件查询轮询最终状态。`DocumentInputEventRecoveryService` 默认在启动和每 2 分钟定时补偿发布仍停留在 `MODEL_PARSE_QUEUED`、`PUBLISH_QUEUED`、`ACCEPTED` 的持久化记录，消费者继续通过条件状态更新认领以保证重复投递幂等；可通过 `WP4_EVENT_RECOVERY_ENABLED=false` 关闭，`WP4_EVENT_RECOVERY_BATCH_SIZE` 和 `WP4_EVENT_RECOVERY_CRON` 调整批量和调度。

候选查询支持 `status`、`sourceRef`、`keyword` 筛选；批量候选操作同时支持简单 `candidateIds` 和携带版本号的 versioned candidates，用于阻断并发脏写。`CUSTOM_API` webhook source 保存 `secretRef`、`eventVersion`、`mappingVersion` 和字段映射；事件查询支持 `sourceId`、`sourceCode`、`eventType`、`status`、`receivedFrom`、`receivedTo`。发布到 WP3 时保留 `source`、`sourceRef`、`sourceUrl` 和 `acceptanceCriteria` 追踪；同一 `externalRequirementId` 重复导入会在 dryRun 中返回 `UPDATE` 和 `diffSummary`，正式发布通过 WP3 应用服务更新既有 `IMPORT` 需求资产，不重复创建。若既有 WP3 需求已进入非 `DRAFT` 状态且存在差异，dryRun 返回 `CONFLICT_REVIEW_REQUIRED`，正式发布会失败并保留人工评审后的资产内容。

AI 文档解析 MVP 通过 WP2 `ModelAccessService` 接入，受 `WP4_MODEL_PARSE_ENABLED` 控制，默认 Prompt key 为 `wp4-document-requirement-parse`。开启后文本、Markdown、Word、PDF、OCR 和 `CUSTOM_API` 导入会先入队，再由事件消费者经 WP2 模型解析生成 `parseSource=MODEL` 的候选项，并保存 `modelInvocationId`、`modelProviderName`、`modelName`；WP2 策略阻断、敏感内容阻断或模型失败时回退到规则解析，候选继续进入人工确认，不会绕过 WP2 或直接发布到 WP3。

WP4 真实文档解析支持 `WORD`、`PDF`、`OCR` sourceType：Word 使用 Apache POI 抽取 doc/docx 文本，PDF 使用 PDFBox 抽取文本型 PDF，OCR 通过 `WP4_OCR_COMMAND` 命令 provider 接收 `{input}` 临时文件并返回识别文本。`/imports` 接受纯文本、raw base64 或 `data:...;base64,...` 内容；`/imports/multipart` 接受 `multipart/form-data` 的 `projectId`、`sourceType`、可选来源字段和 `file`，用于真实文件上传。导入受 `WP4_IMPORT_MAX_CONTENT_BYTES`、`WP4_DOCUMENT_BINARY_MAX_BYTES` 限制；`WP4_BINARY_MIME_VALIDATION_ENABLED` 开启后会校验声明 MIME 与实际文件魔数/内容类型，`WP4_PDF_MAX_PAGES` 和 `WP4_PDF_MAX_PARSE_MILLIS` 会限制 PDF 页数和解析耗时；OCR 额外受 `WP4_OCR_TIMEOUT_SECONDS`、`WP4_OCR_MAX_OUTPUT_CHARS`、`WP4_OCR_MAX_CONCURRENT_PROCESSES` 限流。生产可通过 `WP4_MALWARE_SCAN_COMMAND` 接入命令式文件安全扫描，扫描命令同样接收 `{input}` 临时文件，受 `WP4_MALWARE_SCAN_TIMEOUT_SECONDS`、`WP4_MALWARE_SCAN_MAX_CONCURRENT_PROCESSES` 和 `WP4_MALWARE_SCAN_MAX_OUTPUT_CHARS` 控制；健康接口会返回当前二进制解析、PDF、OCR 和文件扫描配置。

WP4 数据保留清理默认关闭。设置 `WP4_RETENTION_CLEANUP_ENABLED=true` 后，`DocumentInputRetentionCleanupService` 会按 `veri-agent.document-input.retention-cleanup-cron` 清理超过 `WP4_IMPORT_RETENTION_DAYS` 的导入记录/候选和超过 `WP4_WEBHOOK_EVENT_RETENTION_DAYS` 的 webhook 事件，并输出 `veri.agent.document_input.retention.cleanup` 指标；归档和 deadLetter 长期保留策略仍以后续专项为准。

`CUSTOM_API` webhook 使用 `X-VA-Timestamp`、`X-VA-Event-Id`、`X-VA-Idempotency-Key`、`X-VA-Event-Version`、`X-VA-Signature`，签名串为 `timestamp.eventId.idempotencyKey.rawBody`。

Webhook 入口在签名前执行来源保护：`WP4_WEBHOOK_ALLOWED_CIDRS` 配置全局 IP/CIDR 白名单，`WP4_WEBHOOK_TRUSTED_PROXY_CIDRS` 配置可信代理后才信任 `X-Forwarded-For`；`WP4_WEBHOOK_RATE_LIMIT_MAX_REQUESTS` 和 `WP4_WEBHOOK_RATE_LIMIT_WINDOW_SECONDS` 提供按 sourceCode、remoteIp、idempotencyKey 的单实例限流。多实例生产限流仍建议接入网关或 Redis。

当前 webhook 密钥解析优先调用 WP1 `SecretProvider`，`db` profile 支持 `LOCAL_ENCRYPTED` 的 `secret_reference` + `secret_local_store` 密文解析，也支持 `VAULT`/`KMS` provider 通过 `WP1_EXTERNAL_SECRET_RESOLVE_URL` 调用外部 HTTP resolve endpoint；认证令牌只从 `WP1_EXTERNAL_SECRET_AUTH_TOKEN` 注入，不写入库表。解析会校验 ACTIVE、未过期、`WEBHOOK_SIGNING` 用途以及 `CONFIG + document_input_source.id` 作用域。SecretProvider 成功解析结果按 `WP4_WEBHOOK_SECRET_CACHE_TTL_SECONDS` 短 TTL 缓存，source 创建/更新会主动失效，显式配置映射、`wp4-webhook-default` 和 `secret://wp4/*` fallback 不缓存且仅用于 dev/test，可通过 `WP4_LOCAL_WEBHOOK_SECRET_FALLBACK_ENABLED=false` 禁用；轮换时旧密钥至少保留 `max(WP4_WEBHOOK_SECRET_CACHE_TTL_SECONDS, WP4_WEBHOOK_SECRET_ROTATION_OVERLAP_SECONDS)`。配置文件不再内嵌默认 webhook 明文密钥，也不再预置 `wp4-webhook-default` 映射；本地 smoke 若使用 `WP4_WEBHOOK_SECRET=local-document-input-webhook-secret` 签名，启动后端服务时也必须显式设置同一个环境变量；生产建议使用 SecretProvider 并设置 `WP4_LOCAL_WEBHOOK_SECRET_FALLBACK_ENABLED=false`。

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
bash scripts/wp4_frontend_e2e_smoke.sh
bash scripts/wp4_ai_parse_quality_eval.sh
```

## 跨 WP 发布准出与治理

统一发布入口以 `doc/mvp/final/engineering/WP1-WP4-统一发布准出清单.md` 为准，覆盖本地、CI、预发和生产发布。共享契约变更先查 `doc/mvp/final/engineering/WP1-WP4-变更影响矩阵.md`，确认 WP1 context/audit/secret、WP2 invocation、WP3 asset、WP4 import/publish/webhook 的受影响测试；指标和看板按 `doc/mvp/final/engineering/WP1-WP4-指标命名与看板规范.md` 收敛；发布记录使用 `doc/mvp/final/engineering/WP1-WP4-Release-Notes-模板.md`。

预发/生产发布前的专项 runbook：

- WP1 DB 权限：`doc/mvp/final/engineering/WP1-发布前DB权限Runbook.md`
- WP2 provider 与密钥轮换：`doc/mvp/final/engineering/WP2-Provider接入与SecretRef轮换Runbook.md`
- WP4 webhook 签名联调：`doc/mvp/final/engineering/WP4-Webhook签名样例与联调说明.md`
- WP4 外部连接器接入：`doc/mvp/final/engineering/WP4-外部连接器接入Runbook与Mock契约.md`

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
bash scripts/wp1_db_profile_smoke.sh
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
bash scripts/wp2_model_quality_eval.sh
```

```bash
WP4_SERVICE_TOKEN=local-document-input-token \
WP3_SERVICE_TOKEN=local-asset-token \
WP4_WEBHOOK_SECRET=local-document-input-webhook-secret \
bash scripts/wp4_document_input_smoke.sh
```

```bash
bash scripts/wp4_binary_document_smoke.sh
bash scripts/wp4_frontend_e2e_smoke.sh
bash scripts/wp4_ai_parse_quality_eval.sh
```

```bash
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
- 审计日志可显示写操作名称和结果，并支持 `actor/action/resourceType/result/search/startTime/endTime` 组合筛选；`GET /api/v1/management/audit-logs/export` 支持按同一筛选条件导出 UTF-8 CSV，portal-web 审计页按 `audit:export` 提供下载入口；db profile smoke 已覆盖管理对象创建审计、账号锁定/解锁审计、失败登录审计、拒绝审计、资源作用域过滤、设置 CRUD 和敏感设置拒绝。
- 无权限角色访问管理写接口会返回 `FORBIDDEN`。
- `/v3/api-docs` 可生成 OpenAPI 文档，且契约测试保护认证、管理、账号生命周期和设置 CRUD 关键路径。
- WP2 `db` profile 默认种子可直接完成 local echo 调用、调用日志查询、路由规则/组/能力审计、成本汇总、成本报表、成本告警、供应商就绪检查缓存和 CSV 导出 smoke；WP2 聚合门禁可串联模型接入测试、通用模型质量评测、数据库 validation，并按需执行 HTTP smoke / 模块策略 smoke。
- WP2 通用模型评测集当前覆盖 `case-design`、`defect-triage`、`requirement-summary` 三类任务，可通过 `WP2_MODEL_EVAL_TASK=case-design bash scripts/wp2_model_quality_eval.sh` 跑单任务，用于 Prompt/provider 变更前后的质量回归。
- WP3 已补齐资产库前后端闭环：列表分页、需求/API/页面/业务流/用例/追踪链接基础 API、用户态 `asset:*` RBAC、OpenAPI 契约测试、前端资产 API normalizer、资产库导航、需求/API/页面/业务流/测试用例工作台、只读追踪矩阵工作台、历史版本回滚、统一导入导出、页面/业务流追踪、后端聚合影响分析和标准化原型同步骨架。
- WP4 smoke 覆盖 Markdown 导入、候选 `status/sourceRef/keyword` 筛选、versioned 批量确认、dryRun、发布到 WP3、WP3 `source/sourceRef/sourceUrl/acceptanceCriteria` 追踪、发布记录、`CUSTOM_API` source health、`X-VA-*` 签名 webhook、幂等 replay、事件日志、无效签名拒绝和当前导入/候选/发布/webhook metrics。
- WP4 前端 E2E smoke 使用 Playwright 浏览器覆盖文档输入页真实文件上传、候选编辑/确认、发布 dryRun 预览和 webhook 事件重放；本地无托管 Chromium 时可自动使用系统 Chrome，或设置 `WP4_FRONTEND_INSTALL_BROWSERS=1` 安装。
- WP4 二进制文档 smoke 覆盖真实 docx、真实文本 PDF、OCR 命令 provider 和恶意文件扫描接入点；AI 解析质量评测按 TEXT/MARKDOWN/WORD/PDF/OCR/CUSTOM_API 分桶输出标题召回、优先级准确率、验收标准覆盖率，并绑定 promptKey/promptVersion 执行阈值门禁。

## WP1 1～8 项收敛状态

1. 设置已扩展为分页 CRUD、详情、编辑和启停 API，前端设置操作面板已接入。
2. RBAC 已从资源级绑定扩展到 `db` profile 资源作用域过滤、前端菜单权限和按钮权限。
3. 账号与认证审计已覆盖登录成功、失败登录、账号启停、锁定/解锁、重置密码和资料编辑。
4. 审计已扩展到成功、失败、拒绝、变更结果，并持久化 before/after/diff 字段和筛选分页。
5. 敏感设置键会拒绝明文保存，只允许掩码值或 Secret 引用类写法。
6. OpenAPI 契约测试已覆盖设置 CRUD、认证、管理和账号生命周期关键路径。
7. `db` profile smoke 已扩展到设置 CRUD、敏感设置拒绝、失败登录审计、资源作用域过滤和授权拒绝。
8. 已补充预发/生产 app/readonly/migration 数据库角色校验脚本：`scripts/wp1_release_role_validation.sh`。
