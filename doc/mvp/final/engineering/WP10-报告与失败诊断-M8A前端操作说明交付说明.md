# WP10 报告与失败诊断 - M8A 前端操作说明交付说明

| 项目 | 内容 |
|---|---|
| 里程碑 | M8A 前端操作说明 |
| 当前口径 | 补齐 `#reports` 浏览器操作说明，覆盖报告筛选、生成/重试、详情、诊断、草稿、导出、权限和脱敏边界 |
| 日期 | 2026-06-17 |

## 1. 目标和范围

M8A 目标是把 WP10 前端工作台的浏览器操作说明从设计文档和 smoke 记录中独立出来，形成面向测试工程师、测试负责人、项目负责人和发布负责人的用户操作材料。用户可按文档在 `#reports` 完成 P0 主链路，不依赖 curl 或直接调用 API。

本轮范围：

1. 新增 `WP10-报告与失败诊断-前端操作说明.md`，覆盖入口权限、页面结构、筛选、生成、重试、归档、详情、失败诊断、缺陷草稿、导出摘要、状态解释、安全边界和排障。
2. 同步 README 索引、WP10 研发任务拆解、前端页面设计和测试策略，标记 WP10-8.2/M8A 已完成。
3. 记录本交付说明，明确目标、范围、非目标、验证、风险、回滚和五角色结论。

## 2. 非目标范围

1. 不修改 `portal-web` 运行时代码、路由、样式、权限或 Playwright smoke。
2. 不修改 `platform-api` Java 生产代码、API 契约、DB migration 或模型调用逻辑。
3. 不新增外部缺陷系统写入、PDF/Word 完整报告、趋势报表或 WP3/WP5 evidence adapter。
4. 不替代后续 WP10 运维 Runbook、发布准出总说明和剩余工作盘点。

## 3. 涉及模块

| 模块 | 影响 |
|---|---|
| `doc/mvp/final/engineering/WP10-报告与失败诊断-前端操作说明.md` | 新增面向用户的浏览器操作说明。 |
| `doc/mvp/final/engineering/WP10-报告与失败诊断-M8A前端操作说明交付说明.md` | 新增本轮交付记录。 |
| `README.md` | 增加 WP10 前端操作说明和 M8A 交付说明索引。 |
| WP10 研发任务拆解 | 更新 Epic 8 当前推进状态和推荐后续顺序。 |
| WP10 前端页面设计 | 更新当前口径，记录 M8A 操作说明已补齐。 |
| WP10 测试策略 | 更新当前质量结论，纳入 M8A 文档验收。 |

## 4. 风险和回滚

| 风险 | 缓解 |
|---|---|
| 操作说明写入未交付能力 | 对照 `ReportsWorkbench`、`reports.ts`、权限映射和 Playwright smoke，只描述已交付的 `#reports` 主链路。 |
| 文档与后续前端行为漂移 | 后续变更前端按钮、状态或权限时，同步更新本操作说明和 smoke。 |
| 运维排障步骤不足 | M8A 只提供用户侧排障入口；服务端降级、预算、DB 和回滚步骤继续由 M8B Runbook 补齐。 |

回滚方式：回退本次 M8A 文档 commit；运行时代码、DB schema 和既有 WP10 quality gate 不受影响。

## 5. 验收入口和结果

```bash
rg -n "WP10-8.2|M8A|前端操作说明" README.md doc/mvp/final/engineering/WP10-*
git diff --check
cd portal-web && npm test -- --run src/api/reports.test.ts src/permissions.test.ts
cd portal-web && npm run build
```

本轮实际验证结果：

| 命令 | 结果 |
|---|---|
| `rg -n "WP10-8.2|M8A|前端操作说明" README.md doc/mvp/final/engineering/WP10-*` | 通过，README、研发任务拆解、前端页面设计、测试策略、前端操作说明和本交付说明均可检索到 M8A/WP10-8.2 记录。 |
| `git diff --check` | 通过，无空白错误。 |
| `cd portal-web && npm test -- --run src/api/reports.test.ts src/permissions.test.ts` | 通过，2 files / 24 tests passed。 |
| `cd portal-web && npm run build` | 通过；存在既有 Vite chunk size 和 `src/api/auth.ts` dynamic/static import 警告。 |

本轮未修改 Java 生产代码，不适用 `bash scripts/platform_api_java_line_guard.sh`；《阿里巴巴 Java 开发手册》自查和核心逻辑注释补充无新增影响。

## 6. 五角色结论

| 角色 | 结论 | 说明 |
|---|---|---|
| 资深项目经理 | 通过 | 范围限定为 WP10-8.2 前端操作说明和索引同步，回滚路径清晰，不引入运行时变更。 |
| 资深产品经理 | 通过 | 操作说明覆盖用户在 `#reports` 的 P0 主链路、权限边界、非目标和产品验收清单。 |
| 资深服务端架构师 | 无影响 | 本轮不修改服务端 API、DB、模型调用或跨 WP 契约；说明与既有接口边界一致。 |
| 资深前端工程师 | 通过 | 文档口径对齐现有 `ReportsWorkbench`、前端权限、API helper 和 Playwright smoke。 |
| 资深质量工程师 | 通过 | 文档检索、`git diff --check`、前端定向 Vitest 和 build 均已通过；本轮无 Java/DB/API 运行时影响。 |
