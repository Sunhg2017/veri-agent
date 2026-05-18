# WP1 数据库迁移脚本交付审核报告

> 历史归档说明：本文审核对象是早期多租户迁移脚本交付，不代表当前迁移脚本实现。当前数据库以 `db/migration/wp1`、`db/validation`、`doc/mvp/final/engineering/当前实现基线.md` 和当前代码为准。

| 项目 | 内容 |
|---|---|
| 审核角色 | 时迁 / WP1 项目经理 |
| 审核对象 | `db/migration/*.sql`、`db/validation/*.sql`、迁移与校验 README |
| 参考文档 | `WP1-数据库设计.md`、`WP1-数据库设计修正复核报告.md` |
| 审核目标 | 判断迁移脚本与校验脚本是否满足 WP1 数据库设计，是否可以进入数据库执行验证和 CI 接入 |
| 审核日期 | 2026-05-16 |

## 1. 审核结论

**有条件通过。**

本次交付的 WP1 迁移脚本整体覆盖了数据库设计中 P0 必需的 schema、seed 与运行期数据库权限策略模板，核心表、租户隔离字段、软删除唯一索引、Secret 本地密文落点、审计表与 outbox 均已落到 SQL。迁移脚本可以进入临时 PostgreSQL 15+ 数据库做空库执行验证和重复执行 smoke test。

但当前校验脚本存在会阻断 CI 的口径不一致问题：`wp1_schema_validation.sql` 与 `wp1_security_validation.sql` 中部分预期索引名仍采用设计文档示例短名，而实际 DDL 使用了带表名前缀的规范命名；`wp1_seed_validation.sql` 对 `Tester`、`Developer` 的角色作用域预期与 seed 脚本不一致。这些问题会造成校验脚本在正确迁移结果上返回误报 `FAIL`，因此**不建议直接接入 CI 准出**，需先修正校验脚本口径后再作为阻断门禁。

## 2. 迁移脚本覆盖检查

### 2.1 Schema：`V20260516_001__wp1_base_schema.sql`

**覆盖充分。**

已覆盖 WP1 设计中的 22 张核心表：`base_tenant`、`base_department`、`base_department_manager`、`base_department_member`、`iam_user`、`iam_session`、`base_project`、`base_project_department`、`base_project_member`、`base_application`、`base_environment`、`base_environment_variable`、`base_config`、`rbac_permission`、`rbac_role`、`rbac_role_permission`、`rbac_role_binding`、`secret_provider`、`secret_reference`、`secret_local_store`、`audit_log`、`audit_outbox`。

已覆盖的关键设计点包括：

- 多租户：除 `base_tenant`、平台权限字典外，业务与安全相关表基本包含 `tenant_id`。
- 软删除：业务主表和关系表均有 `deleted_at`、`deleted_by`，唯一索引多数使用 `where deleted_at is null`。
- 多人负责人：使用 `base_department_manager`，未落单一负责人字段。
- 项目多部门：使用 `base_project_department`，并用部分唯一索引限制同项目一个启用主责部门。
- 环境双作用域：`base_environment.scope_type` 与 `app_id` check 约束覆盖项目公共环境和应用专属环境。
- 敏感变量：`base_environment_variable` 用 `value_kind`、`plain_value`、`secret_ref`、`secret_provider`、`secret_version`、`masked_value` 拆分，check 约束禁止 `SECRET/SECRET_REF` 保存 `plain_value`。
- SecretProvider：`secret_provider`、`secret_reference`、`secret_local_store` 已覆盖本地密文存储落点。
- RBAC：权限、角色、角色权限、角色绑定表及作用域 check 已覆盖。
- 审计：`audit_log`、`audit_outbox` 已覆盖，并保留幂等键和常用查询索引。

仍需服务层或后续迁移承接的内容已在脚本注释与 README 中说明，包括跨租户归属、部门树无环、环境 `app_id` 与 `project_id` 同属、`scope_id` 资源类型合法性等。

### 2.2 Seed：`V20260516_002__wp1_seed_permissions_roles.sql`

**覆盖基本充分。**

已覆盖：

- `SYSTEM_TENANT` 系统租户初始化，且 `tenant_id` 引用使用该行 UUID。
- P0 权限点初始化，包含 `context:read`、`context:switch`、`context:effective_read`。
- 8 个预置角色：`SuperAdmin`、`TenantAdmin`、`DepartmentManager`、`ProjectOwner`、`AppOwner`、`Tester`、`Developer`、`Auditor`。
- 预置角色权限绑定。
- 默认 `LOCAL_ENCRYPTED` SecretProvider。
- 系统默认配置 `base_config`，包括审计保留、会话 TTL、默认 SecretProvider 等。

注意项：

- 权限点使用 `on conflict (code) do update`，幂等和漂移修正较好。
- 角色、角色权限、默认 provider、默认配置使用 `where not exists` 插入，重复执行不会重复造数，但对已有记录的名称、作用域、描述、配置漂移不会自动修正。
- 角色权限 seed 只补新增绑定，不会软删除已废弃绑定；这符合“历史可解释”的保守策略，但后续权限收敛需要独立迁移。

### 2.3 Runtime Policy：`V20260516_003__wp1_runtime_db_policy.sql`

**作为模板覆盖充分，作为自动权限落地不足。**

脚本已明确：

- 应用角色对业务表、RBAC 关系表、Secret 引用和 outbox 的 DML 权限模板。
- 应用角色对 `audit_log` 仅 `select, insert`，撤销 `update/delete/truncate`。
- 只读角色不得读取 `secret_local_store`。
- 迁移角色拥有 DDL/DML 管理权限。
- 对 `audit_log`、`audit_outbox`、`secret_local_store`、敏感配置列添加注释。

风险是实际 `grant/revoke` 块被注释，默认执行时只会写 comment，不会真正配置数据库角色权限。该处理适合作为跨环境模板，但正式环境必须由 DBA 或 IaC 管道替换角色名并执行授权块，否则安全校验只能得到提示性结论。

## 3. 校验脚本覆盖检查

### 3.1 Schema Validation：`wp1_schema_validation.sql`

**覆盖方向正确，但存在阻塞误报。**

覆盖了核心表存在性、旧表名排除、关键字段、`tenant_id` 覆盖、废弃单负责人字段排除、关键索引、check constraint 等。

阻塞问题：

- 关键索引校验的预期名称与 DDL 不一致。例如校验期望 `uk_project_dept`、`uk_project_primary_dept`、`uk_project_member`、`uk_application_project_code`、`uk_env_project_code`、`uk_env_app_code`、`uk_env_var_key`、`uk_permission_code`、`uk_role_tenant_code`、`uk_role_permission`、`idx_audit_tenant_time` 等；实际 DDL 使用 `uk_base_project_department_project_dept`、`uk_base_project_department_primary`、`uk_base_project_member_project_user`、`uk_base_application_project_code`、`uk_base_environment_project_code`、`uk_base_environment_app_code`、`uk_base_environment_variable_key`、`uk_rbac_permission_code`、`uk_rbac_role_tenant_code`、`uk_rbac_role_permission`、`idx_audit_log_tenant_time` 等。
- 因此在实际 DDL 正确执行后，`schema.key_indexes_exist` 仍会返回 `FAIL`，不适合直接进入 CI 阻断。

### 3.2 Seed Validation：`wp1_seed_validation.sql`

**覆盖方向正确，但角色作用域预期需修正。**

覆盖了 `SYSTEM_TENANT`、P0 权限点、`context:*` 权限点、8 个预置角色、角色幂等、默认本地 SecretProvider、核心角色权限绑定、禁止明文密钥权限点等。

阻塞问题：

- seed 脚本中 `Tester` 的 `scope_type='ENVIRONMENT'`、`Developer` 的 `scope_type='APPLICATION'`。
- 校验脚本期望 `Tester='PROJECT'`、`Developer='PROJECT'`。
- 该差异会导致 `seed.system_builtin_roles_exist` 在实际 seed 正常执行后误报 `FAIL`。需确认最终业务口径后统一 seed 与 validation；从当前 seed 说明看，`Tester` 偏环境使用、`Developer` 偏应用脱敏查看，当前 seed 更贴近角色说明。

### 3.3 Security Validation：`wp1_security_validation.sql`

**覆盖较完整，但索引名称与运行期角色校验仍需环境化。**

覆盖了敏感字段拆分、本地密文表、审计表、`tenant_id` 覆盖、审计与密钥索引、疑似敏感环境变量不得保存为 `PLAIN`、`SECRET/SECRET_REF` 不得保存明文、本地密文字段完整性、应用角色不得更新/删除审计日志、审计日志触发器/规则保护提示等。

阻塞或注意项：

- 审计索引预期名称同样使用 `idx_audit_tenant_time`、`idx_audit_resource`、`idx_audit_actor_time`、`idx_audit_trace`，实际 DDL 为 `idx_audit_log_tenant_time`、`idx_audit_log_resource`、`idx_audit_log_actor_time`、`idx_audit_log_trace`，会造成 `security.audit_secret_indexes_exist` 误报 `FAIL`。
- 应用角色默认只检查 `wp1_app`、`veri_agent_app`，真实环境需要替换或参数化，否则只能得到 `WARN`。
- `audit_log` 没有触发器或 rule 级不可变保护，当前依赖数据库授权与运维流程，安全脚本会给出 `WARN`。P0 可接受，但发布评审需记录真实控制措施。

## 4. 与数据库设计一致性检查

总体一致。

一致项：

- 表命名已统一到 `base_*`、`iam_*`、`rbac_*`、`secret_*`、`audit_*`，未继续使用旧 `sys_*`、`auth_*` 核心表名。
- `auth_policy_version` 未落表，符合修正复核后的 `iam_user.auth_version` 与 `rbac_role.version` 策略。
- `SYSTEM_TENANT` 以 `base_tenant.code` 固化，引用使用 uuid，关闭了前序复核风险。
- 本地 SecretProvider 的 `secret_local_store` 已落表，字段覆盖密文、IV、认证 Tag、算法、主密钥版本，不保存明文。
- 多租户、软删除、多人负责人、项目多部门、环境双作用域、RBAC 作用域、审计 outbox 等核心设计均有数据库结构承载。

需澄清或保持记录的差异：

- 设计文档示例中部分索引短名与实际 DDL 规范长名不一致。此为命名口径差异，不是结构缺失，但校验脚本必须同步。
- `Tester`、`Developer` 默认作用域在设计表、seed、validation 之间需最终确认。目前 seed 为 `Tester=ENVIRONMENT`、`Developer=APPLICATION`，validation 为 `PROJECT`。
- 审计“不可绕过”在数据库层尚未强制到触发器或规则，主要依赖应用写入、outbox 补偿和 runtime grant/revoke。P0 可接受，但必须在实际权限验证中证明应用账号不能修改或删除 `audit_log`。

## 5. SQL 可执行性风险

### 5.1 PostgreSQL 语法

整体使用 PostgreSQL 15+ 可支持语法，包括 `gen_random_uuid()`、`jsonb`、`timestamptz`、部分唯一索引、表达式索引、`on conflict`、`to_regclass`、`has_table_privilege` 等。

执行前置条件是迁移账号具备 `create extension if not exists pgcrypto` 权限，以及目标 schema 对象创建权限。

### 5.2 幂等性

- DDL 使用 `create table if not exists`、`create index if not exists`，空库和重复 smoke test 友好。
- seed 权限点可更新，系统租户和多数 seed 只插入缺失记录，避免重复。
- 但 `create table if not exists` 不会修正已存在表的列、约束、索引定义漂移；真实生产迁移仍应按版本化 migration 逐步演进，不应依赖重复执行同一脚本修复结构。

### 5.3 变量占位

- `V20260516_003__wp1_runtime_db_policy.sql` 的 `:WP1_APP_ROLE` 等占位均在注释块内，默认执行不会因 psql 变量缺失失败。
- 如果启用授权块，必须使用 `psql -v` 或迁移工具变量替换，并确认角色名已存在。

### 5.4 事务

- 当前脚本未显式 `begin/commit`，由 Flyway、Liquibase 或 `psql` 执行模式决定事务边界。
- 空库初始化可接受；生产大表演进时 README 已提示索引可能需要 `create index concurrently` 独立迁移。
- 若迁移工具将整段放入事务，`create extension` 通常可执行，但仍受数据库权限和托管数据库策略限制。

### 5.5 回滚

- README 与设计文档均采用“生产向前修复”策略，未提供 down migration。
- 对初次空库失败，建议走快照恢复或 DBA 审批后清空 schema。
- 该策略符合审计与 seed 历史可解释要求，但 CI/预发应增加空库重建验证，避免失败后人工清理成本过高。

## 6. 安全与敏感数据检查

通过基础检查，但实际权限落地需验证。

已满足：

- 迁移脚本未写入明文密码、Token、Cookie、API Key、Secret 明文。
- 默认 SecretProvider 只保存环境变量名 `WP1_LOCAL_SECRET_MASTER_KEY`，不保存主密钥。
- 敏感环境变量和配置通过 `SECRET/SECRET_REF`、`secret_ref`、`masked_value` 与本地密文表拆分。
- `secret_local_store` 只存密文材料。
- `audit_log` 与 `audit_outbox` 具备审计和补偿结构。
- runtime policy 模板明确应用账号不得更新、删除、截断 `audit_log`，只读账号不得读取 `secret_local_store`。

需执行验证：

- 真实应用数据库角色是否没有 `UPDATE/DELETE/TRUNCATE audit_log` 权限。
- 真实只读角色是否无法读取 `secret_local_store`。
- 审计 JSON、配置 JSON、outbox payload 在服务层是否禁止写入明文敏感值。
- 疑似敏感环境变量 key 是否被服务层强制走 SecretProvider，而不是仅依赖事后 SQL 扫描。

## 7. 阻塞问题与建议修正项

### 阻塞问题

1. **校验脚本索引名称与 DDL 不一致。**  
   影响：`wp1_schema_validation.sql`、`wp1_security_validation.sql` 会在正确 DDL 上误报 `FAIL`，阻断 CI。  
   建议：统一采用实际 DDL 索引名，或将校验从“固定索引名”调整为“索引定义/唯一性/字段组合”校验。

2. **预置角色作用域校验与 seed 不一致。**  
   影响：`wp1_seed_validation.sql` 的 `seed.system_builtin_roles_exist` 会误报 `FAIL`。  
   建议：确认最终口径后同步。若按当前 seed，则校验应为 `Tester=ENVIRONMENT`、`Developer=APPLICATION`。

3. **runtime policy 授权块默认不执行。**  
   影响：迁移后数据库不会自动完成应用账号、只读账号、迁移账号权限隔离。  
   建议：正式环境由 DBA/IaC 创建角色并执行替换后的授权块；CI 可增加一个带测试角色的权限验证 job。

### 建议修正项

1. 为 validation README 增加“索引命名以 DDL 为准，若改名需同步校验”的准出要求，并在 CI 中把误报类检查先修正再启用阻断。
2. 对 seed 中 `where not exists` 的内置角色和默认配置，考虑后续补充 drift validation，发现名称、scope、provider config 与基线不一致时返回 `WARN` 或 `FAIL`。
3. 对 `audit_log` 不可变策略，P0 可先使用权限控制；若合规要求提高，再增加拒绝 `UPDATE/DELETE` 的 trigger/rule 或独立审计归档策略。
4. 对服务层逻辑约束形成单独测试清单：跨租户关联、部门树无环、应用与环境同项目、角色绑定 scope 类型合法性、SecretProvider 本地加密失败回滚。

## 8. 是否建议进入实际数据库执行验证

**建议进入临时数据库执行验证，但不建议直接进入 CI 阻断。**

建议执行范围：

1. 使用 PostgreSQL 15+ 空库按顺序执行 3 个 migration。
2. 重复执行 migration smoke test，观察 DDL 和 seed 幂等表现。
3. 执行 schema、seed、security validation，记录当前已知误报项。
4. 修正校验脚本口径后，再作为 CI 阻断门禁。
5. 在带真实或测试数据库角色的环境中执行 runtime grant/revoke，并运行 security privilege checks。

CI 接入准入条件：

- 修正索引名称误报。
- 修正 `Tester`、`Developer` 角色作用域误报。
- 明确 `WARN` 处理策略：无真实应用角色时 `WARN` 不阻断；真实角色存在且可更新/删除 `audit_log` 时必须阻断。
- 固化执行顺序和 `ON_ERROR_STOP=1`。

## 9. 下一步建议

1. 先修正 validation 与 DDL/seed 的口径差异，避免 CI 首次接入即产生误报。
2. 在临时 PostgreSQL 15+ 空库执行 migration，并保存 schema、seed、security validation 输出。
3. 建立 CI job：空库创建、按序 migration、按序 validation、解析 `FAIL` 阻断、归档 `WARN`。
4. 建立数据库角色验证 job：创建测试 app/readonly/migration role，执行 runtime policy 替换版，验证 `audit_log` 和 `secret_local_store` 权限边界。
5. 将服务层逻辑约束纳入 WP1 API 自动化测试，而不是误放到 DDL 校验里。
