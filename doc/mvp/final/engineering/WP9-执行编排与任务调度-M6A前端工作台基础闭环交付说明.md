# WP9 执行编排与任务调度 - M6A 前端工作台基础闭环交付说明

| 项目 | 内容 |
|---|---|
| 交付日期 | 2026-06-14 |
| 交付范围 | `portal-web` 执行编排入口、API client、权限判断、计划/运行/触发主视图 |
| 涉及模块 | `portal-web/src/api/execution.ts`、`ExecutionWorkbench`、`App.tsx`、`permissions.ts`、WP9 文档 |
| 非目标 | 复杂多节点 DAG 编辑器、Playwright 390px smoke、导出文件下载、生产 cron scanner、供应商 webhook 插件样例 |
| 回滚方式 | 回退本次前端与文档 commit；后端 WP9 M5 API 不受影响 |

## 1. 需求结论

M6A 目标是在 M5 后端触发控制面基础上提供浏览器内可用的执行编排控制台基础闭环。用户具备 `execution:read` 后可看到 `#execution` 入口，具备 `execution:manage` 可创建单节点执行计划和触发配置，具备 `execution:trigger` 可手动触发、取消和重试运行。所有展示字段以摘要、digest、traceId、errorCode 为主，不展示 webhook secret、secretRef 明文、baseUrl 明文或请求响应正文。

## 2. 主要变更

1. 新增 `portal-web/src/api/execution.ts`，覆盖 health、plan、dryRun、run、cancel/retry、trigger、trigger event API，并兼容 camelCase/snake_case 响应。
2. 新增 `ExecutionWorkbench`，展示调度策略指标、计划列表、单节点计划创建、DAG 节点摘要、手动触发、运行详情、取消/重试、触发配置摘要和 trigger dryRun。
3. `App.tsx` 新增 `#execution` 导航和路由；`permissions.ts` 新增 `execution:*` 权限点与按钮判断。
4. 新增 `execution.test.ts` 和权限测试覆盖路径构造、normalization、入口权限和按钮权限。
5. 更新 WP9 研发拆解与前端页面设计文档，标明 M6A 已完成和后续边界。

## 3. 验收标准

| 验收项 | 结论 |
|---|---|
| `execution:read` 控制入口 | 通过 |
| API client camelCase/snake_case 兼容 | 通过 |
| 计划列表、空态、错误态和刷新 | 通过 |
| 单节点计划创建和 DAG dryRun | 通过 |
| 手动触发、运行详情、取消/重试 | 通过 |
| 触发配置摘要、启停、dryRun | 通过 |
| secret/baseUrl/raw payload 不展示 | 通过 |
| 前端测试和构建 | 通过 |

## 4. 风险与后续

1. 当前计划创建是单节点起步，不替代后续多节点 DAG 编辑器。
2. 当前导出权限只展示准入状态，文件下载交互后续补齐。
3. 本轮未新增 Playwright smoke；M6B/M7 需补桌面和 390px 视口端到端验证。
4. Cron scanner 仍由后端后续切片承接，前端只展示 cron metadata 与 readiness。
