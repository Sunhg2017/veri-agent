# WP1 当前可持续研发底座交付说明

| 项目 | 内容 |
|---|---|
| 工作包 | WP1 平台基础底座 |
| 当前口径 | 单平台、多部门、多项目、多应用、多环境 |
| 基础包路径 | `com.songhg.veri.agent` |
| 更新时间 | 2026-05-21 |
| 交付结论 | 可作为后续 WP 持续研发底座 |

## 1. 当前交付范围

WP1 当前已从早期平台实例分层设计调整为单平台控制面，不建设实例管理、实例管理员、实例级配置和跨实例隔离。后续研发以部门、项目、应用、环境、角色和资源作用域作为权限与数据边界。

当前已落地能力：

1. `platform-api` 后端样板：初始化、登录、刷新令牌、注销、当前用户、管理视图、部门详情/编辑/启停、用户详情/资料编辑、项目/应用/环境正式创建、详情、编辑和状态流 DTO，环境 webUrl/apiBaseUrl 连通性检查和最近健康结果，设置分页 CRUD/详情/编辑/启停，Secret 引用摘要/创建/轮换/撤销，项目成员、应用负责人、环境授权用户和资源级角色绑定，账号启停、锁定/解锁、重置密码、审计查询、审计导出、Audit outbox 只读运维查询、内部 context/audit 契约。
2. `portal-web` 前端样板：React + TypeScript + Vite 管理后台，支持登录、概览、组织、用户、项目、应用、环境、集成、审计和设置视图；部门、用户、项目、应用、环境和设置页已接入详情回读、基础字段编辑、状态流转、项目成员、应用负责人、环境授权用户、环境连通性和 Secret 引用操作面板；审计页已接入导出入口和 Audit outbox 只读运维侧栏。
3. PostgreSQL 迁移：WP1 schema、seed、运行期数据库权限模板、账号生命周期、审计不可变触发器、审计归档表和受控保留清理函数。
4. 权限基线：预置 8 个平台角色，后端 API 权限点校验，db profile 资源作用域过滤，前端菜单/按钮权限规则测试。
5. 契约基线：OpenAPI 生成与契约测试，覆盖认证、管理、账号生命周期和设置 CRUD，并防止重新引入旧实例边界 API、隔离字段和实例管理员角色。
6. 数据库准出：空库迁移、重复迁移、schema/seed/security validation。
7. 质量门禁：后端测试、前端测试、前端构建、数据库验证统一入口。
8. 权限闭包：`SuperAdmin` 和 `PlatformAdmin` 在 db/local profile 中补齐部门、用户、项目、应用、环境和 Secret 引用读取/创建/轮换/撤销权限，`ProjectOwner`/`AppOwner` 保留 `secret:reference` 引用能力。

## 2. 当前准出命令

WP1 本地总门禁：

```bash
bash scripts/wp1_quality_gate.sh
```

单项验证：

```bash
mvn -pl platform-api test
cd portal-web && npm run test
cd portal-web && npm run build
bash db/validation/run_wp1_db_validation.sh
WP1_BOOTSTRAP_TOKEN=local-init-token bash scripts/wp1_db_profile_smoke.sh
```

当前验证结论：

| 项目 | 结果 |
|---|---|
| `mvn -B -pl platform-api test` | 161 tests passed |
| `cd portal-web && npm run test` | 7 files / 63 tests passed |
| `cd portal-web && npm run build` | passed |
| `bash db/validation/run_wp1_db_validation.sh` | passed，0 WARN / 0 FAIL，覆盖审计保留配置、归档表、受控清理函数、Audit outbox traceId 索引和 app/readonly/migration role 权限边界 |
| `bash scripts/wp1_db_profile_smoke.sh` | passed，覆盖部门详情/编辑/启停，用户详情/资料编辑，项目/应用/环境正式创建、详情、编辑、状态流 DTO，集成配置登记/详情/编辑/启停，设置 CRUD/敏感设置拒绝，项目成员、应用负责人、环境授权用户增删查和资源级角色绑定，资源作用域过滤，管理对象、审计筛选、失败登录审计、会话轮换、账号锁定/解锁和账号生命周期 |
| `bash scripts/wp1_quality_gate.sh` | passed；默认执行 single-platform guard、后端测试、前端测试、前端构建和 WP1 数据库验证，db profile HTTP smoke 需显式设置 `WP1_RUN_DB_SMOKE=1` |

数据库安全校验已在临时库内创建 `wp1_app`、`wp1_readonly`、`wp1_migration` 三个测试角色并套用运行期授权策略。`run_wp1_db_validation.sh` 会额外执行 `wp1_release_role_validation.sql`，在本地临时库验证 app/readonly/migration 三类角色的发布权限边界。`security.audit_log_app_role_append_only` 已可给出确定的 `PASS/FAIL`，当前结果为 `PASS`。`run_wp1_db_validation.sh` 默认发现 `WARN` 也会失败，只有显式设置 `WP1_ALLOW_DB_VALIDATION_WARN=1` 才临时放行。预发或生产环境如果使用不同数据库角色，需要通过 `WP1_RELEASE_APP_ROLE`、`WP1_RELEASE_READONLY_ROLE`、`WP1_RELEASE_MIGRATION_ROLE` 注入真实角色名复用同一检查。审计表本身已具备触发器级 `UPDATE/DELETE` 阻断。

预发/生产真实 app/readonly/migration 数据库角色检查已收敛到 `doc/mvp/final/engineering/WP1-发布前DB权限Runbook.md`。发布流水线建议分层执行：CI 每次跑临时库 `run_wp1_db_validation.sh`，预发/生产发布窗口在迁移后、切流前执行 `scripts/wp1_release_role_validation.sh`，并归档 DBA 复核结果。

环境连通性检查配置：

| 配置 | 默认值 | 说明 |
|---|---|---|
| `WP1_ENV_CONNECTIVITY_CHECK_ENABLED` | `true` | 关闭后仍可记录一次 `SKIPPED` 结果，不发起外部网络探活 |
| `WP1_ENV_CONNECTIVITY_TIMEOUT_MS` | `3000` | 单个 web/API endpoint 的连接与响应超时，代码内限制在 200～30000ms |

`POST /api/v1/management/environments/{key}/connectivity-check` 需要 `environment:edit`，停用环境返回 `INVALID_STATE`；探活失败仅返回 `DOWN`、脱敏消息、可用 HTTP 状态和 traceId，不回显底层异常、堆栈或内部网络细节。最近结果保存在 `base_environment.health_check_json`，`GET` 接口和 portal-web 环境侧栏可读取。

Secret 引用管理口径：

| 能力 | 入口 | 权限 | 安全口径 |
|---|---|---|---|
| 摘要读取 | `GET /api/v1/management/secrets` | `secret:read` | 仅返回 `secretRef/provider/purpose/scope/maskedValue/version/status/time` |
| 创建本地加密引用 | `POST /api/v1/management/secrets` | `secret:manage` | 明文只在请求内使用，db profile 写入 `secret_local_store` AES-256-GCM 密文材料 |
| 轮换本地加密引用 | `POST /api/v1/management/secrets/rotate` | `secret:rotate` | 覆盖本地密文材料，更新版本、掩码和 `rotated_at` |
| 撤销引用 | `POST /api/v1/management/secrets/disable` | `secret:disable` | `secret_reference` 置 `REVOKED`，本地密文置 `REVOKED` |

Secret 响应、列表、前端状态和审计只记录引用、版本、用途、作用域和掩码，不回显 `secretValue`、密文、IV、authTag 或 master key version。当前仅对默认 `LOCAL_ENCRYPTED` provider 提供写入和轮换；Vault/KMS 真实写入仍作为后续 provider 适配。

## 3. 后续研发入口

本轮 1～8 项已完成，后续 WP1 研发可直接在当前底座上推进，推荐优先级如下：

1. 按 `WP1-发布前DB权限Runbook.md` 将预发/生产真实 app/readonly/migration 数据库角色接入发布流水线，确保不是仅临时库角色通过。
2. 补角色定义管理；会话清理已具备 local/db profile 定时清理、保留窗口配置和 `veri.agent.auth.session.cleanup` 指标，审计保留已具备 `db` profile 受控清理、归档表、保留窗口配置和 `veri.agent.audit.retention.cleanup` 指标，Audit outbox 已具备只读运维视图和 traceId 查询索引，环境连通性检查已具备可配置探活和最近结果，Secret 引用已具备本地加密创建/轮换/撤销闭环。
3. 审计日志已支持 `GET /api/v1/management/audit-logs/export` 同步 CSV 导出，要求 `audit:read` + `audit:export`，portal-web 审计页已接入导出按钮和下载状态；后续如需要大批量导出，再演进异步任务和对象存储引用。
4. 审计保留策略以 `doc/mvp/final/engineering/WP1-审计保留策略Runbook.md` 为准：在线表默认保留 365 天，清理默认关闭，开启后先归档到 `audit_log_archive` 再从 `audit_log` 删除；app role 仍不得直接删除审计日志。
5. 复杂状态流拒绝测试已覆盖项目重复、逆向、非法流转和停用后编辑拒绝，以及应用/环境非法状态与停用后编辑拒绝；后续继续扩展生产角色权限的自动化场景。

## 4. 当前文档口径

当前以以下文档和实现为准：

1. `doc/mvp/final/engineering/当前实现基线.md`
2. `doc/mvp/final/WP1-单平台P0交付版-PRD与架构补充.md`
3. `doc/mvp/final/engineering/WP1-单平台化调整说明.md`
4. `doc/mvp/final/engineering/WP1-单平台权限矩阵与菜单矩阵.md`
5. `doc/mvp/final/engineering/WP1-审计事件字典.md`
6. `doc/mvp/final/engineering/WP1-当前可持续研发底座交付说明.md`
7. `doc/mvp/final/engineering/WP1-审计保留策略Runbook.md`
8. 当前代码、迁移脚本、validation 脚本和自动化测试

早期 PRD、架构、测试评估和拆解文档中的多租户、平台实例分层、实例管理员、实例隔离表、业务实例隔离字段、跨实例隔离、独立 WP 服务、HTTP 回调本服务、snake_case API 字段等描述仅作为历史演进记录，不再作为当前准出和研发依据。
