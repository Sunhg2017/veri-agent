# WP8 测试数据与账号池 - 运维 Runbook

| 项目 | 内容 |
|---|---|
| 工作包 | WP8 测试数据与账号池 |
| 文档性质 | 租借、账号、SecretRef、清理任务、脱敏导出和发布准出 Runbook |
| 当前口径 | WP8 当前提供控制面、租借并发保护、清理任务、脱敏导出摘要、文件下载、受控 cleanup adapter 和账号自动开通 adapter；破坏性清理默认关闭 |
| 日期 | 2026-06-24 |

## 1. 适用范围

本 Runbook 适用于 WP8 开发、预发和生产发布准出，以及账号租借卡死、账号锁定、SecretRef 轮换、清理失败、脱敏导出下载异常和账号自动开通排障。WP8 当前仍由 `platform-api` 承载测试数据与账号池控制面，真实清理执行和业务账号开通必须通过显式 adapter 配置进入受控路径。

## 2. 开关和配置

| 配置 | 默认 | 说明 |
|---|---|---|
| `veri-agent.test-data.enabled` | `true` | WP8 控制面总开关；关闭后业务 API 返回 `INVALID_STATE`，health 仍可观测。 |
| `veri-agent.test-data.cleanup-enabled` | `false` | 是否允许后续清理 adapter 执行破坏性动作；当前默认只记录控制面任务。 |
| `veri-agent.test-data.cleanup-adapter-mode` | `DISABLED` | 清理 adapter 模式；生产启用前必须切为 `HTTP` 并配置 URL。 |
| `veri-agent.test-data.cleanup-adapter-url` | 空 | HTTP cleanup adapter 地址。 |
| `veri-agent.test-data.cleanup-adapter-token` | 空 | cleanup adapter Bearer token，只用于出站调用，不允许进入日志、health 或审计。 |
| `veri-agent.test-data.cleanup-adapter-timeout-ms` | `5000` | cleanup adapter 超时。 |
| `veri-agent.test-data.account-provisioning-enabled` | `false` | 是否允许 worker 根据账号池 policy 自动补齐账号。 |
| `veri-agent.test-data.account-provisioning-adapter-mode` | `LOCAL_SECRET_REF` | 账号开通 adapter 模式；可用 `LOCAL_SECRET_REF` 或 `HTTP`。 |
| `veri-agent.test-data.account-provisioning-adapter-url` | 空 | HTTP provisioning adapter 地址。 |
| `veri-agent.test-data.account-provisioning-adapter-token` | 空 | provisioning adapter Bearer token，只用于出站调用。 |
| `veri-agent.test-data.account-provisioning-batch-size` | `20` | 单次 worker tick 扫描可自动开通账号池上限。 |
| `veri-agent.test-data.export-enabled` | `true` | 是否允许数据集和租借脱敏导出摘要。 |
| `veri-agent.test-data.default-lease-ttl-seconds` | `1800` | 默认租借 TTL。 |
| `veri-agent.test-data.max-lease-ttl-seconds` | `14400` | 最大租借 TTL。 |
| `veri-agent.test-data.record-max-count` | `10000` | 单数据集记录数量上限。 |
| `veri-agent.test-data.record-summary-max-bytes` | `2048` | 单条记录脱敏摘要大小上限。 |

生产建议：

1. 首次发布先保持 `cleanup-enabled=false`，确认控制面、租借、导出和下载稳定后，再对真实清理 adapter 做低风险环境准出。
2. 若发现导出或页面泄露风险，优先设置 `export-enabled=false` 或撤销 `testData:export` 权限。
3. 若发现租借状态异常但仍需保留证据，优先禁用对应账号池或将账号置为 `LOCKED`，不要直接删库。
4. 账号自动开通先使用 `LOCAL_SECRET_REF` 或沙箱 HTTP adapter 演练，确认 `minAvailable/maxAccounts` 和 secretRef 前缀后再接真实业务系统。

## 3. 日常验证

开发默认入口：

```bash
bash scripts/wp8_quality_gate.sh
```

release/preprod/prod 模式必须显式启用 managed 并发 smoke：

```bash
WP8_GATE_MODE=release WP8_LEASE_CONCURRENCY_SMOKE=managed bash scripts/wp8_quality_gate.sh
```

单项验证：

```bash
bash scripts/platform_api_java_line_guard.sh
mvn -B -pl platform-api test
cd portal-web && npm test
cd portal-web && npm run build
bash scripts/wp8_frontend_e2e_smoke.sh
bash scripts/wp8_account_lease_concurrency_smoke.sh
bash db/validation/run_wp1_db_validation.sh
git diff --check
```

说明：当前 managed 并发 smoke 复用后端服务测试和 DB repository contract，不启动真实 HTTP 服务或生产清理 worker。外部环境 HTTP 并发压测需要另行制定环境、账号池和回滚策略。

## 4. 发布准出检查点

1. `GET /api/v1/test-data/health` 只展示开关、limits 和安全策略摘要，不展示 secret、allowlist 明细或敏感引用原文。
2. 数据集、账号池、租借、清理和导出 API 均必须按项目 scope 和 RBAC 权限校验。
3. `secretRef` 只允许写入，不得出现在响应、审计 payload、日志、前端 DOM 或导出中。
4. 租借并发必须由数据库条件更新和 active lease 唯一约束兜底，不能只依赖前端防重。
5. `cleanup-enabled=false` 时不得执行破坏性清理动作，只允许记录和重试控制面任务；`cleanup-enabled=true` 时必须确认 health policy 中 adapter ready/provider。
6. 数据集导出摘要和下载文件不得包含完整 record payload、maskedSummary 值、`secretRef` 原文、token、cookie 或 Authorization header。
7. 租借导出摘要和下载文件不得包含租借 token 明文、释放原因原文、健康摘要原文、scopeSummary 值、leasePolicy 值或敏感 key。
8. release gate 必须记录 WP8 quality gate、DB validation、前端 smoke、并发 smoke 和任何跳过项。
9. 账号自动开通必须记录目标账号池、policy、adapter provider、创建数量、失败数量和回滚动作。

## 5. 租借卡死处理

| 场景 | 推荐操作 | 证据 |
|---|---|---|
| 账号长期 `LEASED`，lease 仍为 `ACTIVE` | 先确认 holderRef 是否仍在执行；若执行已结束，使用释放控制面释放，并选择账号回到 `AVAILABLE` 或 `LOCKED`。 | lease id、holderRef、expiresAt、traceId、释放审计。 |
| lease 已过期但账号未回收 | 运行后端过期回收相关验证或等待后续 scheduler；当前无独立 scheduler worker 时按人工释放处理。 | lease status、expiresAt、账号 status、操作人。 |
| 同一账号疑似重复占用 | 立即停止新租借，运行 managed 并发 smoke 和 DB repository contract，检查 active lease 唯一约束。 | 账号 id、active lease 列表、quality gate 结果。 |
| requestKey 重试结果异常 | 比对同一 `projectId + requestKey` 的 payload；payload 不一致应按冲突处理。 | requestKey、requestDigest、holderRef、错误码。 |
| 释放后账号仍不可用 | 查看释放时 `accountStatus` 是否选择 `LOCKED/DISABLED`，再决定人工解锁或继续隔离。 | releaseReason、releasedAt、账号 status、审计记录。 |

不要直接删除 `test_account_lease`、账号或账号池记录。确需数据修复时必须保留工单、SQL、影响范围、备份和回滚动作，并在修复后重跑 WP8 quality gate。

## 6. 账号锁定和健康异常

| 现象 | 常见原因 | 处理 |
|---|---|---|
| 账号状态 `LOCKED` | 释放失败、健康检查失败、人工隔离 | 查看最近 lease、releaseReason digest、lastHealthStatus 和任务错误摘要；确认后改回 `AVAILABLE` 或保持隔离。 |
| 账号状态 `DISABLED` | 人工停用、凭据轮换中、应用侧账号不可用 | 完成轮换或应用侧修复后，再按账号摘要维护流程恢复。 |
| 可用账号数为 0 | 账号池禁用、账号均租出或锁定、roleTags 不匹配 | 检查账号池状态、账号列表、roleTags、active lease 和 TTL。 |
| 健康摘要疑似泄露 | `lastHealthSummary` 写入了响应正文、token 或 cookie | 立即更新摘要为脱敏文本，导出检查必须只保留 presence 和 digest。 |

账号健康摘要只用于排障提示，不是凭据存储位置。任何登录响应、session cookie、Authorization header、业务用户数据正文都不得写入账号摘要。

## 7. SecretRef 轮换

### 7.1 零中断轮换

| 步骤 | 操作 | 验证 |
|---|---|---|
| 1 | 在 WP1 SecretProvider 或外部密钥系统创建新 secret。 | 新 secret 已绑定正确项目和用途。 |
| 2 | 保留旧 secret，不立即吊销。 | 旧租借和运行仍可排障。 |
| 3 | 在 `测试数据` 工作台选择账号池和账号，编辑账号摘要。 | 页面只展示旧 `secretRefDigest`，不回显旧 `secretRef`。 |
| 4 | 在 `secretRef` 输入框写入新 `secret://...` 引用并保存。 | 保存成功后输入框清空，账号只展示新 digest。 |
| 5 | 使用低风险账号池申请一次租借，验证返回摘要仅含 `secretRefDigest`。 | 租借成功，页面和响应不含 `secret://` 原文。 |
| 6 | 观察一个发布窗口后撤销旧 secret。 | 轮换工单记录账号 id、新旧 digest、验证命令和 traceId。 |

### 7.2 紧急轮换

1. 立即禁用受影响账号池，或将受影响账号置为 `DISABLED`/`LOCKED`。
2. 撤销泄露 secret，并创建新 secret。
3. 更新账号摘要的 `secretRef`。
4. 重跑账号租借 smoke 和前端 DOM 脱敏检查。
5. 如果泄露进入导出或审计，关闭 `export-enabled`，修复脱敏后再恢复。

轮换期间不要在日志、release notes、工单、截图、导出摘要或 CI artifact 中记录 secret 明文。

## 8. 清理任务失败处理

| 场景 | 推荐操作 | 证据 |
|---|---|---|
| `cleanup-enabled=false` 但用户认为未清理 | 解释当前只记录控制面任务；真实清理 worker 未启用。 | health `cleanupEnabled=false`、任务 id。 |
| 清理任务 `FAILED` | 查看 errorCode、errorSummary、traceId 和 resultSummary key；确认摘要不含敏感原文。 | task id、attempt、traceId。 |
| 需要重试 | 使用工作台 `重试`，填写新的 retry requestKey；不要直接复制旧错误 payload。 | 新 task attempt、requestKey、traceId。 |
| 清理影响范围不明 | 先禁用对应数据集或账号池新增引用，再复核 targetRef 和 resultSummary。 | dataSetId、targetRef、任务审计。 |
| `cleanup-enabled=true` 但 adapter 未 ready | 检查 `cleanup-adapter-mode/url`，任务会以 `CLEANUP_ADAPTER_NOT_READY` 失败；修复配置后用新 requestKey 重试。 | health policy、配置变更、失败任务。 |
| 真实 adapter 启用失败 | 立即关闭 `cleanup-enabled`，保留任务、adapter 日志和审计，回到控制面只读记录。 | 配置变更、失败任务列表、回滚记录。 |
| adapter 返回敏感摘要 | 关闭 `cleanup-enabled`，修复 adapter 响应字段；后端会过滤常见敏感 key，但上游仍必须整改。 | adapter 响应样例、任务 resultSummary key。 |

当前默认不执行破坏性清理动作，因此“任务创建成功”不等于“外部系统已清理”。真实清理 adapter 必须具备 allowlist、dry-run、幂等、最大影响范围和回滚策略。

## 9. 账号自动开通处理

| 场景 | 推荐操作 | 证据 |
|---|---|---|
| 自动开通未发生 | 确认 `account-provisioning-enabled=true`、worker enabled、账号池 `READY`、`leasePolicy.provisioning.enabled=true`，且可用账号数低于 `minAvailable`。 | health policy、worker tick、pool id。 |
| 达到上限后不再开通 | 检查 `maxAccounts` 与当前账号总数；这是预期保护，不应直接调大到无界。 | pool policy、账号数量。 |
| HTTP provisioning 失败 | 检查 adapter URL/token/超时和返回 errorSummary；修复后等待下次 worker tick。 | adapter 日志、failedProvisioningCount。 |
| 新账号 secretRef 异常 | 立即锁定或禁用新账号，修复 `secretRefPrefix` 或 HTTP adapter 返回值后重新开通。 | account id、secretRefDigest、policy。 |

账号自动开通只写入账号摘要和 `secretRef` 指针，不解析凭据明文。任何真实业务账号撤销、禁用或删除仍按被测系统自己的运维流程执行，并在 WP8 保留账号和审计证据。

## 10. 脱敏导出异常处理

| 现象 | 处理 |
|---|---|
| 导出按钮不可用 | 检查 `testData:export` 权限和 `export-enabled` 开关。 |
| 导出接口返回 `INVALID_STATE` | 确认 `veri-agent.test-data.export-enabled=true`。 |
| 跨项目导出被拒绝 | 按项目 scope 检查用户角色，不应放宽为平台全局读取。 |
| 导出结果缺少记录或租借 | 确认选择对象、分页列表选中项和对象状态；导出只针对当前对象。 |
| 下载文件失败 | 先确认对应 `/export` 摘要可成功，再检查 `/export/download` traceId、浏览器下载拦截和 Content-Disposition。 |
| 导出结果或下载文件出现 `secret://`、token、cookie、Authorization 或原文释放原因 | 立即关闭 `export-enabled`，撤销相关导出权限，修复后端 view 和前端 normalizer，重跑 WP8 quality gate。 |

导出是审计摘要，不是数据搬运工具。文件下载必须沿用当前 redaction policy 和字段白名单，并由文件级 smoke 与泄露扫描覆盖。

## 11. 回滚

发现误租借、清理异常或敏感泄露时按影响面执行：

1. 暂停导出：设置 `veri-agent.test-data.export-enabled=false` 或撤销 `testData:export`。
2. 暂停清理执行：保持或恢复 `veri-agent.test-data.cleanup-enabled=false`。
3. 暂停账号自动开通：设置 `veri-agent.test-data.account-provisioning-enabled=false`，并锁定或禁用异常新账号。
4. 暂停新租借：禁用对应账号池，或将高风险账号置为 `LOCKED/DISABLED`。
5. 保留数据集、账号池、账号、租借、清理任务和审计记录；不要直接删除证据。
6. 修复后重跑 `WP8_GATE_MODE=release WP8_LEASE_CONCURRENCY_SMOKE=managed bash scripts/wp8_quality_gate.sh`。
7. 对涉及数据库或权限 seed 的回滚，追加 `bash db/validation/run_wp1_db_validation.sh`。

## 12. 准出记录

发布、账号池接入、SecretRef 轮换、真实清理或账号自动开通专项工单至少记录：

1. WP8 quality gate 命令和结果。
2. 目标环境、WP8 开关状态和版本。
3. 涉及 projectId、dataSetId、poolId、accountId、leaseId、taskId。
4. SecretRef 轮换的新旧 digest，不记录 secret 明文。
5. 租借并发 smoke 或替代验证结果。
6. 前端 smoke 和 DOM 脱敏检查结果。
7. DB validation 结果，或说明本次未改数据库的原因。
8. cleanup/provisioning adapter provider、开关状态、演练环境和回滚记录。
9. 跳过项、风险、回滚开关和责任人。
