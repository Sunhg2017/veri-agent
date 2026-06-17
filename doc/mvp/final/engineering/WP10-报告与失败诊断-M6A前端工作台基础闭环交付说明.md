# WP10 报告与失败诊断 - M6A 前端工作台基础闭环交付说明

| 项目 | 内容 |
|---|---|
| 里程碑 | M6A 前端工作台基础闭环 |
| 当前口径 | 完成 `portal-web` 报告诊断入口、API helper、报告列表/生成/详情、诊断、缺陷草稿和导出摘要面板；不新增外部缺陷系统写入，不实现 Playwright smoke |
| 日期 | 2026-06-17 |

## 1. 目标和范围

M6A 目标是让用户不依赖 curl，在 `#reports` 工作台完成 WP10 P0 主链路的浏览器内操作：生成报告快照、查看详情和 evidence manifest、触发失败诊断、生成并审阅缺陷草稿、查看 JSON/Markdown 脱敏导出摘要。

本轮范围：

1. 新增 `portal-web/src/api/reports.ts`，覆盖 WP10 health、列表、详情、生成、重试、归档、诊断、缺陷草稿、草稿审阅和导出 API helper。
2. 扩展前端权限矩阵，新增 `reports` 页面和 `report:read/generate/diagnose/export/manage` 按钮权限。
3. 新增 `ReportsWorkbench`，提供 `#reports` 入口、顶部指标、筛选、生成面板、报告列表、详情、诊断、缺陷草稿和导出面板。
4. 页面展示只使用 aggregate-only summary、manifest digest、redaction policy 和 masked payload preview，不渲染原始 runner 产物或模型正文。
5. `scripts/wp10_quality_gate.sh` 纳入 WP10 前端 Vitest 定向用例和 `portal-web` build。

## 2. 非目标范围

1. 不写 Jira、禅道、飞书或任何外部缺陷系统；页面不提供发送按钮。
2. 不实现 Playwright 浏览器 smoke、截图归档或 DOM 自动扫描脚本；M6B/M7 继续承接。
3. 不新增后端 API、DB migration 或 Java 生产逻辑。
4. 不新增趋势报表、报告订阅、PDF/Word 完整报告或原始 artifact 归档。
5. 不改变 WP9 执行编排、WP8 数据证据、WP2 模型接入和 WP1 权限后端契约。

## 3. 安全和体验边界

`ReportsWorkbench` 只展示服务端已脱敏的摘要字段。缺陷草稿区域只展示 `payloadPreview` 的 schema、masked、aggregateOnly 和 `externalSystemWriteAttempted=false` 等安全标记，不渲染完整外部 payload 字段正文。

导出面板展示 `fieldSetVersion`、`contentDigest`、manifest、redaction policy 和 DOM 扫描摘要，不把导出正文作为页面主要内容展开。长 `reportId`、`executionRunId`、digest、traceId 和 evidence refs 均设置换行，避免 390px 视口横向溢出。

## 4. 主要变更

| 模块 | 变更 |
|---|---|
| `portal-web/src/api/reports.ts` | 新增 WP10 API helper 和 normalize helper，兼容 camelCase/snake_case。 |
| `portal-web/src/components/ReportsWorkbench.tsx` | 新增报告诊断工作台，覆盖列表、生成、详情、诊断、草稿和导出面板。 |
| `portal-web/src/App.tsx` | 新增 `#reports` 导航入口，菜单名为“报告诊断”。 |
| `portal-web/src/permissions.ts` | 新增 WP10 页面和按钮权限映射。 |
| `portal-web/src/styles.css` | 新增 WP10 工作台布局、列表、详情、策略和响应式样式。 |
| `portal-web/src/api/reports.test.ts` | 覆盖 API normalize 和 endpoint/payload。 |
| `portal-web/src/permissions.test.ts` | 覆盖 `report:*` 权限和页面访问规则。 |
| `scripts/wp10_quality_gate.sh` | 纳入 WP10 前端 Vitest 定向用例和前端 build。 |

## 5. 风险和回滚

| 风险 | 缓解 |
|---|---|
| 前端误展示敏感字段 | 页面只取摘要/digest/policy 字段；导出正文不作为主要视图展开；后续 M6B 接入 DOM smoke。 |
| 用户误以为缺陷已发外部系统 | 草稿面板文案和 payload preview 均标注平台内草稿，且无外部发送动作。 |
| 权限入口误暴露 | `reports` 页面由 `report:read` 控制，按钮由 `report:*` 权限控制；服务端仍是最终准入。 |
| 工作台影响其他页面 | 新增组件和样式 scoped 到 `.reports-*`，导航仅新增一个页面入口。 |

回滚方式：回退本次 M6A commit；后端 M1-M5B API、DB 和 smoke 不依赖前端入口。若需运行期止血，可临时移除 `#reports` 导航入口或撤销前端构建版本。

## 6. 验证记录

本交付已执行以下验证：

| 命令 | 结果 |
|---|---|
| `cd portal-web && npm test -- --run src/api/reports.test.ts src/permissions.test.ts` | 通过，2 个测试文件、24 个测试通过。 |
| `cd portal-web && npm test` | 通过，27 个测试文件、199 个测试通过。 |
| `cd portal-web && npm run build` | 通过；存在既有 Vite 动静态 import 和 chunk size warning，不影响构建产物。 |
| `bash scripts/wp10_quality_gate.sh` | 通过；覆盖脚本语法、Java 行数门禁、WP10 smoke、OpenAPI/权限测试、前端 Vitest、前端 build 和 DB validation。 |
| `bash scripts/platform_api_java_line_guard.sh` | 通过，`platform-api/src/main/java` 生产 Java 文件均未超过 1200 行。 |
| `mvn -B -pl platform-api test` | 通过，652 个测试通过，0 failures/errors/skipped。 |
| 浏览器 sanity check：`http://127.0.0.1:5173/#reports` | 通过有限检查：前端应用可加载到登录态且无浏览器 error 日志；未启动后端会话，鉴权后工作台 Playwright/DOM smoke 留到 M6B/M7。 |

本轮未修改 Java 生产代码；《阿里巴巴 Java 开发手册》自查结论为无新增 Java 核心逻辑和核心方法注释要求。

## 7. 五角色结论

| 角色 | 结论 | 说明 |
|---|---|---|
| 资深项目经理 | 通过 | M6A 范围限定在前端基础闭环，Playwright smoke 和发布准出不混入本轮完成定义。 |
| 资深产品经理 | 通过 | 用户可在浏览器完成报告、诊断、草稿和导出摘要主链路，外部写入边界清晰。 |
| 资深服务端架构师 | 无影响 | 本轮不改 Java 后端、DB 或接口契约，仅消费 M1-M5B 已实现 API。 |
| 资深前端工程师 | 通过 | `#reports` 工作台、权限、loading/empty/error、响应式和可测性基础已落地。 |
| 资深质量工程师 | 有条件通过 | 已补 API/权限 Vitest 和 build 门禁；Playwright/DOM 自动扫描仍按 M6B/M7 补齐。 |
