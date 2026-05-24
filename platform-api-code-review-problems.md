# Platform API 代码审查问题清单

> 审查日期: 2026-05-23
> 审查范围: `platform-api` 模块全部 Java 源代码及 MyBatis Mapper XML

---

## 处理状态与任务拆解（更新于 2026-05-24）

### 本批处理结论

本轮优先处理安全、审计完整性、启动装配、事务边界、DB 索引和低风险性能/质量项；架构拆分、资源级鉴权、SQL 分页下沉、db-profile Testcontainers 等高影响改造已拆为专项，避免在一次修复中引入跨 WP 行为风险。

| 编号 | 拆分任务 | 状态 | 处理方式 / 下一步 |
|---|---|---|---|
| S1 | 服务 token 路径限制 `X-Caller-Service` 来源 | 已完成 | 新增 `ServiceCallerProperties` 白名单，`ServiceTokenAuthenticationFilter` 和 WP1 内部 audit 入口拒绝非可信 caller；网关剥离外部同名头仍作为发布配置要求 |
| S2 | 审计 actor 为空时保留可追溯元数据 | 已完成 | `PostgresAuditLogWriter` 默认 afterJson 写入 `resourceId`、`actorType`、`actorUserId`，无 actor 标记为 `SYSTEM` |
| S3 | token secret 强度校验 | 已完成 | 已在上一批加入至少 32 bytes 校验并覆盖测试 |
| S4 | 移除配置文件中的默认 webhook 明文密钥 | 已完成 | `application.yml` 不再内嵌默认 secret；测试和 smoke 需显式设置 `WP4_WEBHOOK_SECRET`，生产使用 SecretProvider 并关闭本地 fallback |
| S5 | 登录时序侧信道 | 已完成 | 已在上一批加入 dummy BCrypt 校验路径 |
| S6 / Q1 | SensitiveContentGuard 规则重复和 mask 顺序依赖 | 已完成 | 已在上一批统一规则源并增加直接单测 |
| S7 | 资产批量操作资源级鉴权 | 已完成 | 新增 `ResourceScope` 资源级校验，AssetController 对项目级列表/导入/导出/同步/单资源操作按项目 scope 授权，并补充跨项目拒绝用例 |
| S8 | 文档导入事务边界 | 已完成 | `DocumentInputService.importDocument/importMultipart` 增加事务边界 |
| A1 | Service 单一职责拆分 | 进行中 | 已完成 WP3 `AssetService` 版本历史记录/查询职责拆分到 `AssetVersionHistoryService`，版本回滚和 snapshot 还原职责拆分到 `AssetVersionRollbackService`，资产生命周期变更、拒绝审计和恢复冲突校验拆分到 `AssetLifecycleService`，需求资产列表/读写/导入合并/评审状态/历史版本入口拆分到 `AssetRequirementService`，API 资产列表/读写/路径方法唯一性和状态流转拆分到 `AssetApiService`，页面资产列表/读取/componentTree 序列化和状态流转拆分到 `AssetPageService`，业务流资产列表/读取/flowJson 序列化和状态流转拆分到 `AssetBusinessFlowService`，影响分析图遍历、缺口计算和节点映射拆分到 `AssetImpactAnalysisService`，资产导入导出编排、CSV/JSON/OpenAPI 编解码和导入计划拆分到 `AssetImportExportService`，原型页面同步计划/创建/更新职责拆分到 `AssetPrototypeSyncService`，追踪关系查询/创建、跨项目目标校验和审计拆分到 `AssetTraceLinkService`，测试用例步骤查询/替换、步骤审计和历史记录拆分到 `AssetTestCaseStepService`，项目上下文校验和资产审计写入收敛到 `AssetProjectAuditService`，响应 DTO 映射、生命周期展示归一化和测试步骤响应排序拆分到 `AssetResponseMapper`；已完成 WP2 同步模型调用的 Prompt 渲染、平台策略、路由、预算、fallback、调用记录和审计编排拆分到 `ModelInvocationService`，并让 WP4 AI 解析直接复用该统一调用编排；已将 WP2 成本告警和成本日报聚合职责拆到 `ModelCostAnalysisService`，模型供应商 CRUD、启停、就绪检查、熔断观测和配置校验拆到 `ModelProviderManagementService`，Prompt 版本创建、激活、审批和活跃统计拆到 `PromptTemplateManagementService`；已将 WP4 模型解析人工纠错样本捕获、脱敏快照和审计职责拆分到 `DocumentParseFeedbackCaptureService`，并将 WP4 文档源管理、候选项生命周期、发布编排和响应 DTO 映射拆到 `DocumentSourceManagementService`、`DocumentCandidateWorkflowService`、`DocumentRequirementPublishService`、`DocumentInputResponseMapper`；已将管理台审计查询、配置管理、部门管理、用户管理、项目管理、应用管理、环境管理、角色/权限管理和密钥引用生命周期拆到 `PostgresManagementAuditQueryService`、`PostgresManagementConfigService`、`PostgresManagementDepartmentService`、`PostgresManagementUserService`、`PostgresManagementProjectService`、`PostgresManagementApplicationService`、`PostgresManagementEnvironmentService`、`PostgresManagementRoleService`、`PostgresManagementSecretReferenceService`，并将 local profile 管理台部门列表/状态生命周期拆到 `InMemoryManagementDepartmentService`，用户列表/创建/更新/状态/角色生命周期拆到 `InMemoryManagementUserService`，项目列表/状态流转和项目成员生命周期拆到 `InMemoryManagementProjectService`，应用列表/接入状态和负责人生命周期拆到 `InMemoryManagementApplicationService`，环境列表/状态/连通性检查和授权用户生命周期拆到 `InMemoryManagementEnvironmentService`，集成列表/状态生命周期拆到 `InMemoryManagementIntegrationService`，配置生命周期和敏感配置策略拆到 `InMemoryManagementConfigService`，密钥引用创建/轮换/撤销生命周期拆到 `InMemoryManagementSecretReferenceService`，角色权限生命周期和用户角色分配校验拆到 `InMemoryManagementRoleService`，审计日志查询、CSV 导出和 outbox 查询拆到 `InMemoryManagementAuditQueryService`；整体 A1 保持渐进治理，后续仅针对新增高耦合热点继续跟踪 |
| A2 | 贫血领域模型治理 | 已完成 | 已将 WP3 需求/测试用例评审状态集合与转换规则迁入 `AssetReviewStatus`，将资产生命周期归一化与转换规则迁入 `AssetLifecycleStatus`/`LifecycleManagedAsset`，并将版本初始值、递增和历史版本匹配规则迁入 `AssetVersion`/`VersionedAsset` |
| A3 | 统一权限注解/AOP | 已完成 | 新增 `@RequirePermission` 注解和 AOP 切面；ModelAccessController、DocumentInputController 的普通权限入口改为注解校验；AssetController 保留资源级 `ResourceScope` 显式校验但不再直接读取 `SecurityContextHolder` |
| A4 / Q8 / T3 | local/db profile 行为差异与 db 测试不足 | 已完成 | 新增 PostgreSQL Testcontainers db-profile 契约测试，覆盖 Postgres AuthSessionStore、ModelAccessRepository、AssetRepository 的真实迁移、mapper、约束、分页和事务回滚路径 |
| A5 | 审计写入独立可靠性 | 已完成 | `PostgresAuditLogWriter.record` 使用 `REQUIRES_NEW` 独立事务，db-profile Testcontainers 合约覆盖业务事务回滚时审计仍落库；异步 outbox/失败补偿作为运行治理增强继续跟踪 |
| A6 | 分层异常策略 | 已完成 | 新增 `PlatformAccessDeniedException` 承载权限/资源上下文；`AuthorizationService` 和模型调用主体解析不再抛原生权限异常；`GlobalExceptionHandler` 统一错误响应构造和 `traceId/code/type` 日志上下文 |
| P1 | Asset 列表查询 SQL 分页/过滤/排序下沉 | 已完成 | 新增 `AssetListQuery` 和 Repository 分页/count 契约，五类资产列表下沉到 MyBatis 动态 SQL；local 实现保持同等过滤/分页语义并补充回归测试 |
| P2 | InMemoryAuthSessionStore refresh 查询 O(n) | 已完成 | 已增加 refreshTokenHash 二级索引和回归测试 |
| P3 | Cost Alert distinct 下沉 SQL | 已完成 | 新增 repository distinct project/service 查询接口；Postgres 通过 `SELECT DISTINCT` 下沉，local profile 保持同等去空、排序语义，并补充 service 级回归测试 |
| P4 | CSV 导出转义和流式输出 | 已完成 | `/model-access/invocations/export` 改为 `StreamingResponseBody`，service 按 100 行分页写出 CSV 并保留逗号、引号、换行转义测试 |
| P5 | 预算窗口重复计算 | 已完成 | invocation 内预先计算并复用 `BudgetWindow`，避免 fallback 多 provider 重复计算 |
| Q2 | 中文错误信息 i18n | 专项任务 | 拆为错误码/消息资源化专项，不纳入本批行为修复 |
| Q3 | AssetService 静态 ObjectMapper | 已完成 | 改为注入 Spring `ObjectMapper`，保留手工单测兼容构造器并标注容器构造器 |
| Q4 | AssetService 常量清理 | 已完成 | 已将导入导出资产类型/格式收敛为按资产类型配置的格式矩阵，并将影响分析 `BUSINESS_FLOW`/`TEST_CASE` 作为别名映射到规范 `FLOW`/`CASE`，避免重复含义常量 |
| Q5 | 密码请求 DTO 脱敏 | 已完成 | 已在上一批覆盖 request `toString()` 脱敏 |
| Q6 | String 标识符强类型化 | 专项任务 | 拆为 Value Object 渐进改造专项 |
| Q7 | `AuditLogWriter.denied()` targetName 歧义 | 已完成 | 简化重载不再把 resourceId 写成 targetName，并补充审计单测 |
| D1 | 迁移回滚策略 | 已完成 | 新增迁移发布计划脚本和回滚/前滚 Runbook，统一准出清单已纳入发布证据要求；继续采用 forward-only V migration，不引入 Flyway Undo |
| D2 | `iam_session` cleanup 索引 | 已完成 | 新增 expires/revoked/cleanup 索引 migration，并更新 schema validation |
| D3 | 审计清理索引 | 已有保障 | 现有迁移已包含 `idx_audit_log_time` 和 `idx_audit_outbox_created_at` |
| T1 | AssetService 核心单测 | 已完成 | 新增 `AssetServiceCoreTest`，覆盖需求状态转换拒绝、导入合并/审批后冲突和历史版本回滚生命周期恢复 |
| T2 | SensitiveContentGuard 直接测试 | 已完成 | 已在上一批补充独立测试 |
| M1 | application.yml 分层 | 已完成 | `application.yml` 保留 Spring/server/mybatis/OpenAPI 基线配置；WP1 平台治理、WP2 模型接入、WP3 资产和 WP4 文档输入配置拆到 `application-platform.yml`、`application-model-access.yml`、`application-asset.yml`、`application-document-input.yml` 并通过 `spring.config.import` 引入；新增配置分层绑定契约测试 |
| M2 | API 版本策略 | 已完成 | 新增 `WP1-WP4-API版本化策略.md`；定义 `@ApiVersion`/`ApiLifecycle` Controller 元数据并通过 OpenAPI operation 扩展输出 `x-api-version`、`x-api-lifecycle`、`x-api-version-since`；契约测试覆盖全局版本策略和所有 `/api/v1` operation 的版本生命周期字段 |
| M3 | OpenAPI 文档覆盖 | 已完成 | 新增统一 OpenAPI 文档增强器，为缺少显式 `@Operation` 的公开 API 补齐 summary/tag/标准错误响应说明；新增 contract test 阻止 `/api/v1` operation 漏文档 |
| X1 | 异步模型调用 Job 持久化 | 已完成 | 新增 `ma_invocation_job` 持久化表和 db/local repository，异步任务提交、状态流转、取消、成功结果和失败信息持久化；服务启动时重排 QUEUED 任务并将遗留 RUNNING 标记失败 |
| X2 | RestClient 复用 | 已完成 | 按 baseUrl + timeout 缓存 `RestClient` |
| X3 | 分布式限流/熔断 | 已完成 | 新增 `ProviderResilienceStateStore`，默认 local profile 继续使用本地实现，`redis` profile 下通过 Redis 共享 provider 固定窗口限流和熔断状态 |
| X4 | DocumentInputService Pattern 未使用 | 无影响 | 复核后确认这些 Pattern 被纠错样本脱敏路径使用；当前已随 `DocumentParseFeedbackCaptureService` 拆分迁出 `DocumentInputService`，不再作为缺陷处理 |

### 剩余专项优先级

1. 架构治理专项：A1，按模块逐步拆分并保持接口兼容；已推进 WP3 版本历史、版本回滚、生命周期、需求资产、API 资产、页面资产、业务流资产、影响分析、资产导入导出、原型同步、追踪关系、测试用例步骤和项目审计上下文职责拆分，WP2 模型同步调用编排、成本分析、供应商管理和 Prompt 管理职责拆分，WP4 AI 解析统一调用和模型解析人工纠错回流拆分；已完成 `DocumentInputService` 的文档源、候选项、发布和响应 DTO 映射职责拆分，以及 `PostgresManagementConsoleService` 的审计查询、配置管理、部门管理、用户管理、项目管理、应用管理、环境管理、角色/权限管理和密钥引用职责拆分；local profile 管理台已拆出 `InMemoryManagementDepartmentService`、`InMemoryManagementUserService`、`InMemoryManagementProjectService`、`InMemoryManagementApplicationService`、`InMemoryManagementEnvironmentService`、`InMemoryManagementIntegrationService`、`InMemoryManagementConfigService`、`InMemoryManagementSecretReferenceService`、`InMemoryManagementRoleService` 和 `InMemoryManagementAuditQueryService` 承接部门、用户、项目、项目成员、应用、应用负责人、环境、环境连通性检查、环境授权用户、集成、配置、密钥引用、角色权限和审计查询生命周期。A1 仍作为渐进治理专项保留，后续仅针对新增或复燃的高耦合热点继续跟踪。
2. 运行治理专项：暂无独立剩余项；后续按生产部署形态继续观察 Redis 可用性和网关限流策略。

## 一、安全风险 (Security)

### [CRITICAL] S1: ServiceTokenAuthenticationFilter 信任客户端请求头

- **文件**: `common/security/ServiceTokenAuthenticationFilter.java`
- **问题**: `doFilterInternal()` 方法从请求头 `X-Caller-Service` 和 `X-Delegated-User-Id` 中直接提取调用方身份，未验证这些头是否来自可信代理。
- **风险**: 外部请求若直接到达 API 网关后的服务，可通过伪造 HTTP 头冒充其他服务身份，绕过权限检查执行越权操作。
- **建议**: 
  1. 在 API 网关层剥离外部请求的 `X-Caller-Service` 头
  2. 或增加可信代理 CIDR 白名单验证
  3. 或只从 Service Token 中提取调用方身份，而非请求头

### [CRITICAL] S2: 审计日志记录 Actor 可能为 null

- **文件**: `common/audit/AuditLogWriter.java`
- **问题**: `AuditRecord` 中 `actor` 字段为 `AuthUserPrincipal` 类型但可为 null。`AuditLogWriter.denied()` 静态方法在无 actor 时创建 null 记录。检查 `PostgresAuditLogWriter` 实现发现 actor 的 userId 直接写入数据库时可能为 null。
- **风险**: 审计日志中操作人信息不完整，影响事后追溯。

### [HIGH] S3: 令牌签名密钥明文存储

- **文件**: `application.yml` (line 40)
- **问题**: `veri-agent.auth.token-secret` 必须由 `WP1_AUTH_TOKEN_SECRET` 显式配置；当前 `ensureTokenSecret()` 仅在未配置时抛出异常，但未对令牌密钥强度做任何校验。
- **风险**: 弱密钥可被暴力破解，导致 JWT-like 令牌被伪造。
- **建议**: 增加密钥强度校验（至少 32 字节随机值），启动时检查

### [HIGH] S4: Webhook 签名密钥明文配置

- **文件**: `application.yml` (line 92-94)
- **问题**: `webhook-secret` 和 `webhook-secrets` 以明文方式配置在配置文件中，未加密存储。
- **风险**: Webhook 签名密钥泄露后攻击者可伪造 webhook 请求。
- **建议**: 生产环境应通过外部密钥管理服务（Vault/KMS）注入，配置文件中仅保留引用

### [MEDIUM] S5: 登录接口存在用户存在性检测时序侧信道

- **文件**: `auth/application/AuthService.java` (line 44-53)
- **问题**: 先通过 `findEnabledByUsername()` 查找用户，再通过 `passwordEncoder.matches()` 验证密码。用户是否存在和密码是否错误是两个步骤，可能在响应时间上存在可测量的差异（BCrypt 验证比查找更耗时）。
- **风险**: 攻击者可通过响应时间差异枚举有效用户名。
- **建议**: 始终先执行 BCrypt 验证再检查用户是否存在，或对所有情况使用固定延迟

### [MEDIUM] S6: SensitiveContentGuard 的 Mask 模式存在顺序依赖缺陷

- **文件**: `modelaccess/application/SensitiveContentGuard.java` (line 46-61)
- **问题**: `mask()` 方法依次对字符串应用替换。由于每次替换都修改字符串，后续 Pattern 操作的是已被部分替换的内容。例如：`***EMAIL***` 中包含 `***`，可能与后续掩码中的分隔符冲突。
- **风险**: 掩码结果可能不一致或漏掉某些敏感信息。
- **建议**: 使用占位符映射方式，在一次遍历中完成所有替换

### [MEDIUM] S7: 批量操作接口缺少权限边界校验

- **文件**: `asset/api/controller/AssetController.java`
- **问题**: `requirePermission()` 方法对 `AuthUserPrincipal` 仅检查字符串级权限标识（如 `"asset:manage"`），未结合资源所属项目/组织等上下文进行细粒度鉴权。角色为 `ProjectOwner` 的用户可以管理自己项目的资产，但系统未校验操作的目标资源是否属于该用户的项目。
- **建议**: 引入资源级鉴权（如 `AuthorizationService.require(principal, permission, resourceScope)`）

### [MEDIUM] S8: DocumentInputController 批量操作缺少事务边界

- **文件**: `documentinput/api/controller/DocumentInputController.java`
- **问题**: `importMultipart()` 方法处理文件上传，若业务处理中发生异常可能导致部分数据已写入但后续操作回滚不一致。
- **建议**: 在 Service 方法上添加 `@Transactional` 注解

---

## 二、架构设计 (Architecture)

### [CRITICAL] A1: Service 层严重违反单一职责原则

- **文件**: 
  - `modelaccess/application/ModelAccessService.java`（当前约 343 行，同步调用编排、成本分析、供应商管理和 Prompt 管理职责已拆出）
  - `asset/application/AssetService.java`（当前约 608 行，版本历史/版本回滚/生命周期/需求资产/API 资产/页面资产/业务流资产/影响分析/导入导出/原型同步/追踪关系/测试用例步骤/项目审计上下文/响应 DTO 映射职责已拆出）
  - `documentinput/application/DocumentInputService.java`（当前约 1216 行，源管理/候选流程/发布编排/纠错反馈/响应 DTO 映射已拆出）
  - `management/infrastructure/PostgresManagementConsoleService.java`（当前约 465 行，审计查询/配置管理/部门管理/用户管理/项目管理/应用管理/环境管理/角色权限管理/密钥引用已拆出）
  - `management/infrastructure/InMemoryManagementConsoleService.java`（当前约 410 行，local profile 部门、用户、项目、应用、环境、集成、配置、密钥引用、角色权限和审计查询职责已拆出）
- **问题**: 核心 Service 类规模巨大，混合了路由决策、预算检查、供应商管理、Prompt 管理、审计追踪、CSV导出等不同职责。
- **建议**: 
  - 将 `ModelAccessService.invoke()` 拆分为策略选择器、预算执行器、调用执行器等独立组件
  - `AssetService` 按资产类型拆分为 `RequirementService`、`ApiService`、`PageService`、`TestCaseService`
  - `PostgresManagementConsoleService` 拆分为 `DepartmentService`、`UserService`、`ProjectService` 等
- **当前处理结果**:
  - `DocumentInputService` 已拆出 `DocumentSourceManagementService`、`DocumentCandidateWorkflowService`、`DocumentRequirementPublishService`、`DocumentParseFeedbackCaptureService`。
  - `DocumentInputService` 已继续拆出 `DocumentInputResponseMapper`，承接导入记录、候选项、反馈样本、webhook 事件响应映射和 webhook 审计脱敏视图。
  - `PostgresManagementConsoleService` 已拆出 `PostgresManagementAuditQueryService`、`PostgresManagementConfigService`、`PostgresManagementDepartmentService`、`PostgresManagementUserService`、`PostgresManagementProjectService`、`PostgresManagementApplicationService`、`PostgresManagementEnvironmentService`、`PostgresManagementRoleService`、`PostgresManagementSecretReferenceService`。
  - `InMemoryManagementConsoleService` 已拆出 `InMemoryManagementDepartmentService`、`InMemoryManagementUserService`、`InMemoryManagementProjectService`、`InMemoryManagementApplicationService`、`InMemoryManagementEnvironmentService`、`InMemoryManagementIntegrationService`、`InMemoryManagementConfigService`、`InMemoryManagementSecretReferenceService`、`InMemoryManagementRoleService` 和 `InMemoryManagementAuditQueryService`，分别承接 local profile 部门列表/状态生命周期、用户列表/创建/更新/状态生命周期、项目列表/创建/更新/状态流转/项目成员生命周期、应用列表/创建/更新/启停/负责人生命周期、环境列表/创建/更新/启停/连通性检查/授权用户生命周期、集成列表/创建/更新/启停生命周期、配置列表/创建/更新/启停和敏感配置策略、密钥引用创建/轮换/撤销生命周期、角色权限生命周期/用户角色分配校验，以及审计日志查询/CSV 导出/outbox 查询。
  - `ModelAccessService` 已拆出 `ModelInvocationService`、`ModelCostAnalysisService`、`ModelProviderManagementService` 和 `PromptTemplateManagementService`，分别承接模型调用编排、成本告警/日报聚合、供应商管理与 Prompt 版本/审批管理。
  - `AssetService` 已继续拆出 `AssetTraceLinkService`，承接追踪关系查询/创建、目标资产跨项目校验和追踪关系审计。
  - `AssetService` 已继续拆出 `AssetTestCaseStepService`，承接测试用例步骤查询/替换、步骤审计和历史版本记录。
  - `AssetService` 已继续拆出 `AssetVersionRollbackService`，承接需求/测试用例历史版本回滚、snapshot 还原、回滚归属校验和恢复冲突校验。
  - `AssetService` 已继续拆出 `AssetLifecycleService`，承接需求/API/页面/业务流/测试用例归档、软删、恢复、生命周期拒绝审计和恢复冲突校验。
  - `AssetService` 已继续拆出 `AssetRequirementService`，承接需求列表/读取、导入幂等合并、评审状态流转、版本历史入口、回滚入口和生命周期入口委托。
  - `AssetService` 已继续拆出 `AssetApiService`，承接 API 列表/读取、创建/更新、路径方法唯一性校验、状态流转拒绝审计和生命周期入口委托。
  - `AssetService` 已继续拆出 `AssetPageService`，承接页面列表/读取、创建/更新、componentTree 序列化、状态流转拒绝审计和生命周期入口委托。
  - `AssetService` 已继续拆出 `AssetBusinessFlowService`，承接业务流列表/读取、创建/更新、flowJson 序列化、状态流转拒绝审计和生命周期入口委托。
  - `AssetService` 及 WP3 拆分服务已复用 `AssetProjectAuditService`，统一项目上下文校验、资产单资源审计和批量审计写入。
  - `AssetService` 已继续拆出 `AssetResponseMapper`，承接需求、API、页面、业务流和测试用例响应 DTO 映射，保留生命周期展示归一化和测试步骤响应排序。

### [HIGH] A2: 贫血领域模型（Anemic Domain Model）

- **问题**: 所有 Domain 层对象（`AssetRequirement`, `InvocationRecord`, `DocumentSourceConfig` 等）均为纯数据 Record，不含任何业务方法。业务逻辑全部集中在 Service 层。
- **风险**: 业务规则分散在 Service 方法中，难以复用和测试。领域概念缺乏封装。
- **建议**: 将状态校验（如 `REVIEW_STATUS_TRANSITIONS`）、状态转换、业务规则判断移入 Domain 对象

### [HIGH] A3: 认证逻辑与业务逻辑高度耦合

- **问题**: `AssetController.requirePermission()`、`ModelAccessController.requirePermission()`、`DocumentInputController.requirePermission()` 各有一套独立的权限校验实现（直接读取 `SecurityContextHolder`），存在重复代码。
- **建议**: 抽取统一的 `@RequirePermission` 注解 + AOP 切面实现权限校验
- **处理结果**: 已抽取 `@RequirePermission` + `RequirePermissionAspect`，普通控制器权限由注解统一触发；`AuthorizationService` 提供当前主体读取和服务令牌兼容逻辑；`AssetController` 的项目/资源级授权继续显式调用 `ResourceScope`，但底层主体读取已统一到 `AuthorizationService`。

### [MEDIUM] A4: InMemory 实现与 Postgres 实现行为不一致

- **文件**: 
  - `auth/infrastructure/InMemoryAuthSessionStore.java`
  - `asset/infrastructure/InMemoryAssetRepository.java`
  - `management/infrastructure/InMemoryManagementConsoleService.java`
- **问题**: `@Profile("local")` 下的内存实现与 `@Profile("db")` 下的 Postgres 实现在行为上存在差异（如 InMemoryAuthSessionStore 的 `findByRefreshTokenHash` 使用 O(n) 遍历）。测试时使用 local profile 无法覆盖真实 DB 行为。
- **建议**: 
  - 集成测试应使用 Postgres 实现（Testcontainer 或嵌入式 PG）
  - `local` profile 仅作为开发调试用途，不应作为测试验证的依据

### [MEDIUM] A5: 审计日志写入与业务逻辑在同一事务中

- **问题**: `PostgresAuditLogWriter` 与业务 DAO 使用同一数据源，审计日志写入操作混入业务事务中。业务事务回滚时审计日志也会丢失。
- **建议**: 使用独立数据源或消息队列实现审计日志的可靠异步写入
- **处理结果**: 已为 `PostgresAuditLogWriter.record` 增加 `REQUIRES_NEW` 独立事务边界，并补充 db-profile Testcontainers 合约测试，验证业务事务回滚后审计日志仍然存在。独立数据源、异步 outbox 和失败补偿属于运行治理增强，保留在后续 D1/X 类专项中推进。

### [MEDIUM] A6: 缺少统一的分层异常处理策略

- **问题**: Service 层在不同地方直接抛出 `BusinessException` 和 `AccessDeniedException` 两种异常。Controller 层 `requirePermission()` 抛出的是 Spring 的 `AccessDeniedException`，而 Service 层全部使用自定义 `BusinessException`。GlobalExceptionHandler 同时处理两者，但日志上下文不统一。
- **建议**: 统一使用 `BusinessException`，或定义系统级异常层次结构
- **处理结果**: 已定义平台级 `PlatformAccessDeniedException`，保留与 Spring Security 的 `AccessDeniedException` 兼容性，同时携带 `permission/resourceType/resourceId` 上下文；授权服务和模型调用主体解析改为抛该平台异常。`GlobalExceptionHandler` 统一错误响应构造，并对业务异常、认证失败、权限拒绝和 404 等已处理异常记录 `traceId/code/type`，权限拒绝额外记录权限与资源上下文。

---

## 三、性能问题 (Performance)

### [CRITICAL] P1: Asset 列表查询全量加载后在内存中过滤/排序/分页

- **文件**: `asset/application/AssetService.java`
- **问题**: `listRequirements()` (line 127-136)、`listApis()` (line 389-399)、`listPages()`、`listBusinessFlows()`、`listTestCases()` 全部从 Repository 加载所有数据到内存，然后使用 Stream API 进行过滤、排序、分页。
- **风险**: 当数据量达到数千条时，每次列表查询都需要加载全量数据，内存和 GC 压力骤增，响应时间随数据量线性增长。
- **建议**: 
  - 将过滤、排序、分页下沉到 SQL 层面
  - Repository 接口改为接受查询条件参数，返回分页结果
  - 利用 MyBatis 动态 SQL 实现条件查询

### [HIGH] P2: InMemoryAuthSessionStore 刷新令牌查找为 O(n)

- **文件**: `auth/infrastructure/InMemoryAuthSessionStore.java` (line 37-41)
- **问题**: `findByRefreshTokenHash()` 遍历整个 `ConcurrentHashMap` 查找匹配的 refresh token hash，复杂度 O(n)。
- **风险**: 大量活跃会话时此操作会成为瓶颈。
- **建议**: 增加以 refreshTokenHash 为键的二级索引 `Map<String, UUID>`

### [MEDIUM] P3: Cost Alert 先拉取全量数据再去重

- **文件**: `modelaccess/application/ModelAccessService.java` (line 589-613)
- **问题**: `costAlerts()` 方法在需要生成项目级或服务级告警时，先从数据库加载最多 1000 条记录，然后 stream 提取 distinct projectId/actorService 再逐一查询。
- **建议**: 使用 SQL `SELECT DISTINCT project_id` 直接从数据库获取

### [MEDIUM] P4: CSV 导出使用字符串拼接

- **文件**: `modelaccess/application/ModelAccessService.java` (line 652-673, 1163-1202)
- **问题**: CSV 导出使用 `StringBuilder` 逐行拼接，且所有数据都一次性加载到内存中。未处理特殊字符（如逗号、换行符、引号）的转义。
- **建议**: 
  - 使用 CSV 专用库（如 Apache Commons CSV）
  - 考虑流式写入直接返回 `StreamingResponseBody`

### [LOW] P5: 预算窗口计算重复调用

- **文件**: `modelaccess/application/ModelAccessService.java`
- **问题**: `budgetViolation()` 和 `currentBudgetWindow()` 在单次调用中被多次调用计算同一预算窗口。
- **建议**: 缓存本次调用中 `currentBudgetWindow()` 的结果，避免重复计算

---

## 四、代码质量 (Code Quality)

### [HIGH] Q1: SensitiveContentGuard BLOCK_PATTERNS 和 MASK_PATTERNS 完全重复

- **文件**: `modelaccess/application/SensitiveContentGuard.java` (line 17-34)
- **问题**: `BLOCK_PATTERNS` 和 `MASK_PATTERNS` 是完全相同的 Pattern 列表。两处维护容易导致不同步。
- **建议**: 使用相同的 Pattern 源，或提取常量

### [HIGH] Q2: 大量硬编码中文字符串

- **问题**: 整个代码库中错误消息大量使用中文硬编码字符串，混合在业务逻辑中。不利于国际化（i18n）。
- **建议**: 提取到 messages properties 文件中，或定义常量类

### [MEDIUM] Q3: 静态 ObjectMapper 实例

- **文件**: `asset/application/AssetService.java` (line 112)
- **问题**: `private static final ObjectMapper JSON_MAPPER = new ObjectMapper().findAndRegisterModules();` 创建了未配置的静态 ObjectMapper 实例，与 Spring 容器中的 ObjectMapper 配置（如日期格式、时区、忽略未知属性等）不一致。
- **建议**: 注入 Spring 管理的 ObjectMapper Bean，而非自行创建

### [MEDIUM] Q4: AssetService 中大量未使用的常量

- **文件**: `asset/application/AssetService.java` (line 79-91)
- **问题**: 常量 `IMPACT_SUBJECT_TYPES` 包含 `"FLOW"` 和 `"BUSINESS_FLOW"` 两个重复含义的条目；`PAGE_SOURCES`、`API_SOURCES` 等在代码中通过 `valueIn()` 校验输入时使用，但部分常量（如 `IMPORT_EXPORT_ASSET_TYPES` 含 `"API"` 而在 `IMPORT_EXPORT_FORMATS` 中无 `"OPENAPI"` 对应逻辑）可能已过时。
- **建议**: 清理未使用常量，并确认各常量的实际用途
- **处理结果**: 已拆分规范影响分析 subject type 与兼容别名，保留 `BUSINESS_FLOW`/`TEST_CASE` 入参兼容但内部统一为 `FLOW`/`CASE`；导入导出格式改为 `IMPORT_EXPORT_FORMATS_BY_ASSET_TYPE` 明确声明 `OPENAPI` 仅支持 API，去掉调用点重复判断，并补充非 API OpenAPI 导入拒绝回归测试。

### [MEDIUM] Q5: 密码修改接口新密码在日志中可能泄漏

- **文件**: `auth/application/AuthService.java`
- **问题**: 密码修改流程中 `ChangePasswordRequest` 包含 `oldPassword` 和 `newPassword`，若某处日志记录了整个请求对象，可能包含明文密码。
- **建议**: 在 Request DTO 的 `toString()` 中遮盖密码字段，或确保日志中不记录密码信息

### [LOW] Q6: 大量方法参数使用 String 表示标识符

- **问题**: 代码中 projectId、applicationId、environmentId 等多处使用 `String` 而非强类型包装。容易导致参数传错位置（如 projectId 和 applicationId 互换）。
- **建议**: 为业务标识符定义 Value Object 类型（如 `ProjectId`、`ApplicationId`）

### [LOW] Q7: AuditLogWriter.denied() 方法重载存在意图歧义

- **文件**: `common/audit/AuditLogWriter.java` (line 32-51)
- **问题**: `denied(actor, action, resourceType, resourceId, reason)` 将 `resourceId` 同时作为 `targetName` 传入，而非独立的 targetName。这可能导致审计日志中 targetName 字段取值为 resourceId 而非预期的资源名称。
- **建议**: 明确区分 resourceId 和 targetName 的语义

### [LOW] Q8: 测试使用 @Profile("local") 覆盖真实行为

- **问题**: 大量集成测试依赖 `local` profile 的内存实现进行验证，不涉及真实数据库操作。无法检测 SQL 语法错误、事务边界问题、数据库约束冲突等。
- **建议**: 引入 Testcontainers 执行数据库集成测试

---

## 五、数据库 / SQL 问题 (Database)

### [HIGH] D1: 缺少数据库迁移回滚策略

- **文件**: `db/migration/wp1/` 目录下的 Flyway 迁移脚本
- **问题**: 仅提供 V 版本迁移脚本，未包含 `U` (Undo) 或 `R` (Repeatable) 迁移。生产环境出现迁移失败时无法回滚。
- **建议**: Flyway 官方不推荐 Undo 迁移（需 Teams 版），建议使用可逆的数据库变更策略或做好备份恢复流程
- **处理结果**: 已新增 `scripts/wp1_migration_release_plan.sh`，在发布前生成待执行迁移 manifest、SHA-256 和 release plan；新增 `doc/mvp/final/engineering/WP1-WP4-数据库迁移回滚与前滚策略.md`，明确迁移前备份、失败分流、已切流后的前滚修复和禁止改写已发布 V 脚本；`WP1-WP4-统一发布准出清单.md` 已纳入该脚本和 Runbook 作为预发/生产证据。

### [MEDIUM] D2: AuthMapper XML 中 session 表缺少过期时间索引

- **文件**: `resources/mapper/platform/AuthMapper.xml` (line 135-142)
- **问题**: `cleanupSessions` SQL 使用 `expires_at` 和 `revoked_at` 做范围条件，但表上可能缺少对应索引，导致全表扫描影响线上清理性能。
- **建议**: 为 `iam_session` 表的 `expires_at` 和 `revoked_at` 列分别建立索引

### [MEDIUM] D3: 审计日志表缺少清理策略的索引

- **问题**: `AuditRetentionCleanupService` 按 `created_at` 删除过期审计日志，但审计日志表上若缺少 `created_at` 索引，删除操作将非常缓慢。
- **建议**: 确保 `audit_log` 和 `audit_outbox` 表的 `created_at` 列有索引

---

## 六、测试覆盖 (Testing)

### [HIGH] T1: AssetService 核心逻辑缺少单元测试

- **问题**: `AssetService` 包含状态转换、版本历史、导入合并等复杂业务逻辑，但 `test/` 目录下仅有 `AssetControllerTest`（Controller 层集成测试），缺少对 `AssetService` 业务逻辑的单元测试覆盖。
- **建议**: 为状态转换、导入去重、版本回滚等核心逻辑补充单元测试

### [MEDIUM] T2: SensitiveContentGuard 测试覆盖不全

- **问题**: `SensitiveContentGuard` 包含敏感内容检测的核心逻辑，但仅为 `ModelAccessController` 中的策略测试（`ModelAccessPlatformPolicyTest`）间接覆盖，缺少对 Pattern 匹配、掩码行为的直接测试。
- **建议**: 为 `SensitiveContentGuard` 编写独立的参数化单元测试，覆盖各种敏感数据类型

### [MEDIUM] T3: `Profile("db")` 实现类测试覆盖不足

- **问题**: `PostgresAuthSessionStore`、`PostgresAuthIdentityStore`、`PostgresManagementConsoleService` 等 `@Profile("db")` 实现类缺少对应的集成测试。
- **建议**: 使用 Testcontainers + `@ActiveProfiles("db")` 对数据库实现进行集成测试

---

## 七、可维护性 (Maintainability)

### [MEDIUM] M1: 配置文件过于集中且缺乏分层

- **文件**: `application.yml` (~137 行)
- **问题**: 所有模块的配置（auth、audit、model-access、asset、document-input、secret、management）全部堆集在单一配置文件中。
- **建议**: 按模块拆分到独立配置文件（如 `application-auth.yml`、`application-model-access.yml`），通过 `spring.config.import` 引入
- **处理结果**: 已将平台基础治理配置拆入 `application-platform.yml`，模型接入配置拆入 `application-model-access.yml`，资产配置拆入 `application-asset.yml`，文档输入配置拆入 `application-document-input.yml`；主 `application.yml` 仅保留通用 Spring/server/mybatis/OpenAPI 基线并通过 `spring.config.import` 引入模块配置。新增 `ApplicationConfigurationLayeringTest` 校验模块 import 后默认配置可正常绑定，降低配置拆分导致启动失败的风险。

### [LOW] M2: 缺少 API 版本化管理策略

- **问题**: 所有 API 使用 `/api/v1/` 前缀，但未定义明确的版本化策略（如兼容性规则、版本生命周期）。
- **建议**: 制定 API 版本化策略文档，并在 Controller 层增加版本注释
- **处理结果**: 已新增 `doc/mvp/final/engineering/WP1-WP4-API版本化策略.md`，明确 `v1` path 版本载体、兼容/破坏性变更规则、生命周期、废弃接口要求和验收入口。Controller 层新增 `@ApiVersion` 元数据，OpenAPI 通过 `x-api-version-policy` 和 operation 级 `x-api-version`、`x-api-lifecycle`、`x-api-version-since` 暴露版本策略；契约测试会遍历所有 `/api/v1` operation，阻止遗漏版本生命周期字段。

### [LOW] M3: 缺少 OpenAPI 文档注解覆盖

- **问题**: 除 `ModelAccessController` 外，其他 Controller 缺少 `@Operation` 和 `@ApiResponse` 注解，生成的 Swagger 文档不够完整。
- **建议**: 为所有公开 API 添加 Swagger/OpenAPI 注解
- **处理结果**: 已在 `OpenApiConfiguration` 增加公开 API 文档增强器，保留已有显式 `@Operation/@ApiResponse`，并对缺失文档元数据的 `/api/v1` operation 自动补齐领域 tag、summary 和标准 `400/401/403/500` 错误响应说明；`OpenApiContractTest.generatedContractDocumentsEveryApiOperation` 会遍历所有 `/api/v1` operation，校验 `operationId`、summary、tag 和响应 description，避免后续新增接口再次漏文档。

---

## 八、其他问题 (Misc)

### [MEDIUM] X1: 异步模型调用 Job 为纯内存态

- **文件**: `modelaccess/application/ModelInvocationJobService.java`
- **问题**: Job 状态、结果全部存储在内存 `ConcurrentHashMap` 中，服务重启后丢失。且 `ScheduledThreadPoolExecutor` 无队列持久化机制。
- **建议**: 若需要可靠性，考虑使用数据库持久化 + 消息队列调度
- **处理结果**: 已新增 `ma_invocation_job` Flyway 迁移、`ModelInvocationJobRepository` 及 db/local 实现；`ModelInvocationJobService` 改为以 repository 为状态源，任务提交、RUNNING/SUCCEEDED/FAILED/CANCELLED 状态、`invocationId`、错误摘要和成功响应均落库。服务启动时会重新调度持久化 `QUEUED` 任务，并把重启前遗留的 `RUNNING` 标记为 `WORKER_RESTARTED` 失败，避免任务长期悬挂；DB validation 与 Testcontainers 契约已覆盖表、索引、状态约束和仓储生命周期。

### [MEDIUM] X2: OpenAiCompatibleModelProviderClient 按请求创建 RestClient

- **文件**: `modelaccess/infrastructure/OpenAiCompatibleModelProviderClient.java` (line 53-63)
- **问题**: 每次 `call()` 都通过 `restClientBuilder.baseUrl(provider.baseUrl()).requestFactory(...).build()` 创建新的 `RestClient` 实例，无连接池复用。
- **建议**: 缓存具有相同 baseUrl 的 RestClient 实例，或配置连接池

### [LOW] X3: ProviderResilienceManager 的限流基于本地内存

- **文件**: `modelaccess/application/ProviderResilienceManager.java`
- **问题**: 速率限制和熔断器状态基于本地 `ConcurrentHashMap`，多实例部署时各节点独立计数，无法准确控制全局速率。
- **建议**: 多实例部署时考虑使用 Redis 实现分布式限流和熔断
- **处理结果**: 已抽象 `ProviderResilienceStateStore`，`ProviderResilienceManager` 不再直接持有限流/熔断 Map；默认 `!redis` profile 使用 `InMemoryProviderResilienceStateStore` 保持单机行为不变，`redis` profile 使用 `RedisProviderResilienceStateStore` 将熔断状态、打开熔断 provider 索引和固定窗口限流计数写入 Redis。单测覆盖两个 manager 共享同一 store 时的熔断和限流联动，作为多实例共享状态的回归护栏。

### [LOW] X4: 静态常量 SECRET_ASSIGNMENT_PATTERN 未在代码中使用

- **文件**: `documentinput/application/DocumentParseFeedbackCaptureService.java`
- **问题**: `SECRET_ASSIGNMENT_PATTERN`、`EMAIL_PATTERN`、`URL_PATTERN`、`UUID_PATTERN`、`MOBILE_PATTERN`、`LONG_NUMBER_PATTERN` 等 Pattern 曾被标记为未使用。
- **建议**: 清理死代码
- **处理结果**: 复核确认这些 Pattern 属于模型解析人工纠错样本的脱敏快照逻辑；当前已随 `DocumentParseFeedbackCaptureService` 从 `DocumentInputService` 迁出，并补充直接单测覆盖脱敏与审计写入。

---

## 总结

本次审查共发现 **40+** 个问题，其中：

| 严重级别 | 数量 | 关键领域 |
|---------|------|---------|
| CRITICAL | 5 | 安全认证头注入、审计日志完整性、性能瓶颈 |
| HIGH | 7 | 架构耦合、配置安全、数据库设计、测试覆盖 |
| MEDIUM | 18 | 代码质量、性能优化、安全加固 |
| LOW | 10+ | 代码风格、可维护性改进 |

**最优先处理项：**
1. **S1 - ServiceTokenAuthenticationFilter 信任客户端请求头**：存在越权风险
2. **P1 - Asset 列表全量加载到内存**：严重影响系统可伸缩性
3. **A1 - Service 层违反单一职责**：持续增加维护成本
4. **S3/S4 - 密钥明文配置**：生产安全底线
5. **T1 - 核心业务逻辑缺少单元测试**：版本迭代质量保障
