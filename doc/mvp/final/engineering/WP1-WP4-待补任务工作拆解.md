# WP1-WP4 待补任务工作拆解

| 项目 | 内容 |
|---|---|
| 覆盖范围 | WP1 平台基础底座、WP2 模型接入层、WP3 资产管理、WP4 需求与文档输入 |
| 当前基线 | `doc/mvp/final/engineering/当前实现基线.md`、各 WP 当前交付说明、README、代码与自动化测试 |
| 日期 | 2026-05-20 |
| 文档目标 | 将当前 P0 之外或仍缺交付闭环的事项拆成后续可补充、可排期、可验收的任务清单 |

## 1. 总体判断

WP1、WP2、WP4 的当前 P0 口径已经基本收敛，后续以生产硬化、产品化增强和更完整质量门禁为主。WP3 已有后端资产基础 API、迁移和单测，但缺少与 WP1/WP2/WP4 同等级别的 final 交付说明、前端工作台、专属 quality gate 和更完整资产协作闭环，是当前优先级最高的待补区域。

本文只记录当前仍需补充的任务，不重新打开已经被现行基线明确废弃的早期范围，例如多租户/平台实例分层、独立 WP 服务、WP2/WP3 到 WP1 的 HTTP 回调、snake_case API 等。

## 2. 优先级与状态

| 标记 | 含义 | 准出要求 |
|---|---|---|
| P0-B | 补齐交付闭环或生产准出缺口，建议优先进入最近迭代 | 有接口/文档/测试/脚本/验收记录，不能只停留在设计 |
| P1 | 产品化增强或生产硬化，建议在 MVP 稳定后连续补齐 | 有明确 owner、验收标准和回归入口 |
| P2 | 后续专项或企业增强，不阻塞当前 MVP | 保持数据模型、配置和 UI 不阻碍后续接入 |

默认状态：

- `TODO`：尚未开始或当前没有完整实现证据。
- `IN_PROGRESS`：已有基础实现，但还缺文档、测试、前端或生产闭环。
- `DONE-CURRENT`：当前 P0 已完成，仅保留后续增强。

## 3. 跨 WP 统一任务

| 编号 | 任务 | 优先级 | 状态 | 主要产出 | 验收标准 |
|---|---|---|---|---|---|
| ALL-1 | 建立 WP1-WP4 统一发布准出索引 | P0-B | DONE-CURRENT | 已新增 `doc/mvp/final/engineering/WP1-WP4-统一发布准出清单.md`，并在 `README.md` 汇总 test、smoke、db validation、OpenAPI 契约入口 | 新人可按一份清单完成本地、CI、预发和生产准出；命令失败能定位到 WP |
| ALL-2 | 建立跨 WP 变更影响矩阵 | P1 | DONE-CURRENT | 已新增 `doc/mvp/final/engineering/WP1-WP4-变更影响矩阵.md`，覆盖 WP1 context/audit/secret、WP2 invocation、WP3 asset、WP4 import/publish 依赖 | 任一共享契约变更能列出受影响测试和 smoke |
| ALL-3 | 统一 metrics 和 dashboard 命名 | P1 | DONE-CURRENT | 已新增 `doc/mvp/final/engineering/WP1-WP4-指标命名与看板规范.md`，包含指标命名、Grafana/告警建议和 traceId 串联说明 | WP1-WP4 关键链路能按 metrics + 审计/调用日志中的 projectId/actorService/status 观测 |
| ALL-4 | 建立 release notes 模板 | P2 | DONE-CURRENT | 已新增 `doc/mvp/final/engineering/WP1-WP4-Release-Notes-模板.md` | 每次补充任务完成后能说明变更、迁移、配置、风险和回滚 |
| ALL-5 | 后端 Service 职责拆分治理 | P1 | DONE-CURRENT | 已将 WP2 `ModelAccessService` 的同步调用编排、成本告警、成本日报聚合、模型供应商管理和 Prompt 版本/审批管理拆分到专门服务；已将 WP3 `AssetService` 的版本历史、影响分析、导入导出、原型同步、追踪关系、测试用例步骤、项目审计上下文和响应 DTO 映射职责拆分到专门服务；已将 WP4 `DocumentInputService` 的文档源管理、候选项生命周期、发布编排、模型纠错反馈和响应 DTO 映射拆分到专门服务；已将管理台 `PostgresManagementConsoleService` 的审计查询、配置管理、部门管理、用户管理、项目管理、应用管理、环境管理、角色权限管理和密钥引用生命周期拆分到专门服务 | 原 controller/API 契约保持兼容，相关 WP2、WP3、管理台和 WP4 controller 测试通过；后续新增高耦合热点继续按 A1 专项跟踪 |

## 4. WP1 平台基础底座待补任务

当前 WP1 P0 已可作为后续 WP 底座，待补任务以生产化和治理能力为主。

### WP1-A 生产数据库角色与发布校验

| 编号 | 任务 | 优先级 | 状态 | 工作内容 | 验收标准 |
|---|---|---|---|---|---|
| WP1-A1 | 接入真实预发/生产应用数据库角色 | P0-B | IN_PROGRESS | `scripts/wp1_release_role_validation.sh` 已纳入 app/readonly/migration 三类角色参数与本地临时库自校验；下一步需在预发/生产注入真实 `WP1_RELEASE_*` 角色名并归档输出 | 在预发库执行 release role validation，app/readonly 无 DDL 与危险写权限，应用账号无审计 UPDATE/DELETE/TRUNCATE 权限，migration role 承担 schema DDL |
| WP1-A2 | 固化发布前 DB 权限 runbook | P0-B | DONE-CURRENT | 已新增 `doc/mvp/final/engineering/WP1-发布前DB权限Runbook.md`，补充环境变量、连接串、执行时机、失败处理和 DBA 复核说明 | 发布负责人可按文档复现检查，失败项有修复建议 |
| WP1-A3 | CI/发布流水线挂载 DB 权限检查 | P1 | DONE-CURRENT | `.github/workflows/wp1-database-validation.yml` 已挂载临时库 migration/validation 并归档日志；`WP1-发布前DB权限Runbook.md` 补充预发/生产真实角色 validation 挂载口径 | 临时库 CI 每次跑，预发/生产按发布窗口执行真实角色 validation，并产出日志归档 |

### WP1-B 角色定义与权限治理

| 编号 | 任务 | 优先级 | 状态 | 工作内容 | 验收标准 |
|---|---|---|---|---|---|
| WP1-B1 | 自定义角色模型冻结 | P1 | DONE-CURRENT | 已在 `rbac_role`/`rbac_role_permission` 既有模型上固化自定义角色约束：`code` 由调用方指定且唯一，`scopeType` 限定为平台/部门/项目/应用/环境，`is_system`/`is_builtin` 角色只读，权限集合来自启用权限点，版本号随定义变更递增，操作者不能授予自身没有的权限 | 自定义角色不能授予超过操作者自身范围的权限 |
| WP1-B2 | 角色定义管理 API | P1 | DONE-CURRENT | 已新增权限点目录、角色详情、创建、编辑、启停和权限集合替换接口；内置角色不可编辑/停用；写操作接入 `role:create`/`role:edit`、审计和 OpenAPI 契约测试 | API 接入 RBAC、审计、OpenAPI 契约和权限矩阵测试 |
| WP1-B3 | 角色管理前端页面 | P1 | DONE-CURRENT | portal-web 已新增“角色治理”页面，按 `role:read` 控制菜单可见，提供角色列表、自定义角色创建/编辑/启停、权限点分组勾选和角色绑定影响提示；`role:create`/`role:edit` 控制操作入口，内置/系统角色只读 | 非授权用户不可见或不可操作，权限点按 resourceType 分组展示，保存失败显示错误和 traceId |
| WP1-B4 | 权限变更失效自动化 | P1 | DONE-CURRENT | 用户角色分配/解绑已有 `auth_version` 递增；本次补齐自定义角色权限集合、角色基础字段和角色启停变更后对所有当前绑定用户递增 `auth_version` | 旧 token/旧权限摘要不能继续执行新增禁止操作 |

### WP1-C 审计导出、保留和 outbox 运维

| 编号 | 任务 | 优先级 | 状态 | 工作内容 | 验收标准 |
|---|---|---|---|---|---|
| WP1-C1 | 审计导出任务 API | P1 | DONE-CURRENT | 已新增 `GET /api/v1/management/audit-logs/export` 同步 CSV 导出，复用 `actor/action/resourceType/result/search/startTime/endTime` 筛选，要求 `audit:read` + `audit:export`，导出自身写审计 | 导出文件不含敏感明文，导出条件和结果可追踪；异步任务/对象存储引用仍作为后续产品化增强 |
| WP1-C2 | 审计导出前端入口 | P1 | DONE-CURRENT | portal-web 审计页已按 `audit:export` 展示 CSV 导出按钮，提供下载、导出中、失败和 traceId 状态 | 无导出权限不可操作，导出失败有可读错误 |
| WP1-C3 | 审计保留策略 | P1 | DONE-CURRENT | 已新增 `WP1-审计保留策略Runbook.md`、`AuditRetentionCleanupService`、`audit_log_archive` 和受控函数 `wp1_cleanup_audit_log_before`；默认在线保留 365 天、清理默认关闭、30 天硬下限、批量上限和 `veri.agent.audit.retention.cleanup` 指标 | 清理前先归档，app role 仍不能直接 `UPDATE/DELETE/TRUNCATE audit_log`，DB validation 覆盖配置、授权和只清理 cutoff 之前记录 |
| WP1-C4 | Audit outbox 运维视图 | P1 | DONE-CURRENT | 已新增 `GET /api/v1/management/audit-outbox` 只读分页查询，复用 `audit:read`，支持 `status/traceId/search` 过滤；portal-web 审计页已接入 outbox 运维侧栏，展示待补偿、处理中、失败、死信事件摘要；新增 `idx_audit_outbox_trace_id` 表达式索引和 DB validation | 审计写失败可观测；接口和页面不暴露手动重试、重放、删除或原始 payload；traceId 查询具备索引兜底 |

### WP1-D 会话、状态流与运行上下文增强

| 编号 | 任务 | 优先级 | 状态 | 工作内容 | 验收标准 |
|---|---|---|---|---|---|
| WP1-D1 | Redis 会话清理或 DB 会话清理任务 | P1 | DONE-CURRENT | 已补 `AuthSessionCleanupService`，local profile 清理内存会话，db profile 通过 MyBatis 删除过期/已撤销 `iam_session`；支持 `WP1_SESSION_CLEANUP_ENABLED`、`WP1_SESSION_CLEANUP_RETENTION_SECONDS`、`WP1_SESSION_CLEANUP_INTERVAL_MS`，并记录 `veri.agent.auth.session.cleanup` 指标 | 长期运行不会积累过期会话；清理行为不影响有效会话 |
| WP1-D2 | 复杂状态流拒绝测试 | P1 | DONE-CURRENT | 已扩展项目重复/逆向/非法状态流、停用后编辑拒绝，以及应用/环境非法状态与停用后编辑拒绝测试；项目非法状态流写入 `DENIED` 拒绝审计，db profile 通过独立事务保留拒绝事件 | 非法状态变更返回稳定错误码并写拒绝审计 |
| WP1-D3 | 环境连通性检查 | P1 | DONE-CURRENT | 已新增 `GET/POST /api/v1/management/environments/{key}/connectivity-check`，`POST` 复用 `environment:edit` 并把最近结果写入 `base_environment.health_check_json`；支持 `WP1_ENV_CONNECTIVITY_CHECK_ENABLED` 和 `WP1_ENV_CONNECTIVITY_TIMEOUT_MS`，portal-web 环境页已增加连通性侧栏 | 停用环境不可执行；探活失败只返回脱敏状态、HTTP 状态和 traceId，不暴露内部异常 |
| WP1-D4 | Secret 引用写入和轮换管理 | P1 | DONE-CURRENT | 已新增 `GET/POST /api/v1/management/secrets`、`POST /api/v1/management/secrets/rotate`、`POST /api/v1/management/secrets/disable`，db profile 写入 `LOCAL_ENCRYPTED` 密文材料并更新 `secret_reference/secret_local_store`，portal-web 系统设置页已增加 Secret 引用侧栏 | 明文只进入创建/轮换请求，不入响应、列表、前端持久状态或审计；轮换覆盖本地密文材料并递增/更新版本，撤销后引用与本地密文均为不可用状态 |

### WP1-E 企业身份与审批预留

| 编号 | 任务 | 优先级 | 状态 | 工作内容 | 验收标准 |
|---|---|---|---|---|---|
| WP1-E1 | SSO/LDAP/企业组织同步方案 | P2 | DONE-CURRENT | 已新增 `WP1-企业身份与审批预留方案.md`，冻结身份源模型、外部账号映射、外部用户 ID、SSO 登录兼容策略、部门同步映射和冲突处理口径 | 不影响当前本地账号登录；后续可按方案接入 OIDC/SAML/LDAP/企微/钉钉/飞书组织同步 |
| WP1-E2 | 项目/权限申请审批预留 | P2 | DONE-CURRENT | 已新增 `WP1-企业身份与审批预留方案.md`，定义审批对象、审批任务、审批状态机、API/权限/审计预留和审批通过后写入 `rbac_role_binding` 的关系 | 当前授权链路不被阻塞，后续审批可接入同一审计体系，且必须复用现有角色绑定、审计和 `auth_version` 失效机制 |

## 5. WP2 模型接入层待补任务

当前 WP2 P0 后端能力和门禁已较完整，后续重点是前端治理、生产 provider 运维和模型质量体系。

### WP2-A 模型接入管理前端

| 编号 | 任务 | 优先级 | 状态 | 工作内容 | 验收标准 |
|---|---|---|---|---|---|
| WP2-A1 | 模型供应商管理页面 | P1 | DONE-CURRENT | 已新增 `portal-web` 模型接入管理台供应商页，支持列表、创建、编辑、启停、就绪检查、熔断恢复和成本配置 | 前端使用现有 `/api/v1/model-access/providers` 契约，浏览器只使用登录用户 Bearer；密钥只填 `apiKeyRef=env:VARIABLE_NAME` 引用 |
| WP2-A2 | Prompt 版本管理页面 | P1 | DONE-CURRENT | 已新增 Prompt key 查询、版本列表、新建版本、激活和双版本 diff 展示；后端补用户态激活审计 | 每个 promptKey 只有一个 ACTIVE 版本；激活写审计 |
| WP2-A3 | 调用日志与成本页面 | P1 | DONE-CURRENT | 已新增日志筛选、summary、CSV 导出、成本日报和告警展示；后端补 `modelAccess:export` 导出权限 | 日志只展示 preview/digest，不展示 prompt 明文和敏感内容；导出权限受控 |

### WP2-B Provider 生产硬化

| 编号 | 任务 | 优先级 | 状态 | 工作内容 | 验收标准 |
|---|---|---|---|---|---|
| WP2-B1 | Provider 级限流和并发控制 | P1 | DONE-CURRENT | 已补 provider 级请求窗口限流和并发 semaphore，支持 `WP2_PROVIDER_RATE_LIMIT_MAX_REQUESTS`、`WP2_PROVIDER_RATE_LIMIT_WINDOW_SECONDS`、`WP2_PROVIDER_MAX_CONCURRENT_REQUESTS`；超限/并发满返回 `BUDGET_EXCEEDED` 并记录 `BLOCKED` 调用日志 | 超限返回稳定错误码并记录 BLOCKED 调用日志 |
| WP2-B2 | 熔断状态观测与手动恢复 | P1 | DONE-CURRENT | 已新增 `GET /api/v1/model-access/providers/{id}/resilience` 和 `POST /api/v1/model-access/providers/{id}/circuit/reset`，健康接口暴露打开熔断 provider 数 | 运维可判断当前 provider 是否被短时熔断 |
| WP2-B3 | 外部 provider runbook | P1 | DONE-CURRENT | 已新增 `doc/mvp/final/engineering/WP2-Provider接入与SecretRef轮换Runbook.md`，覆盖 OpenAI-compatible、私有模型、代理网关的配置、探活和故障处理 | 新 provider 接入不需要阅读源码 |
| WP2-B4 | SecretRef 轮换流程 | P1 | DONE-CURRENT | `WP2-Provider接入与SecretRef轮换Runbook.md` 已说明当前 `apiKeyRef=env:VARIABLE_NAME` 口径和后续 SecretProvider 对齐的双引用轮换流程 | 轮换期间不中断可用 provider，旧 secretRef/apiKeyRef 可控失效 |

### WP2-C 策略、预算和合规模型

| 编号 | 任务 | 优先级 | 状态 | 工作内容 | 验收标准 |
|---|---|---|---|---|---|
| WP2-C1 | 高级路由策略 | P1 | DONE-CURRENT | 已新增 provider `routingGroup/capabilities`、调用 `capability`、配置化 `routing-rules` 和调用日志 `routingRuleName/routingGroup/modelCapability`；规则支持按项目、敏感级别、调用服务、模型能力和供应商组匹配，并可用 `LOWEST_COST` 在组内按预估成本选择 provider | 高敏资源仍不能路由公开模型；路由结果写入调用日志、CSV 和 WP1 审计摘要 |
| WP2-C2 | 预算策略产品化 | P1 | DONE-CURRENT | 已在平台/项目预算基础上补齐调用服务日预算 `WP2_DAILY_CALLER_SERVICE_COST_LIMIT`、成本告警 `actorService` 查询和 `WP2_BUDGET_OVERRUN_ACTION=BLOCK/FALLBACK` 超预算动作 | 超预算前通过 `WP2_COST_ALERT_WARNING_RATIO` 返回 WARNING；超预算后默认阻断，配置 `FALLBACK` 时跳过超预算 provider 并尝试后续低成本候选 |
| WP2-C3 | 敏感内容检测扩展 | P1 | DONE-CURRENT | 已扩展手机号、邮箱、银行卡疑似长号、企业内部 token/secret/private key 模式，并保留现有 key/token/password/Bearer/身份证号阻断；新增单测覆盖 | 命中后阻断并在日志/响应中保留稳定错误；自定义正则和阻断/脱敏策略配置仍作为后续增强 |
| WP2-C4 | Prompt 评审与审批 | P2 | DONE-CURRENT | 已新增高风险 Prompt `highRisk` 标记、`approvalStatus` 审批状态、审批人/时间/说明字段，以及 approve/reject API 和 portal-web 操作入口 | 高风险 Prompt 未审批不能激活；审批通过和驳回保留审批人、审批时间与版本说明，回滚到旧 ACTIVE 版本仍沿用现有激活入口 |

### WP2-D 模型质量和异步能力

| 编号 | 任务 | 优先级 | 状态 | 工作内容 | 验收标准 |
|---|---|---|---|---|---|
| WP2-D1 | 通用模型评测集框架 | P1 | DONE-CURRENT | 已新增测试侧 `ModelEvaluationRunner`、`wp2-model-eval/corpus.json` 和 `scripts/wp2_model_quality_eval.sh`，当前覆盖 `case-design`、`defect-triage`、`requirement-summary` 三类任务，并支持 `WP2_MODEL_EVAL_TASK` 按任务类型过滤 | Prompt 或 provider 变更可执行 `bash scripts/wp2_model_quality_eval.sh`，或用 `WP2_MODEL_EVAL_TASK=case-design` 跑单任务评测；低于 scenario pass、required term recall 或 forbidden term clean 阈值会失败 |
| WP2-D2 | 流式响应支持 | P2 | DONE-CURRENT | 已新增 `POST /api/v1/model-access/invocations/stream` SSE 接口和 `portal-web` 流式消费 helper；MVP 先复用同步 invocation 的策略、预算、provider 调用和调用日志落盘，再按 `metadata/delta/done` 事件输出响应分片 | 同步 `POST /invocations` 契约不变；流式调用仍写入调用日志；真实 provider 原生 token streaming 作为后续增强 |
| WP2-D3 | 异步长任务调用 | P2 | DONE-CURRENT | 已新增 `POST /api/v1/model-access/invocations/jobs`、`GET /api/v1/model-access/invocations/jobs/{jobId}` 和 `POST /api/v1/model-access/invocations/jobs/{jobId}/cancel`；任务状态以 `ma_invocation_job` 持久化表和 repository 为状态源，复用 `ModelInvocationService` 的策略、预算、provider 调用和调用日志落盘；服务启动时重排 `QUEUED` 并将遗留 `RUNNING` 标记失败 | 任务可查询，未开始任务可稳定取消，运行中任务 best-effort 取消；成功/失败仍写入既有调用日志和 WP1 审计，单 JVM worker 可在重启后恢复队列状态；多实例分布式调度作为后续运行增强 |

## 6. WP3 资产管理待补任务

WP3 已完成资产库前后端主闭环，并在 2026-05-23 补齐历史版本回滚、前端导入导出工作流、后端聚合影响分析、页面/业务流追踪关系和标准化原型同步骨架。后续重点转向真实第三方账号连接器、可视化追踪编辑、多跳评分和测试执行结果闭环。

### WP3-A 交付文档与契约冻结

| 编号 | 任务 | 优先级 | 状态 | 工作内容 | 验收标准 |
|---|---|---|---|---|---|
| WP3-A1 | WP3 final 交付说明 | P0-B | DONE-CURRENT | 已新增 `doc/mvp/final/engineering/WP3-测试资产管理-当前交付说明.md`，覆盖当前实现、范围、非范围、API、数据模型、验证命令和后续入口 | 文档达到 WP1/WP2/WP4 当前交付说明同等级别 |
| WP3-A2 | WP3 PRD/架构补充 | P0-B | DONE-CURRENT | 已新增 `doc/mvp/final/engineering/WP3-测试资产管理-PRD与架构补充.md`，明确资产类型、状态流、追踪关系、权限、审计、导入导出边界 | 产品、后端、前端、测试可据此拆 issue |
| WP3-A3 | OpenAPI 契约测试 | P0-B | DONE-CURRENT | 已新增 `AssetOpenApiContractTest`，固定 `/api/v1/asset` 关键路径、Bearer 鉴权、字段和无租户回归 | 契约测试阻止未评审字段变化 |
| WP3-A4 | API 分页和筛选口径统一 | P0-B | DONE-CURRENT | 列表已统一返回 `items/index/size/total`，支持 `projectId/status/keyword/source` 基础筛选 | 与当前平台分页口径一致，兼容 WP4 调用路径 |

### WP3-B 后端资产能力补齐

| 编号 | 任务 | 优先级 | 状态 | 工作内容 | 验收标准 |
|---|---|---|---|---|---|
| WP3-B1 | 资产编码生成与唯一性策略 | P0-B | DONE-CURRENT | requirement/api/page/flow/testCase 已由服务端生成短 code，数据库保持 project+code 唯一约束 | db 和 local profile 行为一致，冲突返回稳定错误 |
| WP3-B2 | 资产状态流和非法转换 | P0-B | DONE-CURRENT | 已冻结 DRAFT/REVIEWING/APPROVED/DEPRECATED 等状态流，并覆盖非法转换拒绝测试 | 非法转换阻断并写审计 |
| WP3-B3 | 版本、历史、diff 和回滚 | P1 | DONE-CURRENT | 需求/测试用例返回版本号；创建、编辑、WP4 DRAFT 幂等更新、步骤替换和回滚保存 append-only 历史、字段 diff、快照、变更人和 traceId；portal-web 详情页提供历史/diff/rollback 入口 | 需求和测试用例可通过 versions API 回看历史版本，并通过 rollback API 回到历史快照且生成新的 `ROLLBACK` 历史记录 |
| WP3-B4 | 软删除、归档和恢复策略 | P1 | DONE-CURRENT | 已为需求、API、页面、业务流和测试用例新增独立 `lifecycleStatus=ACTIVE/ARCHIVED/DELETED`、生命周期查询/更新 API、默认 ACTIVE 列表过滤、恢复唯一性冲突校验和需求/用例生命周期历史 | 删除不破坏 trace link 和审计追溯；`DELETED` 复用 `deleted_at` 释放现有 partial unique index，恢复冲突返回稳定错误 |
| WP3-B5 | 导入/导出能力和前端工作流 | P1 | DONE-CURRENT | 已新增 `/api/v1/asset/imports` 与 `/api/v1/asset/exports`，支持需求、API、测试用例 CSV/JSON 导入导出，并支持 API 资产轻量 OpenAPI 导入导出；导入提供 `dryRun`、逐行 action/status/errors，导出仅输出业务字段不包含 traceId/snapshot/history；portal-web 提供统一导入预检、正式导入和导出下载面板 | 导出脱敏，导入有 dryRun 和错误明细，前端按 `asset:manage/asset:export` 控制入口 |
| WP3-B6 | API 资产 OpenAPI 导入 | P1 | DONE-CURRENT | OpenAPI 导入解析 `paths` 下的 path、method、summary、description、request/response schema 与 `info.version`，写入 API `source/sourceRef/version`；重复导入按 `projectId + path + httpMethod` 命中既有 API 后返回 `LINK_EXISTING` 或 `UPDATE` | 重复导入幂等更新同一接口，不重复创建；dryRun 可预览 create/update/link 结果 |
| WP3-B7 | 页面资产原型输入与同步骨架 | P2 | DONE-CURRENT | 页面资产已为 Figma/蓝湖/Axure/手工来源保留 `sourceRef/sourceVersion/componentTree/screenshotUrl`；新增 `/api/v1/asset/prototype-sync`，支持标准化页面数组 dryRun、sourceRef 幂等创建/更新、逐行结果和审计；portal-web 页面资产页提供同步表单 | 无真实第三方凭据时也可同步外部导出的标准化页面数据；真实账号授权、分页拉取、远端删除语义和沙箱 smoke 后续按 P2 连接器专项扩展 |
| WP3-B8 | 页面/业务流追踪关系 | P1 | DONE-CURRENT | trace link 创建、查询、响应 DTO 和 DB 索引已扩展 `pageId/flowId`；跨项目校验沿用现有 WP1 context；需求详情可展示 API/Page/Flow/Case 关系 | 需求可关联页面和业务流，查询可按 `pageId/flowId` 过滤，跨项目目标资产被拒绝 |
| WP3-B9 | 后端聚合影响分析 | P1 | DONE-CURRENT | 新增 `GET /api/v1/asset/impact`，支持按项目或单个资产主体聚合需求、API、页面、业务流、测试用例节点，返回数量、节点列表、缺口和生成时间；契约测试固定入口 | 后端可直接返回包含 page/flow/case/API 的影响分析结果，前端 helper 可消费该契约 |

### WP3-C 权限、审计与上下文

| 编号 | 任务 | 优先级 | 状态 | 工作内容 | 验收标准 |
|---|---|---|---|---|---|
| WP3-C1 | 用户态 RBAC 接入 | P0-B | DONE-CURRENT | 资产 API 已支持内部 service token 和登录用户 Bearer，用户态按 `asset:*` 权限校验；项目授权拒绝依赖 WP1 context 并已由契约测试固定 | 项目上下文未授权或不可用时资产读写被拒绝 |
| WP3-C2 | 资产权限点矩阵 | P0-B | DONE-CURRENT | 已定义 `asset:read/manage/review/export`，同步内置角色、DB seed、前端菜单和按钮规则 | 前端菜单和按钮可按权限隐藏 |
| WP3-C3 | 审计事件字典 | P0-B | DONE-CURRENT | 已新增 `doc/mvp/final/engineering/WP3-审计事件字典.md`，冻结资产写操作、拒绝、追踪链接以及后续导入/导出预留审计 action/resourceType | 当前写操作和拒绝可审计；后续导入/导出沿用同一字典扩展 |
| WP3-C4 | 与 WP1 context/audit 契约测试 | P0-B | DONE-CURRENT | 已新增 `AssetContextAuditContractTest`，覆盖停用项目、未授权项目上下文、审计写失败不产生脏资产；既有测试覆盖 service token、用户 Bearer、跨项目引用拒绝和 WP4 发布回读 | 上下文和审计异常不产生脏资产 |

### WP3-D 前端资产工作台

| 编号 | 任务 | 优先级 | 状态 | 工作内容 | 验收标准 |
|---|---|---|---|---|---|
| WP3-D1 | 资产库导航和路由 | P0-B | DONE-CURRENT | portal-web 已增加资产库入口、hash 深链和 P1 类型入口，并按 `asset:read` 展示 | 无权限用户不可访问；刷新和深链可用 |
| WP3-D2 | 需求资产页面 | P0-B | DONE-CURRENT | 已实现需求列表、详情、创建、编辑、状态流入口、来源追踪、历史版本查看和回滚 | WP4 发布的 IMPORT 需求可在页面查看 source/sourceRef/sourceUrl；需求可从历史版本回滚 |
| WP3-D3 | API 资产页面 | P1 | DONE-CURRENT | portal-web 已开放 API 资产页，支持列表、详情、创建、编辑、`method/path/status/source/keyword` 筛选、schema 展示，并通过统一导入导出面板支持 OpenAPI 导入导出 | 接口路径和方法可筛选；OpenAPI 导入可预检和正式导入；重复创建由后端唯一性约束阻断 |
| WP3-D4 | 页面和业务流页面 | P1 | DONE-CURRENT | portal-web 已开放页面资产与业务流资产页，支持列表、详情、创建、编辑、`projectId/status/source/keyword` 筛选、JSON 预览和编辑校验；页面资产页支持标准化原型同步表单 | JSON 字段展示不撑破布局，编辑前校验合法 JSON，页面/流程创建编辑走现有 WP3 契约，原型同步可 dryRun 并展示逐行结果 |
| WP3-D5 | 测试用例与步骤页面 | P1 | DONE-CURRENT | portal-web 已开放测试用例页，支持列表、详情、创建、编辑、状态筛选、关联需求/API 展示与跳转、步骤新增/删除/上移/下移、整体保存、历史版本查看和回滚 | 步骤顺序稳定，保存失败保留本地编辑草稿，创建/编辑/步骤保存走现有 WP3 用例契约；用例可从历史版本回滚 |
| WP3-D6 | 追踪矩阵和影响分析 | P1 | DONE-CURRENT | portal-web 已开放追踪矩阵页，基于现有需求/API/用例列表和 `/api/v1/asset/links` 做前端只读聚合，展示 requirement-api-case 覆盖状态、缺 API/用例缺口、孤立 API/用例和一跳影响范围；资产 API helper 已接入后端 `/impact` 聚合契约 | 可按需求查看覆盖 API/用例，按 API/用例反查相关需求和用例；后端影响分析已覆盖需求、API、页面、业务流、用例 |
| WP3-D7 | 前端导入导出工作流 | P1 | DONE-CURRENT | 需求和测试用例工作台接入 `AssetImportExportPanel`，支持资产类型/格式/projectId/dryRun/内容输入、导入预检、正式导入、导出下载和 traceId 展示 | 无 `asset:manage` 不可导入，无 `asset:export` 不可导出；导入成功可刷新当前工作台 |

### WP3-E 质量门禁与集成

| 编号 | 任务 | 优先级 | 状态 | 工作内容 | 验收标准 |
|---|---|---|---|---|---|
| WP3-E1 | WP3 quality gate | P0-B | DONE-CURRENT | 已新增 `scripts/wp3_quality_gate.sh` 和 `.github/workflows/wp3-asset-management.yml`，串联后端测试、OpenAPI/上下文契约、db validation、前端资产测试和可选 smoke | 一条命令可完成 WP3 本地准出，CI 可在资产相关变更中复用同一入口 |
| WP3-E2 | WP3 HTTP smoke | P0-B | DONE-CURRENT | 已新增 `scripts/wp3_asset_smoke.sh`，覆盖资产 CRUD、分页、状态拒绝、trace link 和可选用户 Bearer 读 | smoke 失败能输出 traceId 和失败资源 |
| WP3-E3 | WP3 DB validation 扩展 | P0-B | DONE-CURRENT | `wp_all_schema_validation.sql` 已覆盖 WP3 核心表、关键字段、唯一索引、sourceRef 幂等索引和无 tenant_id 回归 | 核心表、唯一索引、sourceRef 幂等索引和无 tenant_id 回归均可验证 |
| WP3-E4 | portal-web 测试 | P1 | DONE-CURRENT | 已增加资产 API normalizer 测试和权限测试，前端构建通过 | 前端构建和测试覆盖主流程 |
| WP3-E5 | 与 WP4 发布链路回归 | P0-B | DONE-CURRENT | WP4 controller/smoke 覆盖发布到 WP3 后需求回读、幂等更新和冲突阻断 | WP4 dryRun/正式发布不会重复创建 WP3 需求 |

## 7. WP4 需求与文档输入待补任务

WP4 本轮 P0 已覆盖真实文件上传、Word/PDF/OCR、AI 解析、SecretProvider 和 golden set。待补任务主要是生产安全、连接器扩展和长期运营能力。

### WP4-A Webhook 生产安全

| 编号 | 任务 | 优先级 | 状态 | 工作内容 | 验收标准 |
|---|---|---|---|---|---|
| WP4-A1 | Webhook IP/CIDR 白名单 | P0-B | DONE-CURRENT | 已新增 webhook ingress guard，支持全局/按 sourceCode CIDR 白名单与可信代理 `X-Forwarded-For` 解析，并补 controller 级拒绝事件测试 | 非白名单请求在签名前被拒绝并记录安全事件 |
| WP4-A2 | Webhook 请求限流 | P0-B | DONE-CURRENT | 已按 sourceCode、remoteIp、idempotencyKey 增加单 JVM 内存限流，并补 controller 级超限拒绝与事件记录测试 | 超限返回稳定错误码，不进入业务解析 |
| WP4-A3 | Webhook 自动重试调度 | P1 | DONE-CURRENT | 已增加 `WP4_WEBHOOK_AUTO_RETRY_ENABLED`、`WP4_WEBHOOK_AUTO_RETRY_BATCH_SIZE` 和 `WP4_WEBHOOK_AUTO_RETRY_CRON`，调度器按批次重放签名有效、payload 可用且未达上限的失败事件，达到上限沿用死信策略 | retryCount、deadLetter、replayTraceId 可查询；自动重试默认关闭 |
| WP4-A4 | Webhook 签名样例和联调包 | P1 | DONE-CURRENT | 已新增 `doc/mvp/final/engineering/WP4-Webhook签名样例与联调说明.md`，提供 cURL、Node.js、Java 签名样例和错误排查说明 | 外部系统可按样例完成联调 |

### WP4-B OCR 与二进制解析生产硬化

| 编号 | 任务 | 优先级 | 状态 | 工作内容 | 验收标准 |
|---|---|---|---|---|---|
| WP4-B1 | OCR 隔离 worker 方案 | P1 | DONE-CURRENT | 已补 `WP4_OCR_WORKER_MODE=HTTP_WORKER`、`WP4_OCR_WORKER_URL`、`WP4_OCR_WORKER_TOKEN` 和 `WP4_OCR_LOCAL_COMMAND_FALLBACK_ENABLED`；`platform-api` 可调用外部 HTTP OCR worker，生产可关闭本地命令 fallback；健康接口和 portal-web 展示 worker/fallback 状态；新增 `WP4-OCR隔离Worker接入Runbook.md` | OCR 超时、崩溃或高 CPU 可隔离在独立 worker；未配置或异常时返回可读错误，不在生产误执行本地命令 |
| WP4-B2 | 恶意文件扫描 | P1 | DONE-CURRENT | 已补 `WP4_MALWARE_SCAN_COMMAND` 命令式文件扫描 provider，支持超时、并发和输出截断配置；Word/PDF/OCR 二进制解析前先扫描，健康接口暴露扫描开关和并发余量 | 被标记恶意文件不进入解析，错误摘要不泄露内部路径 |
| WP4-B3 | 文件类型嗅探和 MIME 校验 | P1 | DONE-CURRENT | `binaryMimeValidationEnabled` 已接入 data URL 声明 MIME 与文件魔数/内容嗅探校验，覆盖 PDF、DOC/DOCX 和常见图片类型；健康接口暴露配置 | 伪造 MIME 被拒绝或按真实类型处理 |
| WP4-B4 | PDF 页数/解析时间限制 | P1 | DONE-CURRENT | `pdfMaxPages/pdfMaxParseMillis` 已在 PDFBox 解析路径生效，并由单测覆盖页数超限和解析耗时超限；健康接口暴露配置 | 超限失败可读，临时文件清理稳定 |
| WP4-B5 | 高保真解析专项 | P2 | DONE-CURRENT | 已新增 `WP4-高保真解析专项评估.md`，冻结表格结构、图片语义、页眉页脚、批注/修订、附件抽取的能力分层、metadata 契约、样本集、指标和 worker 路线 | 不影响当前文本抽取链路；后续代码落地必须继续通过候选确认，不得绕过 WP2 策略、WP3 发布和 WP1 审计 |

### WP4-C AI 解析质量体系

| 编号 | 任务 | 优先级 | 状态 | 工作内容 | 验收标准 |
|---|---|---|---|---|---|
| WP4-C1 | 扩大 golden corpus | P1 | DONE-CURRENT | 已将 `corpusVersion=wp4-c1-2026-05-22` 扩展到 12 个样本、28 条期望需求，`TEXT/MARKDOWN/WORD/PDF/OCR/CUSTOM_API` 六类各至少 2 个样本；覆盖平台、金融、零售、医疗、制造、物流行业，以及长文档、表格需求、歧义优先级、异常格式和 OCR 低置信度场景 | 样本版本、行业、难度、覆盖标签和 sourceType 分布由 `DocumentAiParseQualityEvaluationTest` 校验；低于阈值或缺少覆盖场景会阻断评测 |
| WP4-C2 | 按文档类型拆分指标 | P1 | DONE-CURRENT | `DocumentAiParseQualityEvaluationTest` 已按 `sourceType` 分别输出标题召回、优先级准确率和验收标准覆盖率，并要求六类 sourceType 都进入评测 | 任一类型低于阈值能定位 |
| WP4-C3 | Prompt 版本和评测绑定 | P1 | DONE-CURRENT | golden corpus 已记录 `promptKey=wp4-document-requirement-parse`、`promptVersion=v1`，评测输出 parserVersion `rule-json-v1` 并校验 prompt 元数据 | Prompt 变更必须跑对应评测 |
| WP4-C4 | 模型解析人工纠错回流 | P2 | DONE-CURRENT | 已在 `parseSource=MODEL` 候选人工编辑时通过 `DocumentParseFeedbackCaptureService` 自动生成 `document_input_parse_feedback_sample`，保存字段差异、WP2 invocation 追踪、输入摘要和脱敏前后快照，并提供 `GET /api/v1/document-input/feedback-samples` 供后续人工入库 golden corpus | 纠错样本仅保留摘要/脱敏内容，支持按 candidate/import/project/parseSource/curationStatus 查询，可进入 corpus 人工筛选 |

### WP4-D 外部连接器扩展

| 编号 | 任务 | 优先级 | 状态 | 工作内容 | 验收标准 |
|---|---|---|---|---|---|
| WP4-D0 | 外部连接器接入 Runbook 与 mock 契约 | P2 | DONE-CURRENT | 已新增 `WP4-外部连接器接入Runbook与Mock契约.md`，冻结 Confluence、飞书、钉钉、语雀的统一配置 schema、secretRef 口径、连接测试、分页拉取、版本游标、错误码、同步任务状态、安全和准出策略 | 无真实凭证时仍保持预留状态；后续真实连接器可按同一 mock 契约、沙箱 smoke 和 SecretProvider 口径逐个平台灰度接入 |
| WP4-D1 | Confluence 真实连接器 | P2 | TODO | OAuth/API 拉取、空间/页面映射、版本和权限策略 | 真实页面可导入，失败可重试，secretRef 不泄露 |
| WP4-D2 | 飞书文档连接器 | P2 | TODO | 飞书开放平台凭证、文档 token、版本、内容转换 | 真实文档可进入候选确认 |
| WP4-D3 | 钉钉文档连接器 | P2 | TODO | 钉钉文档 API、凭证、文档标识和同步任务 | 同步状态和最近错误可在 UI 展示 |
| WP4-D4 | 语雀连接器 | P2 | TODO | 知识库、文档、版本、凭证和同步策略 | 不影响已有 CUSTOM_API 和文件导入 |

### WP4-E SecretProvider 与 Vault/KMS 生产治理

| 编号 | 任务 | 优先级 | 状态 | 工作内容 | 验收标准 |
|---|---|---|---|---|---|
| WP4-E1 | 外部 Vault/KMS provider 健康检查 | P1 | DONE-CURRENT | 已为外部 SecretProvider 增加 `WP1_EXTERNAL_SECRET_TIMEOUT_SECONDS`、`WP1_EXTERNAL_SECRET_MAX_RETRIES` 和 `WP1_EXTERNAL_SECRET_HEALTH_URL`；resolve 对短暂网络/5xx/429 失败重试，`/api/v1/document-input/health` 返回脱敏的 `externalSecretProvider` 摘要并记录 `veri.agent.document_input.secret_provider.health` | provider 未配置、健康端点缺失、UP/DOWN 均可观测；健康摘要不暴露 endpoint、token、secretRef 或明文；生产禁用 fallback 后仍不会回退到 dev/test secret |
| WP4-E2 | mTLS 或签名认证方案 | P1 | DONE-CURRENT | 已为外部 SecretProvider resolve/health 请求增加可选 HMAC-SHA256 签名认证，支持 `WP1_EXTERNAL_SECRET_SIGNING_KEY_ID` 与 `WP1_EXTERNAL_SECRET_SIGNING_SECRET`；请求头包含算法、keyId、timestamp、nonce 和 canonical 签名，且可与既有 Bearer token 并用；mTLS 证书链仍作为后续增强 | 认证失败只返回状态类错误，不回显 secretRef、签名密钥、endpoint 或明文；未配置签名密钥时保持旧 Bearer/无签名行为 |
| WP4-E3 | Secret 缓存和轮换策略 | P1 | DONE-CURRENT | 已新增 `WP4_WEBHOOK_SECRET_CACHE_TTL_SECONDS` 和 `WP4_WEBHOOK_SECRET_ROTATION_OVERLAP_SECONDS`；webhook resolver 仅缓存 SecretProvider 成功解析结果，source 创建/更新后主动失效，配置/default fallback 不进入缓存；`/api/v1/document-input/health` 和 portal-web 状态面板暴露缓存开关、TTL、轮换窗口和缓存数 | TTL 到期、TTL=0、主动失效、fallback 不缓存均有测试；轮换期间新旧 SecretProvider 引用需至少重叠 `max(TTL, rotationOverlap)` 后再撤销旧密钥 |
| WP4-E4 | Secret 解析审计增强 | P1 | DONE-CURRENT | 已在 SecretProvider resolve 路径记录成功/失败审计，包含 provider、用途、调用方、作用域、版本和 `secretRefDigest`；审计 `resourceId` 使用 `sha256:<digest>`，不落完整 secretRef 或明文 | 安全审计能通过 digest 追踪 secretRef 使用情况，且不会暴露密钥明文、完整 secretRef、外部 endpoint、Bearer token 或签名密钥 |

### WP4-F 数据保留、清理与前端体验

| 编号 | 任务 | 优先级 | 状态 | 工作内容 | 验收标准 |
|---|---|---|---|---|---|
| WP4-F1 | 原文快照和事件保留策略 | P1 | DONE-CURRENT | 已新增导入记录/候选和 webhook 事件保留天数配置：`WP4_IMPORT_RETENTION_DAYS`、`WP4_WEBHOOK_EVENT_RETENTION_DAYS`；过期 import、candidate 和 webhook event 清理前写入 `document_input_retention_archive`，保留 `recordType/recordId/projectId/sourceCode/payloadDigest/originalCreatedAt/snapshotJson/archivedAt` | 清理不破坏已发布资产来源追踪；归档表不提供前端明文查看入口，恢复需 DBA/运维受控执行 |
| WP4-F2 | 清理任务与归档 | P1 | DONE-CURRENT | `DocumentInputRetentionCleanupService` 定时清理入口已补齐清理前归档、成功/失败指标标签和 `RETENTION_CLEANUP` 审计；`WP4_RETENTION_CLEANUP_ENABLED` 默认关闭，cron 可配置；local/db profile 支持清理过期导入、候选和 webhook 事件 | 清理过程有指标和审计，过期数据先归档再从在线查询范围移除 |
| WP4-F3 | 前端 E2E smoke | P1 | DONE-CURRENT | 已新增 `portal-web` Playwright smoke、`npm run test:wp4-smoke` 与 `scripts/wp4_frontend_e2e_smoke.sh`，通过浏览器 mock 后端契约覆盖真实文件上传、候选编辑/确认、发布 dryRun 预览和 webhook 事件重放 | 核心用户路径可在本地或 CI 复现；本地无 Playwright 托管浏览器时脚本可自动使用系统 Chrome，或设置 `WP4_FRONTEND_INSTALL_BROWSERS=1` 安装 Chromium |
| WP4-F4 | 解析失败体验优化 | P1 | DONE-CURRENT | 已补 OCR 未配置、PDF 无文本、PDF/文件/payload 超限、webhook 签名失败的可操作错误提示；portal-web 统一展示错误码、Trace ID 和下一步建议，导入失败后自动刷新失败记录 | 用户能知道下一步应配置 OCR、换文件或联系管理员；后端错误码保持稳定，签名失败不泄露 secret 或完整签名 |

## 8. 建议里程碑

| 里程碑 | 目标 | 包含任务 | 准出标准 |
|---|---|---|---|
| M0：补齐 WP3 交付闭环 | 让 WP3 达到 WP1/WP2/WP4 同等级别的可验收形态 | WP3-A、WP3-C、WP3-E | WP3 交付说明、契约测试、quality gate、HTTP smoke 可用 |
| M1：生产准出硬化 | 补齐最容易影响上线安全和运维的缺口 | WP1-A、WP1-C、WP2-B、WP4-A、WP4-B | 预发 release validation、webhook 白名单/限流、OCR 安全策略均有测试或 runbook |
| M2：管理台产品化 | 补齐日常运营和业务使用界面 | WP1-B、WP2-A、WP3-D、WP4-F | 管理员不依赖 curl 完成主要配置和排错 |
| M3：质量与智能化增强 | 建立可长期迭代的质量体系 | WP2-D、WP4-C、WP3 追踪矩阵 | Prompt/解析器变更有评测门禁，资产覆盖率可视化 |
| M4：企业集成扩展 | 接入协作文档和企业身份体系 | WP1-E、WP4-D | WP1-E 预留方案已冻结；WP4-D 共同 runbook/mock 契约已冻结，真实连接器仍需外部凭证/沙箱后逐个平台推进；外部系统接入不破坏当前 MVP 链路 |

## 9. 推荐下一步

1. `WP4-D1-D4` 真实外部连接器需要 Confluence、飞书、钉钉、语雀的沙箱凭证和 API 访问策略；共同前置已在 `WP4-外部连接器接入Runbook与Mock契约.md` 冻结，拿到沙箱后先按 mock 契约补真实 connector smoke，再逐个平台灰度启用。
2. `WP4-B5` 后续进入代码落地时，先扩展 Word 表格/批注/页眉页脚 metadata，再新增 `wp4-high-fidelity-corpus` 和 `scripts/wp4_high_fidelity_parse_eval.sh`，最后把 PDF 版式和图片语义放入隔离 worker。
3. `WP1-E` 后续进入代码落地时，先做默认关闭的身份源配置和审批只读/草稿能力，再灰度 SSO 登录、组织同步 dryRun 和审批必经策略。
4. 每完成一个任务，补充对应交付说明、测试命令和 release note，并按当前约定提交清晰 commit。
