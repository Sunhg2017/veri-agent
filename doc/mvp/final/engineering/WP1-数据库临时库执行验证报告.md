# WP1 数据库临时库执行验证报告

| 项目 | 内容 |
|---|---|
| 验证对象 | WP1 PostgreSQL migration、seed、runtime policy template、validation SQL |
| 验证角色 | 时迁 / WP1 项目经理，明澈 / 测试专家 |
| 验证环境 | Docker PostgreSQL 15 Alpine 临时库 |
| 验证日期 | 2026-05-17 |
| 验证结论 | 通过 |

## 1. 验证结论

**通过。**

WP1 数据库迁移脚本已在 PostgreSQL 15 临时空库完成真实执行验证。五段 migration 按顺序执行成功，重复执行 smoke test 成功，schema、seed、security 三类 validation 均未出现 `FAIL`。

临时库验证脚本已创建 `wp1_app`、`wp1_readonly`、`wp1_migration` 三个测试数据库角色，并套用运行期授权策略。security validation 已可验证审计表 append-only 权限，当前无 `WARN`、无 `FAIL`。脚本默认发现 `WARN` 也会以非零状态退出，避免 CI 带着人工复核项误通过。

## 2. 验证环境

| 项目 | 值 |
|---|---|
| 数据库版本 | PostgreSQL 15 Alpine |
| 容器名称 | `veri-agent-wp1-pg-ci` |
| 数据库 | `veri_agent_wp1_test` |
| 执行方式 | Docker 容器内 `psql` |
| 日志目录 | `build/wp1-db-validation/` |
| 本地依赖 | 不依赖宿主机 `psql`，仅依赖 Docker |

## 3. 执行范围

### 3.1 Migration 执行顺序

1. `db/migration/wp1/V20260516_001__wp1_base_schema.sql`
2. `db/migration/wp1/V20260516_002__wp1_seed_permissions_roles.sql`
3. `db/migration/wp1/V20260516_003__wp1_runtime_db_policy.sql`
4. `db/migration/wp1/V20260517_004__wp1_account_lifecycle.sql`
5. `db/migration/wp1/V20260517_005__wp1_audit_immutability.sql`

### 3.2 Validation 执行顺序

1. `db/validation/wp1_schema_validation.sql`
2. `db/validation/wp1_seed_validation.sql`
3. `db/validation/wp1_security_validation.sql`

### 3.3 幂等性 smoke test

在首轮 migration 与 validation 完成后，已重复执行全部 migration 与 validation：

- `V20260516_001__wp1_base_schema.sql` 重跑时对象已存在均跳过，未报错。
- `V20260516_002__wp1_seed_permissions_roles.sql` 重跑时 seed 数据保持幂等，未产生重复角色、重复权限或重复 provider。
- `V20260516_003__wp1_runtime_db_policy.sql` 重跑仅刷新 comment/template 说明，未报错。
- `V20260517_004__wp1_account_lifecycle.sql` 重跑保持账号生命周期字段和索引幂等，未报错。
- `V20260517_005__wp1_audit_immutability.sql` 重跑可重复创建审计防修改函数和触发器，未报错。
- `run_wp1_db_validation.sh` 在首轮与重跑后均重新套用运行期角色授权策略，保证权限校验幂等。
- 三类 validation 重跑结果与首轮一致。

## 4. 验证结果

| 验证项 | 首轮结果 | 重跑结果 | 结论 |
|---|---|---|---|
| Migration 001 base schema | 成功 | 成功 | 通过 |
| Migration 002 seed permissions roles | 成功 | 成功 | 通过 |
| Migration 003 runtime DB policy template | 成功 | 成功 | 通过 |
| Migration 004 account lifecycle | 成功 | 成功 | 通过 |
| Migration 005 audit immutability | 成功 | 成功 | 通过 |
| Schema validation | 全部 `PASS` | 全部 `PASS` | 通过 |
| Seed validation | 全部 `PASS` | 全部 `PASS` | 通过 |
| Security validation | 10 项 `PASS`，0 项 `WARN`，0 项 `FAIL` | 10 项 `PASS`，0 项 `WARN`，0 项 `FAIL` | 通过 |

## 5. 关键通过点

1. **核心表结构完整。** `base_*`、`iam_*`、`rbac_*`、`secret_*`、`audit_*` 核心表均创建成功。
2. **最终命名口径一致。** 校验脚本未发现旧版 `sys_*`、`auth_*` 核心表名混入。
3. **单平台口径达标。** 校验脚本确认当前 WP1 表结构未混入 `tenant_id`，也未保留旧版租户表。
4. **关键索引与唯一约束存在。** 部门、项目/应用/环境、RBAC 绑定、审计查询、Secret 查询等关键索引均通过检查。
5. **Seed 数据完整且幂等。** P0 权限点、`context:*` 权限、8 个内置角色、角色权限绑定、默认 `LOCAL_ENCRYPTED` provider 均通过检查。
6. **敏感字段拆分达标。** 环境变量、Secret reference、本地密文表满足明文、密文、引用、mask 分离要求。
7. **审计不可变保护已落库。** `audit_log` 已具备触发器级 `UPDATE/DELETE` 阻断，临时库已验证 `wp1_app` 对审计表不可 `UPDATE/DELETE/TRUNCATE`。
8. **无 `FAIL`。** 首轮与重跑 validation 均未出现发布阻断项。

## 6. 日志附件

| 日志 | 说明 |
|---|---|
| `build/wp1-db-validation/migration.log` | 首轮 migration 执行日志 |
| `build/wp1-db-validation/migration-rerun.log` | 重跑 migration 执行日志 |
| `build/wp1-db-validation/wp1_schema_validation.out` | 首轮 schema validation 输出 |
| `build/wp1-db-validation/wp1_seed_validation.out` | 首轮 seed validation 输出 |
| `build/wp1-db-validation/wp1_security_validation.out` | 首轮 security validation 输出 |
| `build/wp1-db-validation/rerun-wp1_schema_validation.out` | 重跑 schema validation 输出 |
| `build/wp1-db-validation/rerun-wp1_seed_validation.out` | 重跑 seed validation 输出 |
| `build/wp1-db-validation/rerun-wp1_security_validation.out` | 重跑 security validation 输出 |

## 7. 后续准出要求

1. **CI 阻断规则。** CI 中应以 validation 输出出现 `FAIL` 或未显式放行的 `WARN` 作为阻断条件；当前临时库验证正常情况下不应出现 `WARN`。
2. **真实环境 DB role 权限复核。** 在预发或生产库创建真实应用角色、只读角色、迁移角色，替换 runtime policy 模板后验证：
   - 应用角色可以追加写 `audit_log`。
   - 应用角色不能 `UPDATE/DELETE/TRUNCATE audit_log`。
   - 只读角色不能读取 `secret_local_store` 密文字段。
   - 迁移角色和运行角色权限分离。
3. **服务层约束继续由 API 自动化覆盖。** 部门树无环、环境 `app_id` 与 `project_id` 归属一致、`scope_id` 类型合法、敏感配置不得明文落库等规则不完全依赖 DDL，需要进入 WP1 API 测试。
4. **进入后端骨架前置条件已满足。** 数据库 migration、seed、validation 可以作为 WP1 平台 API 的初始落库契约输入。

## 8. 最终建议

建议将 WP1 数据库 migration 与 validation 纳入 CI，作为后续 `platform-api` 后端骨架、权限服务、部门/项目/应用/环境 API 开发的准入检查。

下一步在当前 WP1 底座上继续增强：

1. 真实应用数据库角色、只读角色、迁移角色的 CI 权限验证 job。
2. 更细粒度的 RBAC 权限矩阵自动化用例。
3. 部门、项目、应用、环境配置 API 的异常路径和审计覆盖补强。
4. 与后续 WP2/WP3 的上下文传递契约联调。
