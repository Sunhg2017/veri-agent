# WP8 测试数据与账号池 - 技术设计与接口契约

| 项目 | 内容 |
|---|---|
| 工作包 | WP8 测试数据与账号池 |
| 角色产出 | 资深服务端架构师 |
| 文档性质 | 技术设计、数据模型、接口契约和服务端质量约束 |
| 当前口径 | 在 `platform-api` 内承载 WP8 领域模块，复用 WP1 项目/应用/环境、RBAC、SecretProvider、审计和 traceId；已补齐脱敏导出文件下载、XXL-JOB 触发的受控 cleanup worker/HTTP adapter 和真实业务账号自动开通 HTTP adapter |
| 版本 | v0.1 |
| 日期 | 2026-06-26 |

## 1. 架构原则

1. WP8 是测试数据和账号池控制面，不是数据执行器、浏览器执行器或调度平台。
2. 所有资源必须绑定项目 scope；应用、环境作为进一步过滤和授权上下文。
3. 账号凭据只保存 `secretRef` 和 digest，不在数据库、日志、审计、API 响应或导出中出现明文。
4. 数据记录只保存脱敏摘要、schema、外部引用和有限样本，不复制生产敏感数据。
5. 账号租借必须使用条件更新或唯一约束保证并发安全，不能依赖前端防重。
6. 清理任务必须幂等，失败证据可审计；破坏性 adapter 只能在显式配置、开关开启和 adapter ready 后执行，且不能保存敏感原文。
7. WP7/WP9 通过应用服务或 API 引用 WP8，不直接读写 WP8 表。

## 2. 模块划分

| 包 | 职责 |
|---|---|
| `testdata.api` | Controller、request/response DTO、OpenAPI contract 注解。 |
| `testdata.application` | 数据集、账号池、租借、清理任务、导出、账号自动开通和审计应用服务。 |
| `testdata.domain` | 数据集、账号池、账号、租借、任务状态机和值对象。 |
| `testdata.infrastructure` | MyBatis mapper、SecretProvider adapter、WP1 context adapter、租借并发 repository、配置型 HTTP cleanup/provisioning adapter。 |
| `testdata.config` | 数据大小、租借 TTL、worker、导出脱敏、清理任务和账号开通 adapter 开关。 |

## 3. 数据模型草案

| 表 | 关键字段 | 说明 |
|---|---|---|
| `test_data_set` | `id`、`project_id`、`application_id`、`environment_id`、`code`、`name`、`status`、`schema_json`、`sensitivity_level`、`cleanup_policy_json`、`source_type`、`source_ref_digest` | 数据集目录和脱敏 schema。 |
| `test_data_record` | `id`、`data_set_id`、`project_id`、`record_key`、`status`、`record_digest`、`masked_summary_json`、`external_ref_digest`、`tags_json` | 数据记录摘要，不保存敏感原文。 |
| `test_data_task` | `id`、`project_id`、`data_set_id`、`task_type`、`status`、`request_key`、`target_ref`、`started_at`、`finished_at`、`error_code`、`error_summary` | 准备、刷新、清理和回滚任务。 |
| `test_account_pool` | `id`、`project_id`、`application_id`、`environment_id`、`code`、`name`、`status`、`lease_policy_json`、`default_ttl_seconds` | 账号池目录。 |
| `test_pooled_account` | `id`、`pool_id`、`project_id`、`account_key`、`display_name`、`status`、`role_tags_json`、`scope_summary_json`、`secret_ref_digest`、`secret_ref_cipher`、`last_health_status` | 可租借账号，凭据只通过 SecretProvider 或加密引用表达。 |
| `test_account_lease` | `id`、`pool_id`、`account_id`、`project_id`、`status`、`holder_type`、`holder_ref`、`request_key`、`lease_token_digest`、`expires_at`、`released_at`、`release_reason` | 租借记录和过期控制。 |
| `test_account_role_matrix` | `id`、`project_id`、`pool_id`、`role_code`、`resource_scope_json`、`menu_scope_json`、`scenario_tags_json` | 权限账号矩阵。 |

约束要求：

- 所有生产表必须有 `created_at`、`updated_at`、`created_by`、`updated_by` 或明确例外。
- `code` 在项目内唯一；`request_key` 在项目和任务/租借类型内幂等。
- active lease 对同一 `account_id` 必须唯一，除非账号池策略声明 read-only shared。
- 状态字段必须有 check constraint。
- `secret_ref_cipher` 或 SecretProvider 持久化字段不得在 mapper 查询摘要中返回。
- 需要为 `project_id/application_id/environment_id/status`、`expires_at`、`request_key` 建索引。

## 4. 配置项草案

| 配置 | 默认值 | 说明 |
|---|---|---|
| `veri-agent.test-data.enabled` | `true` | WP8 控制面总开关。 |
| `veri-agent.test-data.record-max-count` | `10000` | 单数据集记录数量上限。 |
| `veri-agent.test-data.record-summary-max-bytes` | `2048` | 单条记录脱敏摘要大小上限。 |
| `veri-agent.test-data.default-lease-ttl-seconds` | `1800` | 默认租借 TTL。 |
| `veri-agent.test-data.max-lease-ttl-seconds` | `14400` | 最大租借 TTL。 |
| `veri-agent.test-data.worker-enabled` | `true` | 是否允许 WP8 worker 执行；生产运行时仍需 `veri-agent.xxl-job.enabled=true` 并配置 `testDataWorkerJob`。 |
| `veri-agent.test-data.worker-interval-ms` | `5000` | worker 建议轮询间隔，供外部调度配置和 health 展示使用。 |
| `veri-agent.test-data.worker-initial-delay-ms` | `30000` | worker 建议启动延迟，供外部调度配置和 health 展示使用。 |
| `veri-agent.test-data.worker-id` | `wp8-test-data-worker` | 记录在 worker tick、审计和任务摘要中的 worker 标识。 |
| `veri-agent.test-data.worker-task-batch-size` | `10` | 单个 worker tick 可认领的待处理任务上限。 |
| `veri-agent.test-data.lease-recovery-batch-size` | `50` | 单个 worker tick 可回收的过期租约上限。 |
| `veri-agent.test-data.account-health-check-batch-size` | `100` | 单个 worker tick 可扫描的账号健康检查上限。 |
| `veri-agent.test-data.cleanup-enabled` | `false` | 默认不执行破坏性清理，只记录任务。 |
| `veri-agent.test-data.cleanup-adapter-mode` | `DISABLED` | 清理 adapter 模式；当前支持 `DISABLED/HTTP`。 |
| `veri-agent.test-data.cleanup-adapter-url` | 空 | HTTP cleanup adapter 地址；仅 mode=HTTP 且 URL 非空时 ready。 |
| `veri-agent.test-data.cleanup-adapter-token` | 空 | 调用 cleanup adapter 的 Bearer token，不出现在 health、审计或前端。 |
| `veri-agent.test-data.cleanup-adapter-timeout-ms` | `5000` | cleanup adapter HTTP 超时。 |
| `veri-agent.test-data.account-provisioning-enabled` | `false` | 是否允许 worker 按账号池策略自动补齐业务账号。 |
| `veri-agent.test-data.account-provisioning-adapter-mode` | `DISABLED` | 账号开通 adapter 模式；真实业务开通要求 `HTTP`，`LOCAL_SECRET_REF` 仅作本地指针演练。 |
| `veri-agent.test-data.account-provisioning-adapter-url` | 空 | HTTP provisioning adapter 地址；mode=HTTP 时必须配置，且 URL 非空才进入真实 ready。 |
| `veri-agent.test-data.account-provisioning-adapter-token` | 空 | 调用 provisioning adapter 的 Bearer token，不出现在 health、审计或前端。 |
| `veri-agent.test-data.account-provisioning-adapter-timeout-ms` | `5000` | provisioning adapter HTTP 超时。 |
| `veri-agent.test-data.account-provisioning-batch-size` | `20` | 单个 worker tick 扫描自动开通账号池上限。 |
| `veri-agent.test-data.export-enabled` | `true` | 允许导出脱敏摘要。 |

## 5. 接口契约草案

统一前缀：`/api/v1/test-data`

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| `GET` | `/health` | 匿名健康检查 | 返回 WP8 开关、限制、worker 配置和安全策略摘要；控制面 worker 已可观测，但 `cleanup-enabled=false` 时仍不会执行破坏性清理。 |
| `POST` | `/data-sets` | `testData:manage` | 创建数据集。 |
| `GET` | `/data-sets` | `testData:read` | 按项目、应用、环境、状态分页查询数据集。 |
| `GET` | `/data-sets/{id}` | `testData:read` | 查询数据集详情和记录摘要。 |
| `PATCH` | `/data-sets/{id}` | `testData:manage` | 更新名称、状态、schema、清理策略。 |
| `POST` | `/data-sets/{id}/archive` | `testData:manage` | 归档数据集，阻断新引用。 |
| `POST` | `/data-sets/{id}/records` | `testData:manage` | 批量写入脱敏记录摘要或外部引用。 |
| `POST` | `/data-sets/{id}/generate-records` | `testData:manage` | 对 `GENERATED` 数据集按 schema 自动生成脱敏记录摘要。 |
| `GET` | `/data-sets/{id}/export` | `testData:export` | 导出数据集脱敏摘要、字段计数、摘要键名和 redaction policy。 |
| `GET` | `/data-sets/{id}/export/download` | `testData:export` | 下载数据集脱敏导出 JSON 文件，内容与 `/export` 摘要一致。 |
| `POST` | `/data-tasks` | `testData:cleanup` | 创建准备、刷新、清理或回滚任务。 |
| `GET` | `/data-tasks` | `testData:read` | 查询任务列表。 |
| `POST` | `/account-pools` | `testData:manage` | 创建账号池。 |
| `GET` | `/account-pools` | `testData:read` | 分页查询账号池。 |
| `GET` | `/account-pools/{id}` | `testData:read` | 查询账号池详情、账号摘要和策略。 |
| `PATCH` | `/account-pools/{id}` | `testData:manage` | 更新账号池名称、状态、策略和默认 TTL。 |
| `POST` | `/account-pools/{id}/disable` | `testData:manage` | 禁用账号池，阻断新增账号和后续租借。 |
| `POST` | `/account-pools/{id}/archive` | `testData:manage` | 归档账号池，阻断后续维护。 |
| `POST` | `/account-pools/{id}/accounts` | `testData:manage` | 新增账号摘要和 `secretRef`。 |
| `PATCH` | `/accounts/{id}` | `testData:manage` | 更新账号状态、角色标签、健康摘要或替换 `secretRef`。 |
| `POST` | `/leases` | `testData:lease` | 按角色、标签、环境申请账号租借。 |
| `GET` | `/leases` | `testData:read` | 查询租借记录。 |
| `POST` | `/leases/{id}/renew` | `testData:lease` | 续租 active lease。 |
| `POST` | `/leases/{id}/release` | `testData:lease` | 释放账号并可创建清理任务。 |
| `GET` | `/leases/{id}/export` | `testData:export` | 导出租借脱敏摘要。 |
| `GET` | `/leases/{id}/export/download` | `testData:export` | 下载租借脱敏导出 JSON 文件，内容与 `/export` 摘要一致。 |

### M2 已落地切片

当前代码已实现并验证以下数据集控制面后端路径：

- `POST /api/v1/test-data/data-sets`
- `GET /api/v1/test-data/data-sets`
- `GET /api/v1/test-data/data-sets/{id}`
- `PATCH /api/v1/test-data/data-sets/{id}`
- `POST /api/v1/test-data/data-sets/{id}/archive`
- `POST /api/v1/test-data/data-sets/{id}/records`
- `POST /api/v1/test-data/data-sets/{id}/generate-records`
- `GET /api/v1/test-data/data-sets/{id}/export`
- `GET /api/v1/test-data/data-sets/{id}/export/download`

实现约束：

1. `testData:read/manage` 通过 `@RequirePermission` 和项目作用域解析器生效。
2. `schema_json` 仅允许结构化对象，`fields` 的 `name/type` 做有限白名单校验。
3. `cleanup_policy_json` 只保存摘要，不执行破坏性清理。
4. 数据集详情和记录列表只返回摘要、digest、tags 和时间戳。
5. 数据集归档后禁止继续修改或导入记录摘要。
6. 控制面总开关 `veri-agent.test-data.enabled=false` 时，业务 API 返回 `INVALID_STATE`，health API 保持可观测。
7. 数据集脱敏导出受 `testData:export` 和 `veri-agent.test-data.export-enabled` 控制，只返回 `schemaVersion/exportedAt/dataSet/recordCount/schemaFieldCount/sensitiveFieldCount/records/redactionPolicy`；其中 records 只包含 `recordKey/recordDigest/externalRefDigest/tags/maskedSummaryKeys/createdAt/updatedAt`，不返回 maskedSummary 值、完整 record payload、secretRef 原文、token、cookie 或 Authorization header。
8. `POST /data-sets/{id}/generate-records` 仅允许 `sourceType=GENERATED` 且状态为 `DRAFT/READY` 的数据集调用；生成逻辑只基于 schema 字段类型构造规则化样本摘要，生成后仍通过既有 `test_data_record` 摘要模型持久化，不新增敏感字段存储面。
9. `/data-sets/{id}/export/download` 复用同一 export view，经 `application/json` attachment 返回，不新增对象存储或敏感字段面。

### M3 已落地切片

当前代码已实现并验证以下账号池控制面后端路径：

- `POST /api/v1/test-data/account-pools`
- `GET /api/v1/test-data/account-pools`
- `GET /api/v1/test-data/account-pools/{id}`
- `PATCH /api/v1/test-data/account-pools/{id}`
- `POST /api/v1/test-data/account-pools/{id}/disable`
- `POST /api/v1/test-data/account-pools/{id}/archive`
- `POST /api/v1/test-data/account-pools/{id}/accounts`
- `PATCH /api/v1/test-data/accounts/{id}`

实现约束：

1. 账号池和账号均通过 `@RequirePermission` 和项目作用域解析器生效。
2. 账号池 code 在项目内唯一，账号 key 在 pool 内唯一。
3. `secretRef` 只接受 `secret://` 写入引用；服务端只保存 SHA-256 digest，不保存明文或密文值。
4. 账号池详情和账号详情只返回摘要、digest、角色标签、健康状态和时间戳。
5. `AVAILABLE/LOCKED/DISABLED/ARCHIVED` 属于 M3 人工维护状态，`LEASED/EXPIRED` 留给 M4 租借流程。
6. 账号池归档后禁止继续维护；账号池禁用后禁止新增账号。
7. 控制面总开关关闭时，账号池维护 API 返回 `INVALID_STATE`，health API 仍可观测。

### M4 已落地切片

当前代码已实现并验证以下租借、释放和清理任务后端路径：

- `POST /api/v1/test-data/leases`
- `GET /api/v1/test-data/leases`
- `GET /api/v1/test-data/leases/{id}`
- `GET /api/v1/test-data/leases/{id}/export`
- `GET /api/v1/test-data/leases/{id}/export/download`
- `POST /api/v1/test-data/leases/{id}/renew`
- `POST /api/v1/test-data/leases/{id}/release`
- `POST /api/v1/test-data/data-tasks`
- `GET /api/v1/test-data/data-tasks`
- `GET /api/v1/test-data/data-tasks/{id}`
- `POST /api/v1/test-data/data-tasks/{id}/retry`

实现约束：

1. 租借 API 使用 `testData:lease`，清理任务 API 使用 `testData:cleanup`，查询使用 `testData:read`；列表查询未带 `projectId` 时按平台 scope 处理并由 RBAC 决定是否允许。
2. `POST /leases` 按 `projectId + requestKey` 幂等，重复请求返回已有租借；首次租借只选择 `AVAILABLE` 账号，并通过条件更新将账号置为 `LEASED`。
3. DB 侧 `uk_test_account_lease_active_account` 保证同一账号最多一个 active lease；repository contract 覆盖重复 active lease 拒绝。
4. 租借响应只返回 `leaseTokenDigest`、账号摘要和 `secretRefDigest`；不返回凭据明文、租借 token 明文或 `secret_ref_cipher`。
5. `POST /leases/{id}/renew` 只允许未过期 `ACTIVE` 租借续租，TTL 不得超过 `max-lease-ttl-seconds`。
6. `POST /leases/{id}/release` 为终态幂等；账号释放后默认回到 `AVAILABLE`，失败场景可转入 `LOCKED`。
7. `TestDataWorkerService` 由外部 XXL-JOB handler `testDataWorkerJob` 触发，保留 `runOnce()` 作为手工维护和测试入口；单次 tick 顺序执行过期租约回收、真实账号自动开通、账号健康检查和待处理任务认领；方法级同步与 repository 条件更新共同避免同 JVM 重入和重复认领。
8. `data-tasks` 当前由 worker 推进准备、刷新、回滚和清理任务终态；`PREPARE/REFRESH` 校验数据集为 `READY` 并写入摘要，`ROLLBACK` 记录控制面成功，`CLEANUP` 在 `cleanupEnabled=false` 时以 `CLEANUP_TASK_NOT_ALLOWED` 失败，在 adapter 未 ready 时以 `CLEANUP_ADAPTER_NOT_READY` 失败，adapter ready 后调用受控清理 adapter 并记录 provider、externalCleanupId、影响数量和安全摘要。
9. 账号健康检查只做控制面一致性修复，不登录外部系统、不读取凭据：无 active lease 的 `LEASED/EXPIRED` 账号会被收敛到 `LOCKED`，存在 active lease 的 `AVAILABLE` 账号会被修正回 `LEASED`。
10. 租借脱敏导出受 `testData:export` 和 `veri-agent.test-data.export-enabled` 控制，只返回 `schemaVersion/exportedAt/lease/pool/account/lifecycleSummary/redactionPolicy`；其中 lease 只包含 holder、状态、时间戳、`requestDigest`、`leaseTokenDigest`、释放原因存在标记和释放原因 digest，pool/account 只包含摘要、digest、安全 key 名和健康状态，不返回 secretRef 原文、租借 token 明文、释放原因原文、健康摘要原文、scope/lease policy 值、token、cookie 或 Authorization header。
11. `/leases/{id}/export/download` 复用同一 export view，经 `application/json` attachment 返回，不暴露租借 token 明文、释放原因原文或 `secretRef` 原文。

### M8J/M8K 已落地切片：文件下载、cleanup adapter 和真实账号自动开通

新增应用层 port：

- `TestDataCleanupAdapter`：提供 `ready/provider/cleanup`，当前实现 `ConfiguredTestDataCleanupAdapter` 支持 HTTP POST JSON。
- `TestAccountProvisioningAdapter`：提供 `ready/provider/provision`，当前实现 `ConfiguredTestAccountProvisioningAdapter` 支持 `LOCAL_SECRET_REF` 和 HTTP POST JSON；其中只有 HTTP mode + URL + 全局开关 + XXL-JOB worker runtime ready 计入真实业务账号自动开通 ready。

cleanup adapter 请求字段只包含任务和数据集摘要：`taskId/projectId/dataSetId/dataSetCode/dataSetStatus/recordCount/cleanupPolicyKeys/taskType/requestKey/targetRef/attempt/workerId/requestedAt`。成功响应可返回：

```json
{
  "success": true,
  "externalCleanupId": "cleanup-20260624-001",
  "affectedResourceCount": 3,
  "summary": {
    "deletedRows": 3,
    "adapter": "sandbox-cleaner"
  }
}
```

失败响应可返回 `success=false/errorCode/errorSummary`；非 2xx 响应也按失败处理。`summary` 只保留安全 key 和标量值，过滤 secret、token、password、cookie、credential、authorization 等敏感 key。

账号池 `leasePolicy.provisioning` 示例：

```json
{
  "provisioning": {
    "enabled": true,
    "minAvailable": 2,
    "maxAccounts": 3,
    "accountKeyPrefix": "auto-admin",
    "displayNamePrefix": "Auto admin",
    "secretRefPrefix": "secret://wp8/provisioned/admin",
    "roleTags": ["admin"],
    "scopeSummary": {
      "tenant": "alpha"
    }
  }
}
```

自动开通只扫描 `READY` 账号池，并受 `account-provisioning-enabled`、XXL-JOB worker runtime、HTTP adapter ready、`minAvailable/maxAccounts` 和 batch size 共同限制。`LOCAL_SECRET_REF` 不调用真实业务系统，不会被 health 标记为真实 ready。HTTP adapter 请求中的 `accountKey` 同时作为外部开户幂等键，adapter 必须对重复请求返回同一账号或可审计冲突；响应中的 `scopeSummary/summary` 同样过滤敏感 key；返回的 `secretRef` 只用于服务端写入和 digest/cipher 处理，不在响应、前端或审计中回显原文。

## 6. 关键请求体

### 创建数据集

```json
{
  "projectId": "uuid",
  "applicationId": "uuid",
  "environmentId": "uuid",
  "code": "checkout-smoke-users",
  "name": "Checkout smoke users",
  "schema": {
    "fields": [
      { "name": "userId", "type": "STRING", "sensitive": false },
      { "name": "phone", "type": "STRING", "sensitive": true }
    ]
  },
  "cleanupPolicy": {
    "mode": "MANUAL_CONFIRM",
    "ttlSeconds": 86400
  },
  "sourceType": "MANUAL"
}
```

### 生成模拟记录摘要

```json
{
  "count": 3,
  "recordKeyPrefix": "checkout-user",
  "tags": ["smoke", "generated"]
}
```

响应只返回摘要与 digest：

```json
{
  "dataSetId": "uuid",
  "generatedCount": 3,
  "records": [
    {
      "recordKey": "checkout-user-001",
      "recordDigest": "64-char-sha256",
      "maskedSummary": {
        "customerId": "customerId-sample-1",
        "active": false,
        "riskScore": "riskScore-masked-1"
      },
      "tags": ["generated", "synthetic", "smoke"]
    }
  ]
}
```

### 新增账号

```json
{
  "accountKey": "qa-admin-01",
  "displayName": "QA Admin 01",
  "roleTags": ["ADMIN", "APPROVER"],
  "scopeSummary": {
    "projectId": "uuid",
    "applicationId": "uuid"
  },
  "secretRef": "secret://wp8/accounts/qa-admin-01"
}
```

响应只能返回：

```json
{
  "id": "uuid",
  "accountKey": "qa-admin-01",
  "displayName": "QA Admin 01",
  "status": "AVAILABLE",
  "roleTags": ["ADMIN", "APPROVER"],
  "secretRefDigest": "sha256:..."
}
```

### 创建租借

```json
{
  "projectId": "uuid",
  "applicationId": "uuid",
  "environmentId": "uuid",
  "poolId": "uuid",
  "roleTags": ["ADMIN"],
  "holderType": "EXECUTION_RUN",
  "holderRef": "run-uuid",
  "ttlSeconds": 1800,
  "requestKey": "wp9-run-uuid-admin"
}
```

响应只能返回摘要和 digest：

```json
{
  "id": "uuid",
  "status": "ACTIVE",
  "holderType": "EXECUTION_RUN",
  "holderRef": "run-uuid",
  "requestKey": "wp9-run-uuid-admin",
  "leaseTokenDigest": "64-char-sha256",
  "account": {
    "id": "uuid",
    "accountKey": "qa-admin-01",
    "status": "LEASED",
    "roleTags": ["ADMIN"],
    "secretRefDigest": "64-char-sha256"
  }
}
```

### 创建清理任务

```json
{
  "projectId": "uuid",
  "dataSetId": "uuid",
  "taskType": "CLEANUP",
  "requestKey": "cleanup-run-uuid",
  "targetRef": "lease:run-uuid",
  "resultSummary": {
    "reason": "release"
  }
}
```

## 7. 状态机

账号状态：

```text
AVAILABLE -> LEASED -> AVAILABLE
AVAILABLE -> LOCKED -> AVAILABLE
AVAILABLE -> DISABLED -> ARCHIVED
LEASED -> EXPIRED -> LOCKED
LEASED -> RELEASED -> AVAILABLE
```

租借状态：

```text
ACTIVE -> RELEASED
ACTIVE -> EXPIRED
ACTIVE -> REVOKED
```

数据任务状态：

```text
PENDING -> RUNNING -> SUCCEEDED
PENDING -> RUNNING -> FAILED
PENDING -> CANCELED
FAILED -> PENDING
```

## 8. 跨 WP 契约

| 消费方 | 契约 |
|---|---|
| WP6 | 可在接口自动化 run request 中引用 `dataSetRef` 或 `accountLeaseRef`；WP6 不解析账号密码。 |
| WP7 | UI/E2E runner 通过 `accountLeaseRef` 取账号摘要和 `secretRefDigest`，不接收 `secretRef` 原文；后续凭据注入必须由受控 SecretProvider adapter 以 `accountLeaseRef` 为句柄完成。 |
| WP9 | execution plan 的 `API_TEST` 节点可声明 `accountLease.accountPoolRef/applicationId/environmentId/roleTags/ttlSeconds/requestKey`；运行时调用 WP8 申请 lease，结束时释放并只保存安全摘要。 |
| WP10 | 报告只读取 WP8 准备/租借/清理摘要，不展示 secret、数据正文或敏感字段。 |

### M5 已落地切片

当前代码已实现 WP8 跨 WP 应用层契约切片，覆盖：

- `AcquireExecutionAccountLeaseCommand` / `ReleaseExecutionAccountLeaseCommand`
- `TestDataExecutionAccountLeaseResponse`
- `TestDataRunnerAccountContractResponse`
- `TestDataReportEvidenceQuery`
- `TestDataReportEvidenceResponse`
- `TestDataCrossWpReferenceService`

实现约束：

1. WP9 通过 `TestDataCrossWpReferenceService#acquireExecutionRunLease` 获取 `accountLeaseRef`，由 WP8 继续持有租借状态机，WP9 只保存租借引用和脱敏摘要。
2. WP7 通过 `runnerAccountContract` 获取账号摘要和 `secretRefDigest`，不接收密码、token、cookie、租借 token 明文或 `secret://` 原文。
3. WP10 通过 `reportEvidence` 读取准备、租借和清理证据，只返回引用、状态、计数和 digest，不返回原始数据正文或清理 payload。
4. 跨 WP 读取仍通过 WP8 repository 和应用服务完成，不直连其他工作包表。
5. 定向契约测试已覆盖 WP9 lease adapter、WP7 runner contract 和 WP10 summary contract 的脱敏边界。

字段白名单：

| 契约 | 允许字段 | 禁止字段 |
|---|---|---|
| WP9 lease adapter | `accountLeaseRef`、`projectId`、`status`、`expiresAt`、`releasedAt`、账号摘要、策略布尔值 | 密码、token、cookie、`secret://`、`secretRef` 原文、租借 token 明文、`secret_ref_cipher`、数据正文 |
| WP7 runner contract | `accountLeaseRef`、租借状态、过期时间、账号 key/displayName/status/roleTags/scopeSummary、`secretRefDigest`、凭据策略布尔值 | 密码、token、cookie、`secret://`、`secretRef` 原文、租借 token 明文 |
| WP10 report evidence | `dataSetRef/accountLeaseRef/cleanupTaskRef`、状态、计数、schema 字段数、cleanup policy digest、targetRef digest、resultSummary digest、resultSummary keys、traceId、时间戳、`holderType/holderRef`、脱敏账号摘要 | record payload、masked summary value、cleanup result value、错误正文原文、密码、token、cookie、`secret://`、`secretRef` 原文 |

WP9 可持久化字段白名单：`accountLeaseRef`、执行 run ref、`requestKey`、租借状态、`expiresAt`、`releasedAt`、账号摘要中的非敏感展示字段和 `secretRefDigest`。WP9 不保存 `leaseTokenDigest`、`secretRef` 原文、凭据明文或清理结果值。释放失败或重复释放由 WP8 租借状态机返回稳定错误；WP9 只能按 `accountLeaseRef + executionRunRef` 重试释放，不得直接更新 WP8 表。

账号摘要中的 `scopeSummary` 只能保存和返回项目、应用、环境、角色、菜单或资源范围等非敏感摘要；不得包含 secret、token、cookie、密码、登录响应、业务数据正文或外部凭据引用。

## 9. 审计事件草案

| 事件 | 场景 |
|---|---|
| `test_data.data_set.created` | 创建数据集。 |
| `test_data.data_set.updated` | 更新数据集。 |
| `test_data.data_set.archived` | 归档数据集。 |
| `test_data.record.imported` | 写入数据记录摘要。 |
| `test_data.task.created` | 创建准备或清理任务。 |
| `test_data.task.completed` | 任务完成或失败。 |
| `test_data.account_pool.created` | 创建账号池。 |
| `test_data.account.updated` | 新增或更新账号摘要。 |
| `test_data.account.leased` | 账号租借成功。 |
| `test_data.account.released` | 账号释放。 |
| `test_data.account.lease_expired` | 租借过期回收。 |
| `test_data.exported` | 导出脱敏摘要。 |

审计 payload 只允许包含资源 ID、状态、digest、计数、操作者、traceId 和错误码，不允许包含 `secretRef` 原文、密码、token、cookie 或数据正文。

## 10. 错误码草案

| 错误码 | 场景 |
|---|---|
| `TEST_DATA_SET_NOT_READY` | 数据集不可被引用。 |
| `TEST_DATA_RECORD_TOO_LARGE` | 记录摘要超过限制。 |
| `TEST_DATA_SCOPE_DENIED` | 项目、应用或环境 scope 越权。 |
| `ACCOUNT_POOL_DISABLED` | 账号池禁用。 |
| `ACCOUNT_NOT_AVAILABLE` | 没有满足条件的可用账号。 |
| `ACCOUNT_LEASE_CONFLICT` | 并发租借冲突或 requestKey payload 不一致。 |
| `ACCOUNT_LEASE_EXPIRED` | 租借已过期，不能续租或释放为成功。 |
| `ACCOUNT_SECRET_REF_INVALID` | secretRef 格式或 SecretProvider 校验失败。 |
| `CLEANUP_TASK_NOT_ALLOWED` | 清理开关关闭或策略不允许自动清理。 |

## 11. 安全和性能约束

1. 所有租借查询必须按项目 scope 和权限过滤，禁止通过 ID 枚举跨项目账号。
2. 账号租借使用数据库条件更新或唯一 active lease 索引，后续可引入 Redis 锁但不能作为唯一一致性来源。
3. 清理任务默认只记录；执行动作必须显式开启 `cleanup-enabled`，且由 HTTP adapter 明确 allowlist、幂等和影响范围。
4. 列表接口分页最大 100，记录摘要批量写入需要限制条数和大小。
5. 导出 API 必须按字段白名单生成，禁止导出完整 record payload 或 secretRef 原文。

## 12. 验证要求

1. 单元测试覆盖租借选择、并发冲突、状态流、TTL、续租、释放和过期回收。
2. Controller 测试覆盖权限、项目 scope、错误码、分页和 traceId。
3. DB validation 覆盖 WP8 表、约束、索引、runtime role 权限和幂等迁移。
4. OpenAPI contract 测试覆盖真实路径、权限注解和脱敏响应字段。
5. Java 生产文件必须满足 1200 行门禁，并按核心状态机和并发逻辑补充必要注释。

### M3 当前验证口径

M3 后端账号池切片当前采用以下最小验证：

```bash
mvn -B -pl platform-api -Dtest=TestAccountPoolControllerTest,TestAccountPoolServiceTest,TestDataOpenApiContractTest,TestDataHealthControllerTest,OpenApiContractTest,PermissionCodeUsageTest,PersistenceProfileBoundaryTest test
bash scripts/platform_api_java_line_guard.sh
bash db/validation/run_wp1_db_validation.sh
```

租借并发、跨 WP adapter 和前端页面验证不属于 M3 后端账号池切片完成定义，已按 M4-M6 分阶段承接；数据集脱敏导出摘要已在 M6C 补齐，租借脱敏导出摘要已在 M6D 补齐，真实 cleanup worker、导出文件下载和真实业务账号自动开通 HTTP adapter 已在 M8J/M8K 补齐。外部 HTTP 并发压测和更细粒度前端筛选分页仍按后续增强推进。

### M8B/M8C 文档化运维边界

M8B/M8C 不改变本技术契约的 API、DB、权限或状态机实现，只补齐操作说明与运维 Runbook。服务端边界保持：

1. 所有控制面和导出操作继续由 RBAC 与项目 scope 决定最终准入。
2. `secretRef` 只作为账号摘要写入输入，查询、审计、导出和前端展示只允许 digest。
3. active lease 唯一性继续由数据库条件更新和唯一约束兜底。
4. `cleanup-enabled=false` 时只记录清理任务控制面，不执行破坏性 adapter。
5. `export-enabled=false` 可作为导出风险的首要回滚开关。

### M8I 发布准出收口边界

M8I 当时不改变本技术契约的 API、DB、权限、状态机、配置项或跨 WP 应用层契约，只把当时实现能力和后续专项边界集中同步到发布准出说明、剩余工作盘点、README 和当前实现基线。M8J/M8K 已在该基线后补齐文件下载、cleanup worker/HTTP adapter 和真实业务账号自动开通 HTTP adapter。服务端准出口径调整为：

1. 当前 WP8 范围无剩余 P0 服务端功能开发项；目标环境发布前仍需按 release gate 执行实际验证。
2. 真实文件下载、受控 cleanup worker 和真实业务账号自动开通已经提供配置化实现；真实账号开通要求 HTTP provisioning adapter，不把 `LOCAL_SECRET_REF` 演练模式计入生产 ready；外部 HTTP 并发压测、生产容量指标和多实例运维演练仍不属于本轮服务端完成定义。
3. WP7 runner 凭据注入、WP9 调度自动申请/释放和 WP10 报告证据消费均已消费 WP8 已提供的引用与摘要契约；后续只在对应 WP 内继续扩展真实执行、报告和运维能力，不再作为 WP8 当前缺口。
4. 本切片触达 Java 生产代码，必须执行 Java 行数门禁和阿里巴巴 Java 开发手册自查；核心逻辑已在 worker、adapter 和下载方法附近补充必要注释。
