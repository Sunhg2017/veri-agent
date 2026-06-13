# WP9 执行编排与任务调度 - M7C 执行摘要导出交付说明

| 项 | 内容 |
|---|---|
| 交付阶段 | M7C 执行摘要导出 |
| 交付日期 | 2026-06-14 |
| 交付范围 | 脱敏 run export API、导出审计、OpenAPI contract、前端 API helper、运行详情导出入口、测试矩阵更新 |
| 非目标 | 文件下载落盘、WP10 完整报告生成、runner 原始产物导出、供应商 webhook 插件样例、外部 webhook HTTP smoke |
| 涉及模块 | `platform-api` WP9 execution controller/application/view/test、`portal-web` execution API/workbench/test、WP9 文档 |
| 回滚方式 | 回退本次 M7C commit；既有 run 详情、取消、重试、CRON scanner 和 scheduler gate 不受影响 |

## 1. 目标与范围

M7C 目标是补齐 WP9 P0 “执行摘要导出”闭环。`GET /api/v1/execution/runs/{id}/export` 按 run 所属项目 scope 校验 `execution:export`，返回 schema 版本、导出时间、已脱敏 run detail、节点状态计数和 redaction policy，并写 `execution.run.exported` 审计。

## 2. 主要变更

1. 新增 `ExecutionRunExportResponse`，导出格式为 `wp9-run-export-v1`。
2. `ExecutionRunService#exportRun` 复用既有 run detail 脱敏视图，不读取或拼接 runner 原始产物。
3. 导出策略显式声明 `rawOutputExported=false`、`rawRequestResponseExported=false`、`rawBaseUrlExported=false`、`secretRefsExported=false`、`claimTokenExported=false`、`triggerPayloadExported=false`。
4. `ExecutionRunController` 新增 `/runs/{id}/export` endpoint，并纳入 OpenAPI contract。
5. `portal-web` 新增 `exportExecutionRun` helper、`normalizeExecutionRunExport` 和运行详情“导出摘要”按钮，页面展示 schema、导出时间、节点状态计数和 secret 禁出策略。

## 3. 验收入口

```bash
mvn -B -pl platform-api -Dtest=ExecutionRunControllerTest,OpenApiContractTest test
cd portal-web && npm test -- --run src/api/execution.test.ts
cd portal-web && npm run build
```

## 4. 风险与后续

1. 本切片返回 JSON 摘要，不做浏览器文件下载或 CSV/PDF 生成。
2. 导出复用 WP9 已脱敏的 run detail；若未来新增 runner 原始 artifact 存储，必须重新审查 redaction policy。
3. 供应商 webhook 插件样例和外部 webhook HTTP smoke 仍归后续 M7/M8 切片。
