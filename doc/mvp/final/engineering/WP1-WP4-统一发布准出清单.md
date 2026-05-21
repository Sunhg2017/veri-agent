# WP1-WP4 统一发布准出清单

| 项目 | 内容 |
|---|---|
| 覆盖范围 | WP1 平台基础底座、WP2 模型接入层、WP3 资产管理、WP4 需求与文档输入 |
| 适用阶段 | 本地自测、CI、预发发布、生产发布 |
| 当前服务形态 | 单个 `platform-api` Java 服务 + `portal-web` 前端 |
| 日期 | 2026-05-21 |

## 1. 使用原则

1. WP1、WP2、WP3、WP4 是领域工作包，不是服务拆分；发布准出以同一个 `platform-api` 和同一套数据库迁移为核心。
2. 本地和 CI 先跑可重复、无外部依赖的门禁；预发和生产再跑依赖真实连接串、真实密钥和真实 provider 的门禁。
3. 命令失败时按命令所属 WP 定位，跨 WP 失败优先看 `traceId`、`projectId`、`actorService`、`status` 和审计/调用日志。
4. 所有 release notes 必须引用本清单中的已执行命令、输出位置和未执行原因。

## 2. 一页准出索引

| 阶段 | 范围 | 命令 | 失败定位 |
|---|---|---|---|
| 基础后端 | WP1-WP4 后端单元、集成、OpenAPI 契约 | `mvn -B -pl platform-api test` | `platform-api/src/test/java` 中失败用例所属包；OpenAPI 快照输出 `build/openapi/wp1-v1.json` |
| 基础前端 | 管理台测试 | `cd portal-web && npm run test` | `portal-web` 测试输出 |
| 基础前端 | 管理台构建 | `cd portal-web && npm run build` | Vite/TypeScript 构建输出 |
| WP1 DB | 临时库迁移、seed、安全、跨 WP schema validation | `bash db/validation/run_wp1_db_validation.sh` | `build/wp1-db-validation/`，包含 WP1/WP2/WP3/WP4 统一迁移结果 |
| WP1 smoke | 已启动 `db` profile 平台 API | `WP1_BOOTSTRAP_TOKEN=local-init-token bash scripts/wp1_db_profile_smoke.sh` | 管理控制面、RBAC、审计、资源作用域 |
| WP1 quality gate | WP1 本地聚合门禁 | `bash scripts/wp1_quality_gate.sh` | 后端测试、前端测试、前端构建、WP1 DB validation |
| WP1 预发/生产 DB 权限 | 真实应用数据库角色 | `WP1_RELEASE_DATABASE_URL=... WP1_RELEASE_APP_ROLE=... bash scripts/wp1_release_role_validation.sh` | `release.role.*`、`release.audit_log.*`、`release.audit_retention_cleanup.*`、`release.secret_local_store.*` 检查；详见 `WP1-发布前DB权限Runbook.md` |
| WP2 DB | WP2 model access schema/seed/security | `bash db/validation/run_wp2_db_validation.sh` | `build/wp2-db-validation/` |
| WP2 smoke | 已启动平台 API 的模型接入 API | `WP2_SERVICE_TOKEN=local-model-access-token bash scripts/wp2_model_access_smoke.sh` | provider、prompt、invocation、日志、成本、导出 |
| WP2 policy smoke | WP2 消费 WP1 context/audit 策略 | `WP1_SERVICE_TOKEN=local-platform-service-token WP1_AUTH_TOKEN_SECRET=local-auth-secret WP2_SERVICE_TOKEN=local-model-access-token bash scripts/wp2_module_policy_smoke.sh` | 公开模型策略、敏感级别升级、本地模型调用 |
| WP2 quality gate | WP2 聚合门禁 | `bash scripts/wp2_quality_gate.sh` | 后端测试、portal-web 模型接入测试与构建、WP2 DB validation；HTTP smoke 需 `WP2_RUN_HTTP_SMOKE=1`，策略 smoke 需 `WP2_RUN_POLICY_SMOKE=1` |
| WP3 API | WP3 资产基础 API、版本历史、上下文/审计契约与 OpenAPI 覆盖 | `mvn -B -pl platform-api -Dtest=AssetControllerTest,AssetContextAuditContractTest,AssetOpenApiContractTest test` | 资产 CRUD、版本递增、history/diff、trace link、WP1 context/audit 异常、OpenAPI 中 `/api/v1/asset/**` |
| WP3 quality gate | WP3 本地/CI 聚合门禁 | `bash scripts/wp3_quality_gate.sh` | 后端资产测试、OpenAPI/上下文契约、前端资产测试、WP1-WP4 统一 DB validation |
| WP3 schema | WP3 表、索引、版本历史 append-only 权限和无租户字段回归 | `bash db/validation/run_wp1_db_validation.sh` | `wp_all_schema_validation.sql` 与 `wp1_security_validation.sql` 中 `asset_*` 检查 |
| WP4 smoke | 已启动平台 API 的文档输入主链路 | `WP4_SERVICE_TOKEN=local-document-input-token WP3_SERVICE_TOKEN=local-asset-token WP4_WEBHOOK_SECRET=local-document-input-webhook-secret bash scripts/wp4_document_input_smoke.sh` | 导入、候选、发布、webhook、事件日志、metrics |
| WP4 binary | Word/PDF/OCR 文本抽取 | `bash scripts/wp4_binary_document_smoke.sh` | docx、PDF、OCR provider、SecretProvider 测试 |
| WP4 AI 质量 | golden corpus 质量门禁 | `bash scripts/wp4_ai_parse_quality_eval.sh` | 标题召回、优先级准确率、验收标准覆盖率 |
| WP1-WP4 E2E | 统一平台端到端 smoke | `WP1_BOOTSTRAP_TOKEN=local-init-token WP2_SERVICE_TOKEN=local-model-access-token WP3_SERVICE_TOKEN=local-asset-token WP4_SERVICE_TOKEN=local-document-input-token WP4_WEBHOOK_SECRET=local-document-input-webhook-secret bash scripts/wp_all_integration_test.sh` | WP1 context、WP2 invocation、WP3 asset、WP4 publish/webhook 串联 |

## 3. 推荐执行顺序

### 3.1 本地或 PR CI

```bash
mvn -B -pl platform-api test
```

```bash
cd portal-web && npm run test
```

```bash
cd portal-web && npm run build
```

```bash
bash db/validation/run_wp1_db_validation.sh
```

```bash
bash scripts/wp1_quality_gate.sh
```

```bash
bash scripts/wp2_quality_gate.sh
```

```bash
bash scripts/wp3_quality_gate.sh
```

```bash
bash scripts/wp4_binary_document_smoke.sh
bash scripts/wp4_ai_parse_quality_eval.sh
```

### 3.2 预发发布窗口

1. 部署 `platform-api` 的候选版本到预发，连接预发 PostgreSQL。
2. 对预发库执行真实角色权限校验：

```bash
WP1_RELEASE_DATABASE_URL='postgres://dba_readonly:***@preprod-db:5432/veri_agent' \
WP1_RELEASE_APP_ROLE='wp1_app_preprod' \
bash scripts/wp1_release_role_validation.sh
```

3. 对已启动预发服务执行跨 WP smoke：

```bash
WP_ALL_BASE_URL='https://preprod.example.test' \
WP1_BOOTSTRAP_TOKEN='***' \
WP2_SERVICE_TOKEN='***' \
WP3_SERVICE_TOKEN='***' \
WP4_SERVICE_TOKEN='***' \
WP4_WEBHOOK_SECRET='***' \
bash scripts/wp_all_integration_test.sh
```

4. 如果本次影响 WP2 外部 provider，按 `WP2-Provider接入与SecretRef轮换Runbook.md` 执行 provider check 和最小 invocation。
5. 如果本次影响 WP4 webhook，按 `WP4-Webhook签名样例与联调说明.md` 用外部系统或联调脚本完成签名请求。

### 3.3 生产发布窗口

| 检查 | 要求 |
|---|---|
| 变更影响矩阵 | 已按 `WP1-WP4-变更影响矩阵.md` 列出受影响 WP、测试入口和回滚点 |
| 迁移 | 生产 migration 计划、回滚或前滚策略已确认；应用角色不执行 DDL |
| DB 权限 | `scripts/wp1_release_role_validation.sh` 对真实生产 app role 通过，确认 app role 不能直接删改 `audit_log` 但可执行受控审计保留清理函数，DBA 复核项完成 |
| Secret/provider | 新旧密钥、provider、环境变量或 SecretProvider 引用均已进入轮换窗口 |
| 指标告警 | 发布期间看板和告警已打开，traceId 能从响应串到审计/调用日志 |
| release notes | 已使用 `WP1-WP4-Release-Notes-模板.md` 填写验证、风险和回滚 |

## 4. 失败分流

| 失败现象 | 优先 owner | 先看 |
|---|---|---|
| `mvn -pl platform-api test` 失败 | 对应领域后端 owner | 测试类包名、失败断言、OpenAPI 契约 diff |
| 前端测试或构建失败 | 前端 owner | `portal-web` 输出、权限/菜单 mock、类型错误 |
| `run_wp1_db_validation.sh` 失败 | DB/平台 owner | `build/wp1-db-validation/*.out` 中 `FAIL/WARN` |
| `wp1_release_role_validation.sh` 失败 | DBA + WP1 owner | 真实 app role、audit append-only、secret local store 权限 |
| WP2 provider check 失败 | WP2 + 运维 owner | `apiKeyRef`、env var、baseUrl、401/403/429/5xx、熔断窗口 |
| WP2 invocation 被阻断 | WP2 + WP1 owner | `MODEL_POLICY_VIOLATION`、`BUDGET_EXCEEDED`、项目敏感级别和 `allowPublicModel` |
| WP4 publish 失败 | WP4 + WP3 owner | dryRun 结果、`CONFLICT_REVIEW_REQUIRED`、WP3 需求状态和 sourceRef |
| WP4 webhook 签名失败 | WP4 + 外部系统 owner | rawBody 是否一致、时间戳窗口、eventId/idempotencyKey、secretRef |
| E2E smoke 失败 | 发布负责人分流 | 第一条失败的 `PASS/FAIL` 标签、响应 `traceId` |

## 5. 准出记录要求

每次预发或生产发布至少保存以下材料：

1. 本清单中实际执行命令、执行环境和结果。
2. DB validation 输出目录或 CI artifact 链接。
3. 预发/生产 release role validation 输出。
4. 受影响 provider、secretRef/env key、webhook sourceCode、projectId。
5. 失败项、豁免项、owner、补救或回滚动作。
6. 填写完成的 release notes。
