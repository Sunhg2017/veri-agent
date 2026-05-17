# WP1 数据库设计交付审核报告

| 项目 | 内容 |
|---|---|
| 审核角色 | 时迁 / WP1 数据库设计交付审核 |
| 审核对象 | `WP1-数据库设计.md`、`WP1-数据库测试与校验清单.md` |
| 对照依据 | WP1 最终版 PRD、架构设计最终版、P0 API 契约、权限矩阵与菜单矩阵 |
| 审核目标 | 判断数据库设计与测试校验清单是否可作为研发落地输入 |

## 1. 审核结论

**有条件通过。**

数据库设计主干已经覆盖 WP1 MVP P0 的核心域模型，能够作为 DDL 脚本生成和迁移脚本设计的基础输入；但在进入正式 DDL 生成前，必须先修正测试清单中的表名/字段名口径，并补齐本地 `SecretProvider` 密文存储落点或明确只使用外部密钥服务。

当前不建议直接以现状进入研发自测和 CI 断言落地，因为测试清单大量使用 `sys_*`、`auth_*`、`base_env_variable`、`secret_store`、`auth_policy_version` 等旧口径，而数据库设计采用 `base_*`、`iam_*`、`rbac_*`、`base_environment_variable`、`secret_provider`、`secret_reference`。若不先统一，DDL 可生成，但 P0 数据库校验会误报或漏报。

## 2. 范围覆盖检查

| WP1 范围 | 数据库设计覆盖情况 | 审核意见 |
|---|---|---|
| 多租户 | `base_tenant`、系统租户、租户内业务表 `tenant_id` | 覆盖。`base_tenant` 自身不额外保存 `tenant_id` 可接受。 |
| 多部门 | `base_department`、`base_department_member`、`base_department_manager` | 覆盖。支持部门树、多人负责人、主部门。 |
| 用户与会话 | `iam_user`、`iam_session` | 覆盖。包含用户状态、`auth_version`、会话撤销字段。 |
| 多项目 | `base_project`、`base_project_department`、`base_project_member` | 覆盖。项目多部门和多人负责人表达符合最终口径。 |
| 多应用 | `base_application` | 覆盖。未落单一负责人字段，符合冻结要求。 |
| 环境配置 | `base_environment`、`base_environment_variable`、`base_config` | 覆盖。项目公共环境/应用专属环境通过 `scope_type` + `app_id` 区分。 |
| RBAC | `rbac_permission`、`rbac_role`、`rbac_role_permission`、`rbac_role_binding` | 覆盖。角色编码和作用域模型基本一致。 |
| SecretProvider | `secret_provider`、`secret_reference` | 部分覆盖。缺少本地加密密文表或等价存储说明。 |
| 操作审计 | `audit_log`、`audit_outbox` | 覆盖。支持审计补偿、租户查询和拒绝审计。 |

## 3. 一致性检查

### 3.1 与 PRD / 架构 / API / 权限矩阵一致项

1. 角色编码使用 `SuperAdmin`、`TenantAdmin`、`DepartmentManager`、`ProjectOwner`、`AppOwner`、`Tester`、`Developer`、`Auditor`，与最终 PRD 和权限矩阵一致。
2. 项目与部门采用 `base_project_department` 多对多关系，主责部门用 `is_primary` 表达，符合架构冻结口径。
3. 部门负责人、项目负责人、应用负责人均未落单一负责人字段，通过关联表、成员关系和 `rbac_role_binding` 表达，符合 PRD 和架构。
4. 环境支持项目公共环境和应用专属环境，`base_environment.scope_type` 与 `app_id` 组合约束符合 API 契约。
5. 敏感变量设计区分 `PLAIN`、`SECRET`、`SECRET_REF`，不在环境变量表保存敏感明文，符合 API 契约。
6. 审计使用 `audit_log` + `audit_outbox`，且平台级审计要求使用系统租户，与架构和 API 契约一致。

### 3.2 不一致或需冻结口径项

| 问题 | 影响 | 结论 |
|---|---|---|
| 测试清单表名仍使用 `sys_tenant`、`sys_user`、`auth_role`、`base_env_variable`、`secret_store` 等旧名 | P0 结构校验、索引校验、敏感字段校验会与数据库设计不匹配 | 必须修正 |
| 数据库设计使用 `rbac_*`，架构设计部分章节仍保留 `auth_*` 历史表名 | 架构旧口径可能误导研发和 QA | 以数据库设计为候选最终表名，但需在报告后统一冻结 |
| 测试清单要求 `auth_policy_version`，数据库设计未建该表 | 权限缓存失效校验缺少统一落点 | 需明确采用 `iam_user.auth_version` 还是新增租户级 policy version |
| 架构提到本地 `secret_store` 保存密文，数据库设计只建 `secret_provider` 和 `secret_reference` | `LOCAL_ENCRYPTED` provider 无密文持久化落点 | 必须补齐或明确 P0 不启用本地 provider |
| API / 权限矩阵包含 `context:*` 权限点，API 第 4 章 P0 权限点清单未列，但数据库种子列入 | 契约章节之间有轻微不一致 | 建议统一 API 权限点总表 |

## 4. DDL 可落地性检查

### 4.1 已满足项

1. 主键：核心表均以 `uuid primary key` 建模，符合研发落地要求。
2. 唯一约束：租户编码、租户内编码、项目部门、项目成员、角色权限、角色绑定等关键唯一性均有约束或部分唯一索引设计。
3. 索引：租户内查询、权限计算、审计查询、环境作用域查询均有基础索引。
4. 软删除：基础组织对象、项目、应用、环境、授权关系均包含 `deleted_at/deleted_by`，唯一索引多数排除软删除数据。
5. `tenant_id`：除 `base_tenant`、`rbac_permission`、`schema_migration`、审计逻辑例外外，业务表均设计 `tenant_id`。
6. 审计字段：核心可变更表具备 `created_by/created_at/updated_by/updated_at/deleted_by/deleted_at/version`。
7. 迁移策略：已包含 Flyway/Liquibase、发布顺序、回滚、修复、并发建索引建议。

### 4.2 需修正或补充项

1. `secret_reference` 只保存引用元数据，不保存 `LOCAL_ENCRYPTED` 的密文、IV、tag、key version。若 P0 默认 provider 是本地加密，必须新增本地密文表或在设计中明确外部 KMS/Vault 为唯一落点。
2. `base_config` 的 `value_kind='SECRET_REF'` 未用 check 约束强制 `secret_ref is not null`，也未限制敏感配置误入 `value_json`，建议补充 DDL 约束或明确应用层强校验。
3. `rbac_role`、`secret_reference` 等表的 `scope_type/status` 部分枚举约束不完整，建议 DDL 生成阶段补齐 check constraint。
4. `audit_log` 只追加不可篡改更多依赖数据库账号权限和运维流程，DDL 草案未体现触发器或权限策略，需在迁移/部署设计中补充。
5. `SYSTEM_TENANT` 在文档中既像编码常量，又用于 `tenant_id` 口径；DDL 的 `tenant_id` 是 uuid，需明确系统租户是一条 `base_tenant.code='SYSTEM_TENANT'` 的记录，审计保存其 uuid。
6. 跨租户父部门、应用归属项目、角色绑定 `scope_id` 类型合法性等仍依赖服务层校验，DDL 生成时应同步输出服务层约束清单，避免误以为数据库已完全兜底。

## 5. 测试清单可执行性检查

测试清单覆盖面充分，包含表结构、约束、索引、租户隔离、软删除、唯一性、状态流、敏感字段、审计 outbox、种子数据、迁移、安全和性能，方向正确。

但当前**不可直接作为自动化断言输入**，主要原因是表名和字段名不一致：

| 清单口径 | 数据库设计口径 | 涉及测试 |
|---|---|---|
| `sys_tenant` | `base_tenant` | DB-STRUCT-001、DB-STRUCT-002、DB-IDX-001、DB-SECRET-004 |
| `sys_department` | `base_department` | DB-STRUCT-001、DB-STRUCT-004、DB-IDX-002、DB-IDX-005 |
| `sys_user` | `iam_user` | DB-STRUCT-001、DB-STRUCT-002、DB-SECRET-001 |
| `sys_session` | `iam_session` | DB-STRUCT-001、DB-STRUCT-002 |
| `auth_permission` | `rbac_permission` | DB-STRUCT-001、DB-SEED-001 |
| `auth_role` | `rbac_role` | DB-STRUCT-001、DB-IDX-002、DB-SEED-002 |
| `auth_role_permission` | `rbac_role_permission` | DB-IDX-005、DB-SEED-003 |
| `auth_role_binding` | `rbac_role_binding` | DB-IDX-007、DB-UNIQ-009 |
| `base_env_variable` | `base_environment_variable` | DB-STRUCT-001、DB-STRUCT-006、DB-IDX-003、DB-SECRET-002 |
| `secret_store` | 未在数据库设计中落表 | DB-STRUCT-001、DB-SECRET-003、DB-SECRET-007 |
| `auth_policy_version` | 未在数据库设计中落表 | DB-CONS-010、DB-STATE-006 |

测试清单需按最终表名更新后，才能进入 CI 迁移校验。否则会出现“设计正确但测试失败”或“旧表通过但新表未覆盖”的风险。

## 6. 阻塞问题与必须修正项

1. **统一最终表名前缀和表名。** 建议冻结数据库设计中的 `base_*`、`iam_*`、`rbac_*`、`audit_*`、`secret_*` 作为 WP1 DDL 口径，并同步修改测试清单中的旧表名。
2. **补齐 SecretProvider 本地密文存储模型。** 若 P0 使用 `LOCAL_ENCRYPTED`，必须有 `secret_store` 或等价表保存 `cipher_text/iv/tag/master_key_version`；若只保存外部引用，则需修改架构、测试和默认 seed，取消本地密文表预期。
3. **明确权限缓存版本策略。** 在 `auth_policy_version` 与 `iam_user.auth_version` / 租户级 policy version 之间二选一或组合建模，并同步 DDL、API 响应和测试断言。
4. **修正测试清单 P0 断言。** DB-STRUCT、DB-IDX、DB-SECRET、DB-SEED 中所有旧表名、旧字段名必须改为最终数据库设计口径。
5. **明确系统租户落库规则。** 需要写清 `SYSTEM_TENANT` 是 `base_tenant.code`，所有平台审计表的 `tenant_id` 保存系统租户 uuid。

## 7. 不阻塞但建议研发前完善项

1. 在 DDL 草案中补齐 `base_config`、`rbac_role`、`rbac_role_permission.effect`、`secret_reference.scope_type` 等枚举和敏感字段 check 约束。
2. 补充 `updated_at` 自动更新时间策略，可采用应用层统一填充或数据库 trigger，但需二选一。
3. 输出“数据库强约束 vs 服务层逻辑约束”清单，特别是跨租户关联、部门树无环、应用属于项目、角色绑定作用域合法性。
4. 审计不可篡改建议增加数据库权限方案：应用账号禁止 `UPDATE/DELETE audit_log`，迁移账号与运行账号分离。
5. 审计分区策略可在 P0 DDL 生成时预留，至少明确是否首版建普通表、后续迁移为分区表。
6. 种子数据建议生成可重复执行脚本，并覆盖新增 `context:*` 权限点与权限矩阵总表的一致性校验。
7. 补充幂等键存储策略。API 契约要求创建类接口支持 `Idempotency-Key`，数据库设计只在审计/outbox 中出现，业务创建幂等记录是否独立落表需明确。

## 8. 建议下一步

**建议进入数据库 DDL 脚本生成和迁移脚本设计，但必须先完成上述阻塞项的文档修正。**

推荐顺序：

1. 冻结最终表名映射，统一数据库设计、测试清单、DDL 脚本和 QA SQL 断言。
2. 决策并补齐 SecretProvider 本地密文存储与权限版本策略。
3. 基于修正后的数据库设计生成首版 DDL migration。
4. 将测试清单转换为可执行 SQL 元数据扫描、接口造数断言和迁移 CI 校验。
5. 组织一次 DDL 评审，重点评审敏感字段、审计 outbox、租户隔离、软删除唯一索引和种子幂等。
