# WP10 报告与失败诊断 - M8B 运维 Runbook 交付说明

| 项目 | 内容 |
|---|---|
| 里程碑 | M8B 运维 Runbook |
| 当前口径 | 补齐 WP10 报告生成、AI 诊断降级、导出阻断、模型预算、敏感泄露和回滚排障 Runbook |
| 日期 | 2026-06-17 |

## 1. 目标和范围

M8B 目标是完成 WP10-8.3 运维 Runbook，让运维、QA、发布负责人和研发值班人员可按步骤处理报告生成失败、evidence manifest 异常、AI 诊断降级、模型预算阻断、缺陷草稿异常、导出阻断、前端 DOM 脱敏异常和紧急回滚。

本轮范围：

1. 新增 `WP10-报告与失败诊断-运维Runbook.md`，覆盖开关配置、日常验证、发布准出、报告生成、evidence manifest、AI 诊断、导出、缺陷草稿、敏感泄露、DB/权限/审计、前端 smoke、回滚和准出记录。
2. 同步 README 索引、WP10 研发任务拆解、技术设计和测试策略，标记 WP10-8.3/M8B 已完成。
3. 记录本交付说明，明确目标、范围、非目标、验证、风险、回滚和五角色结论。

## 2. 非目标范围

1. 不修改 `platform-api` Java 生产代码、API 契约、DB migration、模型调用或脚本逻辑。
2. 不修改 `portal-web` 运行时代码、样式、路由或 Playwright smoke。
3. 不新增真实生产自动恢复任务、外部缺陷系统写入、PDF/Word 完整报告、趋势报表或 WP3/WP5 evidence adapter。
4. 不替代后续 WP10 发布准出总说明和剩余工作盘点。

## 3. 涉及模块

| 模块 | 影响 |
|---|---|
| `doc/mvp/final/engineering/WP10-报告与失败诊断-运维Runbook.md` | 新增 WP10 运维排障和回滚 Runbook。 |
| `doc/mvp/final/engineering/WP10-报告与失败诊断-M8B运维Runbook交付说明.md` | 新增本轮交付记录。 |
| `README.md` | 增加 WP10 运维 Runbook 和 M8B 交付说明索引。 |
| WP10 研发任务拆解 | 更新 Epic 8 当前推进状态和后续顺序。 |
| WP10 技术设计与接口契约 | 记录 M8B Runbook 已补齐，不改变运行时契约。 |
| WP10 测试策略 | 更新当前质量结论，纳入 M8B 文档准出。 |

## 4. 风险和回滚

| 风险 | 缓解 |
|---|---|
| Runbook 写入未实现的自动恢复能力 | 仅描述当前已存在的开关、脚本、状态和人工排障步骤，不承诺后台自愈或外部系统写入。 |
| 排障过程扩大敏感信息传播 | Runbook 明确只记录 digest、traceId 和命中类别，不复制敏感原文。 |
| 后续代码变更导致 Runbook 漂移 | 后续修改 WP10 开关、状态机、错误码、脚本或导出策略时同步更新本 Runbook。 |

回滚方式：回退本次 M8B 文档 commit；运行时代码、DB schema、脚本和既有 WP10 quality gate 不受影响。

## 5. 验收入口和结果

```bash
rg -n "WP10-8.3|M8B|运维 Runbook|运维Runbook|报告生成失败|AI 诊断降级|导出阻断" README.md doc/mvp/final/engineering/WP10-*
git diff --check
cd portal-web && npm test -- --run src/api/reports.test.ts src/permissions.test.ts
cd portal-web && npm run build
```

本轮实际验证结果：

| 命令 | 结果 |
|---|---|
| `rg -n "WP10-8.3|M8B|运维 Runbook|运维Runbook|报告生成失败|AI 诊断降级|导出阻断" README.md doc/mvp/final/engineering/WP10-*` | 通过，README、研发任务拆解、技术设计、测试策略、Runbook 和本交付说明均可检索到 M8B/WP10-8.3 记录。 |
| `git diff --check` | 通过，无空白错误。 |
| `cd portal-web && npm test -- --run src/api/reports.test.ts src/permissions.test.ts` | 通过，2 files / 24 tests passed。 |
| `cd portal-web && npm run build` | 通过；存在既有 Vite chunk size 和 `src/api/auth.ts` dynamic/static import 警告。 |

本轮未修改 Java 生产代码，不适用 `bash scripts/platform_api_java_line_guard.sh`；《阿里巴巴 Java 开发手册》自查和核心逻辑注释补充无新增影响。

## 6. 五角色结论

| 角色 | 结论 | 说明 |
|---|---|---|
| 资深项目经理 | 通过 | 范围限定为 WP10-8.3 运维 Runbook 和索引同步，回滚路径清晰，不引入运行时变更。 |
| 资深产品经理 | 通过 | Runbook 明确报告、诊断、草稿和导出的用户影响、非目标和人工确认边界。 |
| 资深服务端架构师 | 通过 | Runbook 对齐现有配置开关、状态机、错误码、DB validation、审计事件和跨 WP aggregate-only 边界。 |
| 资深前端工程师 | 无影响 | 本轮不改前端运行时代码；Runbook 仅引用既有 `#reports` smoke、DOM 扫描和权限边界。 |
| 资深质量工程师 | 通过 | 文档检索、`git diff --check`、前端定向 Vitest 和 build 均已通过；本轮无 Java/DB/API 运行时影响。 |
