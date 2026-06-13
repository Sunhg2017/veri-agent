# WP9 执行编排与任务调度 - M6B 前端 DAG 编辑与计划操作交付说明

| 项 | 内容 |
|---|---|
| 交付阶段 | M6B 前端 DAG 编辑与计划操作 |
| 交付日期 | 2026-06-14 |
| 交付范围 | `portal-web` 多节点 DAG 草稿、计划更新/归档、触发事件切换查看、helper 测试 |
| 非目标 | 后端导出接口、图形化 DAG 画布、Playwright 390px smoke、生产 cron scanner |
| 涉及模块 | `ExecutionWorkbench`、`executionDagEditor.ts`、`execution.ts` 测试、WP9 文档 |
| 回滚方式 | 回退本次前端与文档 commit；后端 WP9 M5/M6A API 不受影响 |

## 1. 目标与范围

M6B 目标是在 M6A 工作台基础上补齐计划编辑的实际闭环：用户选择既有计划后可回填多节点 DAG 草稿，编辑节点 key、类型、依赖、bundle、baseUrlRef、caseIds、runtime secretRefs、timeout、failurePolicy 和 retry 次数，并通过 `PATCH /api/v1/execution/plans/{id}` 保存；也可归档计划。触发配置区域支持按触发器切换最近事件，帮助定位 webhook/cron 幂等证据。

## 2. 主要变更

1. 新增 `executionDagEditor.ts`，集中处理 DAG 草稿、校验、payload 构建和摘要展示。
2. `ExecutionWorkbench` 支持多节点 DAG 编辑、新建/编辑模式切换、计划更新、计划归档和触发事件切换查看。
3. `executionDagEditor.test.ts` 覆盖多节点 payload、重复 key、缺失依赖、环检测和脱敏 secretRef 回填。
4. `execution.test.ts` 补充 `archiveExecutionPlan` endpoint 包装验证。
5. 更新 WP9 研发拆解和前端页面设计文档，标明 M6B 已推进和后续剩余边界。

## 3. 验收标准

| 验收项 | 结论 |
|---|---|
| 选中计划后回填 DAG 草稿 | 通过 |
| 多节点创建/更新 payload 与后端契约一致 | 通过 |
| 前端阻断重复 key、缺失依赖、环依赖、timeout/retry 越界 | 通过 |
| runtime secretRefs 脱敏对象不被回填为可提交明文 | 通过 |
| 计划归档入口受 `execution:manage` 控制 | 通过 |
| 触发事件可按触发器切换查看 | 通过 |

## 4. 风险与后续

1. 当前仍是表单式 DAG 编辑，不是图形化画布；复杂编排可在后续交互切片升级。
2. 后端尚未实现 `/runs/{id}/export`，前端本轮不提供下载按钮，避免误导用户。
3. Playwright 桌面和 390px smoke 尚未新增，需在 M6C/M7 纳入。
4. Cron scanner 仍由后端后续切片承接，前端只展示 metadata 和 readiness。
