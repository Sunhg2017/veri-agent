# WP8 测试数据与账号池 - 测试策略与用例脚本

| 项目 | 内容 |
|---|---|
| 工作包 | WP8 测试数据与账号池 |
| 角色产出 | 资深质量工程师 |
| 文档性质 | 测试策略、用例矩阵、脚本门禁和准出要求 |
| 当前口径 | WP8 分 M2-M6 推进；当前本轮聚焦 M3 账号池控制面，租借、释放、清理任务、脱敏导出、前端工作台和跨 WP 引用契约按后续里程碑承接 |
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
| 导出面板 | 显示 redaction policy，不展示 secretRef 原文。 |
| 响应式主链路 | 桌面和 390px 均可完成数据集、账号池、租借、释放和任务查看。 |

## 6. 安全专项

1. API 响应快照检查：不得包含 `password`、`token`、`cookie`、`Authorization`、`secret://` 原文。
2. 审计 payload 检查：只能包含 digest、计数、状态、资源 ID 和错误码。
3. 日志检查：租借、释放、清理失败日志不输出 secretRef 原文或数据记录原文。
4. 前端 DOM 检查：Playwright smoke 中扫描页面文本不包含输入 secretRef。
5. 导出检查：导出摘要带 redaction policy，禁止完整 record payload。

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

WP8 专项门禁建议：

```bash
bash scripts/wp8_quality_gate.sh
```

M1 foundation 准出最小门禁：

```bash
git diff --check
bash scripts/platform_api_java_line_guard.sh
mvn -B -pl platform-api -Dtest=TestDataHealthControllerTest,OpenApiContractTest,PermissionCodeUsageTest test
mvn -B -pl platform-api test
bash db/validation/run_wp1_db_validation.sh
```

M1 只验证权限 seed、DB foundation、运行时 DB 角色授权、审计事件字典配置、health API 和 OpenAPI contract；数据集 CRUD、账号池 CRUD、租借并发、清理 worker、导出和前端主链路按 M2-M6 对应 story 另行准出。

前端 smoke 建议：

```bash
bash scripts/wp8_frontend_e2e_smoke.sh
```

并发租借 smoke 建议：

```bash
bash scripts/wp8_account_lease_concurrency_smoke.sh
```

## 8. WP8 Quality Gate 草案

`scripts/wp8_quality_gate.sh` 建议串联：

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

## 9. 准出标准

1. 数据集、账号池、租借、释放和清理任务主链路后端测试通过。
2. 租借并发测试证明同一账号不会被重复 active lease 占用。
3. secretRef、密码、token、cookie 和敏感数据原文不出现在响应、审计、日志、前端 DOM 和导出中。
4. 前端权限、loading/empty/error 和响应式 smoke 通过。
5. DB validation 覆盖新增表、约束、索引和 runtime role 权限。
6. 无法运行真实清理 adapter 时，必须说明当前只验证控制面和幂等任务记录，真实清理另行专项准出。
