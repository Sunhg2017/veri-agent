# WP8 测试数据与账号池 - M6C 数据集脱敏导出摘要交付说明

| 项目 | 内容 |
|---|---|
| 工作包 | WP8 测试数据与账号池 |
| 里程碑 | M6C 数据集脱敏导出摘要 |
| 日期 | 2026-06-15 |
| 当前口径 | 补齐数据集脱敏导出摘要后端接口、前端面板、API helper、Vitest 和 Playwright smoke；不实现真实文件下载、租借导出或真实 cleanup worker |

## 1. 目标、范围和非目标

目标：

1. 为数据集补齐 `GET /api/v1/test-data/data-sets/{id}/export` 脱敏导出摘要接口。
2. 在 `#test-data` 工作台数据集 tab 中提供“脱敏导出摘要”按钮和结果面板。
3. 明确导出 redaction policy，证明导出不包含完整 record payload、maskedSummary 值或 secretRef 原文。

范围：

1. `platform-api` 新增数据集导出 view、service 方法和 controller endpoint。
2. `portal-web/src/api/testData.ts` 新增导出 helper、类型和 normalizer。
3. `portal-web/src/components/TestDataWorkbench.tsx` 新增导出状态、按钮和结果面板。
4. `portal-web/e2e/wp8-test-data.smoke.playwright.ts` 新增导出 mock 和浏览器断言。
5. WP8 PRD、任务拆解、技术设计、前端设计、测试策略和启动准备文档同步当前状态。

非目标：

1. 不提供 CSV/JSON 文件下载。
2. 不导出租借摘要、清理审计摘要或跨 WP 报告证据。
3. 不启动真实 HTTP 后端或生产数据库做外部并发压测。
4. 不启用真实 cleanup worker 或破坏性清理 adapter。

## 2. 主要变更

1. 后端导出响应只包含数据集元信息、记录数量、schema 字段数量、敏感字段数量、record digest、external ref digest、tags、`maskedSummaryKeys` 和 redaction policy。
2. `testData:export` 通过现有项目 scope 解析器校验；`veri-agent.test-data.export-enabled=false` 时导出返回 `INVALID_STATE`。
3. 前端导出面板只展示 schema version、计数、policy、digest、tags 和 keys；按钮受 `testData:export` 与 `health.exportEnabled` 控制。
4. 前端 normalizer 对 redaction policy 保留布尔安全声明，同时继续过滤敏感字符串值。

## 3. 验收标准

1. 导出接口出现在 OpenAPI contract，且权限、开关和项目 scope 生效。
2. 后端响应和前端 DOM 不出现 `secret://`、原始记录正文、maskedSummary 值、token、cookie 或 Authorization header。
3. Vitest 覆盖导出路径、normalizer 和 redaction policy。
4. Playwright smoke 覆盖导出按钮点击和 DOM 脱敏扫描。
5. Java 行数门禁、WP8 quality gate、前端 build 和必要后端定向测试通过。

## 4. 风险和回滚

| 风险 | 处置 | 回滚方式 |
|---|---|---|
| 导出字段过宽导致敏感值暴露 | 后端使用专用 export view，不复用详情响应；前端只渲染 keys/digest/policy | 回滚 controller endpoint、service 方法和前端导出面板 |
| 权限误配导致只读用户可导出 | 使用 `testData:export` 并复用数据集项目 scope；MVC 测试覆盖 read-only 拒绝 | 临时关闭 `veri-agent.test-data.export-enabled` |
| 前端 policy 被 sanitizer 误删 | 单独 normalizer 保留布尔安全声明 | 回滚 normalizer 或隐藏导出 policy 面板 |
| 与真实文件下载范围混淆 | 文档明确当前只提供控制面 JSON 摘要 | 后续单独拆分文件下载 story |

## 5. 五角色结论

| 角色 | 结论 | 说明 |
|---|---|---|
| 资深项目经理 | 通过 | 范围限定在数据集导出摘要，真实文件下载、租借导出和 cleanup worker 不纳入本轮；回滚可关闭 `export-enabled` 或回退导出切片。 |
| 资深产品经理 | 通过 | 导出面板满足测试工程师查看摘要和 redaction policy 的核心诉求，不展示敏感原文、maskedSummary 值或完整记录正文。 |
| 资深服务端架构师 | 通过 | 后端接口复用现有权限和数据集仓储，专用 view 避免复用详情响应造成值泄露，审计只写计数和策略。 |
| 资深前端工程师 | 通过 | 导出按钮、结果面板、loading/error/traceId 和响应式布局沿用工作台现有模式，按钮受权限和 health 开关控制。 |
| 资深质量工程师 | 通过 | 定向后端、前端、smoke、build、Java 行数门禁、全量默认入口和 WP8 release quality gate 均已通过。 |

## 6. 验证结果

```bash
bash scripts/platform_api_java_line_guard.sh
mvn -B -pl platform-api -Dtest=TestDataSetControllerTest,TestDataSetServiceTest,TestDataOpenApiContractTest,OpenApiContractTest,PermissionCodeUsageTest test
cd portal-web && npm test -- api/testData.test.ts permissions.test.ts
bash scripts/wp8_frontend_e2e_smoke.sh
cd portal-web && npm run build
WP8_GATE_MODE=release WP8_LEASE_CONCURRENCY_SMOKE=managed bash scripts/wp8_quality_gate.sh
mvn -B -pl platform-api test
cd portal-web && npm test
git diff --check
```

结果：

1. Java 行数门禁通过，生产 Java 文件均未超过 1200 行。
2. 后端定向测试通过，覆盖导出权限、开关、OpenAPI、字段计数和敏感值不返回。
3. 前端定向 Vitest 通过，覆盖导出 helper、normalizer、权限 helper 和 redaction policy。
4. Playwright smoke 通过，桌面和 390px 均覆盖导出点击和 DOM 脱敏扫描。
5. `portal-web` build 通过；存在既有 chunk size 和动态/静态 import warning，不影响构建结果。
6. WP8 release quality gate 通过，包含 DB validation 和 managed 租借并发 smoke。
7. `platform-api` 全量测试通过，631 个测试 0 失败 0 错误；Surefire 退出阶段提示 fork JVM 30 秒 kill，但构建结果为 success。
8. `portal-web` 全量 Vitest 通过，26 个文件 195 个测试通过。
9. `git diff --check` 通过。
