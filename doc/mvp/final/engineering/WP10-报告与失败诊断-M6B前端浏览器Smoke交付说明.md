# WP10 报告与失败诊断 - M6B 前端浏览器 Smoke 交付说明

| 项目 | 内容 |
|---|---|
| 里程碑 | M6B 前端浏览器 Smoke |
| 当前口径 | 新增 `#reports` 工作台 Playwright smoke、DOM 禁止字段扫描、390px 响应式检查和 WP10 quality gate 接入 |
| 日期 | 2026-06-17 |

## 1. 目标和范围

M6B 目标是把 M6A 的浏览器主链路从人工 sanity check 升级为可重复执行的 Playwright smoke。测试使用 mock API 固定 WP10 前后端契约，不启动真实后端、不写外部系统，覆盖桌面和 390px 两个视口。

本轮范围：

1. 新增 `portal-web/e2e/wp10-reports.smoke.playwright.ts`。
2. 新增 `portal-web` npm script：`npm run test:wp10-smoke`。
3. 新增 `scripts/wp10_frontend_e2e_smoke.sh`，沿用 WP4/WP6/WP8/WP9 的系统 Chrome 探测和可选浏览器安装约定。
4. `scripts/wp10_quality_gate.sh` 纳入 WP10 Playwright smoke，并支持 `WP10_SKIP_FRONTEND_E2E=1` 显式跳过。
5. 更新 WP10 研发拆解、前端页面设计、测试策略和 README 索引。

## 2. 非目标范围

1. 不启动真实 `platform-api` 或真实 PostgreSQL 做浏览器 E2E。
2. 不新增后端 API、DB migration 或 Java 生产逻辑。
3. 本切片交付时不实现 WP3/WP5 evidence adapter；按当前基线，该能力已由后续 M9 里程碑承接交付。
4. 本切片交付时不实现外部缺陷系统写入、PDF/Word 报告、趋势报表或真实敏感内容专项拦截评测；按当前基线，PDF/Word 报告已由后续 M12 里程碑承接交付。

## 3. 覆盖项

| 覆盖项 | 结论 |
|---|---|
| `#reports` 鉴权入口和 `report:*` 权限 | 已覆盖 |
| 报告列表、详情和 evidence manifest 展示 | 已覆盖 |
| 报告生成表单、本地 UUID 校验后的 payload | 已覆盖 |
| 失败诊断触发、AI_READY 展示和人工确认提示 | 已覆盖 |
| 缺陷草稿生成、REVIEWED 审阅和 masked payload preview | 已覆盖 |
| JSON/Markdown 导出摘要 manifest、digest 和 policy 展示 | 已覆盖 |
| DOM 禁止字段样本扫描 | 已覆盖 |
| 390px 视口无横向溢出 | 已覆盖 |

## 4. 风险和回滚

| 风险 | 缓解 |
|---|---|
| mock API 与真实后端契约漂移 | smoke 只作为浏览器主链路补充；后端 OpenAPI/controller/smoke 仍在 WP10 quality gate 中执行。 |
| DOM 扫描误杀策略字段 | smoke 扫描真实禁止样本，如 `secret://`、`Authorization`、`lease token`、`raw prompt`、`runner stdout`，不误杀 `rawPromptStored=false` 等策略键。 |
| 浏览器依赖缺失 | 脚本优先使用系统 Chrome；必要时可用 `WP10_FRONTEND_INSTALL_BROWSERS=1` 安装 Chromium。 |

回滚方式：回退本次 M6B commit；M1-M6A 后端和前端基础功能不依赖该 smoke 脚本。若 CI 临时缺浏览器，可使用 `WP10_SKIP_FRONTEND_E2E=1` 显式跳过，并在准出说明记录风险。

## 5. 验收入口和结果

```bash
cd portal-web && npm run test:wp10-smoke
bash scripts/wp10_frontend_e2e_smoke.sh
bash scripts/wp10_quality_gate.sh
```

本地无托管 Chromium 时可执行：

```bash
WP10_FRONTEND_INSTALL_BROWSERS=1 bash scripts/wp10_frontend_e2e_smoke.sh
```

本轮实际验证结果：

| 命令 | 结果 |
|---|---|
| `bash scripts/wp10_frontend_e2e_smoke.sh` | 通过，Playwright Chromium/system Chrome，2 tests passed，覆盖 desktop 和 mobile。 |
| `cd portal-web && npm test -- --run src/api/reports.test.ts src/permissions.test.ts` | 通过，2 files / 24 tests passed。 |
| `cd portal-web && npm test` | 通过，27 files / 199 tests passed。 |
| `cd portal-web && npm run build` | 通过，存在既有 Vite chunk size 和 dynamic import/static import 警告。 |
| `bash -n scripts/wp10_frontend_e2e_smoke.sh scripts/wp10_quality_gate.sh && git diff --check` | 通过。 |
| `bash scripts/platform_api_java_line_guard.sh` | 通过，Platform API 生产 Java 文件均不超过 1200 行。 |
| `bash scripts/wp10_quality_gate.sh` | 通过，包含脚本语法、Java line guard、WP10 后端 smoke、后端/OpenAPI 定向测试、前端 Vitest、Playwright smoke、前端 build、合并 DB validation。 |
| `mvn -B -pl platform-api test` | 通过，652 tests，0 failures，0 errors，0 skipped；收尾阶段有 surefire fork JVM 退出警告但 Maven `BUILD SUCCESS`。 |

补充说明：直接执行 `cd portal-web && npm run test:wp10-smoke` 在本机缺少 Playwright 托管 Chromium 缓存时会失败，官方入口 `scripts/wp10_frontend_e2e_smoke.sh` 已通过 `PW_CHROME_CHANNEL=chrome` 使用系统 Chrome 通过。CI 若无系统 Chrome，可设置 `WP10_FRONTEND_INSTALL_BROWSERS=1` 安装 Chromium。

本轮未修改 Java 生产代码，不涉及新增核心 Java 业务逻辑或核心方法注释；《阿里巴巴 Java 开发手册》相关人工自查结论为无新增 Java 规范风险。

## 6. 五角色结论

| 角色 | 结论 | 说明 |
|---|---|---|
| 资深项目经理 | 通过 | M6B 范围限定在前端 smoke 和门禁接入，回滚清晰。 |
| 资深产品经理 | 通过 | 浏览器主链路覆盖报告、诊断、草稿和导出摘要，符合用户无需 curl 的验收口径。 |
| 资深服务端架构师 | 无影响 | 本轮不改 Java、DB 或服务端接口；mock 对齐 M1-M5B 既有契约。 |
| 资深前端工程师 | 通过 | 已覆盖桌面/390px、权限入口、表单、状态、长 digest 换行和 DOM 安全扫描。 |
| 资深质量工程师 | 通过 | Playwright smoke 已接入 WP10 quality gate，后续真实敏感拦截专项仍按 M7/M8 推进。 |
