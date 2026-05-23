# WP1 企业身份与审批预留方案

| 项目 | 内容 |
|---|---|
| 覆盖任务 | `WP1-E1 SSO/LDAP/企业组织同步方案`、`WP1-E2 项目/权限申请审批预留` |
| 适用阶段 | M4 企业集成扩展前置方案冻结 |
| 当前结论 | 方案已冻结；当前不启用真实 SSO/LDAP/组织同步/审批流 |
| 日期 | 2026-05-23 |

## 1. 目标、范围与非目标

目标：

1. 定义企业身份源、外部账号、部门同步映射和冲突处理口径，保证后续接入 OIDC/SAML/LDAP/企业微信/钉钉/飞书时不破坏当前本地账号登录。
2. 定义项目创建、权限申请和环境变更等审批对象、状态机、审计事件和与 `rbac_role_binding` 的关系，保证后续审批流可以复用现有 WP1 RBAC 和审计体系。
3. 明确默认关闭、可灰度启用和可回滚的 feature flag 边界，避免 M4 预留影响 WP1-WP4 当前 MVP 链路。

范围：

- 方案层定义身份源模型、外部用户 ID、部门同步冲突策略、审批请求模型、审批状态和角色绑定关系。
- 约束后续数据库、API、前端和测试实现必须遵守的兼容规则。
- 说明与 `iam_user.external_id`、`base_department`、`base_department_member`、`rbac_role_binding`、`audit_log`、`secret_reference` 的关系。

非目标：

- 不新增运行时代码、数据库迁移、前端页面或自动化同步任务。
- 不实现真实 SSO 登录、LDAP 拉取、企业通讯录同步、审批 API、审批 UI、机器人通知或审批卡片。
- 不改变当前用户名密码登录、项目成员维护、应用负责人维护、环境授权用户维护和角色绑定的即时生效行为。

回滚方式：若后续发现方案口径不合适，回滚本文档和待补任务状态变更即可；当前没有运行时变更和数据库变更。

## 2. 当前基线与设计原则

当前 WP1 是单平台控制面，不建设多租户表和平台实例分层。企业身份和审批预留必须遵守以下原则：

1. `iam_user` 仍是平台操作主体的权威本地用户记录，`auth_version` 仍是会话和权限失效的统一版本号。
2. `base_department` 和 `base_department_member` 仍是平台内组织结构与部门成员关系的权威视图。外部组织同步只能写入或更新受控映射，不允许外部系统直接写 WP1 表。
3. `rbac_role_binding` 仍是权限生效的唯一授权表。审批通过只产生授权意图，实际授权必须通过既有角色绑定服务写入，并同步写审计和递增相关用户 `auth_version`。
4. 企业身份源密钥、LDAP bind password、OIDC client secret、SAML private key 和 webhook/token 均只能通过 `secret_reference` 保存引用，不得进入配置响应、审计明文、日志或导出。
5. 所有企业身份和审批能力默认关闭；关闭时当前本地账号、角色绑定和项目/应用/环境管理路径必须完全保持现状。

## 3. 企业身份源模型

后续实现建议新增 `identity_source` 作为身份源配置表，字段口径如下：

| 字段 | 说明 |
|---|---|
| `id` | 身份源 ID。 |
| `code` | 平台内唯一编码，例如 `corp-oidc`、`ldap-main`。 |
| `name` | 管理台展示名称。 |
| `source_type` | `LOCAL`、`OIDC`、`SAML`、`LDAP`、`WECHAT_WORK`、`DINGTALK`、`FEISHU`。 |
| `status` | `PLANNED`、`ENABLED`、`DISABLED`。预留但未配置完成时使用 `PLANNED`。 |
| `login_enabled` | 是否允许作为登录入口。 |
| `sync_enabled` | 是否允许组织/账号同步。 |
| `config_json` | 非敏感配置摘要，例如 issuer、tenant/domain、scope、attribute mapping。 |
| `secret_ref` | 身份源凭证引用，指向 `secret_reference`，只保存引用。 |
| `signing_cert_ref` | SAML 或 webhook 签名证书引用，可为空。 |
| `user_match_policy` | `EXTERNAL_ID_FIRST`、`EMAIL_FIRST`、`USERNAME_FIRST`、`MANUAL_ONLY`。 |
| `department_match_policy` | `EXTERNAL_ID_FIRST`、`CODE_FIRST`、`PATH_FIRST`、`MANUAL_ONLY`。 |
| `default_department_id` | 自动创建用户时的默认部门，可为空；为空时进入待处理队列。 |
| `last_sync_status` | `NEVER_RUN`、`SUCCEEDED`、`PARTIAL_FAILED`、`FAILED`。 |
| `last_sync_at` | 最近同步完成时间。 |

兼容规则：

- 当前 `iam_user.external_id` 可继续作为单一外部身份 ID 的兼容字段；后续多身份源接入时不得继续把不同来源的外部 ID 混写到该字段。
- 多身份源场景必须新增 `identity_external_account` 显式映射，避免不同 provider 的 `sub`、LDAP DN 或 openId 发生碰撞。
- 身份源配置更新、启停和同步触发都必须写 `audit_log`，资源类型建议使用 `identity_source`。

## 4. 外部账号映射

后续建议新增 `identity_external_account` 记录外部身份与本地用户的绑定关系：

| 字段 | 说明 |
|---|---|
| `id` | 映射 ID。 |
| `source_id` | 身份源 ID。 |
| `external_user_id` | 外部稳定用户 ID，例如 OIDC `sub`、LDAP entryUUID、飞书 open_id。 |
| `external_username` | 外部登录名或账号名。 |
| `external_email` | 外部邮箱，允许为空。 |
| `external_mobile` | 外部手机号，允许为空。 |
| `local_user_id` | 绑定的 `iam_user.id`。 |
| `link_status` | `PENDING_LINK`、`LINKED`、`CONFLICT`、`DISABLED_EXTERNAL`、`IGNORED`。 |
| `external_version` | 外部数据版本或修改时间，用于幂等同步。 |
| `raw_digest` | 外部原始记录摘要，不保存完整原文。 |
| `last_seen_at` | 最近在外部源出现时间。 |
| `last_synced_at` | 最近同步到本地时间。 |

唯一性与安全规则：

1. 同一 `source_id + external_user_id` 只能绑定一个未删除映射。
2. 同一 `source_id + local_user_id` 默认只能有一个 `LINKED` 账号；多账号绑定必须显式开启并审计。
3. 自动匹配优先级为显式映射、`iam_user.external_id` 兼容字段、邮箱、手机号、用户名。邮箱/手机号/用户名命中多用户时进入 `CONFLICT`，不得自动绑定。
4. 外部账号禁用默认只把映射标记为 `DISABLED_EXTERNAL`，是否停用本地 `iam_user` 由身份源 deprovision 策略控制。生产默认策略建议为“禁用登录，不自动删除用户和审计历史”。
5. 外部身份绑定、解绑、冲突处理和禁用必须递增对应用户 `auth_version`，确保旧会话不能绕过身份状态变化。

## 5. 部门同步与冲突策略

后续组织同步建议新增 `identity_department_binding` 记录外部部门与本地部门的映射：

| 字段 | 说明 |
|---|---|
| `source_id` | 身份源 ID。 |
| `external_department_id` | 外部稳定部门 ID。 |
| `external_parent_id` | 外部父部门 ID。 |
| `external_path` | 外部路径快照。 |
| `local_department_id` | 绑定的 `base_department.id`。 |
| `sync_status` | `PENDING_LINK`、`LINKED`、`CONFLICT`、`DISABLED_EXTERNAL`、`IGNORED`。 |
| `managed_fields` | 允许身份源覆盖的字段集合，例如 `name`、`parent`、`members`。 |
| `last_synced_at` | 最近同步时间。 |

冲突处理：

1. 默认本地手工维护优先。外部同步只更新明确标记为外部托管的字段，不能覆盖本地管理员刚刚修改的项目、应用、环境和角色授权。
2. 部门匹配优先级为显式映射、外部 ID、部门 `code`、完整路径。路径或名称重复时进入 `CONFLICT`，需要管理员人工处理。
3. 外部部门删除或不可见时，本地部门默认置为 `DISABLED_EXTERNAL` 映射状态，不自动硬删除 `base_department`，也不删除历史审计。
4. 成员同步默认只维护 `base_department_member`。除非启用独立策略，不得自动增删 `rbac_role_binding`，避免外部通讯录变动直接扩大权限。
5. 成员从主部门移走时必须保证仍有一个主部门；无法确定新主部门时进入 `CONFLICT`，不落半成品关系。
6. 同步任务必须输出 `created/updated/disabled/conflict/skipped` 计数、`traceId`、耗时和脱敏错误摘要。

## 6. SSO 登录兼容策略

SSO 登录只负责认证身份，不直接授予业务权限：

1. 用户通过 OIDC/SAML 等完成外部认证后，WP1 根据 `identity_external_account` 查找本地 `iam_user`。
2. 找不到本地用户时，根据身份源策略选择自动创建、进入待激活、进入待绑定或拒绝登录。默认建议为待绑定或待激活。
3. 已绑定用户必须满足本地 `iam_user.status=ENABLED`，且外部映射不是 `DISABLED_EXTERNAL`，才能创建 `iam_session`。
4. SSO 用户的会话、刷新令牌、注销、用户停用、角色变更和 `auth_version` 规则与本地密码用户一致。
5. 本地密码登录入口保留；只有在明确开启 `WP1_LOCAL_PASSWORD_LOGIN_DISABLED=true` 后才允许按环境禁用。
6. SSO 回调必须防 CSRF/replay，校验 `state`、`nonce`、签名、issuer、audience、时间窗口和重放缓存。

## 7. 审批对象模型

后续审批流建议新增 `approval_request` 作为审批主表：

| 字段 | 说明 |
|---|---|
| `id` | 审批请求 ID。 |
| `request_type` | `PROJECT_CREATE`、`PROJECT_STATUS_CHANGE`、`PROJECT_MEMBER_GRANT`、`ROLE_BINDING_GRANT`、`ROLE_BINDING_REVOKE`、`ROLE_DEFINITION_CHANGE`、`ENVIRONMENT_CHANGE`、`SECRET_ROTATION`。 |
| `scope_type` | `PLATFORM`、`DEPARTMENT`、`PROJECT`、`APPLICATION`、`ENVIRONMENT`。 |
| `scope_id` | 作用域资源 ID，平台级为空。 |
| `target_resource_type` | 最终影响的资源类型，例如 `base_project`、`rbac_role_binding`。 |
| `target_resource_id` | 已存在资源 ID；创建类请求可为空。 |
| `requested_by` | 发起用户 ID。 |
| `risk_level` | `LOW`、`MEDIUM`、`HIGH`。 |
| `status` | 审批状态，见下一节。 |
| `payload_json` | 脱敏后的申请内容，不保存密钥明文。 |
| `reason` | 申请原因。 |
| `decision_summary` | 审批结论摘要。 |
| `applied_at` | 审批通过后实际应用时间。 |
| `trace_id` | 申请链路 ID。 |

审批步骤建议由 `approval_task` 承载：

| 字段 | 说明 |
|---|---|
| `request_id` | 审批请求 ID。 |
| `step_order` | 审批顺序。 |
| `approver_type` | `USER`、`ROLE`、`DEPARTMENT_MANAGER`。 |
| `approver_id` | 用户、角色或部门 ID。 |
| `status` | `PENDING`、`APPROVED`、`REJECTED`、`SKIPPED`、`EXPIRED`。 |
| `decision_by` | 实际审批人。 |
| `decision_note` | 审批说明。 |
| `decided_at` | 审批时间。 |

## 8. 审批状态机

`approval_request.status` 建议固定为：

| 状态 | 含义 | 可转向 |
|---|---|---|
| `DRAFT` | 草稿，未提交 | `SUBMITTED`、`CANCELLED` |
| `SUBMITTED` | 已提交，等待分配审批步骤 | `IN_REVIEW`、`CANCELLED`、`EXPIRED` |
| `IN_REVIEW` | 审批中 | `APPROVED`、`REJECTED`、`CANCELLED`、`EXPIRED` |
| `APPROVED` | 审批已通过，尚未应用或等待应用 | `APPLIED`、`APPLY_FAILED` |
| `REJECTED` | 已驳回 | 终态 |
| `CANCELLED` | 发起人或管理员取消 | 终态 |
| `EXPIRED` | 超过有效期 | 终态 |
| `APPLIED` | 已应用到目标资源 | 终态 |
| `APPLY_FAILED` | 应用失败，可人工重试或回滚 | `APPROVED`、`CANCELLED` |

关键约束：

1. `APPROVED` 不等于权限已生效，只有成功写入目标资源并完成审计后才进入 `APPLIED`。
2. `REJECTED`、`CANCELLED`、`EXPIRED` 不得产生角色绑定、项目状态变更或环境变更。
3. 审批应用失败不得留下半成品授权；若目标服务已经部分成功，必须通过补偿审计和 `APPLY_FAILED` 明确暴露。
4. 每次状态变化必须写 `audit_log`，审批详情中的敏感字段只记录脱敏摘要和是否变化。

## 9. 审批与角色绑定关系

审批流与现有 `rbac_role_binding` 的关系如下：

1. 当前角色绑定 API 仍保持即时生效，直到显式开启审批 feature flag。
2. 后续开启审批后，受控动作先创建 `approval_request`，不会直接写 `rbac_role_binding`。
3. 审批通过并应用时，由 WP1 服务层调用既有角色绑定逻辑写入 `rbac_role_binding`，继续执行资源作用域校验、用户状态校验、审计写入和 `auth_version` 递增。
4. 建议后续在 `rbac_role_binding` 增加 `approval_request_id` 字段，或在过渡期把 `approvalRequestId` 写入 `condition_json`，用于追溯该授权来自哪个审批。
5. 审批撤销不等于自动解绑。若要撤销已应用授权，必须创建 `ROLE_BINDING_REVOKE` 审批或走当前解绑接口，并单独审计。
6. 部门级角色绑定审批必须解析影响用户集合并在审批详情中展示影响范围；应用时仍以 `subject_type=DEPARTMENT` 绑定，不展开成单个用户绑定。

## 10. API 与权限预留

后续 API 建议沿用当前 WP1 管理 API 风格：

| API | 用途 | 权限 |
|---|---|---|
| `GET /api/v1/management/identity-sources` | 身份源列表 | `identity:read` |
| `POST /api/v1/management/identity-sources` | 创建身份源 | `identity:manage` |
| `PATCH /api/v1/management/identity-sources/{id}` | 更新身份源 | `identity:manage` |
| `POST /api/v1/management/identity-sources/{id}/sync-jobs` | 触发同步任务 | `identity:sync` |
| `GET /api/v1/management/identity-sync-jobs/{jobId}` | 查询同步任务 | `identity:read` |
| `GET /api/v1/management/approval-requests` | 审批列表 | `approval:read` |
| `POST /api/v1/management/approval-requests` | 发起审批 | 目标资源原权限 + `approval:create` |
| `POST /api/v1/management/approval-requests/{id}/approve` | 审批通过 | `approval:review` |
| `POST /api/v1/management/approval-requests/{id}/reject` | 审批驳回 | `approval:review` |
| `POST /api/v1/management/approval-requests/{id}/apply` | 应用审批结果 | `approval:apply` |

所有响应继续使用 camelCase JSON、`traceId` 和统一错误码。分页继续使用 `index/size/items/total`。

## 11. Feature Flag 与默认值

建议预留以下配置，默认均为关闭：

| 配置 | 默认值 | 说明 |
|---|---|---|
| `WP1_ENTERPRISE_IDENTITY_ENABLED` | `false` | 是否展示和启用企业身份源能力。 |
| `WP1_IDENTITY_SYNC_ENABLED` | `false` | 是否允许组织/账号同步任务执行。 |
| `WP1_SSO_LOGIN_ENABLED` | `false` | 是否开放 SSO 登录入口。 |
| `WP1_LOCAL_PASSWORD_LOGIN_DISABLED` | `false` | 是否禁用本地密码登录。 |
| `WP1_APPROVAL_WORKFLOW_ENABLED` | `false` | 是否启用审批请求能力。 |
| `WP1_APPROVAL_REQUIRED_FOR_ROLE_BINDING` | `false` | 是否让角色绑定必须先审批。 |
| `WP1_APPROVAL_REQUIRED_FOR_PROJECT_CREATE` | `false` | 是否让项目创建必须先审批。 |
| `WP1_APPROVAL_REQUIRED_FOR_ENVIRONMENT_CHANGE` | `false` | 是否让环境变更必须先审批。 |

关闭任一 flag 时，相关 API 应返回 `FEATURE_DISABLED` 或不展示前端入口，不得改变当前授权链路。

## 12. 审计事件预留

身份源建议事件：

- `IDENTITY_SOURCE_CREATE`
- `IDENTITY_SOURCE_UPDATE`
- `IDENTITY_SOURCE_ENABLE`
- `IDENTITY_SOURCE_DISABLE`
- `IDENTITY_SYNC_START`
- `IDENTITY_SYNC_FINISH`
- `IDENTITY_SYNC_CONFLICT`
- `EXTERNAL_ACCOUNT_LINK`
- `EXTERNAL_ACCOUNT_UNLINK`

审批建议事件：

- `APPROVAL_SUBMIT`
- `APPROVAL_APPROVE`
- `APPROVAL_REJECT`
- `APPROVAL_CANCEL`
- `APPROVAL_EXPIRE`
- `APPROVAL_APPLY`
- `APPROVAL_APPLY_FAILED`

审计要求：

1. 事件必须包含 `traceId`、操作者、目标资源、作用域、结果和脱敏摘要。
2. 身份源凭证、LDAP bind password、OIDC client secret、SAML 私钥、外部原始用户记录和审批中的 secret 明文不得进入审计。
3. 审批应用角色绑定时，必须同时保留 `APPROVAL_APPLY` 和既有角色绑定审计，便于从审批追到最终权限变化。

## 13. 验收口径

本方案完成后，`WP1-E1/E2` 的当前验收口径为：

1. 已定义身份源模型、外部用户 ID 映射、部门同步冲突策略和 SSO 登录兼容路径。
2. 已定义审批对象、审批状态机、审批与角色绑定关系、API/权限/审计预留。
3. 明确当前不实现真实 SSO/LDAP/组织同步/审批流，不影响本地账号登录和当前授权即时生效路径。
4. 明确后续实现必须使用 `secret_reference` 保存敏感凭证引用，必须复用 `audit_log`、`rbac_role_binding` 和 `auth_version`。
5. 已参考 `WP1-WP4-统一发布准出清单.md`：本轮仅文档与里程碑状态变更，无运行时、数据库、权限 seed、前端构建配置或接口契约变更；准出以文档检查和仓库 diff 检查为主，若后续落地代码/迁移/API，则必须追加 WP1 quality gate、DB validation、前端测试和 smoke。

## 14. 后续落地建议

1. 第一阶段只实现身份源配置只读/编辑和审批请求只读列表，默认 `PLANNED`，不开放真实同步和登录。
2. 第二阶段实现外部账号手工绑定和 SSO 登录闭环，仍不自动同步组织。
3. 第三阶段实现组织同步 dryRun、冲突队列和人工确认，再开放受控应用。
4. 第四阶段按 request type 灰度审批必经策略，先从高风险角色绑定和环境变更开始，不一次性拦截所有管理动作。
