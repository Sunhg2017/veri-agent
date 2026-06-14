# WP8 测试数据与账号池 - 正式启动准备

| 项目 | 内容 |
|---|---|
| 工作包 | WP8 测试数据与账号池 |
| 角色产出 | 资深项目经理 |
| 文档性质 | 正式启动前范围冻结、里程碑、风险和准入清单 |
| 当前口径 | `platform-api` 先承载 WP8 控制面；`portal-web` 新增测试数据工作台；不抢跑 WP7 UI/E2E runner、WP9 调度和真实业务账号自动开通 |
| 版本 | v0.1 |
| 日期 | 2026-06-15 |

## 1. 启动结论

WP8 可以进入正式研发准备完成状态。当前启动包已冻结目标、范围、非目标、跨 WP 依赖、接口契约、前端入口、测试策略、任务拆解、风险和回滚方式。

WP8 的核心目标是把测试数据、账号池、租借、释放和清理任务变成可治理的控制面，为 WP7 UI/E2E 稳定执行、WP9 执行编排和 WP10 报告摘要提供可审计输入。首期不建设真实业务系统账号自动开通，不复制生产数据，不运行浏览器自动化，不执行未受控的破坏性清理。

## 2. 目标

1. 支持项目、应用、环境内的数据集目录、记录摘要、schema 和清理策略。
2. 支持账号池、账号摘要、角色标签、SecretProvider 引用和账号健康状态。
3. 支持账号租借、续租、释放、过期回收和清理任务，保证并发安全和幂等。
4. 提供 `dataSetRef/accountPoolRef/accountLeaseRef/cleanupTaskRef` 跨 WP 引用契约。
5. 提供 `#test-data` 前端工作台，覆盖数据集、账号池、租借、释放、清理和数据集脱敏导出摘要。
6. 建立 WP8 后端、前端、DB、并发 smoke 和 quality gate 验证入口。

## 3. 范围

| 范围项 | 说明 |
|---|---|
| 数据集 | 项目内创建、查询、编辑、归档，保存 schema、敏感字段、记录摘要和清理策略。 |
| 账号池 | 应用/环境内维护账号池、账号摘要、角色标签、状态、健康摘要和默认 TTL。 |
| secretRef | 账号凭据只保存引用和 digest，不回显明文，不导出原值。 |
| 租借 | 按角色、标签、holder 和 TTL 申请账号，支持幂等、续租、释放和过期回收。 |
| 清理任务 | 记录数据准备、刷新、清理、回滚任务，默认不执行破坏性动作。 |
| 权限审计 | 新增 `testData:read/manage/lease/cleanup/export`，所有变更写审计。 |
| 前端 | 新增测试数据工作台，覆盖权限、状态、错误、traceId 和响应式。 |
| 质量门禁 | 后端测试、前端测试、构建、DB validation、并发 smoke 和 WP8 quality gate。 |

## 4. 非目标

| 非目标 | 说明 | 后续承接 |
|---|---|---|
| UI/E2E 脚本生成和浏览器执行 | WP8 只提供账号和数据引用。 | WP7 |
| 执行计划 DAG 和调度 | WP8 不调度测试任务，只记录租借和清理任务。 | WP9 |
| Allure 报告和 AI 失败诊断 | WP8 只提供准备、租借和清理摘要。 | WP10 |
| 真实业务账号自动开通 | 首期只管理应用提供或人工登记的账号引用。 | 应用接入适配器 |
| 生产数据复制或脱敏管道 | 涉及合规和数据治理，不纳入 P0。 | 数据治理专项 |
| 明文凭据存储 | 密码、token、cookie 不得落库或导出。 | 不做 |

## 5. 涉及模块

| 模块 | 影响 |
|---|---|
| `platform-api` | 后续新增 `testdata` 领域模块，承载数据集、账号池、租借、清理和导出控制面。 |
| WP1 平台基础 | 复用项目、应用、环境、RBAC、SecretProvider、审计、traceId 和资源 scope。 |
| WP3 测试资产 | 可关联用例、页面、业务流和数据需求摘要；不直接写 WP3 表。 |
| WP6 OpenAPI 接口自动化 | 后续可引用 `dataSetRef/accountLeaseRef`，不解析账号密钥。 |
| WP7 UI/E2E | 后续通过 `accountLeaseRef` 获取账号摘要和 `secretRefDigest`；runner 不接收 `secretRef` 原文，真实凭据注入由后续受控 SecretProvider adapter 承接。 |
| WP9 执行编排 | 后续执行节点可申请和释放账号 lease，并关联 cleanup task。 |
| WP10 报告诊断 | 后续读取准备、租借、清理摘要作为报告证据。 |
| `portal-web` | 已新增 `#test-data` 工作台基础闭环、API helper、数据集/租借脱敏导出面板、Playwright 桌面/390px smoke 和 DOM secretRef 扫描。 |
| `db/migration/wp1` | 后续新增 WP8 数据集、账号池、租借和任务表。 |
| `scripts` | 已新增 WP8 quality gate、并发租借 managed smoke 和前端 smoke；release 模式要求显式启并发 smoke。 |

## 6. 五角色启动交付

| 角色 | 本轮交付 | 结论 |
|---|---|---|
| 资深项目经理 | 本启动准备和研发任务拆解，冻结目标、范围、依赖、里程碑、风险和回滚 | 通过 |
| 资深产品经理 | WP8 需求文档与 PRD，定义用户价值、业务流程和验收标准 | 通过 |
| 资深服务端架构师 | 技术设计与接口契约，定义 DB、状态机、API、SecretProvider 和跨 WP 集成 | 通过 |
| 资深前端工程师 | 前端页面设计，定义入口、页面、状态、权限和可测性 | 通过 |
| 资深质量工程师 | 测试策略与用例脚本，定义测试矩阵、并发 smoke、DB validation 和准出 | 通过 |

## 7. 里程碑

| 里程碑 | 目标 | 主要交付物 | 准出标准 |
|---|---|---|---|
| M0 启动准备 | 文档、范围、任务拆解冻结 | 6 份 WP8 启动文档 | 五角色评审无阻断 |
| M1 基础控制面 | 权限、DB、模块骨架、health | `testdata` 模块、权限 seed、schema | OpenAPI contract、DB validation 通过 |
| M2 数据集 | 数据集 CRUD、记录摘要、清理策略 | data set API、record summary | 敏感字段不泄露 |
| M3 账号池 | 账号池、账号摘要、secretRef digest | account pool API | secretRef 不回显 |
| M4 租借和清理 | 租借、续租、释放、过期、清理任务 | lease API、cleanup task | 并发租借唯一、幂等 |
| M5 跨 WP 引用 | WP7/WP9/WP10 引用契约 | adapter port、contract docs | 不直连跨 WP 表 |
| M6 前端闭环 | 工作台完成主链路 | portal-web 页面 | Vitest、Playwright smoke 通过 |
| M7 准出门禁 | quality gate、DB validation、并发 smoke | `scripts/wp8_quality_gate.sh` | release gate 明确 |

M8B/M8C 当前推进说明：账号池、租借和清理任务后端切片已按 `platform-api` API 落地，跨 WP 引用契约已通过 `TestDataCrossWpReferenceService` 落成应用层切片。`portal-web` 已新增 `#test-data` 工作台基础闭环，覆盖 API client、权限入口、数据集/账号池/租借/清理任务基础面板、数据集脱敏导出摘要、租借脱敏导出摘要、traceId 错误展示和 secretRef 不回显；已补齐 `scripts/wp8_frontend_e2e_smoke.sh`、`scripts/wp8_account_lease_concurrency_smoke.sh`、`scripts/wp8_quality_gate.sh` 和桌面/390px Playwright smoke。当前已新增前端操作说明和运维 Runbook，覆盖用户浏览器主链路、租借卡死、账号锁定、SecretRef 轮换、清理失败和脱敏导出异常排障。真实 cleanup worker 和导出文件下载仍按后续任务推进。

## 8. 启动准入清单

| 检查项 | 要求 | 状态 |
|---|---|---|
| 需求范围 | 只做测试数据与账号池控制面，不做 WP7/WP9/WP10 职责 | 通过 |
| 安全边界 | 不保存 secret 明文、token、cookie、生产数据原文和完整外部引用 | 通过 |
| 输入资产 | WP1 项目/应用/环境、WP3 资产、WP7/WP9 引用场景可复用 | 通过 |
| 权限审计 | 新增 testData 权限点和审计事件必须入 DB seed、权限测试和文档 | 已纳入 WP8-1.x、WP8-7.x |
| 并发一致性 | 账号租借必须以 DB 条件更新或唯一约束保证 | 已纳入 WP8-4.2 |
| 验证入口 | 后续必须提供后端、前端、DB validation、并发 smoke 和 quality gate | 已纳入 WP8-7.x |

## 9. 风险和回滚

| 风险 | 处置 | 回滚方式 |
|---|---|---|
| 账号被重复租借 | DB 条件更新和 active lease 唯一约束，release gate 跑并发 smoke | 暂停租借入口，锁定冲突账号，人工释放 lease |
| secretRef 泄露 | 响应白名单、审计白名单、前端 DOM smoke 和导出脱敏检查 | 回滚导出或账号详情接口，轮换受影响 secret |
| 清理误删业务数据 | P0 默认不执行破坏性清理，清理 adapter 必须 allowlist 和人工确认 | 关闭 cleanup-enabled，保留任务记录 |
| 账号长期锁定 | 过期回收、人工复核和清理失败重试 | 手动撤销 lease，账号转 AVAILABLE 或 DISABLED |
| 与 WP7/WP9 范围混淆 | WP8 只提供引用和 lease，不执行浏览器或 DAG | 隐藏跨 WP 操作入口，保留只读引用 |
| 数据集含敏感原文 | record summary 限制、敏感字段校验和导出白名单 | 归档数据集，删除违规摘要并保留审计 |

## 10. 回滚方式

1. 文档阶段回滚本组 WP8 文档和 README 索引即可，无运行时影响。
2. 后续代码阶段优先通过配置关闭 `veri-agent.test-data.enabled`、`cleanup-enabled` 或隐藏前端入口。
3. 数据库迁移遵循前滚修复优先，生产环境不做破坏性 drop。
4. 已产生的租借、释放、清理和导出审计保留，不直接删除证据。
5. 若跨 WP 引用异常，WP7/WP9 回退到手工传入账号或数据引用，不影响 WP8 只读查询。

## 11. 验收标准

1. 五角色文档均完成且口径一致。
2. PRD、技术契约、前端设计、测试策略和任务拆解互相引用的范围一致。
3. 明确目标、范围、非目标、跨 WP 依赖、权限、审计、安全、验证入口和回滚方式。
4. 启动文档阶段只引入文档和 README 索引；WP8 M1 推进阶段允许引入权限、DB foundation、运行时配置、health API、OpenAPI contract 和最小测试，不引入数据集 CRUD、账号池 CRUD、租借执行或清理 worker。
5. M1 变更必须通过 `mvn -B -pl platform-api -Dtest=TestDataHealthControllerTest,OpenApiContractTest,PermissionCodeUsageTest test`、`bash scripts/platform_api_java_line_guard.sh`、`bash db/validation/run_wp1_db_validation.sh` 和 `git diff --check`。
