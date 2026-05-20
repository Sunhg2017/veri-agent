# WP1 发布前 DB 权限 Runbook

| 项目 | 内容 |
|---|---|
| 覆盖任务 | WP1-A2 发布前 DB 权限 runbook、WP1-A3 CI/发布流水线挂载说明 |
| 适用环境 | CI 临时库、预发、生产 |
| 核心脚本 | `db/validation/run_wp1_db_validation.sh`、`scripts/wp1_release_role_validation.sh` |
| 日期 | 2026-05-20 |

## 1. 目标

1. 在 CI 中持续验证 WP1-WP4 统一迁移、seed、安全约束和单平台 schema 口径。
2. 在预发/生产发布前验证真实应用数据库角色，不只依赖临时库测试角色。
3. 确认应用角色只能执行运行期需要的 DML，不拥有迁移 DDL 权限；审计表保持 append-only；本地密文表不能被运行期角色直接读取。

## 2. 角色和输入

| 输入 | 必填 | 示例 | 说明 |
|---|---|---|---|
| `WP1_RELEASE_DATABASE_URL` | 是 | `postgres://dba_readonly:***@preprod-db:5432/veri_agent` | 用于执行权限检查的 PostgreSQL 连接串；建议使用 DBA 提供的只读检查账号或受控运维账号 |
| `WP1_RELEASE_APP_ROLE` | 否 | `wp1_app_preprod` | 真实应用运行期数据库角色；默认 `wp1_app` |
| `psql` | 是 | `psql --version` | 执行真实环境 release role validation |
| DBA 复核记录 | 生产必填 | 工单链接 | 确认 app/migration/readonly 角色名和授权策略 |

连接串和密码不得写入 release notes 明文；归档日志中只保留脱敏 URL、角色名和检查结果。

## 3. CI 临时库门禁

每个 PR 或合并到主干后执行：

```bash
bash db/validation/run_wp1_db_validation.sh
```

脚本会启动临时 PostgreSQL 容器，顺序执行 `db/migration/wp1` 下 WP1/WP2/WP3/WP4 的统一迁移，并运行：

1. `wp1_schema_validation.sql`
2. `wp1_seed_validation.sql`
3. `wp1_security_validation.sql`
4. `wp_all_schema_validation.sql`
5. `wp4_document_input_validation.sql`

输出目录默认是 `build/wp1-db-validation/`。任何 `FAIL` 必须阻断；`WARN` 默认也阻断，只有临时排障时才允许显式设置 `WP1_ALLOW_DB_VALIDATION_WARN=1`，并在 release notes 中登记豁免。

## 4. 预发/生产 release role validation

在迁移完成、应用切流前执行：

```bash
WP1_RELEASE_DATABASE_URL='postgres://dba_readonly:***@preprod-db:5432/veri_agent' \
WP1_RELEASE_APP_ROLE='wp1_app_preprod' \
bash scripts/wp1_release_role_validation.sh
```

建议归档输出：

```bash
mkdir -p build
WP1_RELEASE_DATABASE_URL='postgres://dba_readonly:***@preprod-db:5432/veri_agent' \
WP1_RELEASE_APP_ROLE='wp1_app_preprod' \
bash scripts/wp1_release_role_validation.sh | tee build/wp1-release-role-validation-preprod.out
```

当前脚本硬门禁：

| 检查 | 通过条件 | 失败处理 |
|---|---|---|
| `release.role.exists` | `WP1_RELEASE_APP_ROLE` 在目标库存在 | DBA 创建或修正真实 app role 后重跑 |
| `release.audit_log.append_only` | app role 对 `audit_log` 只有 `SELECT/INSERT`，没有 `UPDATE/DELETE/TRUNCATE` | 立即撤销危险权限，确认审计不可变触发器仍存在后重跑 |
| `release.secret_local_store.not_readable` | app role 不能 `SELECT secret_local_store` | 撤销直接读取本地密文表权限；应用只能通过 SecretProvider resolve |

## 5. DBA 复核项

生产发布前 DBA 需要额外确认当前脚本之外的 DDL 风险：

```sql
select n.nspname as schema_name
from pg_namespace n
where has_schema_privilege(:'WP1_RELEASE_APP_ROLE', n.oid, 'CREATE')
  and n.nspname not in ('pg_catalog', 'information_schema');
```

期望：返回 0 行。

```sql
select n.nspname as schema_name, c.relname as object_name, c.relkind
from pg_class c
join pg_namespace n on n.oid = c.relnamespace
where pg_get_userbyid(c.relowner) = :'WP1_RELEASE_APP_ROLE'
  and n.nspname not in ('pg_catalog', 'information_schema');
```

期望：返回 0 行；应用角色不应拥有业务表、索引、序列或视图。

```sql
select grantee, table_schema, table_name, privilege_type
from information_schema.role_table_grants
where grantee = :'WP1_RELEASE_APP_ROLE'
  and privilege_type in ('TRUNCATE', 'TRIGGER', 'REFERENCES')
order by table_schema, table_name, privilege_type;
```

期望：返回 0 行，除非 DBA 在工单中说明受控例外。

## 6. 发布流水线挂载建议

| 阶段 | 触发 | 命令 | 日志归档 | 阻断规则 |
|---|---|---|---|---|
| PR/CI | 每次提交 | `bash db/validation/run_wp1_db_validation.sh` | `build/wp1-db-validation/` | `FAIL` 或未放行 `WARN` 阻断 |
| 主干 nightly | 每晚或合并后 | `bash scripts/wp1_quality_gate.sh` | CI job artifact | 任一命令非 0 阻断 |
| 预发发布 | migration 后、应用启动前 | `scripts/wp1_release_role_validation.sh` | `build/wp1-release-role-validation-preprod.out` 或 CI artifact | 任一 `release.*` FAIL 阻断 |
| 生产发布 | DBA 授权后、切流前 | `scripts/wp1_release_role_validation.sh` + DBA 复核 SQL | 发布工单附件 | 任一 FAIL、DDL 风险未解释阻断 |
| 权限变更 | app/migration/readonly role 变更后 | 重跑 release role validation | 变更工单附件 | 未通过不得继续流量切换 |

## 7. 常见失败处理

| 现象 | 可能原因 | 修复建议 |
|---|---|---|
| `WP1_RELEASE_DATABASE_URL is required` | 未注入连接串 | 在受保护 CI secret 或发布终端注入；不要提交到仓库 |
| `psql is required` | 执行环境缺少 PostgreSQL 客户端 | 在发布镜像或 runner 中安装 `psql` |
| `release.role.exists` FAIL | 角色名错误或 DBA 未创建 | 确认真实 app role 名称，设置 `WP1_RELEASE_APP_ROLE` 后重跑 |
| `release.audit_log.append_only` FAIL | app role 缺少 `INSERT/SELECT` 或拥有危险权限 | DBA 按运行期授权模板修正；确认没有审计 UPDATE/DELETE/TRUNCATE |
| `release.secret_local_store.not_readable` FAIL | app role 可直接读本地密文表 | 撤销 `secret_local_store` SELECT；通过 SecretProvider 使用密钥 |
| DBA DDL 复核返回行 | app role 拥有 schema CREATE 或对象 owner | 转移对象 owner 到 migration role，撤销 schema CREATE |

## 8. 准出记录

release notes 中至少记录：

1. CI 临时库 validation 命令、结果和 artifact。
2. 预发/生产 `WP1_RELEASE_APP_ROLE` 的脱敏角色名。
3. release role validation 输出。
4. DBA 复核 SQL 结果或工单链接。
5. 任何 WARN/豁免、owner、关闭时间。
