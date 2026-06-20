# WP7 Web 管理后台 UI/E2E - 测试策略与用例脚本

| 项目 | 内容 |
|---|---|
| 工作包 | WP7 Web 管理后台 UI/E2E |
| 角色产出 | 资深质量工程师 |
| 文档性质 | 测试策略、用例矩阵、脚本门禁和准出要求 |
| 当前口径 | 覆盖场景控制面、bundle 静态校验、受控 runner、WP8 凭据摘要契约、WP9 `UI_TEST` 交接、WP10 证据摘要、前端主链路、artifact 脱敏扫描和 WP7 quality gate |
| 版本 | v0.1 |
| 日期 | 2026-06-18 |

## 1. 测试目标

1. 验证 WP7 只消费和输出 aggregate-only 摘要，不暴露账号凭据和原始敏感产物。
2. 验证场景、bundle、运行和 Flaky 的状态机、权限、项目 scope、审计和 traceId 稳定。
3. 验证 WP8 `accountLeaseRef` / `secretRefDigest` 契约不被突破，凭据注入只发生在受控 runner 内部。
4. 验证运行失败分类、artifact manifest、导出和前端 DOM 都通过脱敏边界。
5. 验证受控 artifact 下载只暴露 opaque ref、受权文件名和内容类型，不暴露宿主机路径。
6. 验证前端工作台在桌面与 390px 视口的主链路可用。
7. 验证 WP9/WP10 仅能拿到脱敏执行摘要，不读取原始产物或源码正文。

## 2. 测试范围

| 模块 | 覆盖 |
|---|---|
| Health | 开关、runner 模式、allowlist、artifact policy、credential policy 和安全摘要。 |
| Scene | create/list/detail/update/archive、项目 scope、状态保护、来源摘要绑定。 |
| Bundle | create/detail/list、静态校验、submit-review/approve/reject、危险 import/硬编码凭据阻断。 |
| Run | create/detail/list/cancel/export、requestKey 幂等、runner disabled、lease invalid、allowlist、timeout。 |
| Artifact | screenshot/trace/runner log、`HAR`、`JUNIT_XML` 和受控 `VIDEO` manifest、digest、storageRef、size、redaction flags，以及受控下载端点。 |
| Flaky | 标记、更新、查询、审计和状态流。 |
| WP8 adapter | `runnerAccountContract` 摘要字段白名单和项目越权拦截。 |
| WP9 contract | `UI_TEST` 节点摘要输出、错误码和未就绪语义。 |
| WP10 evidence | run summary、artifact manifest、failure bucket、Flaky 标记输出。 |
| Frontend | 权限入口、场景编辑、bundle 评审、运行详情、Flaky 标记、loading/empty/error/409。 |
| Security | 禁止字段扫描、导出阻断、审计 payload 脱敏、DOM 脱敏。 |
| DB | 表、约束、索引、权限 seed、审计事件和 validation。 |

## 3. P0 用例矩阵

| 编号 | 优先级 | 场景 | 期望 |
|---|---|---|---|
| WP7-HEALTH-001 | P0 | 查询 health | 返回开关、runner、allowlist、artifact policy、credential policy，不泄露 secret。 |
| WP7-SCENE-001 | P0 | 创建场景 | 成功保存 `DRAFT` 场景，写 `ui_e2e.scene.created` 审计。 |
| WP7-SCENE-002 | P0 | 非同项目来源摘要绑定 | 返回 `UI_E2E_RESOURCE_SCOPE_DENIED`。 |
| WP7-SCENE-003 | P0 | 归档场景后再次编辑 | 返回状态冲突。 |
| WP7-BUNDLE-001 | P0 | 生成 bundle 摘要 | 返回 digest、fixture 摘要和静态校验结果。 |
| WP7-BUNDLE-002 | P0 | bundle 含危险 import 或硬编码凭据 | 进入 `STATIC_CHECK_FAILED` 或返回 `UI_E2E_STATIC_CHECK_FAILED`。 |
| WP7-BUNDLE-003 | P0 | 未审批 bundle 触发运行 | 返回 `UI_E2E_BUNDLE_NOT_READY`。 |
| WP7-RUN-001 | P0 | runner disabled 时创建运行 | 返回 `UI_E2E_RUNNER_DISABLED` 或 `EXECUTION_RUNNER_NOT_READY`。 |
| WP7-RUN-002 | P0 | `APPROVED` 场景 + 合法 `accountLeaseRef` 创建运行 | 进入 `QUEUED/RUNNING`，返回脱敏账号摘要。 |
| WP7-RUN-003 | P0 | `accountLeaseRef` 越权或无效 | 返回 `UI_E2E_ACCOUNT_LEASE_INVALID`。 |
| WP7-RUN-004 | P0 | baseUrl 不在 allowlist | 返回 `UI_E2E_BASE_URL_NOT_ALLOWED`。 |
| WP7-RUN-005 | P0 | 重复 `sceneId + requestKey` | 幂等回放既有运行。 |
| WP7-RUN-006 | P0 | 运行超时 | 进入 `TIMEOUT`，失败摘要脱敏。 |
| WP7-RUN-007 | P0 | 运行取消 | `RUNNING -> CANCELED`，runner 接到取消信号或返回稳定 cancel not supported 语义。 |
| WP7-ART-001 | P0 | 采集 screenshot/trace 摘要 | 返回 digest、size、storageRef、redaction flags。 |
| WP7-ART-002 | P0 | artifact 摘要命中敏感字段 | 返回 `UI_E2E_ARTIFACT_POLICY_BLOCKED`，导出阻断。 |
| WP7-ART-003 | P0 | 下载已落入受控存储的 artifact | 返回 200、正确 content type 和 attachment 文件名，不暴露真实路径。 |
| WP7-ART-004 | P0 | 下载不存在或未就绪 artifact | 返回 `UI_E2E_ARTIFACT_DOWNLOAD_NOT_READY` 或 404，不暴露文件系统错误。 |
| WP7-CLASSIFY-001 | P0 | locator 失效 | failure bucket 输出 locator/selector 类。 |
| WP7-CLASSIFY-002 | P0 | 权限拒绝页面 | failure bucket 输出 permission 类。 |
| WP7-CLASSIFY-003 | P0 | 账号租借异常 | failure bucket 输出 account/data 类，不泄露 token。 |
| WP7-FLAKY-001 | P0 | 标记 `FLAKY_CANDIDATE` | 成功写入并可查询。 |
| WP7-FLAKY-002 | P0 | 无 `uiE2e:flaky` 权限标记 | 403。 |
| WP7-WP8-001 | P0 | WP8 runner contract 返回摘要 | 只含 `accountLeaseRef`、账号摘要、`secretRefDigest` 和策略布尔值。 |
| WP7-WP9-001 | P0 | 向 WP9 输出 run 摘要 | 只输出 aggregate-only 字段，不输出原始 artifact。 |
| WP7-WP10-001 | P0 | 向 WP10 输出 evidence 摘要 | 包含 failure bucket、artifact refs、Flaky 标记和步骤级摘要。 |
| WP7-PERM-001 | P0 | 无 `uiE2e:read` 查详情 | 403，前端直达展示无权限态。 |
| WP7-PERM-002 | P0 | 无 `uiE2e:execute` 触发运行 | 403，前端隐藏按钮。 |

## 4. 安全测试

1. API 响应、日志、审计、导出、artifact 下载响应头和前端 DOM 不包含密码、token、cookie、Authorization、`secret://` 原文、租借 token 明文或宿主机路径。
2. screenshot/trace/runner log、`HAR`、`JUNIT_XML` 和受控 `VIDEO` manifest 摘要命中禁止字段时必须阻断，而不是回显敏感命中值。
3. `scopeSummary`、`sourceSummary`、`failureSummary` 和 `resultSummary` 只允许白名单 key。
4. baseUrl allowlist 必须阻断未批准域名或环境。
5. runner 关闭时不能通过任何旁路实际执行浏览器脚本。
6. bundle 静态校验必须阻断危险 import、无限等待和硬编码凭据。

## 5. 前端测试

| 用例 | 期望 |
|---|---|
| 无权限直达 `#ui-e2e` | 展示无权限态，不请求业务数据。 |
| 场景列表空态 | 展示空态和新建入口。 |
| 创建场景缺少步骤 | 本地校验阻断。 |
| bundle 驳回无原因 | 本地校验阻断。 |
| runner disabled | 运行按钮禁用并展示解释。 |
| 运行详情失败 | 展示错误码、traceId 和失败分类。 |
| Flaky 标记成功 | 列表和详情同步更新。 |
| 390px 窄屏 | 无横向溢出，digest/traceId 自动换行。 |
| DOM 脱敏扫描 | 页面文本不包含禁止字段样本。 |

## 6. 建议脚本入口

代码阶段新增：

```bash
bash scripts/wp7_browser_smoke.sh
bash scripts/wp7_runner_smoke.sh
bash scripts/wp7_artifact_redaction_eval.sh
bash scripts/wp7_quality_gate.sh
```

`wp7_quality_gate.sh` 应串联：

1. 脚本语法检查。
2. `bash scripts/platform_api_java_line_guard.sh`。
3. 后端 scene/bundle/run/artifact/flaky/permission/audit 测试。
4. `mvn -B -pl platform-api test` 或定向 WP7 测试。
5. 前端 WP7 Vitest。
6. `cd portal-web && npm run build`。
7. `bash db/validation/run_wp1_db_validation.sh`。
8. `wp7_browser_smoke.sh`，开发模式可 mock，release 模式应走 managed。
9. `wp7_runner_smoke.sh`，覆盖 pass/fail/timeout/cancel/lease invalid。
10. `wp7_artifact_redaction_eval.sh`，扫描 server/export/frontend DOM 禁止字段。

## 7. DB Validation

WP7 涉及数据库、权限、审计、账号摘要契约和前端体验，必须覆盖：

1. `ui_e2e_scene`、`ui_e2e_scene_step`、`ui_e2e_bundle`、`ui_e2e_bundle_review`、`ui_e2e_run`、`ui_e2e_run_step_result`、`ui_e2e_artifact_manifest`、`ui_e2e_flaky_mark` 表存在。
2. 唯一约束、状态 check、索引和时间字段存在。
3. `uiE2e:read/manage/review/execute/export/flaky` 权限 seed 存在。
4. 审计事件字典包含 WP7 事件。
5. schema 不引入 `tenant_id` 回归，不破坏当前单平台模型。

## 8. 准出标准

1. 场景、bundle、运行、Flaky 主链路可以在前后端完成。
2. 权限、项目 scope、审计和 traceId 覆盖主链路。
3. WP8/WP9/WP10 契约均不读取敏感原文。
4. artifact 摘要、导出和 DOM 脱敏扫描命中必须为 0。
5. 后端、前端、构建、DB validation、browser smoke、runner smoke、artifact eval 和 WP7 quality gate 按影响面通过。

## 9. 当前质量结论

截至 2026-06-20，WP7 已进入运行时代码实现和质量门禁阶段。本轮最小必要验证包括：

1. 后端单测和 OpenAPI 契约测试。
2. 前端 Vitest。
3. Playwright 浏览器 smoke。
4. 受控 runner smoke。
5. artifact 脱敏专项评测。
6. DB validation 和 quality gate。
