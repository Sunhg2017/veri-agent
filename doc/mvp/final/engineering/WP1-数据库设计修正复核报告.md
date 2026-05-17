# WP1 数据库设计修正复核报告

| 项目 | 内容 |
|---|---|
| 复核角色 | 时迁 / WP1 数据库设计修正复核 |
| 复核对象 | `WP1-数据库设计.md`、`WP1-数据库测试与校验清单.md`、`WP1-P0-API契约.md`、`WP1-数据库设计交付审核报告.md` |
| 复核目标 | 判断前序阻塞项是否关闭，以及是否可进入 DDL 脚本生成和迁移脚本设计 |
| 复核日期 | 2026-05-16 |

## 1. 复核结论

**通过。**

本轮修正已关闭前序审核报告中影响 DDL 生成的主要阻塞项：测试清单表名已统一到 `base_*`、`iam_*`、`rbac_*`、`secret_*`、`audit_*` 口径；本地 `SecretProvider` 已补齐 `secret_local_store` 密文落点；权限版本策略已明确不建设 `auth_policy_version`，改用 `iam_user.auth_version` 与 `rbac_role.version`；`context:*` 权限点已在 API 契约、数据库种子和角色权限建议中保持一致。

数据库设计可以进入 DDL 脚本生成和迁移脚本设计。DDL 阶段仍需把文档中的逻辑约束、种子变量和数据库账号权限策略转成可执行脚本与校验断言。

## 2. 前序阻塞项关闭情况

| 前序阻塞项 | 复核结果 | 说明 |
|---|---|---|
| 测试清单仍使用旧表名 | 已关闭 | 测试清单 `DB-STRUCT-001` 已核对 `base_tenant`、`iam_user`、`base_environment_variable`、`secret_provider`、`secret_reference`、`secret_local_store`、`rbac_role`、`rbac_role_binding` 等最终表名，未继续以 `sys_*`、`auth_*`、`base_env_variable`、`secret_store` 作为核心模型。 |
| SecretProvider 本地密文存储落点缺失 | 已关闭 | 数据库设计新增 `secret_local_store`，字段覆盖 `cipher_text`、`iv`、`auth_tag`、`algorithm`、`master_key_version`、`status`，并说明只保存密文材料、不保存明文；测试清单也新增 `DB-SECRET-003` 和 `DB-SECRET-007` 校验本地密文表与失败回滚。 |
| `auth_policy_version` 策略不明确 | 已关闭 | 数据库设计明确 P0 不单独建设 `auth_policy_version` 表，采用 `iam_user.auth_version` 和 `rbac_role.version`；测试清单 `DB-CONS-010` 已改为校验用户级版本递增与角色版本变化。 |
| `context:*` 权限点口径不一致 | 已关闭 | API 契约第 4 章列出 `context:read`、`context:switch`、`context:effective_read`；数据库设计权限种子和预置角色权限建议同步包含 `context:*` 或具体 context 权限点。 |
| 系统租户落库规则需明确 | 基本关闭 | 数据库设计已明确系统租户编码固定为 `SYSTEM_TENANT`，并在本地 provider seed 中使用 `:system_tenant_id`。DDL 阶段需进一步固化为一条 `base_tenant.code='SYSTEM_TENANT'` 的种子记录，并确保审计表 `tenant_id uuid` 保存该记录的 uuid，而不是字符串字面量。 |

## 3. 当前剩余风险

1. 部分跨租户关联、部门树无环、环境 `app_id` 与 `project_id` 归属、角色绑定 `scope_id` 类型合法性仍依赖服务层校验。DDL 生成时需要同步输出“数据库强约束 vs 服务层逻辑约束”清单，避免测试误判。
2. `audit_log` 不可篡改主要依赖数据库账号权限、触发器或运维流程。迁移脚本应区分应用账号和迁移账号权限，至少禁止应用账号 `UPDATE/DELETE audit_log`。
3. `SYSTEM_TENANT` 在 API 和测试清单中仍有少量表达为审计 `tenant_id=SYSTEM_TENANT` 的简写。由于 DDL 字段为 `uuid`，脚本和 SQL 断言需统一解析为系统租户 uuid。
4. `base_config`、`secret_reference.scope_type`、`rbac_role.scope/status` 等枚举和敏感字段规则虽已有设计口径，但 DDL 生成时仍需补齐 check constraint 或明确应用层强校验。
5. 本地 `LOCAL_ENCRYPTED` provider 的主密钥来源已建议使用环境变量，仍需在部署设计中补充主密钥轮换、备份恢复和泄露应急流程。

## 4. 是否建议进入 DDL 脚本生成

**建议进入。**

当前数据库设计已经具备生成首版 PostgreSQL 15+ DDL 的基础条件，测试清单也已经可以作为迁移后 SQL 元数据扫描、接口造数断言和 CI 准出门槛的输入。

进入 DDL 阶段时，建议把以下内容作为脚本生成的硬性输入：

1. 核心表、索引、唯一约束、check constraint 与软删除部分唯一索引。
2. `SYSTEM_TENANT`、P0 权限点、8 个预置角色、默认 `LOCAL_ENCRYPTED` provider 的幂等 seed。
3. `secret_local_store` 与 `secret_reference` 的关联约束和敏感字段不可明文校验用例。
4. 权限缓存版本策略，即用户状态、角色绑定、部门成员关系、角色权限变更时递增对应版本或清理缓存。
5. 审计表只追加权限策略和 `audit_outbox` 补偿机制。

## 5. 下一步建议

1. 生成首版 DDL migration，并用测试清单 `DB-STRUCT`、`DB-IDX`、`DB-SEED` 部分先做空库校验。
2. 编写 seed 幂等脚本，优先覆盖系统租户、权限点、角色、角色权限、默认 SecretProvider。
3. 把服务层逻辑约束整理成研发任务和接口自动化用例，重点覆盖跨租户、部门树、作用域授权、敏感变量回滚。
4. 补充数据库账号权限脚本，区分迁移账号、应用账号和只读测试账号。
5. 组织 DDL 评审，重点评审租户隔离、软删除唯一索引、审计不可篡改、SecretProvider 本地密文、权限版本失效链路。
