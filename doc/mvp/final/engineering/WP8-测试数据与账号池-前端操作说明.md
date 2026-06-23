# WP8 测试数据与账号池 - 前端操作说明

| 项目 | 内容 |
|---|---|
| 工作包 | WP8 测试数据与账号池 |
| 文档性质 | 浏览器操作说明、产品验收辅助材料 |
| 当前口径 | 用户可在 `#test-data` 工作台完成数据集、账号池、账号摘要、租借、释放、清理任务和脱敏导出摘要主链路，不依赖 curl |
| 日期 | 2026-06-15 |

## 1. 适用范围

本文面向测试工程师、自动化工程师、项目负责人和审计人员，说明如何通过 `portal-web` 的 `测试数据` 工作台完成 WP8 当前已交付的浏览器操作。本文不替代运维排障 Runbook，也不承诺真实文件下载、真实 cleanup worker 或真实账号自动开通；WP7/WP9/WP10 对 WP8 契约的消费已在对应工作包交付，后续如扩大 WP8 自身能力仍需独立准出。

## 2. 入口与权限

| 操作 | 权限 |
|---|---|
| 进入 `#test-data`、查看策略、列表、详情和摘要 | `testData:read` |
| 创建、更新、归档数据集和账号池，维护账号摘要 | `testData:manage` |
| 申请、续租和释放账号租借 | `testData:lease` |
| 创建和重试清理任务控制面记录 | `testData:cleanup` |
| 生成数据集或租借脱敏导出摘要 | `testData:export` |

无 `testData:read` 时侧边导航不展示 `测试数据`，直达 `#test-data` 只展示无权限态，不拉取业务数据。按钮置灰只代表前端体验控制；所有操作仍以服务端权限、项目 scope、对象状态和 WP8 开关为最终准入来源。

## 3. 页面结构

| 区域 | 用途 |
|---|---|
| 顶部指标 | 查看数据集数量、可用账号数、ACTIVE 租借数和失败任务数。 |
| WP8 控制面策略 | 查看控制面、清理执行、脱敏导出、记录上限、默认 TTL 和导出权限。 |
| 数据集 tab | 创建/保存/归档数据集，导入记录摘要，查看记录 digest，生成数据集脱敏导出摘要。 |
| 账号池 tab | 创建/保存/禁用/归档账号池，新增或编辑账号摘要，替换 `secretRef`。 |
| 租借 tab | 申请租借、续租、释放账号，查看 lease token digest 和租借脱敏导出摘要。 |
| 清理任务 tab | 创建准备/刷新/清理/回滚任务控制面记录，查看失败摘要并重试。 |

## 4. 数据集操作

1. 进入 `测试数据` 后选择 `数据集` tab。
2. 在数据集表单填写 `projectId`、`code`、`名称`、状态、可选 `applicationId`/`environmentId`、敏感级别、`sourceType` 和 `sourceRefDigest`。
3. 在 `schema JSON` 中填写字段结构摘要，在 `cleanupPolicy JSON` 中填写清理策略摘要。两者必须是 JSON object。
4. 点击 `创建` 新增数据集。选择列表中已有数据集后，表单会回填详情，可点击 `保存` 更新。
5. 不再允许新引用的数据集点击 `归档`，归档后历史详情仍可只读查看。
6. 在 `导入记录摘要` 中填写 `recordKey`、`recordDigest`、可选 `externalRefDigest`、`tags` 和 `maskedSummary JSON`，点击 `导入`。
7. 记录摘要区域只展示 record key、record digest 和脱敏摘要键值摘要；不得录入或期望展示生产敏感原文。

## 5. 数据集脱敏导出摘要

1. 选择目标数据集。
2. 确认策略区 `脱敏导出=ENABLED`，且当前账号具备 `testData:export`。
3. 在 `脱敏导出摘要` 区点击 `导出摘要`。
4. 导出结果展示 `schemaVersion`、记录数、字段数、敏感字段数、导出时间、redaction policy、record digest、tags 和 `maskedSummaryKeys`。
5. 当前导出是控制面 JSON 摘要视图，不提供 CSV/JSON 文件下载。
6. 导出结果不展示 maskedSummary 值、完整记录正文、`secretRef` 原文、token、cookie 或 Authorization header。

## 6. 账号池和账号摘要操作

1. 选择 `账号池` tab。
2. 在账号池表单填写 `projectId`、`code`、`名称`、状态、可选 `applicationId`/`environmentId`、默认 TTL 和 `leasePolicy JSON`。
3. 点击 `创建` 新增账号池。选择已有账号池后，可点击 `保存` 更新策略，点击 `禁用` 阻断新租借，点击 `归档` 关闭后续维护。
4. 在 `新增账号摘要` 中填写 `accountKey`、`displayName`、状态、`roleTags`、`lastHealthStatus`、`lastHealthSummary`、`scopeSummary JSON` 和 `secretRef`。
5. 新增账号必须输入 `secretRef`；编辑已有账号时 `secretRef` 留空表示不替换。
6. 保存成功后 `secretRef` 输入框会清空，页面只展示 `secretRefDigest` 短摘要。
7. 点击账号卡片可进入编辑模式；点击 `新增` 可清空选择并新增下一条账号摘要。

安全边界：

1. `secretRef` 只作为写入输入，不能在列表、详情、toast、导出面板或错误提示中回显。
2. `scopeSummary JSON` 会过滤 password、token、cookie、secret、credential、Authorization 等敏感 key。
3. `lastHealthSummary` 适合记录简短健康摘要，不应保存登录响应正文、cookie、token 或完整错误 payload。

## 7. 租借、续租和释放

1. 选择 `租借` tab。
2. 填写 `projectId`、`poolId`、`holderType`、`holderRef`、`requestKey`、`ttlSeconds`、可选 `applicationId`/`environmentId` 和 `roleTags`。
3. 点击 `申请`。同一项目下相同 `requestKey` 会按服务端幂等规则返回既有租借；payload 不一致时会返回冲突。
4. 选择租借记录后，可在 `续租 TTL` 中填写新的 TTL，点击 `续租`。
5. 释放前选择 `释放账号状态`，必要时填写 `释放原因`，点击 `释放`。
6. 释放成功后租借进入终态，账号按选择状态回到 `AVAILABLE`、进入 `LOCKED` 或 `DISABLED`。

排障时优先记录页面展示的 traceId、lease id、holderRef、requestKey、账号池 id 和错误码。前端不会展示租借 token 明文，只展示 `leaseTokenDigest`。

## 8. 租借脱敏导出摘要

1. 选择目标租借记录。
2. 确认策略区 `脱敏导出=ENABLED`，且当前账号具备 `testData:export`。
3. 在 `租借脱敏导出摘要` 区点击 `导出摘要`。
4. 导出结果展示 schema version、租借状态、holder、账号摘要、导出时间、`leaseTokenDigest`、`requestDigest`、`secretRefDigest`、释放原因 digest、scope key、lease policy key、健康摘要 presence 和 redaction policy。
5. 导出结果不展示 `secretRef` 原文、租借 token 明文、释放原因原文、账号健康摘要原文、scopeSummary 值、leasePolicy 值、token、cookie 或 Authorization header。

## 9. 清理任务操作

1. 选择 `清理任务` tab。
2. 查看表单说明：`cleanupEnabled=false` 时，当前环境只记录控制面任务，不执行破坏性清理动作。
3. 填写 `projectId`、可选 `dataSetId`、`taskType`、`requestKey`、`targetRef` 和 `resultSummary JSON`。
4. `taskType` 支持 `PREPARE`、`REFRESH`、`CLEANUP` 和 `ROLLBACK`。
5. 点击 `创建` 生成控制面任务记录。
6. 选择失败或需复核任务后，可填写 `retry requestKey`，点击 `重试`。
7. 任务列表展示 task type、project、requestKey、traceId、状态、attempt、errorCode 和 finished time。

`resultSummary JSON` 会过滤敏感 key。不要把生产数据正文、登录响应、cookie、token、Authorization header 或 SecretProvider 明文写入任务摘要。

## 10. 状态解释

| 状态 | 用户含义 |
|---|---|
| `ACTIVE` / `READY` | 对象可用，可被查询、引用或操作。 |
| `DRAFT` | 草稿数据集，适合保存未完成 schema。 |
| `DISABLED` | 已禁用，阻断新增引用或租借。 |
| `ARCHIVED` | 已归档，只保留历史证据。 |
| `AVAILABLE` | 账号可租借。 |
| `LEASED` | 账号已被 active lease 占用。 |
| `LOCKED` | 账号需人工复核，不应自动回到可用态。 |
| `EXPIRED` | 租借已过期，需按过期回收或人工释放流程处理。 |
| `RELEASED` | 租借已释放。 |
| `PENDING` / `RUNNING` | 清理或准备任务处于等待/执行记录状态。 |
| `SUCCEEDED` | 任务成功收敛。 |
| `FAILED` / `BLOCKED` | 任务失败或被策略阻断，需要排障或重试。 |

## 11. 常见排障

| 现象 | 处理 |
|---|---|
| 看不到 `测试数据` | 检查账号是否具备 `testData:read`。 |
| 创建/保存按钮置灰 | 检查 `testData:manage` 权限、登录状态和当前对象是否已归档。 |
| 租借申请失败 | 检查 `testData:lease` 权限、账号池状态、账号可用数、roleTags、TTL 和 requestKey 幂等冲突。 |
| 续租失败 | 检查租借是否仍为 ACTIVE、TTL 是否超过最大值、账号池是否仍可用。 |
| 释放后账号未回可用 | 检查释放时选择的账号状态；失败策略可把账号置为 LOCKED。 |
| 清理任务没有真实执行 | 查看策略区 `清理执行`；当前默认 `cleanupEnabled=false` 只记录控制面任务。 |
| 导出按钮置灰 | 检查 `testData:export` 权限和 `exportEnabled` 开关。 |
| 页面出现敏感原文 | 立即停止发布或验收，保留 traceId 和截图，重跑 WP8 Playwright smoke 与脱敏测试。 |

## 12. 产品验收清单

1. 用户可不依赖 curl 完成数据集创建、更新、归档、记录摘要导入和数据集脱敏导出摘要查看。
2. 用户可不依赖 curl 完成账号池创建、更新、禁用、归档和账号摘要维护。
3. 用户可不依赖 curl 完成账号租借、续租、释放和租借脱敏导出摘要查看。
4. 用户可不依赖 curl 创建和重试清理任务控制面记录，并理解 `cleanupEnabled=false` 的安全含义。
5. 无权限、空态、加载中、错误、按钮置灰和 traceId/errorCode 展示均可解释。
6. 页面不展示 `secretRef` 原文、账号凭据、租借 token 明文、释放原因导出原文、健康摘要导出原文、生产数据正文、cookie、token 或 Authorization header。
7. 桌面和 390px 窄屏主链路由 WP8 Playwright smoke 覆盖，无页面横向溢出和按钮重叠。
