# WP9 执行编排与任务调度 - M6C 前端浏览器 Smoke 交付说明

| 项 | 内容 |
|---|---|
| 交付阶段 | M6C 前端浏览器 Smoke |
| 交付日期 | 2026-06-14 |
| 交付范围 | WP9 execution 工作台 Playwright smoke、npm script、shell 验收入口、文档同步 |
| 非目标 | 后端导出接口、生产 cron scanner、供应商 webhook 插件样例、聚合 WP9 quality gate |
| 涉及模块 | `portal-web/e2e`、`portal-web/package.json`、`scripts`、WP9 文档 |
| 回滚方式 | 回退本次前端 smoke、脚本和文档 commit；既有 WP9 后端与工作台功能不受影响 |

## 1. 目标与范围

M6C 目标是把 WP9 前端工作台主链路纳入可重复执行的浏览器级 smoke。测试使用 mock API 固定后端契约，覆盖真实 React 页面、hash 路由、权限入口、表单交互、运行操作、触发器操作和 390px 响应式布局。

## 2. 主要变更

1. 新增 `portal-web/e2e/wp9-execution.smoke.playwright.ts`，覆盖桌面和 390px 两个视口。
2. 新增 `npm run test:wp9-smoke`，可单独运行 WP9 前端 smoke。
3. 新增 `scripts/wp9_frontend_e2e_smoke.sh`，沿用 WP4/WP6 的系统 Chrome 探测和可选浏览器安装约定。
4. 更新 WP9 研发拆解、前端页面设计和测试策略文档，标明 M6C 已完成和 M7 剩余边界。

## 3. 覆盖项

| 覆盖项 | 结论 |
|---|---|
| `#execution` 鉴权入口和 execution 权限 | 通过 |
| 调度 health 与 policy 展示 | 通过 |
| 多节点计划创建 payload | 通过 |
| 计划更新 payload | 通过 |
| DAG dryRun 结果展示 | 通过 |
| 手动运行 requestKey/reason | 通过 |
| 运行取消和失败运行重试 | 通过 |
| 触发器创建、dryRun、事件查看和启停 | 通过 |
| 触发器列表不展示 secretRef 明文 | 通过 |
| 390px 视口无横向溢出 | 通过 |

## 4. 验收入口

```bash
cd portal-web && npm run test:wp9-smoke
bash scripts/wp9_frontend_e2e_smoke.sh
```

本地无托管 Chromium 时可执行：

```bash
WP9_FRONTEND_INSTALL_BROWSERS=1 bash scripts/wp9_frontend_e2e_smoke.sh
```

## 5. 风险与后续

1. Smoke 使用 mock API 固定前端契约，不替代后端 service/controller/DB validation。
2. 后端执行摘要导出接口尚未落地，前端仍不展示导出按钮。
3. M7 仍需补 `scripts/wp9_quality_gate.sh`，聚合后端、前端、build、DB validation、Playwright smoke 和 managed scheduler smoke。
4. 生产 cron scanner 和供应商 webhook 插件样例仍按后续切片推进。
