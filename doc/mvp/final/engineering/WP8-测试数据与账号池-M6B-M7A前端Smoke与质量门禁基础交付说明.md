# WP8 测试数据与账号池 - M6B/M7A 前端 Smoke 与质量门禁基础交付说明

| 项目 | 内容 |
|---|---|
| 工作包 | WP8 测试数据与账号池 |
| 里程碑 | M6B 前端浏览器 smoke、M7A quality gate 基础 |
| 日期 | 2026-06-15 |
| 当前口径 | 补齐 WP8 前端桌面/390px smoke、DOM secretRef 原文扫描、账号租借 managed smoke 和聚合质量门禁脚本；不实现脱敏导出面板或真实 cleanup worker |

## 1. 目标、范围和非目标

目标：

1. 为 `#test-data` 工作台新增可重复的 Playwright smoke，覆盖桌面和 390px 主链路。
2. 在浏览器层验证输入的 `secretRef` 原文不会出现在 DOM、toast 或错误区域。
3. 新增 WP8 前端 smoke、账号租借 managed smoke 和聚合 quality gate 脚本入口。
4. 修复新建账号池后沿用旧账号选择态的问题，确保新池新增账号走创建路径而不是误更新旧账号。

范围：

1. `portal-web/e2e/wp8-test-data.smoke.playwright.ts`：mock `/api/v1/test-data` 契约，覆盖数据集、账号池、租借和清理任务主链路。
2. `portal-web/package.json`：新增 `test:wp8-smoke`。
3. `scripts/wp8_frontend_e2e_smoke.sh`：WP8 Playwright smoke 入口。
4. `scripts/wp8_account_lease_concurrency_smoke.sh`：本地 managed 账号租借并发 smoke。
5. `scripts/wp8_quality_gate.sh`：聚合脚本语法、Java 行数、后端定向测试、DB repository contract、前端测试、Playwright smoke、build 和 DB validation。
6. `portal-web/src/components/TestDataWorkbench.tsx`：新建账号池后清空 `selectedAccountId`。

非目标：

1. 不实现脱敏导出面板和导出文件下载。
2. 不启动真实 HTTP 后端或生产数据库做外部并发压测。
3. 不启用真实 cleanup worker 或破坏性清理 adapter。
4. 不修改 Java 生产代码或数据库 schema。

## 2. 主要变更

1. 前端 smoke 覆盖创建数据集、导入记录摘要、创建账号池、写入账号 secretRef、申请/续租/释放租借、创建/重试清理任务。
2. Smoke 在桌面和 390px 下扫描页面文本，断言不包含输入的 `secret://wp8/ui-smoke-admin` 和敏感测试值。
3. Smoke 在 390px 下校验 `test-data-workbench` 无页面横向溢出。
4. `wp8_quality_gate.sh` 支持 development/release 模式、plan-only、前端 E2E/DB validation 显式跳过开关，并在 release 模式要求 `WP8_LEASE_CONCURRENCY_SMOKE=managed`。

## 3. 验收标准

1. `bash scripts/wp8_frontend_e2e_smoke.sh` 通过，且同时运行 desktop 和 mobile 两个 Playwright 用例。
2. `bash scripts/wp8_account_lease_concurrency_smoke.sh` 通过，覆盖第二个 active lease 冲突和 DB active lease 唯一约束。
3. `WP8_QUALITY_GATE_PLAN_ONLY=1 bash scripts/wp8_quality_gate.sh` 能列出完整门禁计划。
4. 前端定向测试和 build 通过。
5. 不声明脱敏导出面板、真实 cleanup worker 或外部 HTTP 并发压测已完成。

## 4. 五角色结论

| 角色 | 结论 | 说明 |
|---|---|---|
| 资深项目经理 | 通过 | M6B/M7A 范围限定在 smoke、门禁和一个前端状态修复，回滚可按脚本和前端组件单点回退。 |
| 资深产品经理 | 通过 | 浏览器主链路覆盖测试工程师核心操作，secretRef 红线具备 DOM 级证据；脱敏导出仍是后续范围。 |
| 资深服务端架构师 | 通过 | Playwright mock 对齐 `/api/v1/test-data` 已有契约，managed 并发 smoke 复用后端和 DB 约束测试，不误声明新增后端能力。 |
| 资深前端工程师 | 通过 | 修复新建账号池后误沿用旧账号选择态；smoke 覆盖桌面/390px、权限入口、表单、状态和脱敏展示。 |
| 资深质量工程师 | 通过 | 已提供前端 smoke、并发 managed smoke 和 quality gate 基础入口；release gate 显式要求并发 smoke。 |

## 5. 未完成项

1. 脱敏导出面板、导出文件下载和导出结果 redaction policy 展示仍未实现。
2. 真实 HTTP 服务级并发压测未实现；当前并发 smoke 是本地 managed 测试入口。
3. cleanup worker 和破坏性清理 adapter 未启用。
