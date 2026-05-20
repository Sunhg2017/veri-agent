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

## 4. WP1 平台基础底座待补任务

当前 WP1 P0 已可作为后续 WP 底座，待补任务以生产化和治理能力为主。

### WP1-A 生产数据库角色与发布校验

| 编号 | 任务 | 优先级 | 状态 | 工作内容 | 验收标准 |
|---|---|---|---|---|---|
| WP1-A1 | 接入真实预发/生产应用数据库角色 | P0-B | IN_PROGRESS | 将 `scripts/wp1_release_role_validation.sh` 的执行参数纳入预发/生产发布流程；明确真实 app/migration/readonly 账号名 | 在预发库执行 release role validation，应用账号无 DDL、无审计 UPDATE/DELETE/TRUNCATE 权限 |
| WP1-A2 | 固化发布前 DB 权限 runbook | P0-B | DONE-CURRENT | 已新增 `doc/mvp/final/engineering/WP1-发布前DB权限Runbook.md`，补充环境变量、连接串、执行时机、失败处理和 DBA 复核说明 | 发布负责人可按文档复现检查，失败项有修复建议 |
| WP1-A3 | CI/发布流水线挂载 DB 权限检查 | P1 | DONE-CURRENT | `.github/workflows/wp1-database-validation.yml` 已挂载临时库 migration/validation 并归档日志；`WP1-发布前DB权限Runbook.md` 补充预发/生产真实角色 validation 挂载口径 | 临时库 CI 每次跑，预发/生产按发布窗口执行真实角色 validation，并产出日志归档 |

### WP1-B 角色定义与权限治理

| 编号 | 任务 | 优先级 | 状态 | 工作内容 | 验收标准 |
|---|---|---|---|---|---|
| WP1-B1 | 自定义角色模型冻结 | P1 | TODO | 明确角色编码、作用域、是否可编辑、权限点集合、版本号和越权授权规则 | 自定义角色不能授予超过操作者自身范围的权限 |
| WP1-B2 | 角色定义管理 API | P1 | TODO | 新增角色列表、详情、创建、编辑、启停、权限配置接口 | API 接入 RBAC、审计、OpenAPI 契约和权限矩阵测试 |
| WP1-B3 | 角色管理前端页面 | P1 | TODO | 在管理台增加角色列表、权限点编辑、角色绑定影响提示 | 非授权用户不可见或不可操作，权限点展示清晰 |
| WP1-B4 | 权限变更失效自动化 | P1 | TODO | 覆盖角色权限变化、角色解绑、用户角色变更后的 `auth_version` 或缓存失效 | 旧 token/旧权限摘要不能继续执行新增禁止操作 |

### WP1-C 审计导出、保留和 outbox 运维

| 编号 | 任务 | 优先级 | 状态 | 工作内容 | 验收标准 |
|---|---|---|---|---|---|
| WP1-C1 | 审计导出任务 API | P1 | TODO | 支持按当前筛选条件创建 CSV 导出任务，导出自身写审计 | 导出文件不含敏感明文，导出条件和结果可追踪 |
| WP1-C2 | 审计导出前端入口 | P1 | TODO | 审计列表增加导出按钮、任务状态、下载或对象存储引用 | 无导出权限不可操作，导出失败有可读错误 |
| WP1-C3 | 审计保留策略 | P1 | TODO | 定义默认保留周期、归档策略、清理任务和配置项 | 清理不破坏业务对象追溯和合规查询 |
| WP1-C4 | Audit outbox 运维视图 | P1 | TODO | 展示待补偿、重试中、失败审计事件；支持按 traceId 查询 | 审计写失败可观测，重试不重复写业务审计 |

### WP1-D 会话、状态流与运行上下文增强

| 编号 | 任务 | 优先级 | 状态 | 工作内容 | 验收标准 |
|---|---|---|---|---|---|
| WP1-D1 | Redis 会话清理或 DB 会话清理任务 | P1 | TODO | 明确当前 profile 的会话存储策略，补过期清理、指标和脚本 | 长期运行不会积累过期会话；清理行为不影响有效会话 |
| WP1-D2 | 复杂状态流拒绝测试 | P1 | TODO | 扩展部门、用户、项目、应用、环境的重复、逆向和非法状态流测试 | 非法状态变更返回稳定错误码并写拒绝审计 |
| WP1-D3 | 环境连通性检查 | P1 | TODO | 对环境 webUrl/apiBaseUrl 增加可配置探活和最近健康结果 | 停用环境不可执行；探活失败不泄露内部错误 |
| WP1-D4 | Secret 引用写入和轮换管理 | P1 | TODO | 对 Secret 引用补创建/轮换/禁用/摘要读取 API 与前端入口 | 明文不入库、不回显、不进审计；轮换后旧引用按策略失效 |

### WP1-E 企业身份与审批预留

| 编号 | 任务 | 优先级 | 状态 | 工作内容 | 验收标准 |
|---|---|---|---|---|---|
| WP1-E1 | SSO/LDAP/企业组织同步方案 | P2 | TODO | 输出身份源模型、外部用户 ID、部门同步冲突策略 | 不影响当前本地账号登录；后续可按方案接入 |
| WP1-E2 | 项目/权限申请审批预留 | P2 | TODO | 定义审批对象、审批状态和与角色绑定的关系 | 当前授权链路不被阻塞，后续审批可接入同一审计体系 |

## 5. WP2 模型接入层待补任务

当前 WP2 P0 后端能力和门禁已较完整，后续重点是前端治理、生产 provider 运维和模型质量体系。

### WP2-A 模型接入管理前端

| 编号 | 任务 | 优先级 | 状态 | 工作内容 | 验收标准 |
|---|---|---|---|---|---|
| WP2-A1 | 模型供应商管理页面 | P1 | TODO | 供应商列表、创建、编辑、启停、就绪检查、成本配置 | 前端使用现有 `/api/v1/model-access/providers` 契约，密钥只填 secretRef |
| WP2-A2 | Prompt 版本管理页面 | P1 | TODO | Prompt key 列表、版本详情、新建版本、激活、diff 展示 | 每个 promptKey 只有一个 ACTIVE 版本；激活写审计 |
| WP2-A3 | 调用日志与成本页面 | P1 | TODO | 日志筛选、summary、CSV 导出、成本日报/告警展示 | 日志不展示 prompt 明文和敏感内容；导出权限受控 |

### WP2-B Provider 生产硬化

| 编号 | 任务 | 优先级 | 状态 | 工作内容 | 验收标准 |
|---|---|---|---|---|---|
| WP2-B1 | Provider 级限流和并发控制 | P1 | TODO | 对供应商、项目、调用服务增加请求速率和并发保护 | 超限返回稳定错误码并记录 BLOCKED 调用日志 |
| WP2-B2 | 熔断状态观测与手动恢复 | P1 | TODO | 暴露 provider 熔断状态、失败窗口、恢复时间和人工 reset 操作 | 运维可判断当前 provider 是否被短时熔断 |
| WP2-B3 | 外部 provider runbook | P1 | DONE-CURRENT | 已新增 `doc/mvp/final/engineering/WP2-Provider接入与SecretRef轮换Runbook.md`，覆盖 OpenAI-compatible、私有模型、代理网关的配置、探活和故障处理 | 新 provider 接入不需要阅读源码 |
| WP2-B4 | SecretRef 轮换流程 | P1 | DONE-CURRENT | `WP2-Provider接入与SecretRef轮换Runbook.md` 已说明当前 `apiKeyRef=env:VARIABLE_NAME` 口径和后续 SecretProvider 对齐的双引用轮换流程 | 轮换期间不中断可用 provider，旧 secretRef/apiKeyRef 可控失效 |

### WP2-C 策略、预算和合规模型

| 编号 | 任务 | 优先级 | 状态 | 工作内容 | 验收标准 |
|---|---|---|---|---|---|
| WP2-C1 | 高级路由策略 | P1 | TODO | 支持按项目、敏感级别、调用服务、模型能力和成本选择供应商组 | 高敏资源仍不能路由公开模型；路由结果可审计 |
| WP2-C2 | 预算策略产品化 | P1 | TODO | 平台/项目/调用服务预算，告警阈值，冻结策略 | 超预算前可告警，超预算后阻断或降级行为可配置 |
| WP2-C3 | 敏感内容检测扩展 | P1 | TODO | 扩展手机号、邮箱、银行卡、企业内部密钥模式和自定义正则 | 命中后阻断或脱敏策略可配置且被测试覆盖 |
| WP2-C4 | Prompt 评审与审批 | P2 | TODO | 高风险 Prompt 激活前审批，保留审批人与版本说明 | Prompt 变更可追溯，回滚到旧版本有明确操作 |

### WP2-D 模型质量和异步能力

| 编号 | 任务 | 优先级 | 状态 | 工作内容 | 验收标准 |
|---|---|---|---|---|---|
| WP2-D1 | 通用模型评测集框架 | P1 | TODO | 抽象类似 WP4 golden corpus 的可复用评测入口 | Prompt 或 provider 变更可按任务类型跑评测 |
| WP2-D2 | 流式响应支持 | P2 | TODO | 评估 SSE/streaming API、调用日志落盘和前端消费 | 不破坏当前同步 invocation 契约 |
| WP2-D3 | 异步长任务调用 | P2 | TODO | 对长文档、长推理任务提供异步 job 模型 | 任务可取消、可查询、可审计 |

## 6. WP3 资产管理待补任务

WP3 是当前最需要补齐的工作包。后端已有需求、API、页面、业务流、测试用例、步骤和追踪链接基础 API，但还缺完整产品、契约和准出闭环。

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
| WP3-B3 | 版本、历史和 diff | P1 | TODO | 保存版本号、变更历史、字段 diff、变更人 | 需求和测试用例可回看历史版本 |
| WP3-B4 | 软删除、归档和恢复策略 | P1 | TODO | 定义删除/归档、引用保护、恢复和唯一性释放规则 | 删除不破坏 trace link 和审计追溯 |
| WP3-B5 | 导入/导出能力 | P1 | TODO | 支持需求、API、测试用例 CSV/JSON/OpenAPI 导入导出 | 导出脱敏，导入有 dryRun 和错误明细 |
| WP3-B6 | API 资产 OpenAPI 导入 | P1 | TODO | 从 OpenAPI 文档导入接口路径、方法、schema 和版本 | 重复导入幂等更新，不重复创建同一接口 |
| WP3-B7 | 页面资产原型输入预留 | P2 | TODO | 为 Figma/蓝湖/Axure 接入保留 sourceRef、componentTree、截图和版本映射 | 不实现真实连接器也不阻碍后续接入 |

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
| WP3-D2 | 需求资产页面 | P0-B | DONE-CURRENT | 已实现需求列表、详情、创建、编辑、状态流入口和来源追踪展示 | WP4 发布的 IMPORT 需求可在页面查看 source/sourceRef/sourceUrl |
| WP3-D3 | API 资产页面 | P1 | DONE-CURRENT | portal-web 已开放 API 资产页，支持列表、详情、创建、编辑、`method/path/status/source/keyword` 筛选、schema 展示和 OpenAPI 导入入口预留 | 接口路径和方法可筛选；创建/编辑走现有 WP3 API 资产契约，重复创建由后端唯一性约束阻断 |
| WP3-D4 | 页面和业务流页面 | P1 | TODO | 页面资产、业务流资产的 CRUD 和可读结构展示 | JSON 字段展示不撑破布局，可编辑可校验 |
| WP3-D5 | 测试用例与步骤页面 | P1 | TODO | 用例列表、详情、步骤编辑、关联需求/API | 步骤顺序稳定，保存失败不丢本地编辑 |
| WP3-D6 | 追踪矩阵和影响分析 | P1 | TODO | 展示 requirement-api-case 覆盖矩阵、缺口和影响范围 | 可按需求查看覆盖 API/用例，按 API 查看相关需求/用例 |

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
| WP4-A3 | Webhook 自动重试调度 | P1 | TODO | 对可重试失败事件增加有限自动重试和死信策略 | retryCount、deadLetter、replayTraceId 可查询 |
| WP4-A4 | Webhook 签名样例和联调包 | P1 | DONE-CURRENT | 已新增 `doc/mvp/final/engineering/WP4-Webhook签名样例与联调说明.md`，提供 cURL、Node.js、Java 签名样例和错误排查说明 | 外部系统可按样例完成联调 |

### WP4-B OCR 与二进制解析生产硬化

| 编号 | 任务 | 优先级 | 状态 | 工作内容 | 验收标准 |
|---|---|---|---|---|---|
| WP4-B1 | OCR 隔离 worker 方案 | P1 | IN_PROGRESS | 已补 `ocrWorkerMode` 配置和健康暴露入口；真实 worker/队列/容器隔离仍待专项实现 | OCR 超时、崩溃或高 CPU 不拖垮 platform-api |
| WP4-B2 | 恶意文件扫描 | P1 | TODO | 接入杀毒或文件扫描组件，支持拒绝高危文件 | 被标记恶意文件不进入解析，错误摘要不泄露内部路径 |
| WP4-B3 | 文件类型嗅探和 MIME 校验 | P1 | DONE-CURRENT | `binaryMimeValidationEnabled` 已接入 data URL 声明 MIME 与文件魔数/内容嗅探校验，覆盖 PDF、DOC/DOCX 和常见图片类型；健康接口暴露配置 | 伪造 MIME 被拒绝或按真实类型处理 |
| WP4-B4 | PDF 页数/解析时间限制 | P1 | DONE-CURRENT | `pdfMaxPages/pdfMaxParseMillis` 已在 PDFBox 解析路径生效，并由单测覆盖页数超限和解析耗时超限；健康接口暴露配置 | 超限失败可读，临时文件清理稳定 |
| WP4-B5 | 高保真解析专项 | P2 | TODO | 表格结构、图片语义、页眉页脚、批注、附件抽取专项评估 | 不影响当前文本抽取链路 |

### WP4-C AI 解析质量体系

| 编号 | 任务 | 优先级 | 状态 | 工作内容 | 验收标准 |
|---|---|---|---|---|---|
| WP4-C1 | 扩大 golden corpus | P1 | IN_PROGRESS | 覆盖更多行业、长文档、表格需求、歧义优先级和异常格式 | 样本数量、类型分布和阈值变更有版本记录 |
| WP4-C2 | 按文档类型拆分指标 | P1 | TODO | 对 TEXT/MARKDOWN/WORD/PDF/OCR/CUSTOM_API 分别统计标题召回、优先级、验收标准 | 任一类型低于阈值能定位 |
| WP4-C3 | Prompt 版本和评测绑定 | P1 | TODO | 记录评测使用的 promptKey、promptVersion、规则解析器版本 | Prompt 变更必须跑对应评测 |
| WP4-C4 | 模型解析人工纠错回流 | P2 | TODO | 将人工编辑结果沉淀为后续评测样本或标注数据 | 纠错样本可脱敏后进入 corpus |

### WP4-D 外部连接器扩展

| 编号 | 任务 | 优先级 | 状态 | 工作内容 | 验收标准 |
|---|---|---|---|---|---|
| WP4-D1 | Confluence 真实连接器 | P2 | TODO | OAuth/API 拉取、空间/页面映射、版本和权限策略 | 真实页面可导入，失败可重试，secretRef 不泄露 |
| WP4-D2 | 飞书文档连接器 | P2 | TODO | 飞书开放平台凭证、文档 token、版本、内容转换 | 真实文档可进入候选确认 |
| WP4-D3 | 钉钉文档连接器 | P2 | TODO | 钉钉文档 API、凭证、文档标识和同步任务 | 同步状态和最近错误可在 UI 展示 |
| WP4-D4 | 语雀连接器 | P2 | TODO | 知识库、文档、版本、凭证和同步策略 | 不影响已有 CUSTOM_API 和文件导入 |

### WP4-E SecretProvider 与 Vault/KMS 生产治理

| 编号 | 任务 | 优先级 | 状态 | 工作内容 | 验收标准 |
|---|---|---|---|---|---|
| WP4-E1 | 外部 Vault/KMS provider 健康检查 | P1 | TODO | 对 HTTP resolve endpoint 增加 timeout、retry、健康摘要和告警 | provider 不可用时失败可观测，不回退到 dev/test secret |
| WP4-E2 | mTLS 或签名认证方案 | P1 | TODO | 外部 secret resolve 入口支持更强认证方式 | 认证失败不会泄露 secretRef 是否存在 |
| WP4-E3 | Secret 缓存和轮换策略 | P1 | TODO | 定义 webhook signing secret 的缓存 TTL、主动失效和轮换窗口 | 轮换期间新旧签名策略明确且可测试 |
| WP4-E4 | Secret 解析审计增强 | P1 | TODO | 记录 resolve 成功/失败、provider、用途、作用域，不记录明文 | 安全审计能追踪 secretRef 使用情况 |

### WP4-F 数据保留、清理与前端体验

| 编号 | 任务 | 优先级 | 状态 | 工作内容 | 验收标准 |
|---|---|---|---|---|---|
| WP4-F1 | 原文快照和事件保留策略 | P1 | TODO | 配置导入原文、候选、webhook raw payload、deadLetter 的保留周期 | 清理不破坏已发布资产来源追踪 |
| WP4-F2 | 清理任务与归档 | P1 | TODO | 定时清理或归档过期导入、失败事件、临时数据 | 清理过程写审计和指标 |
| WP4-F3 | 前端 E2E smoke | P1 | TODO | 用浏览器覆盖真实文件上传、候选编辑、发布预览、事件重放 | 核心用户路径可在本地或 CI 复现 |
| WP4-F4 | 解析失败体验优化 | P1 | TODO | 对 OCR 未配置、PDF 无文本、超限、签名失败给出更准确可读提示 | 用户能知道下一步应配置 OCR、换文件或联系管理员 |

## 8. 建议里程碑

| 里程碑 | 目标 | 包含任务 | 准出标准 |
|---|---|---|---|
| M0：补齐 WP3 交付闭环 | 让 WP3 达到 WP1/WP2/WP4 同等级别的可验收形态 | WP3-A、WP3-C、WP3-E | WP3 交付说明、契约测试、quality gate、HTTP smoke 可用 |
| M1：生产准出硬化 | 补齐最容易影响上线安全和运维的缺口 | WP1-A、WP1-C、WP2-B、WP4-A、WP4-B | 预发 release validation、webhook 白名单/限流、OCR 安全策略均有测试或 runbook |
| M2：管理台产品化 | 补齐日常运营和业务使用界面 | WP1-B、WP2-A、WP3-D、WP4-F | 管理员不依赖 curl 完成主要配置和排错 |
| M3：质量与智能化增强 | 建立可长期迭代的质量体系 | WP2-D、WP4-C、WP3 追踪矩阵 | Prompt/解析器变更有评测门禁，资产覆盖率可视化 |
| M4：企业集成扩展 | 接入协作文档和企业身份体系 | WP1-E、WP4-D | 外部系统接入不破坏当前 MVP 链路 |

## 9. 推荐下一步

1. 下一轮优先补 WP3 P1 的版本历史、软删除恢复、导入导出，以及 API/页面/业务流/用例的工作台扩展。
2. 将 `WP4-B1` 从配置/健康入口推进到真实 worker/队列/容器隔离，并继续补 `WP4-B2` 恶意文件扫描。
3. 在下一轮前端迭代中继续合并规划 `WP2-A` 与 WP3 P1 页面，避免管理台导航和权限模型重复调整。
4. 每完成一个任务，补充对应交付说明、测试命令和 release note，并按当前约定提交清晰 commit。
