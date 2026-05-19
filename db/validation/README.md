# 数据库校验脚本

这些脚本用于在 PostgreSQL 迁移和 seed 执行后做可执行/半自动准出检查，当前覆盖 WP1 平台基础底座、WP2 模型接入层、WP3 资产基础表和 WP4 文档输入。所有 SQL 都尽量返回统一列：

```text
check_name | status | details
```

`status='PASS'` 表示通过，`FAIL` 表示应阻断发布，`WARN` 表示需要人工复核或替换环境参数后再判定。

## 执行顺序

建议在空库迁移、重复迁移、预发升级后按顺序执行：

```bash
psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f db/validation/wp1_schema_validation.sql
psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f db/validation/wp1_seed_validation.sql
psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f db/validation/wp1_security_validation.sql
psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f db/validation/wp_all_schema_validation.sql
psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f db/validation/wp4_document_input_validation.sql
```

CI 可以把输出保存为日志，并检查是否出现 `FAIL`。当前 Docker 临时库脚本会自动创建标准测试角色并验证运行期权限，正常情况下 WP1 validation 不应再出现 `WARN`；默认发现 `WARN` 也会以非零状态退出。如确需临时放行告警，可显式设置 `WP1_ALLOW_DB_VALIDATION_WARN=1`，并在发布评审中记录结论。

如果本机或 CI Runner 没有安装 `psql`，但可使用 Docker，可以直接执行：

```bash
bash db/validation/run_wp1_db_validation.sh
bash db/validation/run_wp2_db_validation.sh
```

脚本会启动临时 PostgreSQL 15 容器，顺序执行 migration、validation，并重复执行一次用于幂等性 smoke test。WP1 脚本会执行 WP1/WP2/WP3/WP4 位于 `db/migration/wp1` 下的统一迁移，并创建 `wp1_app`、`wp1_readonly`、`wp1_migration` 三个测试数据库角色，套用运行期授权策略后验证审计表 append-only 权限和 WP4 schema 准出项。默认输出目录分别为 `build/wp1-db-validation/` 和 `build/wp2-db-validation/`；WP1 出现 `FAIL` 或未显式放行的 `WARN` 时以非零状态退出，WP2 出现 `FAIL` 时以非零状态退出。

## 脚本说明

- `wp1_schema_validation.sql`：校验 WP1 核心表、关键字段、关键索引/约束，确认租户表和 `tenant_id` 未混入当前模型。
- `wp1_seed_validation.sql`：校验 P0 权限点、8 个预置角色、`context:*` 权限点、默认 `LOCAL_ENCRYPTED` provider、核心角色权限绑定。
- `wp1_security_validation.sql`：校验敏感字段拆分、本地密文表、审计表、审计/密钥索引、疑似敏感变量明文风险、审计表 append-only 权限和触发器保护。
- `wp_all_schema_validation.sql`：校验单平台 WP1/WP2/WP3/WP4 核心表、关键字段、关键索引，以及租户表/`tenant_id` 回归。
- `wp2_model_access_validation.sql`：校验 WP2 模型供应商、Prompt 版本、调用日志表、关键索引、明文密钥/Prompt body 字段风险，并确认模型接入表未重新引入 `tenant_id`。
- `wp4_document_input_validation.sql`：校验 WP4 文档输入表、关键字段、索引、状态约束、`DEAD_LETTER` 状态、webhook event/idempotency 唯一索引、默认字段映射和无 `tenant_id` 回归。

## 预期结果

迁移、seed 和临时库运行期角色策略正常时，核心检查应全部返回 `PASS`。安全脚本中的 `security.audit_log_app_role_append_only` 默认检查 `wp1_app` 和 `veri_agent_app` 两个候选应用数据库角色；`run_wp1_db_validation.sh` 会创建 `wp1_app` 并套用标准授权策略，因此本地和 CI 临时库应得到确定的 `PASS/FAIL`。

## 需要替换的环境项

如果预发或生产环境实际应用数据库角色不是 `wp1_app` 或 `veri_agent_app`，请在 `wp1_security_validation.sql` 的 `app_roles(role_name)` CTE 中替换为真实角色名后执行：

```sql
with app_roles(role_name) as (
    values ('your_real_app_role')
)
```

如使用多 schema 部署，执行前请设置 `search_path` 到 WP1 schema，或在连接串中指定目标 schema。脚本默认使用 `current_schema()`。

## 失败排查方向

- 表或字段缺失：回看 DDL migration 是否漏建对象，或对象是否落在非当前 schema。
- 索引缺失：确认唯一索引、部分唯一索引和审计查询索引是否按 WP1 设计命名；若团队改名，需要同步更新校验脚本。
- 索引命名：校验脚本以 DDL 中的实际索引名为准，例如 `uk_base_project_department_project_dept`、`uk_base_environment_variable_key`、`idx_audit_log_time`。DDL 改名时必须同步更新校验脚本，避免 CI 误报。
- seed 缺失：确认权限 seed、角色 seed、角色权限 seed 是否已执行且幂等。
- 敏感字段失败：检查 `SECRET/SECRET_REF` 是否仍写入 `plain_value`，以及疑似密码、token、cookie、api key 的变量是否被错误保存为 `PLAIN`。
- 审计权限失败：确认应用账号没有 `UPDATE/DELETE/TRUNCATE audit_log` 权限，并确认 `trg_audit_log_prevent_update_delete` 已存在。
