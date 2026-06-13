# WP9 执行编排与任务调度 - 测试策略与用例脚本

| 项目 | 内容 |
|---|---|
| 工作包 | WP9 执行编排与任务调度 |
| 角色产出 | 资深质量工程师 |
| 文档性质 | 测试策略、用例矩阵、脚本门禁和准出要求 |
| 当前口径 | 覆盖计划、DAG、手动触发、队列状态、WP6 dispatch、cancel/retry/recovery、webhook/cron 控制面和前端主链路 |
| 版本 | v0.1 |
| 日期 | 2026-06-13 |

## 1. 测试目标

1. 验证 WP9 不绕过 WP1 权限审计和 WP6 runner 安全边界。
2. 验证 DAG 校验、状态机、幂等、取消、重试、超时和恢复可靠。
3. 验证 webhook/cron 控制面默认关闭、启用受签名和权限约束。
4. 验证前端工作台可完成主链路且不泄露敏感信息。
5. 验证 DB migration、权限 seed、审计事件和 quality gate 可作为发布准出入口。

## 2. 测试范围

| 模块 | 覆盖 |
|---|---|
| Plan | 创建、更新、dryRun、归档、状态保护、权限。 |
| DAG | 循环依赖、缺失依赖、跨项目资源、节点输入 schema、失败策略。 |
| Run | 手动触发、requestKey 幂等、状态聚合、节点拓扑推进。 |
| Queue | 条件认领、heartbeat、并发限制、超时回收、人工重放。 |
| WP6 dispatch | approved bundle、runner disabled、allowlist 阻断、timeout、pass/fail 聚合。 |
| Cancel/retry | 运行中取消、终态取消幂等、失败节点重试、retryAttempt。 |
| Trigger | webhook 签名、sourceEventId 幂等、禁用态、cron 元数据。 |
| Export | 脱敏执行摘要、WP10 handoff、审计事件。 |
| Frontend | 权限入口、计划表单、DAG 预览、运行详情、取消重试、移动端。 |

## 3. P0 用例矩阵

| 编号 | 优先级 | 场景 | 期望 |
|---|---|---|---|
| WP9-PLAN-001 | P0 | 创建 READY 计划 | 返回计划详情，审计 `execution.plan.created`。 |
| WP9-PLAN-002 | P0 | 非法 DAG 有环 | 返回 `EXECUTION_DAG_INVALID`，不落 READY。 |
| WP9-PLAN-003 | P0 | 跨项目引用 WP6 bundle | 返回 `EXECUTION_RESOURCE_SCOPE_DENIED`。 |
| WP9-RUN-001 | P0 | 手动触发 READY 计划 | 创建 run/node run，状态从 QUEUED 收敛。 |
| WP9-RUN-002 | P0 | 重复 requestKey | 返回同一 run 或冲突，不重复创建。 |
| WP9-RUN-003 | P0 | DRAFT 计划触发 | 返回 `EXECUTION_PLAN_NOT_READY`。 |
| WP9-RUN-004 | P0 | WP6 runner disabled | 节点归一化为 BLOCKED/FAILED 摘要，不泄露配置。 |
| WP9-RUN-005 | P0 | WP6 run 成功 | execution run 聚合为 SUCCEEDED。 |
| WP9-RUN-006 | P0 | WP6 run 失败 | execution run 聚合为 FAILED 或 PARTIAL_SUCCESS。 |
| WP9-CANCEL-001 | P0 | RUNNING run 取消 | 调用节点 runner cancel，状态收敛为 CANCELED。 |
| WP9-CANCEL-002 | P0 | 终态 run 取消 | 幂等返回当前状态。 |
| WP9-RETRY-001 | P0 | FAILED 节点重试 | 新增 attempt，保留原失败摘要。 |
| WP9-QUEUE-001 | P0 | 多 worker 认领同一 queued node | 只有一个 active claim，节点只从 QUEUED 条件推进到 RUNNING。 |
| WP9-QUEUE-002 | P0 | claimToken 完成成功节点 | 节点 SUCCEEDED，满足依赖的 PENDING 节点推进到 QUEUED。 |
| WP9-QUEUE-003 | P0 | fail-fast 依赖失败 | 下游节点 BLOCKED，run 聚合为 FAILED 或 PARTIAL_SUCCESS。 |
| WP9-QUEUE-004 | P0 | 完成回传包含 stdout/requestBody/token | 危险 key 丢弃，安全 key 中敏感文本脱敏。 |
| WP9-QUEUE-005 | P0 | claim heartbeat 续约 | active claim 延长 expiresAt，并刷新 node heartbeat；过期或非 active claim 拒绝。 |
| WP9-QUEUE-006 | P0 | 过期 claim recovery 未超过节点 timeout | claim 标记 EXPIRED，node RUNNING 重新排队为 QUEUED，旧 claimToken 不能完成。 |
| WP9-QUEUE-007 | P0 | 过期 claim recovery 已超过节点 timeout | claim 标记 EXPIRED，node 标记 TIMEOUT，下游 fail-fast 节点 BLOCKED，run 聚合为 TIMEOUT。 |
| WP9-SCHED-001 | P0 | scheduler 启用后执行 tick | 先 recovery，再按 bounded batch claim，API_TEST 只通过 WP6 应用服务 dispatch，REPORT_HANDOFF 完成摘要。 |
| WP9-SCHED-002 | P0 | scheduler disabled | 不访问 `ExecutionRunService`，tick 返回 noop。 |
| WP9-SCHED-003 | P0 | scheduler dispatch 失败 | 使用脱敏 errorSummary 将 claimed 节点关闭为 FAILED，active claim 不悬挂。 |
| WP9-SCHED-004 | P0 | scheduler 配置越界 | interval、initialDelay、workerId、batchSize 使用 effective 边界值。 |
| WP9-TRIGGER-001 | P0 | webhook disabled | 返回 `EXECUTION_TRIGGER_DISABLED`。 |
| WP9-TRIGGER-002 | P0 | webhook 签名错误 | 返回 `EXECUTION_TRIGGER_SIGNATURE_INVALID`。 |
| WP9-EXPORT-001 | P0 | 导出摘要 | 不包含 secret、baseUrl 明文、stdout/stderr 原文。 |

## 4. 安全测试

1. 越权项目读取计划、触发 run、取消 run、导出 run 必须 403。
2. webhook 无签名、过期签名、错误签名必须拒绝。
3. variables 禁止注入 shell、超长文本和敏感明文。
4. 导出和审计不包含 secretRef 明文、webhook secret、请求响应正文或 stdout/stderr。
5. WP9 不能通过测试替身绕过 WP6 allowlist 和 secretRef 规则。

## 5. 前端测试

| 用例 | 期望 |
|---|---|
| 无权限直达 `#execution` | 显示无权限态，不请求业务数据。 |
| 计划列表空态 | 展示空态和创建入口。 |
| 创建计划表单缺字段 | 本地校验阻断。 |
| dryRun 返回 DAG 错误 | 节点定位错误，展示 traceId。 |
| 手动触发成功 | 跳转运行详情并轮询。 |
| 取消运行 | 按钮 loading，成功后状态更新。 |
| 重试失败节点 | 显示 retryAttempt 和新节点状态。 |
| 移动端 | 无横向溢出，按钮不重叠。 |

## 6. 建议脚本入口

当前已提供：

```bash
bash scripts/wp9_quality_gate.sh
WP9_GATE_MODE=release WP9_SCHEDULER_SMOKE=managed bash scripts/wp9_quality_gate.sh
bash scripts/wp9_scheduler_smoke.sh
bash scripts/wp9_webhook_smoke.sh
```

`wp9_quality_gate.sh` 已串联：

1. 脚本语法检查。
2. 后端 WP9 service/controller/OpenAPI contract 测试。
3. DB validation。
4. 前端 WP9 Vitest。
5. 前端 build。
6. Playwright smoke。
7. managed scheduler smoke，可用 `WP9_SCHEDULER_SMOKE=managed` 显式启用；release gate 必须启用。

`wp9_webhook_smoke.sh` 仍保留为后续供应商 webhook 插件样例/外部 HTTP smoke 入口。

## 7. 准出标准

1. DAG 非法输入不会创建 READY 计划或执行 run。
2. 权限、项目 scope、审计、traceId 覆盖计划、运行、节点、触发和导出。
3. cancel/retry/timeout/recovery 均幂等且状态收敛。
4. webhook/cron 默认关闭，开启后有签名、幂等和审计。
5. WP6 dispatch 不泄露敏感值，不绕过 WP6 runner 安全策略。
6. 后端、前端、构建、DB validation 和 WP9 quality gate 按影响面通过。

## 8. 当前质量结论

M1 已完成基础控制面、DB validation 和 health API 验收。M2 已完成计划与 DAG 后端切片，新增 `ExecutionDagValidatorTest`、`ExecutionPlanControllerTest`，并把 execution plan API 纳入 `OpenApiContractTest`。M3A 已完成手动触发与运行记录后端切片，新增 `ExecutionRunControllerTest`，并把 `/plans/{id}/runs`、`/runs`、`/runs/{id}` 纳入 `OpenApiContractTest`。M3B 已完成取消与控制面重试后端切片，覆盖 `/runs/{id}/cancel`、`/runs/{id}/retry`、终态取消幂等、非重试态拒绝、失败节点 retry attempt、防重复 retry 和 JDBC update/attempt 查询。M3C 已完成内部 queue claim、节点完成回传、依赖推进和 run 聚合后端切片，覆盖 `/internal/queue/claims`、`/internal/queue/node-runs/{id}/complete`、成功节点推动下游 QUEUED、失败节点阻断下游、危险 resultSummary key 丢弃、敏感文本脱敏、active claim 唯一和条件更新 DB contract。M3D 已完成 claim heartbeat、过期 claim recovery、旧 claimToken 拒绝和 TIMEOUT recovery。M4A 已完成 claimed `API_TEST` dispatch 到 WP6 应用服务的后端切片，覆盖 runner disabled 归一为 BLOCKED、WP6 managed success 聚合为 SUCCEEDED、OpenAPI contract、health `wp6DispatchReady=true`、raw baseUrl/secretRef/runner payload 不落 WP9 摘要。M4B 已完成 `baseUrlRef=env:<key>` 环境解析、计划 `runtimeSecretRefs` 安全中继、WP6 `FAILED/TIMEOUT` 结果映射、`runtimeSecretRefs` 格式校验、plan 详情脱敏和 SecretProvider 边界失败脱敏。M4C 已完成后台 scheduler loop 后端切片，覆盖启用后 recovery/claim/dispatch/report handoff、disabled noop、dispatch 失败脱敏关闭 claim、scheduler 配置边界和 health `schedulerLoopReady=true`。M5 已完成触发控制面后端切片，覆盖 trigger 创建/列表/更新/dry-run、secretRef 脱敏、webhook disabled 拒绝、签名错误拒绝、签名成功创建 run、重复 sourceEventId 幂等回放和 OpenAPI contract。

当前已覆盖 plan 创建、列表、详情、更新、dry-run、归档、归档后状态保护、DAG 循环、跨项目 WP6 bundle 拒绝、secretRef 输入脱敏、`runtimeSecretRefs` 引用校验/脱敏、READY 计划手动触发、requestKey 幂等回放、run 列表/详情、取消、重试、内部认领、heartbeat、recovery、节点完成、API_TEST dispatch、baseUrlRef 环境解析、WP6 failure/timeout 映射、后台 scheduler loop、CRON scanner、trigger 管理、webhook 签名与幂等、依赖推进、状态聚合和权限保护。

M6C 已补 `portal-web/e2e/wp9-execution.smoke.playwright.ts` 和 `scripts/wp9_frontend_e2e_smoke.sh`，覆盖桌面与 390px 视口下的执行工作台计划创建/更新、DAG dryRun、手动运行、取消、失败运行重试、触发器创建/dryRun/启停、事件查看和无横向溢出。M7A 已补 `scripts/wp9_quality_gate.sh` 和 `scripts/wp9_scheduler_smoke.sh`，开发模式聚合 WP9 后端、前端、Playwright、build 和 DB validation，release 模式要求显式启用 managed scheduler smoke。M7B 已补生产 CRON scanner 最小闭环，覆盖到期 CRON trigger 创建 CRON run、trigger event 证据、`nextFireAt` 推进和 health `cronScannerReady=true`。M7C 已补执行摘要导出落地，覆盖 `GET /runs/{id}/export` schema、节点状态计数、redactionPolicy、OpenAPI contract、前端 API normalization 和运行详情导出按钮。供应商 webhook 插件样例和外部 webhook HTTP smoke 仍必须按本文件 P0 用例矩阵补齐。
