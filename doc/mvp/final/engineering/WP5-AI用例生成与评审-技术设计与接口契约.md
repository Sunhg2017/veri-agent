# WP5 AI 用例生成与评审 - 技术设计与接口契约

| 项目 | 内容 |
|---|---|
| 工作包 | WP5 AI 用例生成与评审 |
| 角色产出 | 资深服务端架构师 |
| 文档性质 | 技术设计、数据模型、接口契约和服务端质量约束 |
| 当前口径 | WP5 在 `platform-api` 内实现为独立领域模块，不新增独立部署服务；模块内应用服务按任务、生成、评审、质量、发布、冲突和报告拆分；任务本域审计链摘要由报告服务聚合 WP5 任务、评审和发布记录；Prompt 趋势按版本输出聚合准出摘要和准出状态分布；任务创建支持显式 API/页面/业务流上下文资产，并将上下文裁剪策略、`contextAssemblyPolicy` v2 装配策略安全边界、平台默认治理状态快照和 `contextPolicyOperations` v2 运营聚合快照暴露到 health、任务诊断、模型上下文打包、任务上下文摘要和任务全量报告；任务报告导出增加治理聚合行、生成编排策略聚合行、上下文装配策略 v2 聚合行、上下文策略治理聚合行、上下文策略运营 v2 聚合行、模型观测策略聚合行、质量准出阈值策略聚合行、导出审计策略聚合行、安全扫描策略聚合行、归档策略聚合行、Prompt 校准策略聚合行、发布补偿策略聚合行、报告 manifest 聚合行和最终安全扫描 |
| 版本 | v0.23 |
| 日期 | 2026-05-30 |

## 1. 架构原则

1. WP5 是任务/领域拆分，不是独立部署服务拆分，包路径建议为 `com.songhg.veri.agent.testdesign`；模块内部不得用单一 God Object 承载任务、生成、质量、发布、冲突和报告职责。
2. API 使用统一 envelope，JSON 字段使用 camelCase，分页使用 `index`、`size`。
3. SQL 放在 MyBatis Mapper XML，不在 Java 代码拼接 SQL。
4. 不恢复多租户，不在 WP5 表中增加 `tenant_id`。
5. WP5 不直接调用模型厂商 SDK，只通过 WP2 模型接入应用服务。
6. WP5 不直接读写 WP3 表，只通过 WP3 应用服务读取资产、创建用例和创建追踪关系。
7. 生成候选必须人工评审后才能发布到 WP3。
8. Java 代码需符合《阿里巴巴 Java 开发手册》和仓库 `AGENTS.md` 注释准入要求。

## 2. 模块边界

```mermaid
flowchart LR
    UI["portal-web 用例生成工作台"] --> API["WP5 Controller"]
    API --> TASK["TestDesignTaskService"]
    API --> REV["TestDesignCandidateReviewService"]
    API --> QUAL["TestDesignQualityService"]
    API --> PUB["TestDesignPublishService"]
    API --> CONFLICT["TestDesignConflictService"]
    API --> REPORT["TestDesignTaskReportService"]
    TASK --> GEN["TestDesignGenerationService"]
    GEN --> CTX["WP5 generation context assembly"]
    CTX --> WP3R["WP3 Asset Services"]
    GEN --> WP2["WP2 ModelAccessService"]
    PUB --> WP3W["WP3 TestCase/TraceLink Services"]
    CONFLICT --> WP3W
    TASK --> REPO["WP5 Repository"]
    REV --> REPO
    QUAL --> REPO
    PUB --> REPO
    CONFLICT --> REPO
    REPORT --> REPO
    TASK --> AUDIT["WP1 Audit/Authorization"]
    REV --> AUDIT
    PUB --> AUDIT
    CONFLICT --> AUDIT
```

| 组件 | 职责 |
|---|---|
| `TestDesignTaskController` | 任务创建、列表、详情、取消、重试。 |
| `TestDesignCandidateController` | 候选查询、详情、编辑、确认、驳回、忽略、批量操作。 |
| `TestDesignTaskPublishController` | 发布 dryRun、正式发布、发布记录查询和任务评审历史导出。 |
| `TestDesignTaskService` | 任务查询、任务摘要、服务健康、创建、重试、取消、异步消费认领、状态落库、幂等和任务审计。 |
| `TestDesignGenerationService` | 装配脱敏上下文、选择规则模板或 WP2 模型生成、解析模型输出、生成候选批次并执行生成质量校验；不创建任务、不做状态流转、不写审计。 |
| `TestDesignCandidateReviewService` | 候选查询、编辑、确认、驳回、忽略、批量评审和评审记录导出。 |
| `TestDesignQualityService` | 判断空步骤、缺断言、重复风险、覆盖缺口、敏感信息风险和发布就绪。 |
| `TestDesignPublishService` | 发布 dryRun、正式发布、发布记录查询，将已确认候选写入 WP3 测试用例并建立追踪关系。 |
| `TestDesignConflictService` | 发布冲突人工链接和批量冲突处理，复用 WP3 用例需求追踪校验和审计记录。 |
| `TestDesignTaskReportService` | 导出任务级聚合报告，并提供任务本域审计链摘要，避免报告拼装逻辑回流到任务服务。 |
| `TestDesignScopeService` | 为权限解析提供任务/候选项目作用域，不承载业务流。 |
| `TestDesignRepository` | 维护生成任务、候选、评审记录和发布记录。 |

服务拆分准入：`ModuleLayerDependencyTest` 禁止重新引入 `TestDesignService` 或 `Facade` 命名服务，禁止 `TestDesignGenerationService` 持有创建/重试/异步消费入口，并将 WP5 单个应用服务上限收紧为 1200 行。

## 3. 状态机

### 3.1 生成任务状态

```text
DRAFT -> RUNNING -> SUCCEEDED
DRAFT -> RUNNING -> PARTIAL_SUCCESS
DRAFT -> RUNNING -> FAILED
RUNNING -> CANCELLED
FAILED -> RUNNING
PARTIAL_SUCCESS -> RUNNING
SUCCEEDED -> PUBLISHING -> PUBLISHED
SUCCEEDED -> PUBLISHING -> PARTIAL_SUCCESS
```

非法状态流必须返回 `INVALID_STATE` 并写入拒绝审计。

### 3.2 候选状态

```text
GENERATED -> EDITED -> CONFIRMED -> PUBLISHED
GENERATED -> CONFIRMED -> PUBLISHED
GENERATED -> REJECTED
GENERATED -> IGNORED
EDITED -> REJECTED
EDITED -> IGNORED
CONFIRMED -> EDITED
CONFIRMED -> IGNORED
```

已 `PUBLISHED` 候选不可再次编辑，只能查看和跳转 WP3 用例。

## 4. 建议数据模型

### 4.1 `test_design_task`

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | uuid | 主键 |
| `project_id` | varchar(64) | 项目 ID |
| `name` | varchar(160) | 任务名称 |
| `strategy` | varchar(64) | 主策略，如 `FUNCTIONAL` |
| `coverage_types` | text | 逗号分隔覆盖类型或 JSON 数组 |
| `requirement_ids` | jsonb | 本任务需求 ID 列表 |
| `context_options_json` | jsonb | 是否带入 API、页面、流程、历史用例等 |
| `status` | varchar(32) | 任务状态 |
| `prompt_key` | varchar(128) | 默认 `wp5-test-case-design` |
| `prompt_version` | varchar(64) | Prompt 版本 |
| `model_invocation_id` | uuid | WP2 调用 ID |
| `model_provider_name` | varchar(128) | provider 名称 |
| `model_name` | varchar(128) | 模型名称 |
| `input_digest` | varchar(96) | 上下文摘要 hash |
| `context_summary_json` | jsonb | 脱敏后的上下文摘要 |
| `quality_summary_json` | jsonb | 覆盖、重复、质量提示摘要 |
| `candidate_count` | int | 候选数 |
| `confirmed_count` | int | 已确认数 |
| `published_count` | int | 已发布数 |
| `last_error_code` | varchar(64) | 最近错误码 |
| `last_error_message` | varchar(500) | 脱敏错误摘要 |
| `trace_id` | varchar(64) | 最近 traceId |
| `created_by` | varchar(64) | 创建人 |
| `created_at` | timestamptz | 创建时间 |
| `updated_at` | timestamptz | 更新时间 |

建议索引：

- `idx_test_design_task_project_status(project_id, status, updated_at desc)`
- `idx_test_design_task_created_by(created_by, created_at desc)`
- `uk_test_design_task_input(project_id, input_digest, prompt_key, prompt_version)` 可用于幂等提示，不强制唯一阻断。

### 4.2 `test_design_candidate`

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | uuid | 主键 |
| `task_id` | uuid | 任务 ID |
| `project_id` | varchar(64) | 项目 ID |
| `requirement_id` | uuid | WP3 需求 ID |
| `api_id` | uuid | WP3 API ID，可空 |
| `page_id` | uuid | WP3 页面 ID，可空 |
| `flow_id` | uuid | WP3 业务流 ID，可空 |
| `asset_test_case_id` | uuid | 发布后 WP3 测试用例 ID |
| `title` | varchar(200) | 标题 |
| `description` | text | 描述 |
| `precondition` | text | 前置条件 |
| `priority` | varchar(16) | 优先级 |
| `coverage_type` | varchar(32) | 覆盖类型 |
| `steps_json` | jsonb | 步骤数组 |
| `tags` | varchar(500) | 标签 |
| `rationale` | text | 生成依据 |
| `source_refs_json` | jsonb | 来源引用 |
| `quality_flags_json` | jsonb | 质量提示 |
| `duplicate_key` | varchar(128) | 标题/需求/步骤摘要 hash |
| `status` | varchar(32) | 候选状态 |
| `review_comment` | varchar(500) | 驳回或忽略原因 |
| `version` | int | 乐观锁 |
| `created_at` | timestamptz | 创建时间 |
| `updated_at` | timestamptz | 更新时间 |

建议索引：

- `idx_test_design_candidate_task(task_id, status, updated_at desc)`
- `idx_test_design_candidate_project(project_id, requirement_id, status)`
- `idx_test_design_candidate_duplicate(project_id, requirement_id, duplicate_key)`
- `uk_test_design_candidate_source(task_id, requirement_id, duplicate_key)` 防止同任务重复候选。

### 4.3 `test_design_review_record`

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | uuid | 主键 |
| `candidate_id` | uuid | 候选 ID |
| `action` | varchar(32) | `EDIT/CONFIRM/REJECT/IGNORE/PUBLISH` |
| `before_json` | jsonb | 操作前快照 |
| `after_json` | jsonb | 操作后快照 |
| `diff_json` | jsonb | 字段差异 |
| `comment` | varchar(500) | 评审备注 |
| `actor_user_id` | varchar(64) | 操作者 |
| `trace_id` | varchar(64) | traceId |
| `created_at` | timestamptz | 创建时间 |

### 4.4 `test_design_publish_record`

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | uuid | 主键 |
| `task_id` | uuid | 任务 ID |
| `candidate_id` | uuid | 候选 ID |
| `asset_test_case_id` | uuid | WP3 用例 ID |
| `action` | varchar(32) | `CREATE/LINK_EXISTING/SKIPPED/FAILED` |
| `status` | varchar(32) | `SUCCEEDED/FAILED` |
| `message` | varchar(500) | 脱敏摘要 |
| `trace_id` | varchar(64) | traceId |
| `created_at` | timestamptz | 创建时间 |

## 5. Prompt 和模型契约

| 项 | 内容 |
|---|---|
| Prompt key | `wp5-test-case-design` |
| 调用方 | `wp5-test-design` |
| capability | `CHAT` 或 WP2 支持的结构化输出能力 |
| 敏感策略 | 继承项目/应用 `sensitivityLevel` 和 `allowPublicModel` |
| 失败策略 | WP2 阻断时不绕过；可按配置使用规则模板 fallback |

模型输出 JSON 结构：

```json
{
  "cases": [
    {
      "title": "账号密码登录成功",
      "description": "验证有效账号可正常登录后台",
      "precondition": "用户账号已启用",
      "priority": "HIGH",
      "coverageType": "SMOKE",
      "requirementRef": "需求ID或外部引用",
      "apiRefs": ["API ID"],
      "pageRefs": ["PAGE ID"],
      "flowRefs": [],
      "steps": [
        {
          "action": "打开登录页并输入有效用户名和密码",
          "expectedResult": "登录成功并进入系统概览页"
        }
      ],
      "tags": ["auth", "login"],
      "rationale": "覆盖登录需求的主成功路径",
      "riskNotes": ["需准备启用状态账号"]
    }
  ]
}
```

服务端必须对 JSON 做结构校验和业务校验，不能把模型原始字符串直接入库为候选。

### 5.1 上下文摘要契约

当前实现的 `context_summary_json` 只保存脱敏摘要，不保存完整 Prompt 或原始文档正文。任务创建时通过 WP3 应用服务读取需求、追踪链接、关联 API、页面、业务流和历史用例摘要；请求可额外传入 `contextApiIds/contextPageIds/contextFlowIds`，用于显式纳入未建立需求追踪关系但本次生成需要参考的上下文资产。上下文裁剪上限由 `veri-agent.test-design.context-*` 配置驱动，并写入 `contextSummary.limits`、`requestDigest`、模型请求 `contextPacking`、前端任务诊断和后端任务报告的聚合行；`contextAssemblyPolicy` v2 固定输出 `SNAPSHOT_DIGEST_ONLY` 装配模式、`SHA256_CONTEXT_SUMMARY` digest 策略、inputDigest 要求、摘要持久化、WP3 应用服务边界、原文/模型载荷持久化和上下文明细导出红线，并同步到 health、任务响应、`contextSummary.assemblyPolicy`、模型 `contextPacking.assemblyPolicy`、前端任务诊断和任务报告；`contextPolicyGovernance` 固定输出平台默认治理状态，明确当前仅支持部署配置变更，项目/环境覆盖与审批流尚未就绪；`contextPolicyOperations` v2 固定输出平台默认运营模式、策略解析顺序、部署配置回退行为、审批状态、项目/环境覆盖存储和审批流就绪状态，并同步到 health、任务响应、`contextSummary.policyOperations`、模型 `contextPacking.policyOperations`、前端任务诊断和任务报告。WP5 不直连 WP3 表。

```json
{
  "contextVersion": "wp5-context-v1",
  "requirements": [
    {
      "id": "4d76b2c1-0000-4000-8000-000000000001",
      "code": "REQ-001",
      "title": "账号密码登录",
      "acceptanceCriteriaPreview": "登录成功后进入工作台"
    }
  ],
  "linkedAssetsByRequirement": [
    {
      "requirementId": "4d76b2c1-0000-4000-8000-000000000001",
      "apiCount": 1,
      "pageCount": 1,
      "flowCount": 1,
      "apis": [{"id": "a111...", "method": "POST", "path": "/api/login", "summary": "登录接口"}],
      "pages": [{"id": "p111...", "name": "登录页", "urlPattern": "/login"}],
      "flows": [{"id": "f111...", "name": "登录主流程", "priority": "HIGH"}]
    }
  ],
  "existingCasesByRequirement": [
    {
      "requirementId": "4d76b2c1-0000-4000-8000-000000000001",
      "count": 1,
      "cases": [{"id": "c111...", "title": "历史登录主流程用例", "stepCount": 3}]
    }
  ],
  "explicitAssets": {
    "apiCount": 1,
    "pageCount": 1,
    "flowCount": 1,
    "apiIds": ["a222..."],
    "pageIds": ["p222..."],
    "flowIds": ["f222..."],
    "apis": [{"id": "a222...", "method": "POST", "path": "/api/reset-password", "summary": "密码重置接口"}],
    "pages": [{"id": "p222...", "name": "密码重置页", "urlPattern": "/reset-password"}],
    "flows": [{"id": "f222...", "name": "密码重置流程", "priority": "HIGH"}]
  },
  "limits": {
    "linkedAssetsPerRequirement": 5,
    "explicitAssetsPerType": 5,
    "linkedAssetSchemaChars": 240,
    "existingCasesPerRequirement": 5,
    "rawPromptStored": false
  },
  "policyGovernance": {
    "policyVersion": "wp5-context-policy-v1",
    "policySource": "PLATFORM_DEFAULT",
    "governanceStatus": "PLATFORM_DEFAULT_ONLY",
    "changeMode": "DEPLOY_CONFIG_CHANGE",
    "projectOverrideSupported": false,
    "environmentOverrideSupported": false,
    "changeApprovalRequired": true,
    "changeApprovalWorkflowReady": false,
    "effectiveAtTaskCreation": true,
    "aggregateOnly": true
  },
  "policyOperations": {
    "policyVersion": "wp5-context-policy-operations-v2",
    "operationMode": "PLATFORM_DEFAULT_ONLY",
    "policyResolutionOrder": "PLATFORM_DEFAULT_ONLY",
    "policyFallbackBehavior": "DEPLOY_CONFIG_CHANGE_REQUIRED",
    "approvalStatus": "WORKFLOW_NOT_READY",
    "projectOverrideStoreReady": false,
    "environmentOverrideStoreReady": false,
    "changeApprovalWorkflowReady": false,
    "effectivePolicySnapshotMaterialized": true,
    "aggregateOnly": true
  }
}
```

当前可配置项：

- `context-linked-assets-per-requirement`
- `context-explicit-assets-per-type`
- `context-existing-cases-per-requirement`
- `context-requirement-description-chars`
- `context-acceptance-criteria-chars`
- `context-asset-schema-chars`

任务报告只导出上下文规模和策略数字，例如 requirement/linked asset/explicit asset/existing case 计数、`contextPolicy` 上限、`contextAssemblyPolicy` v2 固定装配安全标记、`contextPolicyGovernance` 固定治理状态和 `contextPolicyOperations` v2 固定运营状态；不得导出显式资产 ID、digest 值、API schema、页面树、流程 JSON、需求正文、历史用例步骤、策略审批说明、策略工单、项目/环境覆盖规则、策略 diff 预览或原始 Prompt。报告导出会追加 `generationOrchestrationPolicy` 聚合行，只输出策略版本、同步/异步编排模式、条件认领、幂等创建回放、重复事件安全、事件恢复、运行中超时回收、显式重试、人工任务重试、人工队列事件重发、队列 lag 指标、超时告警、多实例压测证据、有效恢复批次和状态/超时信号等固定标记与计数；不导出事件 ID、队列消息体、事件 payload、恢复明细列表、幂等键或超时错误正文。报告导出会追加 `contextAssemblyPolicy` v2 聚合行，只输出策略版本、`SNAPSHOT_DIGEST_ONLY` 装配模式、`SHA256_CONTEXT_SUMMARY` digest 策略、inputDigest 要求和跟踪、仅持久化摘要、仅通过 WP3 应用服务装配、上下文正文/模型载荷/digest 值/需求正文/schema/页面树/流程 JSON/显式资产 ID/历史步骤均不导出，以及需求快照组、关联资产组、历史用例组、显式资产类型和裁剪上限计数；还会追加 `contextPolicyOperations` 聚合行，只输出策略版本、平台默认运营模式、策略解析顺序、部署配置回退行为、审批状态、项目/环境覆盖存储未就绪、审批流未就绪、任务创建时策略快照已固化、策略 diff/审批备注/工单 URL/覆盖规则不导出和 aggregate-only 标记；还会追加 `modelObservationPolicy` 聚合行，只输出策略版本、`ROUTING_COST_LATENCY_AGGREGATE` 观测模式、WP2 调用引用跟踪、trace/job/routing/token/latency/cost/fallback 是否存在、prompt 载荷不存储、载荷预览不导出、trace/job/invocation ID 原值不导出、provider 错误正文不导出，以及路由元数据、token、成本、延迟聚合计数，不导出模型调用 ID、异步 job ID、traceId 原值、请求/响应预览、原始 Prompt、provider 错误正文或 actor 服务；还会追加 `exportGovernance` 聚合行，声明 `aggregateOnly`、候选正文/评审评论/模型载荷/上下文正文/trace 明细均不允许导出；还会追加 `readinessPolicy` 聚合行，只输出策略版本、阈值来源、准出状态、阻断/风险计数、逐项检查状态、当前值、阈值、单位、严重级别和 advisory-only/publish-blocking 标记，不导出候选证据、检查说明正文、候选 ID 或候选正文；还会追加 `auditPolicy` 聚合行，只声明导出动作、资源类型、项目作用域、是否写审计事件和审计明细不导出，不复制 WP1 audit_log 明细、审计事件 ID、trace 明细或 after-json；还会追加 `safetyScanPolicy` 聚合行，只声明 fail-closed 模式、敏感文本扫描、原始载荷标记扫描、request/response preview 标记扫描和命中详情不导出；还会追加 `archivePolicy` 聚合行，只输出有界保留天数、固定 `platformManaged` 存储策略、是否需要审批、是否允许外发和策略跟踪状态，不输出归档路径、归档备注、审批说明、工单 URL 或其他自由文本；还会追加 `promptCalibrationPolicy` 聚合行，只输出策略版本、样本来源、校准状态、反馈信号计数、样本候选计数、说明覆盖计数、样本维护/长期校准就绪状态和 aggregate-only 标记，不输出样本行、候选 ID、候选正文、评审评论或 Prompt 正文；还会追加 `publishCompensationPolicy` 聚合行，只输出补偿策略版本、回放键族、幂等回放、部分 trace link 修复、失败候选重试、人工冲突链接、异步补偿后台和跨 WP 编排就绪状态，以及 retry/link/manual/conflict/failed 聚合计数，不输出候选 ID、资产用例 ID、sourceRef、trace 明细、发布错误正文或评审说明；最后追加 `reportManifest` 聚合行，只输出报告 schema 版本、字段集版本、manifest 追加前行数、aggregate-only 标记、明细行不导出和完成状态，不输出候选 ID 清单、trace 清单、审计 ID 清单或行级摘要；CSV 返回前还会执行最终安全扫描，命中未脱敏 secret/token/Bearer、原始 Prompt 标记或 request/response preview 标记时阻断导出。

## 6. API 契约

基础路径：`/api/v1/test-design`

### 6.1 任务 API

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| `GET` | `/tasks` | `testDesign:read` | 分页查询任务。 |
| `POST` | `/tasks` | `testDesign:generate` | 创建生成任务。 |
| `GET` | `/tasks/{id}` | `testDesign:read` | 查询任务详情和质量摘要。 |
| `POST` | `/tasks/{id}/retry` | `testDesign:generate` | 重试失败或部分成功任务。 |
| `POST` | `/tasks/{id}/cancel` | `testDesign:generate` | 取消运行中任务。 |

创建任务请求：

```json
{
  "projectId": "project-001",
  "title": "登录需求用例生成",
  "requirementIds": ["4d76b2c1-0000-4000-8000-000000000001"],
  "contextApiIds": ["a2220000-0000-4000-8000-000000000001"],
  "contextPageIds": ["b2220000-0000-4000-8000-000000000001"],
  "contextFlowIds": ["c2220000-0000-4000-8000-000000000001"],
  "coverageTypes": ["SMOKE", "FUNCTIONAL", "EXCEPTION", "BOUNDARY"],
  "caseCountPerRequirement": 5
}
```

任务响应：

```json
{
  "id": "0f0d47d0-0000-4000-8000-000000000001",
  "projectId": "project-001",
  "name": "登录需求用例生成",
  "strategy": "FUNCTIONAL",
  "coverageTypes": ["SMOKE", "FUNCTIONAL", "EXCEPTION", "BOUNDARY"],
  "status": "SUCCEEDED",
  "candidateCount": 8,
  "confirmedCount": 0,
  "publishedCount": 0,
  "promptKey": "wp5-test-case-design",
  "promptVersion": "v1",
  "modelInvocationId": "8e979021-0000-4000-8000-000000000001",
  "modelProviderName": "local-echo",
  "modelName": "echo",
  "qualitySummary": {
    "emptyStepCount": 0,
    "duplicateRiskCount": 1,
    "coverageTypes": ["SMOKE", "FUNCTIONAL"]
  },
  "lastErrorCode": null,
  "lastErrorMessage": null,
  "traceId": "trc_xxx",
  "createdAt": "2026-05-25T10:00:00Z",
  "updatedAt": "2026-05-25T10:00:30Z"
}
```

### 6.2 候选 API

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| `GET` | `/candidates` | `testDesign:read` | 分页查询候选，支持任务、需求、状态、优先级和关键词筛选。 |
| `GET` | `/candidates/{id}` | `testDesign:read` | 查询候选详情。 |
| `PUT` | `/candidates/{id}` | `testDesign:review` | 编辑候选。 |
| `POST` | `/candidates/{id}/confirm` | `testDesign:review` | 确认候选。 |
| `POST` | `/candidates/{id}/reject` | `testDesign:review` | 驳回候选。 |
| `POST` | `/candidates/{id}/ignore` | `testDesign:review` | 忽略候选。 |
| `POST` | `/candidates/{id}/resolve-conflict` | `testDesign:publish` | 人工确认发布冲突并链接既有 WP3 测试用例。 |
| `POST` | `/candidates/batch-action` | `testDesign:review` | 批量确认、驳回、忽略。 |
| `POST` | `/candidates/batch-resolve-conflicts` | `testDesign:publish` | 批量人工处理发布冲突并返回逐项结果。 |

候选查询参数：

| 参数 | 说明 |
|---|---|
| `projectId` | 项目 ID |
| `taskId` | 任务 ID |
| `requirementId` | 需求 ID |
| `status` | 候选状态 |
| `priority` | 优先级 |
| `coverageType` | 覆盖类型 |
| `keyword` | 标题、标签或说明关键词 |
| `index`、`size` | 分页 |

批量操作请求：

```json
{
  "action": "CONFIRM",
  "candidates": [
    {"id": "40c21d62-0000-4000-8000-000000000001", "version": 1}
  ],
  "comment": "本批候选通过评审"
}
```

批量冲突处理请求：

```json
{
  "items": [
    {
      "candidateId": "40c21d62-0000-4000-8000-000000000001",
      "version": 3,
      "caseId": "8b8eb5b4-0000-4000-8000-000000000901"
    }
  ],
  "reason": "人工确认复用既有覆盖",
  "comment": "已比对需求追踪和步骤"
}
```

批量冲突处理响应需要返回 `total/succeededCount/failedCount/items`，每个 item 包含候选 ID、`SUCCEEDED/FAILED`、发布记录或错误摘要。

### 6.3 发布 API

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| `POST` | `/tasks/{id}/publish-dry-run` | `testDesign:publish` | 预览发布结果。 |
| `POST` | `/tasks/{id}/publish` | `testDesign:publish` | 发布确认候选到 WP3。 |
| `GET` | `/tasks/{id}/publish-records` | `testDesign:read` | 查询发布记录。 |

发布请求：

```json
{
  "candidateIds": ["40c21d62-0000-4000-8000-000000000001"],
  "targetStatus": "DRAFT",
  "duplicatePolicy": "REVIEW_REQUIRED"
}
```

发布结果：

```json
{
  "taskId": "0f0d47d0-0000-4000-8000-000000000001",
  "dryRun": true,
  "summary": {
    "createCount": 1,
    "duplicateRiskCount": 0,
    "failedCount": 0
  },
  "items": [
    {
      "candidateId": "40c21d62-0000-4000-8000-000000000001",
      "action": "CREATE",
      "assetTestCaseId": null,
      "message": "将创建 WP3 测试用例"
    }
  ]
}
```

### 6.4 健康和配置 API

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| `GET` | `/health` | `testDesign:read` | 返回 WP5 开关、Prompt、fallback 和质量阈值摘要。 |
| `GET` | `/tasks/{id}/quality/summary` | `testDesign:read` | 返回任务全量质量摘要和按当前阈值计算的任务准出状态。 |
| `GET` | `/quality/prompt-trend` | `testDesign:read` | 返回最近任务按 Prompt key/version 聚合的质量趋势，每个版本桶包含聚合准出摘要，并返回顶层准出状态分布。 |
| `GET` | `/tasks/{id}/report/audit-summary` | `testDesign:read` | 返回任务本域审计链摘要，聚合 WP5 任务、评审记录和发布记录，不查询全局 `audit_log`。 |

质量与趋势响应不得暴露模型密钥、provider token、完整 prompt 内容、候选正文、评审评论或敏感上下文。`prompt-trend.buckets[].readiness` 复用任务质量阈值，只作为 Prompt 运营提示，不改变发布权限或候选状态。`prompt-trend.readinessDistribution` 仅按版本桶聚合 `PASSED/WARNING/BLOCKED/UNKNOWN` 数量和比例，用于运营看板快速识别阻断或风险版本。

## 7. 错误码建议

| 错误码 | 场景 |
|---|---|
| `TEST_DESIGN_TASK_NOT_FOUND` | 任务不存在 |
| `TEST_DESIGN_CANDIDATE_NOT_FOUND` | 候选不存在 |
| `TEST_DESIGN_INVALID_STATE` | 状态流非法 |
| `TEST_DESIGN_CONTEXT_EMPTY` | 未找到可生成的需求上下文 |
| `TEST_DESIGN_MODEL_BLOCKED` | WP2 策略、预算或敏感内容阻断 |
| `TEST_DESIGN_MODEL_OUTPUT_INVALID` | 模型输出结构非法 |
| `TEST_DESIGN_CANDIDATE_VERSION_CONFLICT` | 候选版本冲突 |
| `TEST_DESIGN_DUPLICATE_REVIEW_REQUIRED` | 重复风险需人工处理 |
| `TEST_DESIGN_PUBLISH_FAILED` | 写入 WP3 失败 |

错误响应仍使用统一 envelope。

## 8. 审计事件

| action | resourceType | 触发 |
|---|---|---|
| `CREATE` | `TEST_DESIGN_TASK` | 创建任务 |
| `RETRY` | `TEST_DESIGN_TASK` | 重试任务 |
| `CANCEL` | `TEST_DESIGN_TASK` | 取消任务 |
| `MODEL_GENERATE` | `TEST_DESIGN_TASK` | 调用模型生成 |
| `MODEL_FALLBACK` | `TEST_DESIGN_TASK` | 模型失败后规则 fallback |
| `UPDATE` | `TEST_DESIGN_CANDIDATE` | 编辑候选 |
| `CONFIRM` | `TEST_DESIGN_CANDIDATE` | 确认候选 |
| `REJECT` | `TEST_DESIGN_CANDIDATE` | 驳回候选 |
| `IGNORE` | `TEST_DESIGN_CANDIDATE` | 忽略候选 |
| `PUBLISH_DRY_RUN` | `TEST_DESIGN_TASK` | 发布预览 |
| `PUBLISH` | `TEST_DESIGN_CANDIDATE` | 发布到 WP3 |
| `EXPORT` | `TEST_DESIGN_TASK_REPORT` | 导出任务全量聚合报告 |
| `STATUS_CHANGE_DENIED` | `TEST_DESIGN_TASK/CANDIDATE` | 状态流拒绝 |

审计不记录完整模型输入、完整需求原文、密钥、token 或隐私字段。

## 9. 配置项

| 配置 | 默认 | 说明 |
|---|---|---|
| `WP5_SERVICE_TOKEN` | `local-test-design-token` | WP5 服务间调用令牌 |
| `WP5_GENERATION_MODE` | `RULE_TEMPLATE` | 生成模式；支持 `RULE_TEMPLATE`、`MODEL`、`MODEL_WITH_FALLBACK` |
| `WP5_MODEL_FALLBACK_ENABLED` | `false` | `generation-mode=MODEL` 时是否等价为 `MODEL_WITH_FALLBACK` |
| `WP5_PROMPT_KEY` | `wp5-test-design-v1` | 默认 WP2 Prompt key |
| `WP5_PROMPT_VERSION` | `1.0.0` | WP5 任务记录中的 Prompt 版本口径 |
| `WP5_CASE_COUNT_PER_REQUIREMENT_MAX` | `3` | 单需求最大生成数 |
| `WP2_MAX_PROMPT_CHARS` | `12000` | WP2 模型输入最大字符数 |
| `WP5_DUPLICATE_SIMILARITY_THRESHOLD` | `0.86` | 重复风险阈值 |
| `WP5_BATCH_ACTION_MAX_SIZE` | `100` | 批量操作上限 |
| `WP5_QUALITY_MIN_EXPECTED_STEP_RATIO` | `0.95` | 有预期结果步骤比例阈值 |
| `WP5_REPORT_ARCHIVE_RETENTION_DAYS` | `180` | 任务报告归档保留天数；服务端按 1-3650 有界化后写入 `archivePolicy` 聚合行 |
| `WP5_REPORT_ARCHIVE_EXTERNAL_SHARING_ALLOWED` | `false` | 任务报告归档是否允许外发；只以布尔值进入聚合报告 |
| `WP5_REPORT_ARCHIVE_APPROVAL_REQUIRED` | `true` | 任务报告归档是否要求审批；只以布尔值进入聚合报告 |

Spring 配置命名建议同步提供 `veri-agent.test-design.*` 形式，环境变量作为部署注入入口。

## 10. 服务端代码质量要求

1. 核心服务方法必须有方法级 JavaDoc 或关键逻辑注释，说明输入前提、输出语义、状态流和副作用。
2. 生成任务执行、候选状态流、发布到 WP3、模型 fallback、上下文脱敏和重复检测属于核心逻辑，必须补充必要注释。
3. 所有外部输入使用 Bean Validation 和业务校验双层保护。
4. 事务边界以“候选状态变更”和“发布单批候选”为单位，避免长事务包住模型调用。
5. 模型调用不得在数据库事务内执行。
6. 发布到 WP3 失败时记录发布失败记录，不把候选误标为已发布。
7. 日志只记录 ID、状态、traceId 和脱敏摘要，不记录完整 prompt、需求原文、密钥或用户隐私。
8. 导出类接口必须使用白名单字段和最终安全扫描；新增报告字段时必须证明不会输出候选正文、评审评论、模型请求/响应 preview、上下文正文、trace/job 明细、审计事件明细、候选/trace/审计 ID 清单、行级摘要、安全扫描命中详情、归档自由文本或未脱敏密钥。
9. 单元测试覆盖状态机、校验、重复检测、模型输出解析和发布失败补偿。
