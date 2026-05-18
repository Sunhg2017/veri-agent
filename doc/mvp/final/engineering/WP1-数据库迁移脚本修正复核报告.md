# WP1 数据库迁移脚本修正复核报告

> 历史归档说明：本文复核对象是早期多租户迁移脚本，不代表当前迁移脚本实现。当前数据库以 `db/migration/wp1`、`db/validation`、`doc/mvp/final/engineering/当前实现基线.md` 和当前代码为准。

| 项目 | 内容 |
|---|---|
| 复核角色 | 时迁 / WP1 项目经理 |
| 复核对象 | WP1 数据库迁移脚本、seed 脚本、runtime policy 模板、validation 脚本与 validation README |
| 前序报告 | `WP1-数据库迁移脚本交付审核报告.md` |
| 复核日期 | 2026-05-16 |

## 1. 复核结论

**有条件通过。**

本次修正已关闭前序审核中会造成校验脚本误报 `FAIL` 的两项口径问题：关键索引名已按 DDL 实际命名同步到 schema/security validation，`Tester`、`Developer` 的角色作用域也已与 seed 脚本保持一致。validation README 已补充索引命名以 DDL 为准、改名需同步校验脚本的说明。

`V20260516_003__wp1_runtime_db_policy.sql` 仍保持运行期权限模板形态，授权块默认注释，不会在迁移默认执行时自动完成 DB role 授权。该处理符合“不同环境替换真实角色后执行”的交付口径，但进入正式准出前必须在临时数据库或预发数据库中完成替换执行与权限验证。

## 2. 前序问题关闭情况

| 前序问题 | 当前复核结果 | 结论 |
|---|---|---|
| 校验脚本索引名与 DDL 不一致，导致正确迁移结果被误判 `FAIL` | `wp1_schema_validation.sql` 与 `wp1_security_validation.sql` 中关键索引名已对齐 DDL，例如 `uk_base_project_department_project_dept`、`uk_base_environment_variable_key`、`idx_audit_log_tenant_time`、`idx_audit_log_trace` 等 | 已关闭 |
| `Tester`、`Developer` 角色作用域预期与 seed 不一致 | seed 中 `Tester=ENVIRONMENT`、`Developer=APPLICATION`；`wp1_seed_validation.sql` 的预期已同步为相同口径 | 已关闭 |
| runtime policy 默认不执行，无法自动落地应用/只读/迁移角色权限 | 脚本仍为模板，授权块默认注释；README 与脚本注释已说明需替换真实角色后执行，security validation 对未替换角色返回 `WARN` | 未作为缺陷关闭，转为环境执行前置条件 |
| README 缺少索引命名同步说明 | `db/validation/README.md` 已补充索引命名以 DDL 为准及改名同步要求 | 已关闭 |

## 3. 剩余风险

1. **runtime policy 仍依赖环境化执行。** 默认执行迁移不会配置 `WP1_APP_ROLE`、`WP1_READONLY_ROLE`、`WP1_MIGRATION_ROLE` 的实际权限；若 DBA/IaC 未替换并执行授权块，`audit_log` 追加写、只读账号隔离、`secret_local_store` 访问限制不会自动生效。
2. **security validation 的应用角色校验仍需替换真实角色。** 默认候选角色为 `wp1_app`、`veri_agent_app`；若目标环境使用其他角色名，未替换时只能得到 `WARN`，不能证明真实应用账号权限边界。
3. **审计不可变性主要依赖授权与运维流程。** 当前 DDL 未提供触发器或 rule 级别的 `audit_log` UPDATE/DELETE 防护，P0 可接受，但临时库验证必须证明应用角色不能修改或删除审计日志。
4. **服务层约束仍不在 DDL 中强制。** 跨租户归属、部门树无环、环境 `app_id` 与 `project_id` 同属、`scope_id` 类型合法性仍需 WP1 API 与集成测试覆盖。
5. **尚未看到实际 PostgreSQL 执行输出。** 本次复核基于脚本文本一致性，仍需要临时数据库实跑确认语法、权限、幂等和 validation 输出。

## 4. 是否建议进入临时数据库执行验证与 CI 接入

**建议进入临时数据库执行验证。**

建议按 PostgreSQL 15+ 空库执行：

1. 顺序执行 `V20260516_001__wp1_base_schema.sql`、`V20260516_002__wp1_seed_permissions_roles.sql`、`V20260516_003__wp1_runtime_db_policy.sql`。
2. 重复执行 migration/seed smoke test，确认幂等行为。
3. 执行 `wp1_schema_validation.sql`、`wp1_seed_validation.sql`、`wp1_security_validation.sql`，要求无 `FAIL`；`WARN` 必须记录人工结论。
4. 创建测试应用角色、只读角色、迁移角色，替换 runtime policy 中的角色变量并执行授权块。
5. 复跑 security validation，确认应用角色不能 `UPDATE/DELETE/TRUNCATE audit_log`，只读角色不能读取 `secret_local_store`。

**建议在临时库验证通过后接入 CI。**

CI 接入建议以 validation 中 `FAIL` 作为阻断条件；`WARN` 可以先归档为人工复核项，但当 CI 环境已创建真实测试应用角色后，审计权限相关检查应从 `WARN` 收敛为可判定的 `PASS/FAIL`。

## 5. 下一步建议

1. 立即启动临时 PostgreSQL 15+ 空库执行验证，保存 migration 与 validation 日志作为 WP1 准出附件。
2. 在 CI 中固化执行顺序和 `ON_ERROR_STOP=1`，优先接入 schema、seed、security validation 的 `FAIL` 阻断。
3. 增加一个数据库角色权限验证 job，专门覆盖 runtime policy 替换执行、`audit_log` 追加写边界、`secret_local_store` 只读隔离边界。
4. 将服务层强约束纳入 WP1 API 自动化测试清单，包括跨租户关联、部门树无环、环境归属一致、角色绑定 scope 合法性和敏感配置不得明文落库。
5. 后续若 DDL 索引命名或预置角色 scope 再调整，必须同步更新 validation 脚本与 README，避免 CI 出现口径漂移。
