# WP8 测试数据与账号池 - 发布准出说明

| 项目 | 内容 |
|---|---|
| 工作包 | WP8 测试数据与账号池 |
| 文档性质 | 发布准出、验证记录、风险、回滚和五角色结论 |
| 当前分支 | `codex/wp10-planning` |
| 远端 | `origin/codex/wp10-planning` |
| 日期 | 2026-06-26 |

## 1. 准出结论

WP8 当前承诺范围已经形成可验收闭环。`platform-api` 已提供数据集、账号池、账号摘要、租借、续租、释放、清理任务、跨 WP 引用契约、数据集/租借脱敏导出摘要、文件级下载、XXL-JOB 触发的受控 cleanup worker/HTTP adapter 和真实业务账号自动开通 HTTP adapter；`portal-web` 已提供 `#test-data` 工作台，覆盖数据集、账号池、租借、释放、清理任务、两类脱敏导出摘要、下载按钮和响应式 smoke。WP7 凭据注入、WP9 调度自动申请/释放和 WP10 报告证据消费均已消费 WP8 应用层契约，但不改变 WP8 自身准出边界。

当前准出口径已补齐 WP8 自身文件级下载、真实 cleanup worker/破坏性 adapter 的受控实现，以及真实业务账号自动开通的 HTTP adapter 实现。外部容量压测、多实例运维演练和更细粒度前端体验增强仍作为后续专项记录，不构成本轮发布阻断。

## 2. 范围和非目标

本次准出范围：

1. WP8 后端控制面：health、data set CRUD/record import/export/download、account pool CRUD/account manage/HTTP provisioning、lease API、lease export/download、cleanup task worker/adapter、cross-WP reference contract、权限、审计和 traceId。
2. WP8 前端工作台：入口权限、策略摘要、数据集、账号池、租借、清理任务、脱敏导出摘要、下载按钮、按钮显隐和移动端 smoke。
3. WP8 准出脚本：`platform_api` 定向测试、`npm test`、`npm run build`、Playwright smoke、quality gate、DB validation、Java 行数门禁和并发 smoke。
4. WP8 交付文档：PRD、技术设计、前端页面设计、前端操作说明、测试策略、研发拆解、正式启动准备、运维 Runbook、M8B/M8C 交付说明、本发布准出说明、剩余工作盘点和 M8I 发布准出收口交付说明。

非目标：

1. 不承诺 cleanup/provisioning adapter 对任意业务系统开箱即用；真实业务系统仍需配置 URL、token、allowlist、dry-run、幂等和回滚方案。
2. 不承诺外部 HTTP 并发压测容量、生产容量指标或多实例运维演练。
3. 不实现筛选栏、分页增强或详情抽屉等前端体验专项。
4. 不改变 WP7/WP9/WP10 已通过应用层契约消费 WP8 的边界。

## 3. 验证记录

当前文档收口已执行并通过的验证组合：

```bash
rg -n "WP8-8.4|M8I|发布准出说明|剩余工作盘点|当前 WP8 范围无剩余 P0 功能开发项" README.md doc/mvp/final/engineering/WP8-* doc/mvp/final/engineering/当前实现基线.md
git diff --check
cd portal-web && npm test -- api/testData.test.ts permissions.test.ts
cd portal-web && npm run build
```

结果：

1. 文档引用检查通过，README、WP8 主文档、前端操作说明、运维 Runbook、M8B/M8C 交付说明、本发布准出说明、剩余工作盘点和 M8I 交付说明已形成一致索引。
2. `git diff --check` 通过。
3. `portal-web` 定向 Vitest 通过，25 个测试全部通过。
4. `portal-web` build 通过；仅保留既有 Vite dynamic import/chunk size warning。

本轮涉及 Java、API、前端运行时、前端 smoke mock 和文档变更，准出必须执行 Java 行数门禁、后端测试、前端测试、构建和 WP8 相关脚本检查；无法执行项必须在最终交付说明中记录原因和替代验证。

## 4. 跳过项和原因

未执行真实业务系统破坏性清理演练。原因是本地验证仅覆盖配置型 adapter 契约和任务状态机；连接具体业务系统必须由目标环境提供 allowlist、dry-run、幂等和回滚工单。

未执行 WP8 自身的跨 WP 端到端集成回归。原因是 WP7/WP9/WP10 的真实消费已分别在对应工作包准出与 smoke 中覆盖；本轮 WP8 准出聚焦账号池、租借、清理和导出摘要自身边界。

## 5. 发布风险

| 风险 | 准出控制 | 处置 |
|---|---|---|
| secretRef 或账号健康摘要泄露 | 白名单响应、前端 DOM smoke、导出脱敏和 Runbook 边界 | 关闭 `export-enabled`，保留证据并轮换 secret |
| 账号被重复租借 | DB 唯一 active lease 约束和并发 smoke | 暂停租借入口，锁定冲突账号，人工释放 lease |
| 清理误删业务数据 | 默认 `cleanup-enabled=false`，adapter ready 才允许执行，Runbook 明确 allowlist/dry-run/回滚 | 关闭清理执行，保留任务记录和 adapter 日志 |
| 自动开通账号过量 | 默认 `account-provisioning-enabled=false`，HTTP adapter 才计入真实 ready，并受 `minAvailable/maxAccounts/batchSize` 限制 | 关闭账号自动开通，锁定或禁用异常账号 |
| 任务或租借状态不可解释 | traceId、错误码、状态解释和排障 Runbook | 按 holderRef/requestKey/leaseId/taskId 回溯 |
| 与 WP7/WP9 范围混淆 | 文档明确只提供引用和 lease，不执行浏览器或 DAG | 隐藏跨 WP 操作入口，保留只读引用 |

## 6. 回滚方式

1. 优先通过配置关闭 `veri-agent.test-data.enabled`、`cleanup-enabled`、`export-enabled` 或 `account-provisioning-enabled`，或隐藏前端入口。
2. 若只需回滚文档口径，回滚本组 WP8 文档和 README 索引即可。
3. 数据库迁移遵循前滚修复优先，生产环境不做破坏性 drop。
4. 已产生的租借、释放、清理和导出审计保留，不直接删除证据。
5. 若跨 WP 引用异常，WP7/WP9 回退到手工传入账号或数据引用，不影响 WP8 只读查询。

## 7. 五角色准出结论

| 角色 | 结论 | 说明 |
|---|---|---|
| 资深项目经理 | 通过 | 当前范围、非目标、风险、里程碑和回滚开关已经收口，后续容量/演练专项边界清楚。 |
| 资深产品经理 | 通过 | 用户可通过浏览器完成导出摘要和文件下载，清理与账号自动开通默认受控，产品验收边界清晰。 |
| 资深服务端架构师 | 通过 | 服务端安全边界、RBAC、project scope、secretRef 不回显、active lease 约束和 adapter 契约均已封闭。 |
| 资深前端工程师 | 通过 | 工作台补齐下载入口，操作说明与当前 UI 一致，未承诺未实现筛选/分页增强。 |
| 资深质量工程师 | 通过 | 准出需覆盖 Java 行数门禁、后端/前端测试、构建、脚本检查和下载 smoke。 |

## 8. 后续边界

1. 后续若新增筛选栏、分页、详情抽屉、外部容量压测或多实例运维演练，需要重新补充 PRD、技术设计、测试策略和发布准出说明。
2. 当前 WP8 已提供 HTTP 业务账号自动开通 adapter；真实业务系统接入仍需目标环境 URL、token、allowlist、幂等和回滚准出，`LOCAL_SECRET_REF` 不计入生产 ready。
3. 若变更会触及 Java、API、DB、adapter 或前端运行时代码，必须按仓库默认验证入口扩大验证范围。
