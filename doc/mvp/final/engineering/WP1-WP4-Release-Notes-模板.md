# WP1-WP4 Release Notes 模板

> 每次预发或生产升级前复制本模板，填写后随发布记录归档。未执行的检查必须写明原因、owner 和补救计划。

## 1. 发布摘要

| 项 | 内容 |
|---|---|
| 版本/分支/Commit |  |
| 发布时间 |  |
| 发布环境 | local / CI / preprod / production |
| 发布负责人 |  |
| 参与 owner | WP1：；WP2：；WP3：；WP4：；DBA：；运维： |
| 变更类型 | 功能 / 修复 / 配置 / 迁移 / 文档 / 回滚 |
| 准出结论 | 通过 / 有条件通过 / 不通过 |

## 2. 影响范围

| WP | 是否影响 | 变更摘要 | 受影响契约 |
|---|---|---|---|
| WP1 平台基础 | 否 |  | context / audit / secret / RBAC / DB |
| WP2 模型接入 | 否 |  | provider / prompt / invocation / budget / metrics |
| WP3 资产管理 | 否 |  | requirement / api / page / flow / testCase / trace link |
| WP4 文档输入 | 否 |  | import / candidate / publish / webhook / parser |
| portal-web | 否 |  | route / menu / permission / page |

## 3. 功能与契约变更

| 类型 | 内容 | 兼容性 |
|---|---|---|
| API/OpenAPI |  | 兼容 / 破坏性 |
| 数据库 |  | 无 / 可回滚 / 仅前滚 |
| 配置项 |  | 新增 / 变更 / 废弃 |
| 密钥/provider |  | 新增 / 轮换 / 启停 |
| Metrics/告警 |  | 新增 / 变更 / 无 |
| 文档/runbook |  | 已更新 / 无需更新 |

## 4. 数据库与迁移

| 项 | 内容 |
|---|---|
| 迁移脚本 |  |
| validation 命令 | `bash db/validation/run_wp1_db_validation.sh` / `bash db/validation/run_wp2_db_validation.sh` |
| validation 结果 |  |
| 生产 DB roles | `WP1_RELEASE_SCHEMA` / `WP1_RELEASE_APP_ROLE` / `WP1_RELEASE_READONLY_ROLE` / `WP1_RELEASE_MIGRATION_ROLE` |
| release role validation | `WP1_RELEASE_DATABASE_URL=... WP1_RELEASE_SCHEMA=... WP1_RELEASE_APP_ROLE=... WP1_RELEASE_READONLY_ROLE=... WP1_RELEASE_MIGRATION_ROLE=... bash scripts/wp1_release_role_validation.sh` |
| 回滚/前滚策略 |  |
| DBA 复核 |  |

## 5. 配置与密钥

| 项 | 内容 |
|---|---|
| 新增环境变量 |  |
| 变更环境变量 |  |
| SecretRef/apiKeyRef |  |
| 轮换窗口 |  |
| fallback 策略 |  |
| 过期和禁用计划 |  |

## 6. 验证记录

| 命令 | 环境 | 结果 | 证据/日志 |
|---|---|---|---|
| `mvn -B -pl platform-api test` |  |  |  |
| `cd portal-web && npm run test` |  |  |  |
| `cd portal-web && npm run build` |  |  |  |
| `bash db/validation/run_wp1_db_validation.sh` |  |  |  |
| `bash scripts/wp1_quality_gate.sh` |  |  |  |
| `bash scripts/wp2_quality_gate.sh` |  |  |  |
| `bash scripts/wp4_binary_document_smoke.sh` |  |  |  |
| `bash scripts/wp4_ai_parse_quality_eval.sh` |  |  |  |
| `bash scripts/wp_all_integration_test.sh` |  |  |  |
| 其他专项 |  |  |  |

## 7. 观测与告警

| 看板/指标 | 发布前状态 | 发布后状态 | 备注 |
|---|---|---|---|
| WP1 auth/context/audit/secret |  |  |  |
| WP2 `veri.agent.model_access.*` |  |  |  |
| WP3 asset/upsert/trace |  |  |  |
| WP4 `veri.agent.document_input.*` |  |  |  |
| 应用日志 traceId 抽样 |  |  |  |

## 8. 风险、豁免和回滚

| 风险/豁免 | 等级 | Owner | 缓解动作 | 截止时间 |
|---|---|---|---|---|
|  |  |  |  |  |

回滚步骤：

1. 待填写
2. 待填写
3. 待填写

前滚修复步骤：

1. 待填写
2. 待填写
3. 待填写

## 9. 签字确认

| 角色 | 姓名 | 结论 | 时间 |
|---|---|---|---|
| 发布负责人 |  |  |  |
| WP1 owner |  |  |  |
| WP2 owner |  |  |  |
| WP3 owner |  |  |  |
| WP4 owner |  |  |  |
| DBA/运维 |  |  |  |
