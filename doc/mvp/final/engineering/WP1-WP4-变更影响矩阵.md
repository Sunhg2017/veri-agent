# WP1-WP4 变更影响矩阵

| 项目 | 内容 |
|---|---|
| 覆盖范围 | WP1 context/audit/secret、WP2 invocation、WP3 asset、WP4 import/publish/webhook |
| 用途 | 评估共享契约变更的受影响模块、测试入口和发布风险 |
| 日期 | 2026-05-20 |

## 1. 共享契约矩阵

| 变更点 | 直接 owner | 受影响 WP | 必跑验证 | 风险提示 |
|---|---|---|---|---|
| WP1 context 响应：`projectId`、`applicationId`、`environmentId`、`allowPublicModel`、`sensitivityLevel`、资源状态 | WP1 | WP2、WP3、WP4 | `mvn -B -pl platform-api -Dtest=ModelAccessPlatformPolicyTest,DocumentInputControllerTest,AssetControllerTest test`；`bash scripts/wp2_module_policy_smoke.sh`；`bash scripts/wp_all_integration_test.sh` | 敏感级别映射错误会放开公开模型或阻断合法调用 |
| WP1 audit 写入契约：action、resourceType、result、traceId、before/after/diff | WP1 | WP2、WP3、WP4 | `mvn -B -pl platform-api test`；`WP1_BOOTSTRAP_TOKEN=local-init-token bash scripts/wp1_db_profile_smoke.sh`；`bash scripts/wp_all_integration_test.sh` | audit 写失败不应污染主业务；失败要能通过日志或指标定位 |
| WP1 SecretProvider：`secret_reference`、用途、作用域、过期、provider 状态、外部 Vault/KMS resolve/health/签名认证配置、WP4 webhook secret TTL 缓存/主动失效/轮换窗口、resolve 成功/失败审计 | WP1 | WP4，后续 WP2 | `bash scripts/wp4_binary_document_smoke.sh`；`mvn -B -pl platform-api -Dtest=DocumentWebhookSecretResolverTest,ExternalSecretProviderTest,DocumentInputControllerTest test`；`bash db/validation/run_wp1_db_validation.sh` | 明文、完整 secretRef、Bearer token、签名密钥不得进入响应、审计、日志或健康摘要；resolve 审计仅允许记录 `secretRefDigest` 与 provider/用途/作用域元数据；生产 fallback 关闭后必须拒绝未解析引用；SecretProvider 成功解析结果可短 TTL 缓存但配置/default fallback 不缓存；source 创建/更新必须主动失效；旧密钥撤销不得早于 `max(TTL, rotationOverlap)`；外部 provider 不可用时要能通过健康摘要、指标和审计定位 |
| WP1 RBAC 权限点和菜单权限 | WP1 + 前端 | WP1、WP4、portal-web | `cd portal-web && npm run test`；`mvn -B -pl platform-api test`；`bash scripts/wp1_quality_gate.sh` | 权限点改名会导致按钮误显、API 403 或越权 |
| 统一 envelope、camelCase、分页 `index/size/items/total` | 平台架构 | 全部 | `mvn -B -pl platform-api -Dtest=OpenApiContractTest,ModelAccessOpenApiContractTest,DocumentInputOpenApiContractTest test`；`bash scripts/wp_all_integration_test.sh` | 破坏前端 normalizer、smoke jq 断言和外部联调 |
| WP2 provider 配置：`providerType`、`routingGroup`、`capabilities`、`baseUrl`、`apiKeyRef`、priority、timeout、成本字段 | WP2 | WP2、WP4 AI 解析 | `mvn -B -pl platform-api -Dtest=ModelAccessControllerTest,OpenAiCompatibleModelProviderClientTest test`；`WP2_SERVICE_TOKEN=local-model-access-token bash scripts/wp2_model_access_smoke.sh`；`bash scripts/wp2_model_quality_eval.sh` | 当前 external provider key 使用 `env:VARIABLE_NAME`；误填 secret 明文会被契约和 runbook 阻断；provider/prompt/路由能力变更需确认核心任务评测未降级 |
| WP2 invocation 契约：promptKey、messages、sensitivityLevel、allowPublicModel、capability、响应 `providerName/modelName/totalCost` 和审计 `routingRuleName/routingGroup/modelCapability` | WP2 | WP4 AI 解析、后续智能 WP | `mvn -B -pl platform-api -Dtest=ModelAccessControllerTest,DocumentInputModelParseControllerTest test`；`bash scripts/wp2_model_quality_eval.sh`；`bash scripts/wp4_ai_parse_quality_eval.sh` | WP4 依赖 `modelInvocationId`、`modelProviderName`、`modelName` 做候选追踪；智能任务 prompt 输出需保持 taskType 评测阈值；路由审计字段不得包含 prompt 明文 |
| WP2 策略/预算错误码与成本告警：`MODEL_POLICY_VIOLATION`、`BUDGET_EXCEEDED`、`MODEL_PROVIDER_UNAVAILABLE`、`/cost/alerts?actorService=` | WP2 | WP4 AI 解析、portal-web 成本页 | `mvn -B -pl platform-api -Dtest=ModelAccessBudgetPolicyTest,ModelAccessCallerBudgetPolicyTest,ModelAccessBudgetFallbackPolicyTest,ModelAccessPlatformPolicyTest,DocumentInputModelParseControllerTest test`；`cd portal-web && npm test` | WP4 必须 fallback 到规则解析，不能绕过 WP2；调用服务预算使用 `actorService` 做 drilldown，不进入 Prometheus 高基数标签 |
| WP3 requirement upsert 字段：`externalRequirementId`、`source/sourceRef/sourceUrl`、`acceptanceCriteria`、status | WP3 | WP4 publish | `mvn -B -pl platform-api -Dtest=AssetControllerTest,DocumentInputControllerTest test`；`bash scripts/wp_all_integration_test.sh` | 非 `DRAFT` IMPORT 资产有差异时应返回冲突，不能覆盖人工评审结果 |
| WP3 trace link：requirement/api/testCase 关联 | WP3 | WP4、后续测试生成/执行 WP | `mvn -B -pl platform-api -Dtest=AssetControllerTest test`；`bash scripts/wp_all_integration_test.sh` | 关联字段改动会影响覆盖矩阵和影响分析 |
| WP4 import/candidate/publish 契约 | WP4 | WP3、portal-web | `mvn -B -pl platform-api -Dtest=DocumentInputControllerTest,DocumentInputOpenApiContractTest test`；`bash scripts/wp4_document_input_smoke.sh`；`bash scripts/wp4_frontend_e2e_smoke.sh` | 候选状态、versioned candidates、multipart 上传或 dryRun 字段变化会破坏发布预览 |
| WP4 webhook 安全头、签名串、eventVersion、mappingVersion | WP4 | 外部需求平台、WP1 SecretProvider | `bash scripts/wp4_document_input_smoke.sh`；按 `WP4-Webhook签名样例与联调说明.md` 联调 | raw body 不一致、时间戳超窗或 secretRef 错配会导致合法事件被拒 |
| DB migration under `db/migration/wp1` | DB owner | 全部 | `bash db/validation/run_wp1_db_validation.sh`；必要时 `bash db/validation/run_wp2_db_validation.sh` | 当前 WP1-WP4 共享同一迁移目录；字段/索引变化要同步 validation |
| Metrics 命名与标签 | 对应 WP + 运维 | 全部 | `bash scripts/wp4_document_input_smoke.sh`；`WP2_SERVICE_TOKEN=local-model-access-token bash scripts/wp2_model_access_smoke.sh`；人工查 `/actuator/metrics/**` | 高基数字段不要直接打到 Prometheus 标签；projectId/actorService 用审计/日志 drilldown |

## 2. 变更评审问题清单

任一共享契约变更进入评审前，必须回答：

1. 是否改变 API path、HTTP method、字段名、枚举、错误码、分页或 envelope。
2. 是否改变 WP1 context、audit、SecretProvider、RBAC 的输入或输出。
3. 是否改变数据库表、索引、约束、seed、权限或迁移顺序。
4. 是否影响 `projectId`、`applicationId`、`environmentId`、`actorService`、`traceId` 的传递。
5. 是否影响外部 provider、webhook、OCR、Vault/KMS 这类真实外部依赖。
6. 是否需要更新 OpenAPI 契约、smoke、DB validation、README、release notes。
7. 是否有灰度、回滚或前滚策略；如果不能回滚，是否已标明数据迁移风险。

## 3. 常见变更到测试入口映射

| 变更类型 | 最小测试 | 发布前补充 |
|---|---|---|
| 仅文档 | `git diff --check` | 复核 README 和对应 runbook 链接 |
| WP1 权限、审计、context | `bash scripts/wp1_quality_gate.sh` | `bash scripts/wp2_module_policy_smoke.sh`、`bash scripts/wp_all_integration_test.sh` |
| WP1 DB migration/permission | `bash db/validation/run_wp1_db_validation.sh` | `scripts/wp1_release_role_validation.sh` 对预发/生产真实 role |
| WP2 provider/prompt/invocation | `bash scripts/wp2_quality_gate.sh`；`bash scripts/wp2_model_quality_eval.sh` | `WP2_RUN_HTTP_SMOKE=1 bash scripts/wp2_quality_gate.sh` 或直接 provider check；Prompt 专项可用 `WP2_MODEL_EVAL_TASK=<taskType>` |
| WP2 策略/预算 | `mvn -B -pl platform-api -Dtest=ModelAccessBudgetPolicyTest,ModelAccessCallerBudgetPolicyTest,ModelAccessBudgetFallbackPolicyTest,ModelAccessPlatformPolicyTest test` | WP4 AI 解析 smoke/质量门禁 |
| WP3 asset/upsert | `mvn -B -pl platform-api -Dtest=AssetControllerTest,OpenApiContractTest test` | `bash scripts/wp_all_integration_test.sh` |
| WP4 import/candidate/publish | `mvn -B -pl platform-api -Dtest=DocumentInputControllerTest,DocumentInputOpenApiContractTest test` | `bash scripts/wp4_document_input_smoke.sh`；`bash scripts/wp4_frontend_e2e_smoke.sh` |
| WP4 Word/PDF/OCR | `bash scripts/wp4_binary_document_smoke.sh` | 预发用真实 OCR provider 验证 |
| WP4 AI parse | `bash scripts/wp4_ai_parse_quality_eval.sh` | 开启 `WP4_MODEL_PARSE_ENABLED=true` 后跑 WP4 smoke |
| WP4 webhook/security | `bash scripts/wp4_document_input_smoke.sh` | 外部系统按签名样例完成联调 |

## 4. 变更记录口径

release notes 中的影响范围应按以下格式记录：

```text
影响契约：WP1 context / WP2 invocation / WP3 requirement upsert / WP4 webhook
影响字段：fieldA, fieldB
受影响测试：command A, command B
数据迁移：有/无，脚本和回滚策略
配置和密钥：新增/变更/废弃
观测：metrics/log/audit/traceId
回滚：可回滚/仅前滚，原因
```
