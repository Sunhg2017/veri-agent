# WP1-WP4 数据库迁移回滚与前滚策略

| 项目 | 内容 |
|---|---|
| 覆盖范围 | `db/migration/wp1` 下承载 WP1、WP2、WP3、WP4 的统一 Flyway 迁移 |
| 当前策略 | Forward-only V migration，不使用 Flyway Undo |
| 核心脚本 | `scripts/wp1_migration_release_plan.sh`、`db/validation/run_wp1_db_validation.sh`、`scripts/wp1_release_role_validation.sh` |
| 适用环境 | 本地、CI、预发、生产 |

## 1. 策略结论

1. 本项目不引入 Flyway Undo 脚本。生产失败恢复以“迁移前备份恢复”和“前滚修复迁移”为主。
2. 每次预发或生产迁移前必须生成迁移发布计划，保存目标版本、待执行迁移、文件 SHA-256 和回滚姿态。
3. DDL/DML 已应用后不得手工改写历史 V 脚本；修复必须新增更高版本迁移。
4. 已切流后的生产问题优先前滚修复；只有在数据一致性、审计完整性或安全边界无法通过前滚恢复时，才执行恢复备份和应用回退。
5. 迁移角色、应用角色和只读角色必须分离，迁移前后都要执行 release role validation。

## 2. 发布前计划

生成当前分支的迁移计划：

```bash
bash scripts/wp1_migration_release_plan.sh
```

指定已上线版本和目标版本：

```bash
WP1_RELEASE_NAME='preprod-20260523-asset-release' \
WP1_MIGRATION_CURRENT_VERSION='20260522.027' \
WP1_MIGRATION_TARGET_VERSION='20260523.029' \
bash scripts/wp1_migration_release_plan.sh
```

脚本会输出：

- `build/wp1-migration-release-plan/release-plan.md`
- `build/wp1-migration-release-plan/manifest.tsv`

脚本硬门禁：

| 检查 | 失败处理 |
|---|---|
| 迁移目录存在且包含 `V*.sql` | 修正发布路径或迁移归档 |
| 文件名符合 `VyyyyMMdd_NNN__description.sql` | 重命名并重跑 DB validation |
| Flyway version 不重复 | 重新编号，禁止同版本覆盖 |
| `WP1_MIGRATION_TARGET_VERSION` 存在 | 修正目标版本或合并缺失迁移 |
| 每个迁移写入 SHA-256 | 缺少 hash 工具时修复 runner 镜像 |

## 3. 预发和生产执行顺序

1. 生成迁移发布计划并归档。
2. 执行临时库或 CI DB validation：

```bash
bash db/validation/run_wp1_db_validation.sh
```

3. 创建目标库迁移前备份。生产建议使用 DBA 受控备份工单；小库可额外保存 `pg_dump -Fc` 归档。
4. 使用 migration role 执行 Flyway 迁移，应用运行期账号不得执行 DDL。
5. 执行 release role validation：

```bash
WP1_RELEASE_DATABASE_URL='postgres://dba_readonly:***@preprod-db:5432/veri_agent' \
WP1_RELEASE_SCHEMA='public' \
WP1_RELEASE_APP_ROLE='wp1_app_preprod' \
WP1_RELEASE_READONLY_ROLE='wp1_readonly_preprod' \
WP1_RELEASE_MIGRATION_ROLE='wp1_migration_preprod' \
bash scripts/wp1_release_role_validation.sh
```

6. 启动候选应用版本，执行对应 WP smoke 或 `wp_all_integration_test.sh`。
7. 切流后持续观察错误率、慢查询、审计写入、模型调用和 webhook 失败事件。

## 4. 失败处理矩阵

| 阶段 | 现象 | 处理策略 |
|---|---|---|
| 迁移前 validation 失败 | 临时库迁移、seed 或安全断言失败 | 阻断发布；修复迁移或校验脚本后重跑，不接触目标库 |
| 迁移执行前发现计划不一致 | manifest SHA-256 与发布包不一致 | 阻断发布；重新打包、重新生成计划并复核 |
| 迁移中失败且事务已回滚 | Flyway 报错，目标 schema 未产生部分变更 | 停止发布；修复为新迁移或修正当前未发布脚本后重跑预发 validation |
| 迁移中失败且部分 DDL 已提交 | PostgreSQL 非事务性 DDL、索引或扩展产生部分状态 | 使用备份恢复到迁移前状态，或由 DBA 编写受控前滚清理脚本；禁止手工删改未知对象 |
| 迁移成功但应用未切流 | smoke 或 release role validation 失败 | 优先恢复迁移前备份；若失败仅为权限缺口，可 DBA 修正授权后重跑 validation |
| 已切流后出现兼容问题 | 新代码和新 schema 不兼容、性能或数据问题 | 优先前滚应用和迁移修复；必要时进入维护窗口恢复备份，并回退应用版本 |
| 审计或安全边界受损 | 审计 append-only、secret 权限或角色隔离失败 | 立即停止切流；撤销危险权限，保留证据，按安全事件处理 |

## 5. 前滚修复要求

前滚修复必须满足：

1. 新增更高版本 `VyyyyMMdd_NNN__*.sql`，不得修改已发布 migration。
2. 修复脚本可空库执行，也可在已应用失败状态的目标库幂等执行。
3. 补充或更新 `db/validation` 断言，覆盖本次失败模式。
4. 重跑 `db/validation/run_wp1_db_validation.sh` 和受影响 WP quality gate。
5. release notes 记录原始失败、修复迁移、验证命令和回滚点。

## 6. 备份恢复要求

生产恢复备份前必须确认：

1. 备份时间早于迁移开始时间。
2. 恢复目标、schema、角色和扩展与原库一致。
3. 恢复后立即执行 release role validation。
4. 应用版本同步回退到与恢复后 schema 兼容的版本。
5. 恢复过程、数据丢失窗口和业务补偿动作写入发布工单。

## 7. 准出记录

每次预发或生产 DB 迁移至少归档：

1. `release-plan.md` 和 `manifest.tsv`。
2. 迁移前备份工单或备份文件摘要。
3. `run_wp1_db_validation.sh` 输出目录或 CI artifact。
4. `wp1_release_role_validation.sh` 输出。
5. smoke / quality gate 命令和结果。
6. 失败处理记录、前滚修复迁移或恢复备份证据。
