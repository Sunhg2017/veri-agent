# WP10 报告与失败诊断 - M8C 发布准出说明交付说明

| 项目 | 内容 |
|---|---|
| 里程碑 | M8C 发布准出说明 |
| 当前口径 | 补齐 WP10 发布准出总说明，集中记录当前范围、非目标、验证证据、跳过项、风险、回滚和五角色准出 |
| 日期 | 2026-06-17 |

## 1. 目标和范围

M8C 目标是完成 WP10-8.4 发布准出说明，把 M1-M8B 的功能交付、质量门禁、前端操作说明和运维 Runbook 汇总为当前范围的 release gate 文档。发布负责人可按文档判断 WP10 当前可准出的能力、目标环境需要执行的验证、允许跳过项、风险处置和回滚方式。

本轮范围：

1. 新增 `WP10-报告与失败诊断-发布准出说明.md`，覆盖准出结论、范围和非目标、验证记录、跳过项、发布风险、回滚方式、发布记录要求和五角色准出结论。
2. 同步 README、研发任务拆解、测试策略和当前实现基线，标记 WP10-8.4/M8C 已完成。
3. 记录本交付说明，明确目标、范围、非目标、验证、风险、回滚和五角色结论。

## 2. 非目标范围

1. 不修改 `platform-api` Java 生产代码、API 契约、DB migration、模型调用或脚本逻辑。
2. 不修改 `portal-web` 运行时代码、样式、路由或 Playwright smoke。
3. 不新增 WP3/WP5 evidence adapter、真实 provider 质量评测看板、外部缺陷系统写入、PDF/Word 完整报告、趋势报表或生产容量承诺。
4. 不替代 WP10 剩余工作盘点；WP10-8.5 已在后续 M8D 单独审计当前完成项和后续专项。

## 3. 涉及模块

| 模块 | 影响 |
|---|---|
| `doc/mvp/final/engineering/WP10-报告与失败诊断-发布准出说明.md` | 新增 WP10 发布准出总说明。 |
| `doc/mvp/final/engineering/WP10-报告与失败诊断-M8C发布准出说明交付说明.md` | 新增本轮交付记录。 |
| `README.md` | 增加 WP10 发布准出说明和 M8C 交付说明索引。 |
| WP10 研发任务拆解 | 更新 Epic 8 当前推进状态和后续顺序。 |
| WP10 测试策略 | 更新当前质量结论，纳入 M8C 发布准出说明。 |
| 当前实现基线 | 增加 WP10 当前能力和准出入口索引。 |

## 4. 风险和回滚

| 风险 | 缓解 |
|---|---|
| 把历史完整门禁误读为本轮文档-only 验证 | 发布准出说明分开记录“最近完整质量门禁证据”和“M8A/M8B/M8C 文档收口验证”。 |
| 后续专项被误认为当前已完成 | 非目标和跳过项明确列出 WP3/WP5 adapter、外部缺陷写入、PDF/Word、趋势报表等后续专项。 |
| 目标环境发布跳过必要验证 | 文档明确目标环境至少执行 WP10 quality gate、Maven 全量、前端 test/build 和 DB validation。 |

回滚方式：回退本次 M8C 文档 commit；运行时代码、DB schema、脚本和既有 WP10 quality gate 不受影响。

## 5. 验收入口和结果

```bash
rg -n "WP10-8.4|M8C|发布准出说明|WP10 quality gate|五角色准出|当前 WP10" README.md doc/mvp/final/engineering/WP10-* doc/mvp/final/engineering/当前实现基线.md
git diff --check
cd portal-web && npm test -- --run src/api/reports.test.ts src/permissions.test.ts
cd portal-web && npm run build
```

本轮实际验证结果：

| 命令 | 结果 |
|---|---|
| `rg -n "WP10-8.4|M8C|发布准出说明|WP10 quality gate|五角色准出|当前 WP10" README.md doc/mvp/final/engineering/WP10-* doc/mvp/final/engineering/当前实现基线.md` | 通过，README、研发任务拆解、测试策略、当前实现基线、发布准出说明和本交付说明均可检索到 M8C/WP10-8.4 记录。 |
| `git diff --check` | 通过，无空白错误。 |
| `cd portal-web && npm test -- --run src/api/reports.test.ts src/permissions.test.ts` | 通过，2 files / 24 tests passed。 |
| `cd portal-web && npm run build` | 通过；存在既有 Vite chunk size 和 `src/api/auth.ts` dynamic/static import 警告。 |

本轮未修改 Java 生产代码，不适用 `bash scripts/platform_api_java_line_guard.sh`；《阿里巴巴 Java 开发手册》自查和核心逻辑注释补充无新增影响。

## 6. 五角色结论

| 角色 | 结论 | 说明 |
|---|---|---|
| 资深项目经理 | 通过 | 范围限定为 WP10-8.4 发布准出说明和索引同步，回滚路径清晰，不引入运行时变更。 |
| 资深产品经理 | 通过 | 发布说明明确当前用户主链路、非目标和后续专项，避免把外部缺陷写入或完整报告误标为完成。 |
| 资深服务端架构师 | 通过 | 准出口径对齐现有 API、DB、权限、审计、跨 WP aggregate-only 契约和 WP2 bounded context 边界。 |
| 资深前端工程师 | 通过 | 准出口径引用既有 `#reports` 工作台、前端 smoke、DOM 扫描和操作说明，不改前端运行时代码。 |
| 资深质量工程师 | 通过 | 文档检索、`git diff --check`、前端定向 Vitest 和 build 均已通过；本轮无 Java/DB/API 运行时影响。 |
