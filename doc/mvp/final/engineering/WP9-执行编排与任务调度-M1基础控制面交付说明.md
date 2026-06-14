# WP9 执行编排与任务调度 - M1 基础控制面交付说明

| 项目 | 内容 |
|---|---|
| 工作包 | WP9 执行编排与任务调度 |
| 交付阶段 | M1 基础控制面 |
| 覆盖 Story | WP9-1.1、WP9-1.2、WP9-1.3、WP9-1.4、WP9-1.5 |
| 当前口径 | 完成 execution 权限、DB schema、validation、配置和 health API；不实现 plan CRUD、DAG dryRun、调度执行和前端工作台 |
| 日期 | 2026-06-13 |

## 1. 需求结论

本轮 WP9 推进完成 M1 基础控制面：`platform-api` 新增 `execution` health API、配置项、权限常量、本地内置角色授权、PostgreSQL/Flyway 基础表、权限 seed、审计事件配置摘要和 DB validation。

本轮不实现执行计划 CRUD、DAG 校验、手动触发、队列认领、WP6 dispatch、webhook/cron 触发逻辑、导出接口和 `portal-web` 工作台，这些继续按 `WP9-执行编排与任务调度-研发任务拆解.md` 的 WP9-2.x 到 WP9-6.x 推进。

## 2. 主要变更

| 模块 | 变更 |
|---|---|
| `platform-api` | 新增 `/api/v1/execution/health`，输出调度开关、并发上限、超时、恢复批量和安全边界。 |
| 权限 | 新增 `execution:read/manage/trigger/admin/export` 常量和 local 内置角色授权。 |
| 配置 | 新增 `application-execution.yml`，默认关闭 scheduler、webhook 和 cron。 |
| 安全 | execution health 加入匿名访问和强制改密白名单。 |
| DB | 新增 `execution_plan`、`execution_plan_node`、`execution_run`、`execution_node_run`、`execution_trigger`、`execution_trigger_event`、`execution_queue_claim`。 |
| validation | 新增 `wp9_execution_validation.sql`，并纳入 `run_wp1_db_validation.sh` 和 `wp_all_schema_validation.sql`。 |

## 3. 验收标准

1. `GET /api/v1/execution/health` 可匿名访问，返回统一 envelope、traceId、默认关闭的调度/触发开关和不泄露敏感值的 policy。
2. WP9 权限点可在本地内置权限和 DB seed 中解析，默认角色符合 PRD。
3. WP9 基础表具备主键、外键、状态约束、JSON object 约束、幂等/查询索引、表注释和字段注释。
4. DB validation 能校验 WP9 表、列、约束、索引、权限、角色授权和基础配置。
5. 本轮不调用 runner adapter，不生成脚本，不保存 secret 明文、runner 原始输出或请求响应正文。

## 4. 验证结果

| 命令 | 结果 |
|---|---|
| `git diff --check` | 通过 |
| `mvn -B -pl platform-api test` | 通过，507 tests，0 failures，0 errors |
| `bash db/validation/run_wp1_db_validation.sh` | 通过，包含 WP9 migration 复跑和 `wp9_execution_validation.sql` |
| `cd portal-web && npm test` | 通过，22 files，178 tests |
| `cd portal-web && npm run build` | 通过，保留既有 Vite chunk size 和动态导入提示 |

## 5. 风险与回滚

| 风险 | 处置 | 回滚 |
|---|---|---|
| 新增 DB 表影响发布窗口 | 本轮只新增 WP9 表和权限 seed，不改旧表数据语义 | 回滚分支或按 Flyway 前滚修复策略禁用 WP9 入口 |
| 外部触发误启用 | scheduler、webhook、cron 默认关闭 | 保持 `WP9_*_ENABLED=false` |
| 后续实现绕过 WP6 边界 | health policy 明确 `directRunnerAdapterCallAllowed=false` | 后续 dispatch 仅允许通过 WP6 应用服务 |
| 权限矩阵偏差 | DB validation 检查关键角色授权 | 通过后续 seed 修正并复跑 validation |

## 6. 五角色结论

| 角色 | 结论 | 说明 |
|---|---|---|
| 资深项目经理 | 通过 | 本轮范围限定 M1，未抢跑调度执行和前端工作台。 |
| 资深产品经理 | 通过 | health 输出和权限矩阵符合 PRD，未扩大产品范围。 |
| 资深服务端架构师 | 通过 | DB、权限、配置和 API 均按 WP9 技术契约落地，保留后续可演进边界。 |
| 资深前端工程师 | 无影响 | 本轮未改 `portal-web` 功能代码；前端测试和构建通过。 |
| 资深质量工程师 | 通过 | 后端全量测试、DB validation、前端测试和构建均已执行通过。 |

## 7. 后续任务

1. WP9-2.x：实现 plan CRUD、状态保护、DAG validator、resource resolver 和 dryRun API。
2. WP9-3.x：实现 run/node run 创建、状态聚合、cancel/retry、queue claim 和 timeout recovery。
3. WP9-4.x：通过 WP6 应用服务接入 API_TEST dispatch，不直连 runner adapter。
