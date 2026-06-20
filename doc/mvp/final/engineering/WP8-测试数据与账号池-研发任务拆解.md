# WP8 测试数据与账号池 - 研发任务拆解

| 项目 | 内容 |
|---|---|
| 工作包 | WP8 测试数据与账号池 |
| 角色产出 | 五角色联合任务拆解 |
| 文档性质 | 正式研发前可执行 Story/Task 清单 |
| 当前口径 | `platform-api` + `portal-web` 承载 WP8 控制面；P0 不自动创建真实业务账号，不复制生产数据 |
| 版本 | v0.1 |
| 日期 | 2026-06-15 |

## 1. 拆解原则

1. 先冻结权限、数据模型和安全红线，再做租借并发和前端闭环。
2. 每个任务必须有项目 scope、权限、审计、traceId 和脱敏口径。
3. 账号凭据只走 `secretRef`，任何任务不得要求前端或 API 返回明文。
4. 租借并发以数据库约束和条件更新为准，不依赖前端防重。
5. P0 不建设 WP7 浏览器执行器、WP9 调度实现、WP10 报告诊断、真实账号自动开通和生产数据复制。

## 2. 角色分工

| 角色 | 主要负责 |
|---|---|
| 资深项目经理 | 任务排期、依赖协调、范围控制、风险和回滚。 |
| 资深产品经理 | 数据集、账号池、租借、清理任务的用户流程和验收标准。 |
| 资深服务端架构师 | DB、领域模块、API 契约、租借并发、SecretProvider、跨 WP 引用和安全边界。 |
| 资深前端工程师 | 工作台路由、表单、权限按钮、状态展示、响应式和可测性。 |
| 资深质量工程师 | 功能、安全、并发、DB、前端 smoke 和 quality gate。 |

## 3. 总体里程碑

| 里程碑 | 目标 | 退出标准 |
|---|---|---|
| M0 启动准入 | 文档、范围、任务拆解冻结 | 五角色评审无阻断 |
| M1 基础骨架 | 权限、DB、模块骨架、health | OpenAPI contract 和 DB validation 通过 |
| M2 数据集控制面 | 数据集 CRUD、记录摘要、清理策略 | 数据集可被引用且敏感字段不泄露 |
| M3 账号池控制面 | 账号池、账号摘要、secretRef digest | 账号凭据不回显，状态流可测 |
| M4 租借和清理 | 租借、续租、释放、过期、清理任务 | 并发租借和幂等可测 |
| M5 跨 WP 引用 | WP7/WP9/WP10 引用契约和 adapter port | 不直接读写跨 WP 表 |
| M6 前端闭环 | 测试数据工作台主链路 | Vitest、Playwright、build 通过 |
| M7 质量门禁 | WP8 quality gate、DB validation、并发 smoke | release gate 明确 |

## 4. Epic 0：启动准入

| Story | 优先级 | 负责人 | 任务 | 验收 | 验证 |
|---|---|---|---|---|---|
| WP8-0.1 范围冻结 | P0 | 项目经理 | 确认 WP8 只覆盖测试数据与账号池控制面，冻结非目标 | 6 份启动文档口径一致 | 文档评审 |
| WP8-0.2 依赖清单 | P0 | 服务端架构师 | 梳理 WP1/WP3/WP6/WP7/WP9/WP10 依赖 | 不直连跨 WP 表 | 文档评审 |
| WP8-0.3 安全红线 | P0 | 产品经理、质量工程师 | 冻结 secretRef、敏感数据、导出、审计红线 | 红线被 PRD/技术/测试文档共同引用 | 文档评审 |

## 5. Epic 1：权限、DB 和模块骨架

| Story | 优先级 | 负责人 | 任务 | 验收 | 验证 |
|---|---|---|---|---|---|
| WP8-1.1 权限点 seed | P0 | 服务端架构师 | 新增 `testData:read/manage/lease/cleanup/export` 权限和角色映射 | 默认角色符合 PRD | DB validation、权限测试 |
| WP8-1.2 审计事件字典 | P0 | 服务端架构师、质量工程师 | 定义数据集、账号池、租借、释放、清理和导出审计事件；M1 先通过 `test_data.audit_events` system config 种子固化字典，后续业务 API 写审计时复用同一 action 集 | payload 只含摘要和 digest | DB validation、后续业务审计单测 |
| WP8-1.3 DB schema | P0 | 服务端架构师 | 新增数据集、记录、任务、账号池、账号、租借、矩阵表 | 约束、索引、注释完整 | DB validation |
| WP8-1.4 模块骨架 | P0 | 服务端架构师 | 新建 `testdata` api/application/domain/infrastructure/config 包 | 不破坏现有模块边界 | `mvn -B -pl platform-api test` |
| WP8-1.5 Health API | P0 | 服务端架构师 | `GET /api/v1/test-data/health` 输出限制、开关和 M1 foundation 安全策略摘要 | 不泄露 secret 或 allowlist 明细；不把 CRUD/租借/清理 worker 标为 ready | Controller test |

M1 当前推进状态：权限常量、角色 seed、7 张基础表、表/列注释、审计事件字典配置、只读账号密文字段限制、`testdata` 配置和 health API 已进入实现；数据集 CRUD、账号池 CRUD、租借执行、清理 worker、导出和前端页面仍归属 M2-M6。

## 6. Epic 2：数据集控制面

| Story | 优先级 | 负责人 | 任务 | 验收 | 验证 |
|---|---|---|---|---|---|
| WP8-2.1 DataSet CRUD | P0 | 服务端架构师 | 创建、列表、详情、更新、归档数据集 | project scope 和状态保护正确 | Controller test |
| WP8-2.2 Schema validator | P0 | 服务端架构师 | 校验字段名、类型、敏感标记、摘要大小 | 非法 schema 返回稳定错误码 | Unit test |
| WP8-2.3 Record summary import | P0 | 服务端架构师 | 批量保存脱敏记录摘要和 digest | 不保存敏感原文 | Service test |
| WP8-2.4 Cleanup policy | P0 | 产品经理、服务端架构师 | 保存手动确认、TTL、回滚策略摘要 | 非 READY 数据集不可引用 | Service test |
| WP8-2.5 DataSet export | P1 | 服务端架构师、质量工程师 | 导出数据集脱敏摘要和 redaction policy | 不含完整 record payload、maskedSummary 值或 secretRef 原文 | Security test |

M2 当前推进状态：`platform-api` 已推进数据集控制面后端切片，覆盖 `POST/GET/PATCH/archive /api/v1/test-data/data-sets`、`POST /api/v1/test-data/data-sets/{id}/records` 和 `GET /api/v1/test-data/data-sets/{id}/export`，实现项目 scope 权限、schema validator、脱敏记录摘要 upsert、清理策略摘要、数据集脱敏导出摘要、审计事件和 OpenAPI contract。`veri-agent.test-data.enabled=false` 会阻断业务维护入口，`veri-agent.test-data.export-enabled=false` 会阻断导出入口，health API 保持可观测。M2 当前数据集导出只包含数据集元信息、字段计数、敏感字段计数、record digest、external ref digest、tags、`maskedSummaryKeys` 和 redaction policy，不包含完整 record payload、maskedSummary 值、secretRef 原文、token、cookie 或 Authorization header。

## 7. Epic 3：账号池控制面

| Story | 优先级 | 负责人 | 任务 | 验收 | 验证 |
|---|---|---|---|---|---|
| WP8-3.1 AccountPool CRUD | P0 | 服务端架构师 | 创建、列表、详情、更新、禁用账号池 | 应用/环境 scope 正确 | Controller test |
| WP8-3.2 Account manage | P0 | 服务端架构师 | 新增、更新、禁用、锁定账号摘要 | 只返回 secretRefDigest | Service test |
| WP8-3.3 SecretRef validation | P0 | 服务端架构师 | 校验 secretRef 格式、digest 和替换审计 | 不解析或输出明文 | Unit test |
| WP8-3.4 Role matrix | P1 | 产品经理、前端工程师 | 维护角色、菜单、资源作用域和场景标签 | WP7 权限场景可引用 | Service test |
| WP8-3.5 Health summary | P1 | 服务端架构师 | 保存账号健康状态和最近失败摘要 | 不保存登录响应原文 | Unit test |

M3 当前推进状态：`platform-api` 已推进账号池控制面后端切片，覆盖 `POST/GET/PATCH/disable/archive /api/v1/test-data/account-pools`、`POST /api/v1/test-data/account-pools/{id}/accounts` 和 `PATCH /api/v1/test-data/accounts/{id}`。本轮实现项目 scope 权限、账号池 code 唯一、账号 key 唯一、账号摘要维护、`secretRef` SHA-256 digest、健康摘要、审计事件、OpenAPI contract 和 DB profile 持久化验证。`secretRef` 原文只作为写入输入，服务端不保存、不返回、不写审计；`secret_ref_cipher` 不进入查询投影。M3 本轮未包含 WP8-3.4 独立角色矩阵维护页面、租借并发、续租、释放、过期回收、清理 worker、跨 WP adapter、脱敏导出和前端工作台，这些仍按 M4-M6 推进。

## 8. Epic 4：租借、释放和清理

| Story | 优先级 | 负责人 | 任务 | 验收 | 验证 |
|---|---|---|---|---|---|
| WP8-4.1 Lease API | P0 | 服务端架构师 | 按项目、应用、环境、角色、标签申请账号 | requestKey 幂等 | Controller test |
| WP8-4.2 Lease concurrency | P0 | 服务端架构师、质量工程师 | 使用条件更新或唯一索引保证 active lease 唯一 | 并发只有一个成功 | Repository test、smoke |
| WP8-4.3 Renew API | P0 | 服务端架构师 | active lease 续租并限制最大 TTL | 过期租借不可续租 | Service test |
| WP8-4.4 Release API | P0 | 服务端架构师 | 释放租借，按策略回可用或锁定 | 终态幂等 | Service test |
| WP8-4.5 Expire recovery | P1 | 服务端架构师、质量工程师 | 扫描过期 active lease 并收敛账号状态 | 过期可恢复和审计 | Scheduler smoke |
| WP8-4.6 Cleanup task | P0 | 服务端架构师 | 创建、查询、重试清理任务 | 清理开关关闭时不执行破坏性动作 | Controller test |

M4 当前推进状态：`platform-api` 已推进租借、释放和清理任务后端切片，覆盖 `POST/GET /api/v1/test-data/leases`、`GET/renew/release /api/v1/test-data/leases/{id}`、`POST/GET /api/v1/test-data/data-tasks`、`GET/retry /api/v1/test-data/data-tasks/{id}`。本轮实现 `requestKey` 幂等、角色标签匹配、条件更新抢占账号、active lease 唯一约束、续租 TTL 限制、释放终态幂等、过期回收服务方法、清理任务控制面记录、OpenAPI contract 和 DB profile 持久化验证。清理 worker、租借并发外部 smoke、WP7/WP9 adapter、脱敏导出和前端工作台仍按 M5-M7 推进。

## 9. Epic 5：跨 WP 引用

| Story | 优先级 | 负责人 | 任务 | 验收 | 验证 |
|---|---|---|---|---|---|
| WP8-5.1 WP9 lease adapter | P1 | 服务端架构师 | 定义 execution run 申请/释放账号接口 | WP9 只保存 `accountLeaseRef` | Contract test |
| WP8-5.2 WP7 runner contract | P1 | 服务端架构师、前端工程师 | 定义 UI runner 通过 lease 获取账号摘要和 secretRef 的契约 | runner 不接收密码明文 | 文档评审 |
| WP8-5.3 WP10 summary contract | P1 | 产品经理、质量工程师 | 定义报告可读的准备/租借/清理摘要 | 报告不展示 secret 或数据正文 | Contract test |

M5 当前推进状态：`platform-api` 已新增 `TestDataCrossWpReferenceService` 作为 WP8 跨 WP 应用层契约切片，WP9 lease adapter 通过该服务申请/释放租借并只保存 `accountLeaseRef`，WP7 runner contract 通过该服务读取账号摘要和 `secretRefDigest`，并可在受控 runner adapter 内部把解析后的凭据收敛成脱敏注入计划摘要，WP10 summary contract 通过该服务读取准备、租借和清理证据。当前实现不对外新增独立 HTTP 入口，不直连跨 WP 表，不执行 WP7 浏览器执行、不执行 WP9 调度和不生成 WP10 完整报告。

## 10. Epic 6：前端工作台

| Story | 优先级 | 负责人 | 任务 | 验收 | 验证 |
|---|---|---|---|---|---|
| WP8-6.1 API client | P0 | 前端工程师 | 新增 `portal-web/src/api/testData.ts` 和 normalize helper | payload 不含敏感字段 | Vitest |
| WP8-6.2 权限入口 | P0 | 前端工程师 | 新增 `#test-data` 入口和权限判断 | 无 read 权限不显示 | Vitest |
| WP8-6.3 数据集面板 | P0 | 前端工程师 | 列表、筛选、表单、详情、归档 | loading/empty/error 完整 | Vitest |
| WP8-6.4 账号池面板 | P0 | 前端工程师 | 池列表、账号摘要、secretRef 替换、状态操作 | 不回显 secretRef 原文 | Vitest |
| WP8-6.5 租借面板 | P0 | 前端工程师 | 申请、续租、释放、过期状态 | traceId 和冲突错误可见 | Vitest |
| WP8-6.6 清理任务面板 | P1 | 前端工程师 | 任务列表、创建、重试、失败摘要 | cleanup disabled 可解释 | Vitest |
| WP8-6.7 响应式 smoke | P1 | 前端工程师、质量工程师 | 桌面和 390px 主链路 | 无横向溢出；DOM 无 secretRef 原文 | Playwright smoke |

M6A 当前推进状态：`portal-web` 已新增 `#test-data` 工作台基础闭环，覆盖 `portal-web/src/api/testData.ts` API client/normalize helper、`testData:read/manage/lease/cleanup/export` 前端权限映射、数据集/账号池/租借/清理任务四个基础面板、secretRef 写入后清空且仅展示 `secretRefDigest`、traceId 错误展示和窄屏单列布局。当前 M6A 已通过 `testData.test.ts` 和 `permissions.test.ts` 覆盖路径、payload、权限和脱敏 helper，并通过 `npm run build`。

M6B/M7A 当前推进状态：已新增 `portal-web/e2e/wp8-test-data.smoke.playwright.ts`、`scripts/wp8_frontend_e2e_smoke.sh`、`scripts/wp8_account_lease_concurrency_smoke.sh` 和 `scripts/wp8_quality_gate.sh`。前端 smoke 覆盖桌面和 390px 视口下的数据集创建、记录摘要导入、账号池创建、账号 secretRef 写入后不回显、租借申请/续租/释放、清理任务创建/重试、DOM 不含输入 secretRef 原文和页面无横向溢出；quality gate 聚合脚本语法、Java 行数门禁、WP8 后端定向测试、DB repository contract、前端定向测试、Playwright smoke、前端 build、DB validation，并在 release 模式要求显式执行账号租借并发 smoke。

M6C 当前推进状态：已补齐数据集脱敏导出摘要后端接口和前端导出面板。`portal-web/src/api/testData.ts` 新增 `exportTestDataSet` 和导出 normalizer，`TestDataWorkbench` 数据集 tab 新增“脱敏导出摘要”面板，按钮受 `testData:export` 和 `health.exportEnabled` 控制；Playwright smoke 点击导出并断言 DOM 不含 `secret://` 或敏感测试值。真实文件下载、租借导出和真实 cleanup worker 仍未完成，不纳入本轮完成定义。

M6D 当前推进状态：已补齐租借脱敏导出摘要后端接口和前端导出面板。`platform-api` 新增 `GET /api/v1/test-data/leases/{id}/export`、`TestAccountLeaseExportResponse` 和导出审计，`portal-web/src/api/testData.ts` 新增 `exportTestAccountLease`、导出 normalizer，`TestDataWorkbench` 租借 tab 新增“租借脱敏导出摘要”面板；Playwright smoke 进一步断言导出结果只展示 digest、keys 和 redaction policy，不回显释放原因、健康摘要原文或 secretRef 原文。真实文件下载、真实 cleanup worker 和 WP7/WP9 执行器集成仍未完成，不纳入本轮完成定义。

## 11. Epic 7：质量门禁和发布准出

| Story | 优先级 | 负责人 | 任务 | 验收 | 验证 |
|---|---|---|---|---|---|
| WP8-7.1 后端测试 | P0 | 质量工程师、服务端架构师 | 数据集、账号池、租借、清理、权限、安全测试 | 主路径和错误路径覆盖 | `mvn -B -pl platform-api test` |
| WP8-7.2 前端测试 | P0 | 质量工程师、前端工程师 | api helper、权限、payload、脱敏展示 | 稳定通过 | `cd portal-web && npm test` |
| WP8-7.3 DB validation | P0 | 质量工程师、服务端架构师 | WP8 表、约束、索引、权限纳入 validation | 临时库迁移和复跑通过 | DB validation |
| WP8-7.4 Lease concurrency smoke | P0 | 质量工程师 | 并发申请同一角色账号，验证 active lease 唯一 | release gate 显式启用 | `scripts/wp8_account_lease_concurrency_smoke.sh` |
| WP8-7.5 Frontend smoke | P1 | 质量工程师、前端工程师 | 桌面和移动主链路、DOM 脱敏扫描 | 不泄露 secretRef | `scripts/wp8_frontend_e2e_smoke.sh` |
| WP8-7.6 Quality gate | P0 | 质量工程师 | 聚合后端、前端、构建、DB、smoke 和 Java 行数门禁 | release 模式要求并发 smoke | `scripts/wp8_quality_gate.sh` |

M7A 当前推进状态：`scripts/wp8_quality_gate.sh` 已落地为 development/release 双模式聚合门禁，支持 `WP8_QUALITY_GATE_PLAN_ONLY=1` 预览、`WP8_SKIP_FRONTEND_E2E=1` 显式跳过浏览器 smoke、`WP8_SKIP_DB_VALIDATION=1` 显式跳过 DB validation；release/preprod/prod 模式必须设置 `WP8_LEASE_CONCURRENCY_SMOKE=managed` 执行账号租借并发 smoke。当前并发 smoke 复用 `TestAccountLeaseServiceTest#rejectsSecondActiveLeaseUntilRelease` 和 `DbProfileRepositoryContractTest#testDataRepositoryPersistsLeasesAndCleanupTasksThroughJdbc` 作为本地 managed 证据，不启动真实 HTTP 服务或生产清理 worker。

## 12. Epic 8：文档和交付

| Story | 优先级 | 负责人 | 任务 | 验收 | 验证 |
|---|---|---|---|---|---|
| WP8-8.1 API 契约更新 | P0 | 服务端架构师 | 技术设计随实现更新真实路径、字段、错误码 | OpenAPI test 一致 | 文档评审 |
| WP8-8.2 操作说明 | P1 | 产品经理、前端工程师 | 编写数据集、账号池、租借、释放、清理操作说明 | 用户无需 curl 完成主链路 | 已完成：`WP8-测试数据与账号池-前端操作说明.md` |
| WP8-8.3 Runbook | P1 | 质量工程师、服务端架构师 | 编写租借卡死、账号锁定、secretRef 轮换、清理失败排障 | 运维可按步骤处理 | 已完成：`WP8-测试数据与账号池-运维Runbook.md` |
| WP8-8.4 发布准出说明 | P0 | 项目经理、质量工程师 | 记录验证命令、跳过项、风险、回滚和五角色准出 | 已完成：`WP8-测试数据与账号池-发布准出说明.md` | 文档评审 |

M8B/M8C 当前推进状态：已新增 WP8 前端操作说明、运维 Runbook 和 M8B/M8C 交付说明。该切片只补用户操作路径和运维排障文档，不修改前端运行时代码、服务端接口、数据库结构，不实现真实文件下载、真实 cleanup worker 或 WP7/WP9 执行器接入。

M8I 当前推进状态：已新增 WP8 发布准出说明、剩余工作盘点和 M8I 发布准出收口交付说明，并同步 README、PRD、技术设计、测试策略、正式启动准备和当前实现基线。当前 WP8 范围无剩余 P0 功能开发项；真实文件下载、真实 cleanup worker、WP7/WP9/WP10 真实执行器集成、真实账号自动开通、前端筛选分页详情增强和外部容量压测均作为后续专项，不构成本轮 WP8-8.4 发布阻断。

## 13. P0 完成定义

1. 可创建 READY 数据集并写入脱敏记录摘要。
2. 可创建账号池和账号摘要，响应不回显 secretRef 原文。
3. 可按角色租借账号，active lease 并发唯一。
4. 可续租、释放、过期回收账号，并形成审计。
5. 可创建清理任务，默认不执行破坏性动作。
6. 前端工作台覆盖数据集、账号池、租借、释放、清理和脱敏导出摘要。
7. `mvn -B -pl platform-api test`、`cd portal-web && npm test`、`cd portal-web && npm run build`、DB validation 和 WP8 quality gate 通过。

## 14. 推荐实施顺序

1. 先做 WP8-1.x，确保权限、DB、health 和安全红线稳定。
2. 再做 WP8-2.x，完成数据集和记录摘要。
3. 再做 WP8-3.x，完成账号池和 secretRef 管理。
4. 再做 WP8-4.x，完成租借并发、续租、释放和清理任务。
5. 再做 WP8-5.x，补 WP7/WP9/WP10 引用契约。
6. 前端 WP8-6.x 与后端契约并行推进，但所有按钮以后端权限和状态为准。
7. WP8-7.x 从第一轮迁移开始同步建设，避免最后补门禁。
