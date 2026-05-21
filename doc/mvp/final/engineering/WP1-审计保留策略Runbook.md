# WP1 审计保留策略 Runbook

| 项目 | 内容 |
|---|---|
| 覆盖任务 | WP1-C3 审计保留策略 |
| 适用范围 | `audit_log` 在线保留、归档、受控清理和发布验证 |
| 日期 | 2026-05-21 |

## 1. 策略目标

1. `audit_log` 继续作为运行期在线审计表，供管理台查询、CSV 导出、traceId 排查和近期合规审计使用。
2. 默认在线保留 365 天，清理任务默认关闭；生产开启前必须完成发布窗口评审和真实 app role validation。
3. 清理不授予运行期应用账号 `DELETE/UPDATE/TRUNCATE audit_log`，只允许执行受控函数 `wp1_cleanup_audit_log_before(timestamptz, integer)`。
4. 过期审计会先复制到 `audit_log_archive`，再从在线表删除；清理动作本身写入 `audit.retention_cleanup` 审计事件。

## 2. 配置项

| 配置 | 默认值 | 说明 |
|---|---|---|
| `WP1_AUDIT_RETENTION_CLEANUP_ENABLED` | `false` | 是否启用定时清理。默认关闭，避免未评审环境误删在线审计。 |
| `WP1_AUDIT_RETENTION_DAYS` | `365` | 在线表保留天数。 |
| `WP1_AUDIT_MIN_RETENTION_DAYS` | `30` | 应用侧保留天数下限；数据库函数也拒绝 30 天以内 cutoff。 |
| `WP1_AUDIT_RETENTION_CLEANUP_CRON` | `0 45 3 * * *` | Spring cron，默认每天 03:45 UTC 执行。 |
| `WP1_AUDIT_RETENTION_CLEANUP_BATCH_SIZE` | `1000` | 单次批量上限，应用侧和数据库侧最大都限制为 10000。 |

`base_config` 同步保留 `audit.retention_days`、`audit.retention_cleanup_enabled`、`audit.retention_min_days` 和 `audit.retention_cleanup_batch_size`，用于管理台展示和发布复核；实际定时任务以环境变量/配置文件为准。

## 3. 清理链路

1. `AuditRetentionCleanupService` 仅在 `db` profile 生效，按 cron 调用。
2. 服务计算 `cutoff = now - max(WP1_AUDIT_RETENTION_DAYS, WP1_AUDIT_MIN_RETENTION_DAYS)`。
3. MyBatis 调用 `wp1_cleanup_audit_log_before(cutoff, batchSize)`。
4. 数据库函数校验 cutoff 至少早于当前时间 30 天，按 `created_at < cutoff` 和批量上限选择记录。
5. 被选中记录先写入 `audit_log_archive`，确认已归档后才允许在线表删除。
6. 函数插入 `audit.retention_cleanup` 审计事件，记录 cutoff、deleted 和 batchSize。
7. 应用输出 `veri.agent.audit.retention.cleanup{result=success|failed}` 指标和结构化日志。

## 4. 权限边界

运行期 app role 必须保持：

- 对 `audit_log` 只有 `SELECT/INSERT`。
- 没有 `UPDATE/DELETE/TRUNCATE audit_log`。
- 只有 `EXECUTE wp1_cleanup_audit_log_before(timestamptz, integer)`，没有直接清理表权限。

预发和生产切流前必须执行：

```bash
WP1_RELEASE_DATABASE_URL='postgres://dba_readonly:***@preprod-db:5432/veri_agent' \
WP1_RELEASE_APP_ROLE='wp1_app_preprod' \
bash scripts/wp1_release_role_validation.sh
```

## 5. 验证和回滚

本地/CI 验证：

```bash
mvn -B -pl platform-api test
bash db/validation/run_wp1_db_validation.sh
bash scripts/wp1_quality_gate.sh
```

重点检查：

- `seed.audit_retention_config_*` 确认默认配置存在且安全。
- `security.audit_log_app_role_append_only` 确认 app role 不能直接修改/删除在线审计。
- `security.audit_retention_cleanup_*` 确认受控函数存在、授权正确、只删除 cutoff 之前记录并写入归档表。

回滚方式：

1. 立即设置 `WP1_AUDIT_RETENTION_CLEANUP_ENABLED=false` 并重启/刷新配置，停止后续定时清理。
2. 如需恢复在线查询，可由 DBA 从 `audit_log_archive` 按 `id` 或 `created_at` 受控回填到 `audit_log`，回填前需确认不会与现有 `idempotency_key` 和查询口径冲突。
3. 不允许通过授予 app role `DELETE audit_log` 的方式绕过清理函数。

## 6. 非目标范围

本轮不建设 Audit outbox 运维视图、归档查询 UI、对象存储冷归档、异步导出任务或分区表自动切换。这些能力可在 WP1-C4 或后续生产数据治理专项中继续推进。
