# WP8 测试数据与账号池 - M6A 前端工作台基础闭环交付说明

| 项目 | 内容 |
|---|---|
| 工作包 | WP8 测试数据与账号池 |
| 里程碑 | M6A 前端工作台基础闭环 |
| 日期 | 2026-06-15 |
| 当前口径 | 先打通 `portal-web` 基础入口、API helper、权限和四个控制面面板；脱敏导出、Playwright smoke、DOM secretRef 扫描和 WP8 聚合 quality gate 后续推进 |

## 1. 目标、范围和非目标

目标：

1. 在 `portal-web` 新增 `#test-data` 工作台入口，复用 WP1 登录态和权限模型。
2. 提供 WP8 API client 和 normalize helper，覆盖 M1-M5 已落地后端路径。
3. 提供数据集、账号池、租借和清理任务基础面板，让测试工程师无需直接改数据库即可完成主控制面操作。
4. 保持敏感信息红线：`secretRef` 只作为写入输入，不回显原文；摘要对象过滤 password/token/cookie/secret 等敏感键。

范围：

1. `portal-web/src/api/testData.ts`：封装 health、data-sets、records、account-pools、accounts、leases、data-tasks API。
2. `portal-web/src/permissions.ts` 和 `App.tsx`：新增 `test-data` 页面、导航入口和 `testData:*` 权限映射。
3. `portal-web/src/components/TestDataWorkbench.tsx`：新增四个 tab 的基础工作台 UI。
4. `portal-web/src/api/testData.test.ts` 和 `permissions.test.ts`：覆盖 API helper、payload、权限和脱敏断言。
5. WP8 PRD、任务拆解、前端设计、测试策略和启动准备文档更新 M6A 当前状态。

非目标：

1. 不实现脱敏导出面板和导出文件下载。
2. 不新增 Playwright 桌面/390px smoke 或 DOM secretRef 原文扫描脚本。
3. 不启用真实 cleanup worker，不执行破坏性清理 adapter。
4. 不改 Java 生产代码、DB schema 或后端接口。

## 2. 主要变更

1. API helper 支持后端 camelCase/snake_case 响应归一化，并保留 `secretRefDigest`、`leaseTokenDigest` 等 digest 字段。
2. 工作台四个面板提供基础 loading/empty/error、traceId 展示、状态标签和窄屏单列布局。
3. 账号 secretRef 输入使用 password 控件，新增或替换成功后前端 state 清空；账号列表和租借详情只展示 digest。
4. 清理任务面板展示 `cleanupEnabled=false` 解释，避免误认为已执行破坏性清理。

## 3. 验收标准

1. 无 `testData:read` 权限时不显示 `#test-data` 入口，直达时展示无权限态。
2. `testData:manage` 才可创建/编辑/归档数据集、账号池和账号摘要。
3. `testData:lease` 才可申请、续租和释放租借。
4. `testData:cleanup` 才可创建和重试清理任务。
5. API helper 测试证明敏感摘要字段不会进入前端标准化对象或非必要 payload；新增账号写入允许 `secretRef`，更新账号留空不发送 `secretRef`。
6. `npm test` 和 `npm run build` 通过。

## 4. 验证命令

M6A 最小门禁：

```bash
cd portal-web && npm test -- permissions testData
cd portal-web && npm test
cd portal-web && npm run build
mvn -B -pl platform-api test
bash scripts/platform_api_java_line_guard.sh
bash db/validation/run_wp1_db_validation.sh
git diff --check
```

## 5. 未执行项和后续

1. Playwright 桌面/390px smoke 未执行：M6B/M6C 需补 `wp8_frontend_e2e_smoke.sh` 或等价脚本。
2. DOM secretRef 原文扫描未执行：release gate 需覆盖浏览器渲染后的 DOM 和 toast/error 区域。
3. 脱敏导出未完成：后续需补 `testData:export` 按钮、导出结果面板和导出 payload 测试。
4. WP8 聚合 quality gate 未完成：M7 需整合后端、前端、DB、并发租借 smoke、前端 smoke 和 Java 行数门禁。

## 6. 五角色结论

| 角色 | 结论 | 说明 |
|---|---|---|
| 资深项目经理 | 通过 | M6A 范围清楚，验证链完整，后续 smoke 和导出不混入本次完成定义。 |
| 资深产品经理 | 通过 | 主控制面价值可验收，secretRef 红线有实现和测试证据，脱敏导出仍是后续范围。 |
| 资深服务端架构师 | 通过 | 前端 API client 对齐 `/api/v1/test-data` 契约，不直连跨 WP 内部服务，只展示 digest/traceId。 |
| 资深前端工程师 | 通过 | `#test-data` 路由、权限、工作台基础状态和响应式基础能力已实现。 |
| 资深质量工程师 | 通过 | API helper、权限、payload 和脱敏测试已覆盖，默认门禁已通过。 |
