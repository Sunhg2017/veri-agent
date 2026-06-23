# WP8 测试数据与账号池 - 需求文档与 PRD

| 项目 | 内容 |
|---|---|
| 工作包 | WP8 测试数据与账号池 |
| 角色产出 | 资深产品经理 |
| 文档性质 | 需求文档、产品边界和验收标准 |
| 当前口径 | `platform-api` 承载 WP8 控制面；账号密钥只保存 `secretRef`；不直接复制生产数据、不自动接管被测系统账号生命周期 |
| 版本 | v0.1 |
| 日期 | 2026-06-15 |

## 1. 背景

WP6 已具备 OpenAPI 接口自动化能力，WP9 已具备执行编排和任务调度控制面。WP7 UI/E2E 需要稳定的登录账号、角色矩阵和可恢复测试数据，WP9 执行计划也需要引用可审计的数据集和账号租借结果。WP8 的目标是在平台内建立可治理、可租借、可清理、可审计的测试数据与账号池控制面，降低 UI/E2E、接口批量回归和多角色权限验证中的手工准备成本。

当前实现基线是单平台、单 `platform-api` 服务，不建设多租户。WP8 必须复用 WP1 的项目、应用、环境、RBAC、SecretProvider、审计和 traceId，不直接读写跨 WP 表。

## 2. 用户和价值

| 用户 | 核心诉求 |
|---|---|
| 测试工程师 | 维护项目内可复用的数据集和账号池，执行前快速租借，执行后可释放和清理。 |
| 自动化工程师 | 在 WP6/WP7/WP9 脚本或执行计划中引用稳定的 `dataSetRef`、`accountLeaseRef`，避免硬编码账号和数据。 |
| 项目负责人 | 控制哪些项目、应用、环境可使用哪些数据和账号，并审计租借、释放、清理和失败记录。 |
| 安全/审计人员 | 确认平台不保存账号密码明文、不导出敏感数据、不对生产数据做破坏性清理。 |

## 3. 目标

1. 支持按项目、应用、环境管理测试数据集，记录数据结构摘要、字段敏感级别、生成方式和清理策略。
2. 支持账号池管理，账号由被测应用提供或人工录入引用，平台只保存账号标识、角色矩阵和 `secretRef`。
3. 支持账号租借、锁定、续租、释放和过期回收，保证并发执行时同一账号不会被重复占用。
4. 支持测试数据准备和清理任务控制面，记录幂等 key、执行状态、影响范围、失败摘要和审计证据。
5. 为 WP7/WP9 提供引用契约：`dataSetRef`、`accountPoolRef`、`accountLeaseRef`、`cleanupTaskRef`。
6. 提供前端工作台，让测试工程师无需直接改数据库即可维护数据集、账号池、租借记录和清理任务。

## 4. 范围

| 范围项 | 说明 |
|---|---|
| 数据集目录 | 创建、查询、更新、归档数据集，保存 schema 摘要、字段敏感级别、适用环境和清理策略。 |
| 数据样本引用 | 保存数据样本摘要、外部数据位置引用或生成参数摘要；不保存生产敏感原文。 |
| 模拟数据生成 | 对 `GENERATED` 数据集按 schema 自动构造脱敏记录摘要，降低仅支持外部导入的使用门槛。 |
| 账号池目录 | 按项目、应用、环境、角色管理账号池，支持账号状态、标签、并发限制和健康状态。 |
| 密钥引用 | 账号密码、token、cookie 等凭据只通过 `secretRef` 引用，不在 WP8 表或前端响应中展示明文。 |
| 租借状态机 | 支持 `AVAILABLE/LEASED/LOCKED/EXPIRED/DISABLED` 和租借记录 `ACTIVE/RELEASED/EXPIRED/REVOKED`。 |
| 清理任务 | 支持数据准备、数据回滚、账号释放后的清理任务记录和幂等重试。 |
| 权限和审计 | 新增 `testData:read/manage/lease/cleanup/export` 权限点，所有变更、租借、释放和导出写审计。 |
| 前端工作台 | 数据集、账号池、租借记录、清理任务和策略摘要的可视化管理。 |
| 跨 WP 引用 | WP7/WP9 只通过稳定引用使用账号和数据，不直接读取 secret 或完整数据正文。 |

## 5. 非目标

| 非目标 | 原因 | 后续承接 |
|---|---|---|
| 自动创建真实业务系统账号 | 不同被测系统注册、审批、验证码和权限模型差异大，首期风险高。 | 具体应用接入适配器 |
| 复制生产数据库或生产用户数据 | 涉及合规、安全和脱敏治理，不适合作为 P0。 | 数据治理专项 |
| 执行 UI/E2E 脚本 | WP8 只提供账号和数据引用，不运行浏览器自动化。 | WP7 |
| 调度任务 DAG | WP8 记录准备/清理任务，执行编排由 WP9 承接。 | WP9 |
| 报告诊断和缺陷归因 | WP8 只提供准备和租借证据，不生成完整报告。 | WP10 |
| 保存密码、token、cookie 明文 | 违反安全边界；只能保存 `secretRef` 和 digest。 | 不做 |

## 6. 关键业务对象

| 对象 | 说明 |
|---|---|
| 数据集 `TestDataSet` | 某项目/应用/环境可复用的一组测试数据定义和样本摘要。 |
| 数据记录 `TestDataRecord` | 数据集内的单条记录摘要或外部数据引用，含敏感字段标记。 |
| 数据任务 `TestDataTask` | 准备、刷新、清理或回滚数据的任务记录。 |
| 账号池 `AccountPool` | 某项目/应用/环境的一组可租借账号，按角色和标签管理。 |
| 账号 `PooledAccount` | 账号标识、角色、状态、健康摘要和 `secretRef`。 |
| 租借 `AccountLease` | 一次执行或人工操作对账号的占用记录，含过期时间、持有者和释放结果。 |
| 账号矩阵 `AccountRoleMatrix` | 用于权限场景的账号角色、菜单、资源作用域和适用测试场景。 |

## 7. 主流程

### 7.1 数据集准备

1. 测试工程师选择项目、应用和环境。
2. 新建数据集，填写名称、用途、字段 schema、敏感字段和清理策略。
3. 上传或录入样本摘要，或填写外部数据引用。
4. 若数据集 `sourceType=GENERATED`，用户可按 schema 自动生成一批模拟记录摘要。
5. 平台校验字段敏感标记、项目 scope、生成数量和数据大小限制。
6. 数据集进入 `READY` 后可被 WP6/WP7/WP9 引用。

### 7.2 账号池维护

1. 测试工程师创建账号池，绑定项目、应用、环境和角色用途。
2. 新增账号时只填写账号标识、展示名、角色标签和 `secretRef`。
3. 平台对 `secretRef` 做格式校验和 digest 展示，不解析或回显明文。
4. 账号健康检查首期只记录人工或外部脚本写入结果，不强制登录真实系统。

### 7.3 执行前租借

1. WP7/WP9 或用户提交租借请求，包含项目、应用、环境、角色、标签、执行 ref 和 TTL。
2. 后端按状态、角色、标签和并发限制选择可用账号。
3. 租借成功后返回 `accountLeaseRef`、账号摘要和 `secretRef` digest，不返回凭据明文。
4. 同一账号在 active lease 未释放前不可被其他运行重复租借，除非账号池明确允许只读共享。

### 7.4 释放和清理

1. 执行完成或用户手动释放账号租借。
2. 平台记录释放结果和需要清理的数据/账号副作用。
3. 清理任务按幂等 key 创建，可由 WP9 调度或人工触发。
4. 任务完成后账号回到 `AVAILABLE`，失败则进入 `LOCKED` 或 `NEEDS_REVIEW`。

## 8. 权限模型

| 权限 | 产品语义 |
|---|---|
| `testData:read` | 查看数据集、账号池、租借和清理任务摘要。 |
| `testData:manage` | 创建、编辑、归档数据集和账号池，维护账号摘要。 |
| `testData:lease` | 申请、续租、释放账号和数据引用。 |
| `testData:cleanup` | 创建、执行、重试或确认清理任务。 |
| `testData:export` | 导出脱敏数据集、租借和清理审计摘要。 |

默认建议：

- `SuperAdmin` 和平台管理员拥有全部权限。
- `ProjectOwner`、`AppOwner`、`Tester` 拥有 read/lease；项目负责人和应用负责人可拥有 manage/cleanup。
- `Auditor` 只拥有 read/export 的脱敏视图。

## 9. 状态和边界

| 对象 | 状态 |
|---|---|
| 数据集 | `DRAFT/READY/DISABLED/ARCHIVED` |
| 数据记录 | `ACTIVE/MASKED/INVALID/ARCHIVED` |
| 数据任务 | `PENDING/RUNNING/SUCCEEDED/FAILED/CANCELED` |
| 账号 | `AVAILABLE/LEASED/LOCKED/EXPIRED/DISABLED/ARCHIVED` |
| 租借 | `ACTIVE/RELEASED/EXPIRED/REVOKED` |

边界规则：

1. 非 `READY` 数据集不可被新执行引用。
2. `DISABLED/ARCHIVED` 账号不可新租借，已有租借只能释放或撤销。
3. 清理任务失败后账号默认不回到可用态，必须人工确认或重试成功。
4. `secretRef` 只能新增或替换，不允许前端读取原值。
5. 导出只能包含摘要、digest、状态、时间、操作者和 traceId。

## 10. 产品验收标准

1. 用户可以在项目内维护数据集和账号池，并按应用、环境、角色筛选。
2. 用户可以申请租借指定角色账号，重复租借同一账号被并发保护。
3. 释放、过期和清理任务均有稳定状态和可追踪审计。
4. 前端无权限时隐藏入口和操作按钮，直达路由展示无权限态。
5. 任何响应、导出、审计 payload 均不包含账号密码、token、cookie 或敏感数据原文。
6. WP7/WP9 可通过引用字段使用 WP8 能力，不需要直连 WP8 表。
7. P0 验证包含后端测试、前端测试、构建、DB validation 和 WP8 quality gate。

## 11. 当前产品口径

M2 已推进数据集控制面后端切片：测试工程师可通过 API 创建、查询、更新、归档数据集，并导入脱敏记录摘要。产品边界如下：

1. 数据集必须绑定项目 scope；项目角色未带 `projectId` 查询时按平台范围处理并拒绝。
2. schema 字段名和类型会校验并归一化，记录导入只接受 digest、masked summary、external ref digest 和 tags。
3. 清理策略仅保存摘要，不触发真实清理动作。
4. `ARCHIVED` 只能通过归档接口进入，归档后禁止修改和继续导入。

M3 已推进账号池控制面后端切片：测试工程师可通过 API 创建、查询、更新、禁用和归档账号池，并维护账号摘要。产品边界如下：

1. 账号池必须绑定项目 scope，支持按应用、环境、状态和关键词筛选。
2. 账号只保存 `accountKey/displayName/status/roleTags/scopeSummary/healthSummary` 和 `secretRefDigest`，API 响应和审计不回显 `secretRef` 原文。
3. 新增或替换 `secretRef` 时，服务端只计算 SHA-256 digest；当前切片不解析 SecretProvider、不保存密文值。
4. 账号池 `ARCHIVED` 只能通过归档接口进入；`DISABLED/ARCHIVED` 账号池禁止新增账号。
5. 账号摘要支持 `AVAILABLE/LOCKED/DISABLED/ARCHIVED` 的人工维护状态；`LEASED/EXPIRED` 仍由 M4 租借流程维护。
6. 本轮不包含账号租借、续租、释放、过期回收、清理 worker、脱敏导出、跨 WP adapter 和前端工作台；这些仍按 M4-M6 验收。

M4 已推进租借、释放和清理任务后端切片：测试工程师或后续 WP7/WP9 adapter 可通过 API 申请、续租、释放账号租借，并创建清理任务控制面记录。产品边界如下：

1. 租借请求必须绑定项目 scope、账号池、holder 和 `requestKey`；同一 `projectId + requestKey` 重复请求返回同一租借结果。
2. 租借只选择 `READY` 账号池中的 `AVAILABLE` 账号，并按角色标签匹配；服务端通过条件更新和 active lease 唯一约束防止同一账号并发重复占用。
3. 租借响应只返回账号摘要、`secretRefDigest` 和 `leaseTokenDigest`，不返回账号凭据、租借 token 明文或 `secret://` 原文。
4. active 租借可续租，TTL 受平台最大值限制；释放后账号默认回到 `AVAILABLE`，也可按失败策略转入 `LOCKED`。
5. 过期回收、待处理任务执行和账号健康检查已由 `platform-api` 内置受控 worker 管理；health 中 `cleanupWorkerReady/taskExecutionWorkerReady/leaseRecoveryWorkerReady/accountHealthCheckWorkerReady=true`，但该 worker 仅运行控制面状态推进，不调用破坏性清理 adapter。
6. 清理任务 API 仍默认以控制面记录和审计为主；`cleanupEnabled=false` 时直接返回 `CLEANUP_TASK_NOT_ALLOWED`，即使显式打开开关，当前也只确认 adapter 尚未准出，不执行真实破坏性清理。

M5 已推进跨 WP 引用契约后端切片：WP9 可通过 `TestDataCrossWpReferenceService` 申请和释放账号租借引用，WP7 可通过同一服务读取账号摘要和 `secretRefDigest`，WP10 可读取准备、租借和清理证据。产品边界如下：

1. WP9 只保留 `accountLeaseRef` 和脱敏租借摘要，不保存账号密码或 `secret://` 原文。
2. WP7 runner 只读取账号摘要、角色、状态和 `secretRefDigest`，不接收密码、token 或 cookie 明文。
3. WP10 只读取准备、租借和清理的引用、状态、计数和 digest，不展示数据正文或清理 payload。
4. `secretRefDigest` 仅用于审计、比对和脱敏展示，不是可解析凭据；后续真实 runner 凭据注入必须通过受控 SecretProvider adapter，以 `accountLeaseRef` 为句柄完成。
5. 账号 `scopeSummary` 只能包含项目、应用、环境、角色、菜单或资源范围等非敏感摘要，不得透传 secret、token、cookie、密码或业务数据正文。
6. 这些契约仍在 `platform-api` 应用层完成，不对外新增跨 WP 公共 HTTP 入口。

M6A 已推进前端工作台基础闭环：测试工程师可通过 `#test-data` 入口在 `portal-web` 维护数据集、账号池、账号摘要、租借和清理任务控制面。产品边界如下：

1. `testData:read` 控制入口可见性和直达访问，`testData:manage/lease/cleanup/export` 分别控制维护、租借、清理和导出操作显隐。
2. 前端 `testData` API helper 兼容后端 camelCase/snake_case 响应，并对 `schema/cleanupPolicy/maskedSummary/scopeSummary/resultSummary` 做敏感键过滤，保留 digest 字段。
3. 账号 `secretRef` 只在新增或替换账号摘要时作为写入输入；保存成功后前端表单清空，列表、详情、状态提示和摘要只展示 `secretRefDigest`。
4. 工作台已覆盖四个基础面板的 loading/empty/error、traceId 展示和 `cleanupEnabled=false` 解释。
5. M6B 已补 Playwright 桌面/390px smoke、DOM secretRef 原文扫描脚本和 WP8 聚合 quality gate；M6C 已补数据集脱敏导出结果面板，M6D 已补租借脱敏导出结果面板。分页筛选增强、导出文件下载和真实 cleanup worker 仍是后续范围。

M6C 已推进数据集脱敏导出摘要：测试工程师可通过 `#test-data` 工作台的数据集 tab 点击“导出摘要”，查看 schema version、记录/字段/敏感字段计数、redaction policy、record digest、tags 和 `maskedSummaryKeys`。产品边界如下：

1. 导出入口同时受 `testData:export` 权限和 `veri-agent.test-data.export-enabled` 控制。
2. 导出摘要不展示 maskedSummary 值、完整记录正文、`secretRef` 原文、token、cookie 或 Authorization header。
3. 当前不提供文件下载，不导出租借摘要或清理审计摘要；这些能力按后续增强独立准出。

M6E 已推进模拟数据生成：测试工程师可对 `sourceType=GENERATED` 的数据集通过 `POST /api/v1/test-data/data-sets/{id}/generate-records` 或 `#test-data` 工作台“自动造数”入口，按 schema 自动生成一批脱敏记录摘要。产品边界如下：

1. 仅 `GENERATED` 数据集支持自动造数；`MANUAL/EXTERNAL_REF` 仍通过记录摘要导入维护数据。
2. 生成结果只保存 `recordKey/recordDigest/maskedSummary/tags` 等脱敏摘要，不保存完整 payload、敏感原文或 secret 引用明文。
3. 生成逻辑当前基于 schema 字段类型使用规则化样本值，不调用外部模型、第三方造数服务或真实业务系统。
4. 生成数量仍受 `record-max-count`、单条摘要大小和数据集状态限制；`ARCHIVED` 或非可写状态数据集不可生成。

M6D 已推进租借脱敏导出摘要：测试工程师可通过 `#test-data` 工作台的租借 tab 点击“导出摘要”，查看 schema version、租借状态、holder、账号摘要、账号池摘要、`leaseTokenDigest`、`requestDigest`、释放原因 digest、健康摘要 digest、过滤后的安全 key 名和 redaction policy。产品边界如下：

1. 导出入口同时受 `testData:export` 权限和 `veri-agent.test-data.export-enabled` 控制。
2. 导出摘要不展示 secretRef 原文、租借 token 明文、释放原因原文、账号健康摘要原文、scopeSummary 值、leasePolicy 值、token、cookie 或 Authorization header。
3. 当前不提供文件下载，不触发 cleanup worker，不接入 WP7 真实执行器；WP9 调度自动申请/释放已由 WP9 工作包通过 M5 应用层契约接入。

M8B/M8C 已推进操作说明与运维 Runbook：用户可按 `WP8-测试数据与账号池-前端操作说明.md` 在浏览器内完成数据集、账号池、账号摘要、租借、释放、清理任务和脱敏导出摘要主链路；运维可按 `WP8-测试数据与账号池-运维Runbook.md` 处理租借卡死、账号锁定、SecretRef 轮换、清理失败和脱敏导出异常。产品边界如下：

1. 操作说明只描述当前 `#test-data` 工作台已落地能力，不承诺筛选栏、分页、详情抽屉、真实文件下载或真实 cleanup worker。
2. Runbook 坚持 `cleanup-enabled=false` 默认安全边界，真实清理 adapter 必须后续专项准出。
3. 前端按钮显隐只做体验控制，最终准入仍由后端权限、项目 scope、对象状态和 WP8 开关决定。

M8I 已推进发布准出收口：产品验收口径确认当前 WP8 范围无剩余 P0 功能开发项，用户主链路已由 `#test-data` 工作台、前端操作说明、运维 Runbook、发布准出说明和剩余工作盘点形成闭环。产品边界如下：

1. 当前发布范围覆盖数据集、账号池、租借、释放、清理任务控制面、两类脱敏导出摘要、跨 WP 引用契约和前端工作台主链路。
2. 当前发布范围不承诺真实文件下载、真实 cleanup worker、真实账号自动开通或外部容量压测；WP7 runner 凭据注入、WP9 调度自动申请/释放和 WP10 报告证据消费已属于对应 WP 对 WP8 应用层契约的已接入能力，不改变 WP8 账号池控制面边界。
3. 后续增强必须重新补充 PRD、技术设计、测试策略和发布准出说明，不得在当前 M8I 口径下直接扩大范围。
