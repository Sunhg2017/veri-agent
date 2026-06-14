# WP8 测试数据与账号池 - 测试策略与用例脚本

| 项目 | 内容 |
|---|---|
| 工作包 | WP8 测试数据与账号池 |
| 角色产出 | 资深质量工程师 |
| 文档性质 | 测试策略、用例矩阵、脚本门禁和准出要求 |
| 当前口径 | WP8 分 M2-M7 推进；当前已完成 M6A 前端工作台基础闭环、M6B/M7A 浏览器 smoke 与 quality gate 基础脚本，并在 M6C 补齐数据集脱敏导出摘要、M6D 补齐租借脱敏导出摘要；真实清理 worker 和导出文件下载仍按后续里程碑承接 |
| 版本 | v0.1 |
| 日期 | 2026-06-15 |

## 1. 测试目标

1. 验证数据集、数据记录摘要和清理策略按项目、应用、环境隔离。
2. 验证账号池、账号状态、角色标签和 SecretProvider 引用不泄露敏感信息。
3. 验证租借并发安全、幂等、续租、释放、过期回收和锁定策略。
4. 验证清理任务默认安全、失败可追踪、重试幂等。
5. 验证前端权限、状态、错误提示、响应式和脱敏展示。
6. 验证 WP7/WP9 引用契约可用，但不要求 WP7/WP9 同步开发完成。

## 2. 测试范围

| 范围 | 覆盖点 |
|---|---|
| 数据集 | CRUD、归档、schema 校验、敏感字段、记录摘要、外部引用 digest。 |
| 账号池 | CRUD、账号新增/更新、secretRef 替换、角色标签、健康状态。 |
| 租借 | 申请、并发冲突、requestKey 幂等、续租、释放、过期、撤销。 |
| 清理任务 | 创建、状态流、失败、重试、清理开关关闭。 |
| 权限 | read/manage/lease/cleanup/export 分权和项目 scope。 |
| 安全脱敏 | API 响应、审计、日志、前端 DOM、导出不出现 secret 或敏感原文。 |
| 前端 | helper、payload、按钮权限、loading/empty/error、390px 响应式。 |
| DB | 表、约束、索引、状态 check、active lease 唯一约束、runtime role 权限。 |

## 3. Fixture 要求

| Fixture | 用途 |
|---|---|
| `wp8-data-set-minimal.json` | 最小 READY 数据集。 |
| `wp8-data-set-sensitive.json` | 敏感字段脱敏和摘要校验。 |
| `wp8-account-pool-basic.json` | 基础账号池和角色标签。 |
| `wp8-account-secret-ref.json` | secretRef digest 和不回显校验。 |
| `wp8-lease-concurrency.json` | 并发租借同一账号冲突。 |
| `wp8-cleanup-task.json` | 清理任务状态流和失败摘要。 |

## 4. 后端用例矩阵

| 用例 | 预期 |
|---|---|
| 创建合法数据集 | 返回 `READY/DRAFT` 状态，schema 摘要和敏感字段计数正确。 |
| 数据集 code 重复 | 返回稳定冲突错误，不覆盖旧数据集。 |
| 写入敏感记录摘要 | 响应和 DB 可查询摘要不包含敏感原文。 |
| 归档数据集 | 新引用被阻断，历史引用可只读查询。 |
| 创建账号池 | 绑定项目、应用、环境和默认 TTL。 |
| 新增账号 secretRef | 只返回 `secretRefDigest`，不返回 secretRef 原文。 |
| 替换账号 secretRef | 需要 manage 权限，写审计，旧 digest 不泄露。 |
| 申请可用账号 | 生成 active lease，账号变为 `LEASED`。 |
| 相同 requestKey 重试 | payload 相同返回已有租借，payload 不同返回冲突。 |
| 并发租借同一账号 | 只有一个成功，其余返回 `ACCOUNT_NOT_AVAILABLE` 或 `ACCOUNT_LEASE_CONFLICT`。 |
| 续租 active lease | `expiresAt` 延长但不超过最大 TTL。 |
| 释放租借 | lease 变为 `RELEASED`，账号按策略回到 `AVAILABLE` 或 `LOCKED`。 |
| lease 过期回收 | lease 变 `EXPIRED`，账号进入 `LOCKED` 或可配置回收状态。 |
| 清理开关关闭 | 创建任务可成功，但执行动作返回 `CLEANUP_TASK_NOT_ALLOWED`。 |
| 清理任务失败重试 | 失败摘要保留，重试使用相同 target 和新 attempt。 |
| 越权项目访问 | 403，不泄露资源存在性。 |
| 导出摘要 | 只含白名单字段和 redaction policy。 |
| 租借导出摘要 | 只含 holder/status/time/digest、安全 key 名和 redaction policy，不含 secretRef、token 明文、释放原因原文、健康摘要原文或 policy/scope 值。 |

## 5. 前端用例矩阵

| 用例 | 预期 |
|---|---|
| 无 read 权限 | 菜单隐藏，直达路由展示无权限态。 |
| 数据集表单缺字段 | 本地校验阻断，不提交。 |
| 账号 secretRef 编辑 | 旧值不回显，只显示 digest 和替换入口。 |
| 租借 payload | 包含 project/application/environment/pool/roleTags/holder/TTL/requestKey。 |
| 租借冲突 | 展示错误码、traceId 和刷新入口。 |
| 续租超 TTL | 本地阻断并提示最大 TTL。 |
| 释放并创建清理任务 | payload 不包含 secret 或数据正文。 |
| 清理任务失败 | 展示错误摘要和重试/人工确认入口。 |
| 导出面板 | 显示 redaction policy，不展示 secretRef 原文；租借导出只展示 digest、keys 和 presence 标记。 |
| 响应式主链路 | 桌面和 390px 均可完成数据集、账号池、租借、释放和任务查看。 |

## 6. 安全专项

1. API 响应快照检查：不得包含 `password`、`token`、`cookie`、`Authorization`、`secret://` 原文。
2. 审计 payload 检查：只能包含 digest、计数、状态、资源 ID 和错误码。
3. 日志检查：租借、释放、清理失败日志不输出 secretRef 原文或数据记录原文。
4. 前端 DOM 检查：Playwright smoke 中扫描页面文本不包含输入 secretRef。
5. 导出检查：导出摘要带 redaction policy，禁止完整 record payload、maskedSummary 值、secretRef 原文、租借 token 明文、释放原因原文和健康摘要原文。

## 7. 建议验证入口

默认门禁：

```bash
mvn -B -pl platform-api test
```

```bash
cd portal-web && npm test
```

```bash
cd portal-web && npm run build
```

```bash
bash db/validation/run_wp1_db_validation.sh
```

WP8 专项门禁草案：

```bash
bash scripts/wp8_quality_gate.sh
```

说明：`scripts/wp8_quality_gate.sh` 属于 WP8 后续聚合门禁脚本草案；当前仓库尚未落地该脚本时，不作为 M4 后端切片已执行命令。

M1 foundation 准出最小门禁：

```bash
git diff --check
bash scripts/platform_api_java_line_guard.sh
mvn -B -pl platform-api -Dtest=TestDataHealthControllerTest,OpenApiContractTest,PermissionCodeUsageTest test
mvn -B -pl platform-api test
bash db/validation/run_wp1_db_validation.sh
```

M1 只验证权限 seed、DB foundation、运行时 DB 角色授权、审计事件字典配置、health API 和 OpenAPI contract；数据集 CRUD、账号池 CRUD、租借并发、清理 worker、导出和前端主链路按 M2-M6 对应 story 另行准出。

前端 smoke：

```bash
bash scripts/wp8_frontend_e2e_smoke.sh
```

并发租借 smoke 草案：

```bash
bash scripts/wp8_account_lease_concurrency_smoke.sh
```

说明：`scripts/wp8_frontend_e2e_smoke.sh` 已覆盖桌面和 390px 视口的浏览器主链路、DOM secretRef 原文扫描和页面横向溢出检查；`scripts/wp8_account_lease_concurrency_smoke.sh` 当前为本地 managed smoke，复用后端租借冲突和 DB active lease 唯一约束测试，release gate 必须显式启用。

## 8. WP8 Quality Gate 草案

`scripts/wp8_quality_gate.sh` 已串联：

1. 脚本语法检查：`wp8_quality_gate.sh`、`wp8_frontend_e2e_smoke.sh`、`wp8_account_lease_concurrency_smoke.sh`、`platform_api_java_line_guard.sh`。
2. Java 行数门禁：`bash scripts/platform_api_java_line_guard.sh`。
3. 后端专项测试：数据集、账号池、租借并发、TTL、清理任务、权限、OpenAPI contract。
4. 前端专项测试：`testData.test.ts`、`permissions.test.ts` 和脱敏展示 helper。
5. 前端 Playwright smoke：桌面和 390px 主链路，检查 DOM 不含 secretRef 原文。
6. 前端构建：`npm run build`。
7. DB validation：`bash db/validation/run_wp1_db_validation.sh`。

release 模式必须显式执行并发租借 smoke 和脱敏导出检查：

```bash
WP8_GATE_MODE=release WP8_LEASE_CONCURRENCY_SMOKE=managed bash scripts/wp8_quality_gate.sh
```

### M3 账号池控制面最小门禁

当前 M3 后端切片采用以下验证入口作为最小准出：

```bash
mvn -B -pl platform-api -Dtest=TestAccountPoolControllerTest,TestAccountPoolServiceTest,TestDataOpenApiContractTest,TestDataHealthControllerTest,OpenApiContractTest,PermissionCodeUsageTest,PersistenceProfileBoundaryTest test
bash scripts/platform_api_java_line_guard.sh
bash db/validation/run_wp1_db_validation.sh
```

说明：

1. 这组门禁覆盖账号池 CRUD、禁用/归档、账号摘要新增/更新、`secretRef` digest、不回显原文、OpenAPI contract、权限字面量集中和 profile 边界。
2. 真实 DB repository 变更需要配合 `DbProfileRepositoryContractTest` 或全量 `mvn -B -pl platform-api test` 验证 `secret_ref_cipher` 不进入查询投影。
3. 租借并发、续租、释放、过期回收、清理 worker、前端 smoke 和跨 WP adapter 不属于本轮 M3 完成定义。

### M4 租借和清理任务最小门禁

当前 M4 后端切片采用以下验证入口作为最小准出：

```bash
mvn -B -pl platform-api -Dtest=TestAccountLeaseControllerTest,TestAccountLeaseServiceTest,TestDataTaskControllerTest,TestDataTaskServiceTest,TestDataOpenApiContractTest,TestDataHealthControllerTest,OpenApiContractTest,PermissionCodeUsageTest,PersistenceProfileBoundaryTest test
mvn -B -pl platform-api -Dtest=DbProfileRepositoryContractTest test
bash scripts/platform_api_java_line_guard.sh
bash db/validation/run_wp1_db_validation.sh
```

说明：

1. 这组门禁覆盖租借申请、`requestKey` 幂等、无可用账号冲突、续租 TTL、释放终态幂等、过期回收服务方法、清理任务创建/查询/重试边界、OpenAPI contract、权限字面量集中和 profile 边界。
2. `DbProfileRepositoryContractTest` 覆盖 active lease 唯一约束、账号条件更新、租借/清理任务持久化和 `secret_ref_cipher` 非投影。
3. release 模式仍需追加 `wp8_account_lease_concurrency_smoke.sh` 或等价外部并发 smoke；当前后端切片未启用 scheduler cleanup worker、前端工作台、WP7/WP9 adapter 和脱敏导出。

### M5 跨 WP 引用最小门禁

当前 M5 应用层契约切片采用以下验证入口作为最小准出：

```bash
mvn -B -pl platform-api -Dtest=TestDataCrossWpReferenceServiceTest test
```

说明：

1. 这组门禁覆盖 WP9 lease adapter、WP7 runner contract 和 WP10 summary contract 的脱敏边界。
2. 定向测试验证 `accountLeaseRef`、`secretRefDigest`、计数、状态和 digest 的返回结构，不回显 secret、token、cookie、原始记录正文或清理 payload。
3. 本轮仍不要求新增独立 HTTP 入口、前端页面、scheduler worker 或真实 WP7/WP9/WP10 实现。

M5 定向测试矩阵：

| 测试类 | 覆盖契约 | 必须断言 | 失败条件 |
|---|---|---|---|
| `TestDataCrossWpReferenceServiceTest#wp9AdapterReturnsOnlyLeaseReferenceAndSanitizedAccountSummary` | WP9 lease adapter | 返回 `accountLeaseRef/status/expiresAt/account.secretRefDigest`；重复 `requestKey` 返回同一 lease；释放按 `executionRunRef` 校验 | 出现 `secret://`、密码、租借 token 明文、`secret_ref_cipher`、非本 run 释放成功 |
| `TestDataCrossWpReferenceServiceTest#runnerContractReturnsDigestOnlyAndRejectsReleasedLease` | WP7 runner contract | active lease 可返回账号摘要和 `secretRefDigest`；释放后 runner contract 返回 `INVALID_STATE` | 出现密码、token、cookie、`secret://`、`secretRef` 原文；终态 lease 仍可交给 runner |
| `TestDataCrossWpReferenceServiceTest#wp10ReportEvidenceContainsOnlyReferencesCountsAndDigests` | WP10 summary contract | 返回 `dataSetRef/accountLeaseRef/cleanupTaskRef`、状态、计数、schemaFieldCount、targetRef/resultSummary digest、summary keys、`holderType/holderRef` 和脱敏账号摘要 | 出现 record payload、masked summary value、cleanup result value、targetRef 原文、secret 原文 |
| `TestDataCrossWpReferenceServiceTest#rejectsReportEvidenceFromAnotherProject` | 项目 scope | 跨项目引用返回 `FORBIDDEN` | 任意跨项目 dataSet/lease/task 证据可读 |

M5 完整准出命令：

```bash
mvn -B -pl platform-api -Dtest=TestDataCrossWpReferenceServiceTest,TestAccountLeaseServiceTest,TestDataTaskServiceTest,TestDataOpenApiContractTest test
mvn -B -pl platform-api test
bash scripts/platform_api_java_line_guard.sh
bash db/validation/run_wp1_db_validation.sh
cd portal-web && npm test
cd portal-web && npm run build
git diff --check
```

未执行项和风险边界：

1. 未执行真实 WP7 runner 凭据注入；本轮只验证 runner 可读取脱敏账号契约，真实 SecretProvider adapter 和浏览器登录由 WP7 后续任务承接。
2. 未执行 WP9 scheduler/DAG 自动申请和释放；本轮只验证 WP8 应用层 lease adapter，真实调度编排由 WP9 后续任务接入。
3. 未执行 WP10 完整报告生成；本轮只验证报告证据字段白名单，报告页面、Allure/AI 诊断由 WP10 承接。
4. 未新增 `portal-web` 工作台；前端只运行既有 Vitest 和 build，M6 再补 API helper、权限入口和 Playwright smoke。
5. 未启用 cleanup worker 或破坏性清理；`cleanupEnabled=false` 仍是默认安全边界。

### M6A 前端工作台基础闭环最小门禁

当前 M6A 前端切片采用以下验证入口作为最小准出：

```bash
cd portal-web && npm test -- permissions testData
cd portal-web && npm test
cd portal-web && npm run build
mvn -B -pl platform-api test
bash scripts/platform_api_java_line_guard.sh
bash db/validation/run_wp1_db_validation.sh
git diff --check
```

M6A 定向测试矩阵：

| 测试文件 | 覆盖范围 | 必须断言 | 失败条件 |
|---|---|---|---|
| `portal-web/src/api/testData.test.ts` | WP8 API helper、normalize、payload 构造 | 路径和 method 正确；`schema/cleanupPolicy/scopeSummary/resultSummary` 过滤 password/token/cookie/secret；`secretRefDigest` 保留；新增账号允许写入 `secretRef`，更新账号留空不发送 `secretRef` | 前端 state 或 payload 混入敏感摘要；路径偏离后端契约；digest 被误删 |
| `portal-web/src/permissions.test.ts` | `#test-data` 入口和按钮权限 | `testData:read` 控制页面访问，`testData:manage/lease/cleanup/export` 控制对应操作 | 无 read 权限可见入口；read 权限误授予维护/租借/清理/导出按钮 |

M6A 未执行项和风险边界：

1. Playwright 桌面/390px smoke 已由 M6B 补齐，不再是当前前端 smoke 缺口。
2. 脱敏导出面板已由 M6C 补齐；M6A 当时仅保留 `testData:export` 权限映射和策略摘要展示。
3. DOM secretRef 原文扫描已由 `wp8-test-data.smoke.playwright.ts` 覆盖页面文本和 toast/error 区域的输入 secretRef 检查。
4. 本轮未改 Java 生产代码；Java 行数门禁作为仓库默认验证执行，阿里巴巴 Java 自查不适用于本轮代码变更。

### M6B/M7A 前端 smoke 与 quality gate 基础门禁

当前 M6B/M7A 切片采用以下验证入口作为最小准出：

```bash
bash scripts/wp8_frontend_e2e_smoke.sh
bash scripts/wp8_account_lease_concurrency_smoke.sh
WP8_QUALITY_GATE_PLAN_ONLY=1 bash scripts/wp8_quality_gate.sh
cd portal-web && npm test -- api/testData.test.ts permissions.test.ts
cd portal-web && npm run build
git diff --check
```

M6B/M7A 定向测试矩阵：

| 测试/脚本 | 覆盖范围 | 必须断言 | 失败条件 |
|---|---|---|---|
| `portal-web/e2e/wp8-test-data.smoke.playwright.ts` | 桌面和 390px 浏览器主链路 | 创建数据集、导入记录摘要、创建账号池、写入账号 secretRef、申请/续租/释放租借、创建/重试清理任务；页面文本不含输入 `secret://` 原文；390px 无页面横向溢出 | secretRef 原文进入 DOM；移动端溢出；任一主链路按钮或 payload 失效 |
| `scripts/wp8_frontend_e2e_smoke.sh` | Playwright smoke 入口 | 自动选择可用 Chrome channel，必要时可用 `WP8_FRONTEND_INSTALL_BROWSERS=1` 安装浏览器 | 浏览器依赖缺失且未安装；smoke 失败 |
| `scripts/wp8_account_lease_concurrency_smoke.sh` | 本地 managed 租借并发 smoke | 后端服务拒绝第二个 active lease，DB repository active lease 唯一约束有效 | 第二个 active lease 成功；DB contract 不通过 |
| `scripts/wp8_quality_gate.sh` | WP8 聚合门禁 | development 模式可跑完整门禁；release/preprod/prod 模式要求 `WP8_LEASE_CONCURRENCY_SMOKE=managed` | release 模式未显式启并发 smoke；任一必跑验证失败 |

### M6C 数据集脱敏导出摘要最小门禁

当前 M6C 切片采用以下验证入口作为最小准出：

```bash
bash scripts/platform_api_java_line_guard.sh
mvn -B -pl platform-api -Dtest=TestDataSetControllerTest,TestDataSetServiceTest,TestDataOpenApiContractTest,OpenApiContractTest,PermissionCodeUsageTest test
cd portal-web && npm test -- api/testData.test.ts permissions.test.ts
bash scripts/wp8_frontend_e2e_smoke.sh
cd portal-web && npm run build
WP8_GATE_MODE=release WP8_LEASE_CONCURRENCY_SMOKE=managed bash scripts/wp8_quality_gate.sh
git diff --check
```

M6C 定向测试矩阵：

| 测试/脚本 | 覆盖范围 | 必须断言 | 失败条件 |
|---|---|---|---|
| `TestDataSetControllerTest` | `GET /api/v1/test-data/data-sets/{id}/export` | `testData:export` 权限生效；返回 schema version、字段计数、`maskedSummaryKeys` 和 redaction policy；响应不含 `secret://`、原始数据或 maskedSummary 值 | read/lease 权限可导出；响应出现完整 payload、masked value、secretRef 原文 |
| `TestDataSetServiceTest` | 导出服务和开关 | `export-enabled=false` 返回 `INVALID_STATE`；导出仅携带 digest/tags/keys | 开关失效；导出模型包含 maskedSummary values |
| `TestDataOpenApiContractTest` | OpenAPI 路径 | `/data-sets/{id}/export` 出现在 `/v3/api-docs` | 契约缺失 |
| `portal-web/src/api/testData.test.ts` | 前端 helper 和 normalizer | `exportTestDataSet` 路径正确；normalizer 只保留 `maskedSummaryKeys`，redaction policy 保留布尔安全声明 | 前端模型吸收 maskedSummary 值；policy 被误删 |
| `portal-web/e2e/wp8-test-data.smoke.playwright.ts` | 浏览器导出链路 | 点击“导出摘要”；展示 `wp8-data-set-export-v1` 和 policy；DOM 不含 `secret://` 或敏感测试值 | 导出按钮不可用；导出结果泄露原文 |

M6C 未执行项和风险边界：

1. 不实现真实文件下载，当前导出为控制面 JSON 摘要视图。
2. 不实现租借导出和清理审计导出；本轮只覆盖数据集导出摘要。
3. 不启用真实 cleanup worker 或生产数据复制。
4. 真实 HTTP 服务级并发压测仍由 release 外部环境后续补充；当前 release gate 使用 managed 并发 smoke。

## 9. 准出标准

1. 数据集、账号池、租借、释放和清理任务主链路后端测试通过。
2. 租借并发测试证明同一账号不会被重复 active lease 占用。
3. secretRef、密码、token、cookie 和敏感数据原文不出现在响应、审计、日志、前端 DOM 和导出中。
4. 前端权限、loading/empty/error 和响应式 smoke 通过。
5. DB validation 覆盖新增表、约束、索引和 runtime role 权限。
6. 无法运行真实清理 adapter 时，必须说明当前只验证控制面和幂等任务记录，真实清理另行专项准出。
