# WP10 报告与失败诊断 - M8F 顶层文档基线一致性收口交付说明

| 项目 | 内容 |
|---|---|
| 里程碑 | M8F 顶层文档基线一致性收口 |
| 当前口径 | 对齐启动准备、PRD、技术设计和前端设计的当前基线到 M8E，不修改运行时代码 |
| 日期 | 2026-06-17 |
| 分支 | `codex/wp10-planning` |

## 1. 目标和范围

M8F 目标是清理 WP10 顶层入口文档中的旧阶段口径，让后续发布、评审和专项立项不再误读为“仍处于 M1/M2A 推进中”或“WP3/WP5 evidence adapter 属于当前 P0 缺口”。本轮只更新仍作为当前入口文档使用的启动准备、PRD、技术设计和前端页面设计，不改历史 M1-M7 交付说明中的当时范围描述。

本轮范围：

1. 更新 `WP10-报告与失败诊断-正式启动准备.md`，将启动结论、范围、模块影响、验收标准和里程碑同步到 M8E 当前基线。
2. 更新 `WP10-报告与失败诊断-需求文档与PRD.md`，明确当前已交付 WP9/WP8 主链路、诊断、草稿、导出和 `#reports`，并将 WP3/WP5、外部缺陷写入、完整报告和趋势/BI 归为后续专项。
3. 更新 `WP10-报告与失败诊断-技术设计与接口契约.md`，把当前实现口径调整为 WP9/WP8 evidence manifest 已完成、WP3/WP5 adapter 后续专项，避免接口契约被误读为当前未收口。
4. 更新 `WP10-报告与失败诊断-前端页面设计.md`，同步 `#reports` 工作台、浏览器 smoke、前端操作说明和后续前端增强边界。
5. 同步 README、研发拆解、测试策略、发布准出说明和当前实现基线索引，纳入 M8F 文档基线一致性收口记录。

## 2. 非目标范围

1. 不修改 `platform-api` Java 生产代码、API 契约实现、DB migration、模型调用、脚本逻辑或运行时配置。
2. 不修改 `portal-web` TypeScript、样式、路由、Playwright smoke 或构建配置。
3. 不重写历史 M1-M7 切片交付说明中的阶段性非目标和当时结论；这些记录保留为过程证据。
4. 不实现 WP3/WP5 evidence adapter、真实 provider 质量评测看板、外部缺陷系统写入、PDF/Word 完整报告、原始 runner artifact 归档、趋势/BI、报告订阅或生产容量承诺。

## 3. 涉及模块

| 模块 | 影响 |
|---|---|
| `WP10-报告与失败诊断-正式启动准备.md` | 当前口径、启动结论、范围、模块影响、验收标准同步到 M8E。 |
| `WP10-报告与失败诊断-需求文档与PRD.md` | 当前口径和 P0/P1 范围同步到当前已交付能力和后续专项。 |
| `WP10-报告与失败诊断-技术设计与接口契约.md` | 当前口径、模块边界、接口说明和 M3B 实现说明同步到 WP9/WP8 已完成、WP3/WP5 后续专项。 |
| `WP10-报告与失败诊断-前端页面设计.md` | 当前口径和 M8A 说明同步到 M8E 前端准出状态。 |
| README、研发拆解、测试策略、发布准出说明、当前实现基线 | 增加 M8F 索引和当前质量/准出口径。 |

## 4. 验收入口

```bash
rg -n "M8F|顶层文档基线一致性|v0.4|v0.2|当前范围已推进至 M8E|WP3/WP5 evidence adapter 保持后续专项" README.md doc/mvp/final/engineering/WP10-* doc/mvp/final/engineering/当前实现基线.md
git diff --check
cd portal-web && npm test -- --run src/api/reports.test.ts src/permissions.test.ts
cd portal-web && npm run build
```

本轮实际验证结果：

| 命令 | 结果 |
|---|---|
| `rg -n "M8F|顶层文档基线一致性|v0.4|v0.2|当前范围已推进至 M8E|WP3/WP5 evidence adapter 保持后续专项" README.md doc/mvp/final/engineering/WP10-* doc/mvp/final/engineering/当前实现基线.md` | 通过，确认 M8F 索引、版本号和 WP3/WP5 后续专项口径已覆盖入口文档与索引。 |
| 旧 M1/M2A 阶段误导表述扫描，覆盖启动准备、PRD、技术设计和前端页面设计四份入口文档 | 通过，仅命中新口径 `当前已推进至 M8E`，未命中旧阶段误导表述。 |
| `git diff --check` | 通过。 |
| `cd portal-web && npm test -- --run src/api/reports.test.ts src/permissions.test.ts` | 通过，2 个测试文件、24 个测试通过。 |
| `cd portal-web && npm run build` | 通过；仍有既有 Vite chunk size 和 `src/api/auth.ts` dynamic/static import 警告，本轮未引入。 |

本轮未修改 Java 生产代码，不适用单独执行 `bash scripts/platform_api_java_line_guard.sh`；最近完整门禁已由 M8E 记录，包含 Java 行数门禁、WP10 quality gate、Maven 全量、前端全量测试、前端 build 和 DB validation。

## 5. 风险和回滚

| 风险 | 缓解 |
|---|---|
| 历史交付说明被误改导致过程证据失真 | 本轮只更新顶层入口文档和索引，不重写 M1-M7 阶段性记录。 |
| 文档-only 收口被误认为目标环境 release gate | 明确 M8F 不替代 M8E 本地准出执行记录，也不替代目标环境 release gate。 |
| 后续专项被误读为当前 P0 缺口 | 顶层入口文档统一标注 WP3/WP5、外部缺陷写入、完整报告、趋势/BI 等为后续专项。 |

回滚方式：回退本次 M8F 文档 commit；既有 WP10 控制面、DB schema、脚本、前端工作台和 quality gate 不受影响。

## 6. 五角色结论

| 角色 | 结论 | 说明 |
|---|---|---|
| 资深项目经理 | 通过 | 范围限定为顶层文档基线一致性，回滚路径清晰；本轮文档检索、diff、前端定向测试和 build 已通过。 |
| 资深产品经理 | 通过 | PRD 当前口径已对齐 M8E，后续专项和当前用户主链路边界清晰。 |
| 资深服务端架构师 | 无影响 | 本轮不改服务端运行时、API、DB、权限、审计或模型调用，仅同步技术设计口径。 |
| 资深前端工程师 | 无影响 | 本轮不改前端运行时代码，仅同步前端页面设计当前基线和后续增强边界。 |
| 资深质量工程师 | 通过 | 验收入口和最近完整门禁引用已明确；本轮文档检索、diff、前端定向测试和 build 已通过。 |
