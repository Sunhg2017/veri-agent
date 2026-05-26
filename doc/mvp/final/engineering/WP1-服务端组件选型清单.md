# WP1 服务端组件选型清单

| 项目 | 内容 |
|---|---|
| 工作包 | WP1 平台基础底座 |
| 输出角色 | 子昂 / 服务端架构师 |
| 面向阶段 | MVP Iteration 0、Iteration 1 工程落地 |
| 选型原则 | 稳定优先、显式可控、便于测试、后续可拆 |
| 日期 | 2026-05-17；2026-05-27 补充 Redisson + Kafka 事件驱动基线 |

## 1. 总体结论

WP1 服务端建议以 **Java Spring Boot 单体模块化服务 `platform-api`** 作为首期主服务，承载组织、用户、RBAC、项目、应用、环境、配置、密钥和审计能力。Python/FastAPI 暂不进入 WP1 主链路，仅作为后续 AI Agent、脚本生成、执行适配服务的技术边界预留。

P0 不建议一开始拆成多个微服务。WP1 的核心风险不在服务数量，而在权限一致性、资源作用域、审计完整性和敏感值安全；这些能力放在一个模块化服务内更容易统一上下文、统一事务和统一验收。

## 2. 组件选型总表

| 分层 | 首选组件 | 备选组件 | MVP 决策 | 选型理由 |
|---|---|---|---|---|
| 服务端主语言 | Java 21 LTS | Java 17 LTS | 采用 Java 21 | 新项目可直接采用当前 LTS，兼顾性能、虚拟线程潜力和生态兼容。 |
| 主服务框架 | Spring Boot 3.5.x | Spring Boot 4.x | P0 采用 Spring Boot 3.5.x | 生态更稳，Spring Security、MyBatis、springdoc、测试工具链成熟；Boot 4 可在 P1 做升级评估。 |
| Web 框架 | Spring MVC | Spring WebFlux | 采用 Spring MVC | WP1 以管理后台 API 为主，阻塞式模型足够清晰，排障和团队普及成本低。 |
| 构建工具 | Maven 3.9+ | Gradle | 采用 Maven | 企业 Java 团队普及度高，CI、依赖锁定和多模块管理简单。 |
| 数据库 | PostgreSQL 15+ | MySQL 8、openGauss | 采用 PostgreSQL 15+ | 已完成 WP1 DDL 和验证；JSONB、部分索引、事务和约束能力适合平台底座。 |
| 数据迁移 | Flyway Community | Liquibase | 采用 Flyway | 与当前 `db/migration/V*.sql` 结构天然匹配，规则简单，CI 容易落地。 |
| 数据访问 | MyBatis 3 + Mapper XML | JPA、jOOQ、MyBatis-Plus | 采用 MyBatis 3 | 权限范围过滤、资源作用域和审计查询需要 SQL 可控；SQL 统一维护在 MyBatis XML，不在 Java 代码拼接或维护。 |
| 分页 | MyBatis 分页插件或手写 limit/offset | Spring Data Pageable | P0 优先手写白名单分页 | 排序字段必须白名单，减少 SQL 注入和错误排序风险。 |
| 连接池 | HikariCP | Druid | 采用 HikariCP | Spring Boot 默认集成，轻量稳定，指标接入简单。 |
| 缓存 | Redis 7 + Caffeine | 仅 Redis | P0 采用 Redis，Caffeine 可选 | Redis 管会话、权限缓存和撤销；Caffeine 可做短生命周期本地缓存。 |
| Redis 客户端 | Redisson | Spring Data Redis + Lettuce | P0 采用 Redisson | Redis 不只承担缓存，还承担 provider 熔断/限流/分布式并发信号量等跨实例状态，统一使用 Redisson 客户端。 |
| 认证鉴权 | Spring Security 6 | 自研过滤器 | 采用 Spring Security | 统一认证、鉴权、异常处理、方法级权限和测试支持。 |
| Token | JWT access token + refresh token | 纯 session id | 采用 JWT + refresh token | 适合 Web 后台、同服务内模块调用和后续外部集成；服务端仍用 Redis/DB 支持撤销和 `auth_version` 失效。 |
| 密码哈希 | BCrypt 或 Argon2id | PBKDF2 | P0 采用 BCrypt，P1 评估 Argon2id | BCrypt 落地简单、兼容好；Argon2id 可作为安全增强。 |
| 权限模型 | 自研 RBAC + 作用域校验 | Casbin、OPA | P0 自研 RBAC | 已有明确角色、权限点、scope 模型；Casbin/OPA 会增加调试和团队学习成本。 |
| 参数校验 | Jakarta Validation | 手写校验 | 采用 Jakarta Validation + 业务校验器 | 字段级规则标准化，资源归属和状态流由业务校验器处理。 |
| API 契约 | OpenAPI 3.1 + springdoc-openapi | Swagger 手写文档 | 采用 OpenAPI 3.1 | 支持前后端评审、Mock、契约测试和接口自动化生成。 |
| 统一响应 | 自研 `ApiResponse<T>` | Spring 默认错误结构 | 采用自研统一响应 | 匹配当前 API 契约中的 `code/message/traceId/data`。 |
| 错误码 | 自研错误码枚举 | HTTP status only | 采用自研错误码枚举 | 便于前端提示、自动化断言和审计归因。 |
| 审计写入 | `audit_log` 同步写入 + Kafka trace-aware 异步事件 | 仅 DB outbox、消息队列直接写 | P0 保留同步兜底，`db,kafka` 启用异步审计事件 | 不启用 Kafka 时保持事务内写库；启用 Kafka 时业务链路发布 `audit.log-recorded` 事件，消费者按 traceId 异步落 `audit_log`。 |
| 异步任务 | DB 任务账本 + Kafka/local event bus | Spring Scheduler、Quartz、XXL-JOB | P0 采用事件驱动异步 | 业务提交只持久化任务并发布事件，消费者按 traceId 恢复上下文执行，重复消费由状态机幂等。 |
| 消息队列 | Kafka | RocketMQ、RabbitMQ | P0 引入 Kafka 承载异步领域事件 | WP2 模型异步调用先落地，后续审计 outbox、通知和集成事件可按吞吐与可靠性要求迁移。 |
| 密钥管理 | 本地加密 SecretProvider | Vault、KMS | P0 采用 LocalEncryptedSecretProvider | 用户已确认不需要完全离线，但平台需自建模型和密钥封装；本地 provider 可先跑通，后续适配企业 KMS。 |
| 加密库 | JCA/JCE AES-GCM | Tink | P0 采用 JCA/JCE | Java 标准库足够；减少额外依赖，密钥轮换通过 provider 层控制。 |
| 日志 | Logback + JSON layout | Log4j2 | 采用 Logback | Spring Boot 默认，接入成本低；生产建议 JSON 日志。 |
| Trace | Micrometer Tracing + OpenTelemetry | Sleuth 老版本 | 采用 Micrometer Tracing + OTel | 与 Spring Boot 3.x 体系一致，便于后续接 Jaeger/Tempo。 |
| 指标 | Spring Boot Actuator + Micrometer | 自研指标 | 采用 Actuator + Micrometer | 健康检查、Prometheus 指标和运行状态标准化。 |
| 文档存储 | 对象存储适配接口 | 本地文件 | P0 只预留接口 | WP1 暂不处理大文档资产；后续 WP2/WP3 再接 MinIO/S3/OSS。 |
| 配置管理 | Spring Profiles + 环境变量 | Nacos、Apollo | P0 采用环境变量和配置文件 | 首期减少中间件；企业部署需要时再接配置中心。 |
| 测试框架 | JUnit 5 + AssertJ + Mockito | Spock | 采用 JUnit 5 | Spring Boot 默认生态，团队通用。 |
| 集成测试 | Testcontainers | H2 | 采用 Testcontainers | PostgreSQL 特性较多，H2 不能真实覆盖部分索引、JSONB、约束行为。 |
| API 测试 | Spring MockMvc / REST Assured | Postman 手工集合 | P0 采用 MockMvc + REST Assured | 能进入 CI，覆盖鉴权、错误码、响应结构和审计副作用。 |
| 数据库校验 | 现有 SQL validation + Docker Postgres | 手工 DBA 检查 | 采用现有 validation 脚本 | 已完成临时库验证，可直接进入 CI 基线。 |
| 代码质量 | Maven Checkstyle/Spotless + SpotBugs | SonarQube only | P0 本地插件，P1 接 SonarQube | PR 阶段先有快速阻断；后续统一接企业 Sonar。 |
| 敏感信息扫描 | Gitleaks | TruffleHog | P0 采用 Gitleaks | 配置简单，可在 CI 阻断密钥误提交。 |
| 容器化 | Dockerfile + Docker Compose | Buildpacks | P0 采用 Dockerfile | 可控、易排障；后续再引入镜像构建平台。 |
| CI | GitHub Actions 或 GitLab CI | Jenkins | 按仓库平台选择 | 当前先提供通用脚本和 GitHub Actions 样例；若企业使用 GitLab/Jenkins 可平移。 |

## 3. P0 必选组件清单

### 3.1 `platform-api` 基础栈

| 组件 | 建议版本口径 | 必选原因 |
|---|---|---|
| Java | 21 LTS | 新项目基线，性能和生态兼顾。 |
| Spring Boot | 3.5.x | 企业稳定性优先，避开 P0 期间框架大版本磨合。 |
| Spring MVC | 跟随 Boot BOM | 管理后台 API 的主 Web 栈。 |
| Spring Security | 跟随 Boot BOM | 登录、Token、权限校验、方法级安全。 |
| Maven | 3.9+ | 构建、测试、CI 和依赖管理。 |
| PostgreSQL | 15+ | WP1 数据模型已按 PostgreSQL 验证。 |
| Flyway | 跟随 Boot BOM或显式锁定 | 管理 `db/migration` 脚本版本。 |
| MyBatis | 3.x | 显式 SQL、可控资源过滤、复杂权限查询。 |
| Redis | 7.x + Redisson | 会话撤销、权限缓存、登录失败计数，以及 provider 熔断/限流/分布式并发控制。 |
| Kafka | 3.x | 异步领域事件、跨实例任务派发和后续 outbox 消费演进。 |
| Actuator + Micrometer | 跟随 Boot BOM | 健康检查、指标和运行态观测。 |
| OpenAPI/springdoc | 与 Boot 3.5 兼容版本 | 契约评审、Mock、接口自动化输入。 |

### 3.2 安全与审计组件

| 组件 | P0 落地方式 | 说明 |
|---|---|---|
| JWT | access token 短期有效，refresh token 服务端可撤销 | Token 中携带 `user_id`、`auth_version`、`session_id`；不携带租户字段。 |
| 密码哈希 | BCrypt | 保存哈希，不保存明文；后续支持算法版本升级字段。 |
| SecretProvider | `LocalEncryptedSecretProvider` | 使用环境变量注入主密钥，密文落 `secret_local_store`。 |
| 加密算法 | AES-256-GCM | 存储 `cipher_text`、`iv`、`auth_tag`、`master_key_version`。 |
| 审计 | `audit_log` + `audit.log-recorded` 事件 | 非 Kafka profile 同步记录审计；`db,kafka` profile 发布 trace-aware 事件并异步落库。 |
| 敏感扫描 | Gitleaks | 阻断密钥、Token、密码误提交。 |

### 3.3 测试与准出组件

| 组件 | P0 落地方式 | 说明 |
|---|---|---|
| 单元测试 | JUnit 5、AssertJ、Mockito | 覆盖领域规则、状态流、权限策略。 |
| 集成测试 | Testcontainers PostgreSQL、Redis | 避免 H2 与 PostgreSQL 行为不一致。 |
| API 测试 | MockMvc、REST Assured | 覆盖统一响应、错误码、鉴权、审计副作用。 |
| DB 验证 | `db/validation/run_wp1_db_validation.sh` | 空库迁移、重复迁移、schema/seed/security validation。 |
| CI 门禁 | Maven test + DB validation + Gitleaks | 任一失败阻断合并。 |

## 4. P1/P2 预留组件

| 能力 | P0 处理 | P1/P2 可选组件 | 引入触发条件 |
|---|---|---|---|
| 服务拆分 | `platform-api` 模块化单体 | Spring Cloud Gateway、Nacos、OpenFeign | 多团队独立发布、服务容量或边界压力明显。 |
| 策略引擎 | 自研 RBAC | Casbin、OPA | 权限规则变成客户可配置策略语言。 |
| 消息队列扩展 | WP2 模型任务和审计写入先用 Kafka | Kafka 多 topic、RocketMQ、RabbitMQ | 通知、集成事件或更多异步编排吞吐超过同步路径能力，或需要跨服务解耦。 |
| 分布式调度 | Spring Scheduler | XXL-JOB、Quartz | WP9 需要复杂任务编排、失败重试、分片调度。 |
| 配置中心 | Spring Profiles | Nacos、Apollo、Spring Cloud Config | 多环境、多集群配置变更需要集中治理。 |
| 密钥服务 | LocalEncryptedSecretProvider | Vault、云 KMS、企业 KMS | 客户要求集中密钥、轮换、审计或 HSM 对接。 |
| 文件/对象存储 | 接口预留 | MinIO、S3、阿里 OSS、腾讯 COS | 需求文档、原型、执行附件和报告大量落盘。 |
| 搜索 | PostgreSQL 查询 | Elasticsearch、OpenSearch | 审计、需求、用例、报告需要复杂全文检索。 |
| Python 智能服务 | 不进入 WP1 主链路 | FastAPI、Celery、LangGraph 等 | 进入需求解析、用例生成、执行分析等 WP。 |

## 5. 不建议 P0 引入的组件

| 组件/方案 | 不建议原因 |
|---|---|
| 一上来微服务化拆分 IAM、RBAC、Audit、Project | 增加事务、部署、观测和联调成本；WP1 当前最需要统一上下文和一致性。 |
| WebFlux 全响应式栈 | 管理后台 API 并发模型简单，收益小，团队排障成本高。 |
| JPA 作为主数据访问层 | 权限范围过滤、部分索引、复杂审计查询需要 SQL 显式可控。 |
| H2 作为集成测试数据库 | 无法真实覆盖 PostgreSQL 的部分索引、JSONB、约束和权限行为。 |
| 所有写路径一刀切直接发 MQ | 强一致业务仍以事务内写库为准；Kafka 先承载模型调用、审计写入等天然异步且可按 traceId 追踪的事件。 |
| OPA/Casbin 作为 P0 权限核心 | 当前权限矩阵清晰，自研 RBAC 更便于产品、测试和后端共同验收。 |
| Vault/KMS 作为 P0 强依赖 | 用户已确认不需要完全离线和强企业密钥依赖；本地加密 provider 更适合 MVP 启动。 |

## 6. 服务端模块划分建议

`platform-api` 内部按领域模块拆包，而不是按技术层横向堆叠：

```text
platform-api
  common        # 统一响应、错误码、异常、分页、Trace、基础工具
  security      # Spring Security、Token、Session、认证上下文
  auth          # 用户、登录、会话、密码策略
  management    # 部门、用户、项目、应用、环境、设置、审计管理视图
  authorization # 权限点、角色、角色绑定、权限计算
  integration   # WP1 上下文校验与审计写入应用服务
  modelaccess   # WP2 模型接入领域
  asset         # WP3 测试资产领域
```

模块间依赖原则：

1. `common` 可被所有模块依赖。
2. `security` 可依赖 `iam`、`rbac` 的端口接口，不直接穿透所有表。
3. 业务模块写操作必须调用 `audit` 端口记录审计。
4. `secret` 不向上暴露明文，只暴露引用、掩码和校验能力。
5. WP1/WP2/WP3 在同一 `platform-api` 内通过 Spring 应用服务复用上下文和审计能力；外部集成才使用内部 HTTP API，不直接读库。

## 7. 版本与依赖治理规则

1. 使用 Spring Boot BOM 锁定核心依赖版本，不在业务模块随意覆盖 Spring、Jackson、Netty、Tomcat 等基础库版本。
2. 所有新增依赖必须说明用途、许可证、替代方案和是否进入运行时镜像。
3. 安全组件、加密组件、认证组件不得使用长期无人维护的小众库。
4. 数据库访问必须经过 repository/mapper 层，禁止 controller 直接写 SQL。
5. 资源作用域过滤不得只依赖 MyBatis 插件魔法注入，关键查询必须在 SQL 或 repository 评审中可见。
6. 依赖升级采用小版本滚动、大版本专项评估；Spring Boot 4.x 建议在 WP1 P0 稳定后单独评估。

## 8. CI 基线建议

P0 CI 至少包含以下 job：

| Job | 内容 | 阻断规则 |
|---|---|---|
| Backend Build | `mvn -pl platform-api test` | 编译或测试失败阻断。 |
| DB Migration Validation | `bash db/validation/run_wp1_db_validation.sh` | 出现 `FAIL` 或脚本非零退出阻断。 |
| Secret Scan | `gitleaks detect` | 命中高置信密钥阻断。 |
| OpenAPI Check | 校验 OpenAPI 格式和破坏性变更 | 契约非法或破坏已冻结字段阻断。 |
| Docker Build | 构建 `platform-api` 镜像 | 镜像构建失败阻断。 |

## 9. 待确认项

| 待确认项 | 默认建议 | 影响 |
|---|---|---|
| CI 平台 | 先提供 GitHub Actions 样例，同时保证脚本可迁移到 GitLab/Jenkins | 影响 workflow 文件格式，不影响验证脚本。 |
| 是否必须接企业 SSO | P0 不接，保留 Spring Security 扩展点 | 影响登录 API 和用户来源字段。 |
| Redis 是否为 P0 必备中间件 | 建议 P0 必备 | 影响会话撤销、权限缓存和登录失败锁定实现。 |
| 对象存储是否进入 WP1 | 默认不进入 | 影响文档、附件和导出能力边界。 |
| Spring Boot 版本最终冻结 | 建议 3.5.x 最新补丁 | 影响 pom、springdoc、测试依赖版本。 |

## 10. 架构师最终建议

WP1 的服务端选型应围绕“可控的一致性”展开：用 Spring Boot 单体模块化服务承载控制面核心能力，用 PostgreSQL 和 Redis 解决状态与缓存，用 Flyway 和 validation SQL 把数据库准出自动化，用 Spring Security 统一认证鉴权，并在 `db,kafka` profile 下用 trace-aware 审计事件把写操作从主调用链剥离。

首期不把复杂度前置到微服务、策略引擎和企业级 KMS。等 P0 跑通权限、项目、环境、密钥和审计闭环后，再根据真实用户接入情况逐步替换或扩展。
