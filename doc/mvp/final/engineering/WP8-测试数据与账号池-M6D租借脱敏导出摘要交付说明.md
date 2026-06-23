# WP8 测试数据与账号池 - M6D 租借脱敏导出摘要交付说明

| 项目 | 内容 |
|---|---|
| 工作包 | WP8 测试数据与账号池 |
| 里程碑 | M6D 租借脱敏导出摘要 |
| 日期 | 2026-06-15 |
| 当前口径 | 补齐租借脱敏导出摘要后端接口、前端面板、API helper、Vitest 和 Playwright smoke；本切片交付时不实现真实文件下载、真实 cleanup worker 或 WP7/WP9 执行器集成 |

## 1. 目标、范围和非目标

目标：

1. 为账号租借补齐 `GET /api/v1/test-data/leases/{id}/export` 脱敏导出摘要接口。
2. 在 `#test-data` 工作台租借 tab 中提供“租借脱敏导出摘要”按钮和结果面板。
3. 明确导出 redaction policy，证明导出不包含 secretRef 原文、租借 token 明文、释放原因原文、健康摘要原文、scope/policy 值或敏感 key。

范围：

1. `platform-api` 新增租借导出 view、service 方法和 controller endpoint。
2. `portal-web/src/api/testData.ts` 新增租借导出 helper、类型和 normalizer。
3. `portal-web/src/components/TestDataWorkbench.tsx` 新增租借导出状态、按钮和结果面板。
4. `portal-web/e2e/wp8-test-data.smoke.playwright.ts` 新增租借导出 mock 和浏览器断言。
5. WP8 PRD、任务拆解、技术设计、前端设计、测试策略和启动准备文档同步当前状态。

非目标：

1. 本切片交付时不提供 CSV/JSON 文件下载；按当前基线，平台级真实文件下载能力已由后续对象存储专题提供，但 WP8 租借导出仍维持摘要视图。
2. 不启动真实 cleanup worker 或破坏性清理 adapter。
3. 本切片交付时不接入 WP7/WP9 真实执行器或 SecretProvider 凭据注入；按当前基线，这些消费链路已由后续里程碑承接交付。
4. 不导出清理审计文件或 WP10 完整报告证据。

## 2. 主要变更

1. 后端导出响应只包含租借、账号池和账号摘要、digest、安全 key 名、presence flag、生命周期摘要和 redaction policy。
2. `testData:export` 通过现有租借项目 scope 解析器校验；`veri-agent.test-data.export-enabled=false` 时导出返回 `INVALID_STATE`。
3. 后端审计 `test_data.lease.exported` 只写 schemaVersion、状态、holderType 和脱敏策略布尔值。
4. 前端导出面板只展示 schema version、状态、holder、account、digest、过滤后的安全 key 名和 policy；按钮受 `testData:export` 与 `health.exportEnabled` 控制。
5. 前端 normalizer 对租借导出 key 列表做二次敏感 key 过滤，并保留 redaction policy 中的布尔安全声明。

## 3. 验收标准

1. 导出接口出现在 OpenAPI contract，且权限、开关和项目 scope 生效。
2. 后端响应和前端导出面板不出现 `secret://`、租借 token 明文、释放原因原文、健康摘要原文、token、cookie 或 Authorization header。
3. Vitest 覆盖导出路径、normalizer、digest 保留和敏感 key 过滤。
4. Playwright smoke 覆盖租借导出按钮点击、redaction policy 可见性和导出面板脱敏扫描。
5. Java 行数门禁、WP8 quality gate、前端 build 和必要后端定向测试通过。

## 4. 风险和回滚

| 风险 | 处置 | 回滚方式 |
|---|---|---|
| 导出字段过宽导致释放原因或健康摘要泄露 | 后端使用专用 export view；自由文本只导出 presence flag 和 digest | 回滚 controller endpoint、service 方法和前端租借导出面板 |
| scope/lease policy 键名泄露敏感语义 | 后端和前端 normalizer 均过滤 token、cookie、secret、authorization、credential 等敏感 key | 临时关闭 `veri-agent.test-data.export-enabled` |
| 权限误配导致只读用户可导出 | 使用 `testData:export` 并复用租借项目 scope；MVC 测试覆盖 read-only 和跨项目拒绝 | 回滚 endpoint 或撤销角色 export 权限 |
| 与真实文件下载范围混淆 | 文档明确当前只提供控制面 JSON 摘要；按当前基线，平台级下载能力已存在，但 WP8 租借导出未切换为文件闭环 | 后续单独拆分 WP8 文件下载 story |

## 5. 五角色结论

| 角色 | 结论 | 说明 |
|---|---|---|
| 资深项目经理 | 有条件通过 | 范围、风险、里程碑和回滚方式清晰；条件是保持导出摘要仅白名单字段，后续增强不得回退安全边界。 |
| 资深产品经理 | 有条件通过 | 目标、边界、非目标和验收标准明确；条件是继续坚持只读摘要，不在本切片范围内扩展为文件下载或执行器能力。 |
| 资深服务端架构师 | 通过 | 后端导出契约、权限、审计和脱敏边界已落地，且验证通过。 |
| 资深前端工程师 | 通过 | 导出面板、helper、权限控制和 Playwright smoke 已落地且验证通过。 |
| 资深质量工程师 | 有条件通过 | 测试矩阵和门禁已覆盖关键路径；条件是后续变更继续纳入 quality gate 与并发 smoke。 |

五角色均无阻断项。

## 6. 验证结果

已执行：

```bash
bash scripts/platform_api_java_line_guard.sh
mvn -B -pl platform-api -Dtest=TestAccountLeaseControllerTest,TestAccountLeaseServiceTest,TestDataOpenApiContractTest,OpenApiContractTest,PermissionCodeUsageTest test
cd portal-web && npm test -- api/testData.test.ts permissions.test.ts
bash scripts/wp8_frontend_e2e_smoke.sh
cd portal-web && npm run build
WP8_GATE_MODE=release WP8_LEASE_CONCURRENCY_SMOKE=managed bash scripts/wp8_quality_gate.sh
mvn -B -pl platform-api test
cd portal-web && npm test
git diff --check
```

结果：

| 命令 | 结果 | 说明 |
|---|---|---|
| `bash scripts/platform_api_java_line_guard.sh` | 通过 | `platform-api/src/main/java` 生产 Java 文件均未超过 1200 行。 |
| `mvn -B -pl platform-api -Dtest=TestAccountLeaseControllerTest,TestAccountLeaseServiceTest,TestDataOpenApiContractTest,OpenApiContractTest,PermissionCodeUsageTest test` | 通过 | 18 个定向测试通过，覆盖租借导出 controller/service/OpenAPI/权限契约。 |
| `cd portal-web && npm test -- api/testData.test.ts permissions.test.ts` | 通过 | 2 个测试文件、25 个测试通过，覆盖租借导出 API helper、normalizer 和权限配置。 |
| `bash scripts/wp8_frontend_e2e_smoke.sh` | 通过 | desktop/mobile Playwright smoke 通过；首次运行因文本跨节点断言失败，修正为分项断言后重跑通过。 |
| `cd portal-web && npm run build` | 通过 | 构建通过；保留既有 `electron_mirror`、auth 动静态导入和 chunk size 警告，非本任务新增阻断。 |
| `WP8_GATE_MODE=release WP8_LEASE_CONCURRENCY_SMOKE=managed bash scripts/wp8_quality_gate.sh` | 通过 | release 模式 WP8 门禁通过，包含 Java 行数门禁、后端 WP8 测试、DB profile contract、前端 Vitest、Playwright smoke、前端 build、DB validation 和 managed 租借并发 smoke。 |
| `mvn -B -pl platform-api test` | 通过 | platform-api 全量测试 634 个通过；输出中存在测试容器和用例预期告警，未形成失败。 |
| `cd portal-web && npm test` | 通过 | portal-web 全量 Vitest 26 个测试文件、195 个测试通过。 |
| `git diff --check` | 通过 | 未发现 whitespace error。 |

Java 准入自查：

1. 已按《阿里巴巴 Java 开发手册》对命名、异常、日志、集合、权限、安全、兼容性和测试覆盖做人工自查，未发现阻断项。
2. `TestAccountLeaseService.exportLease` 新增方法级 JavaDoc，说明脱敏边界、权限/开关校验和审计副作用；自由文本仅导出 presence flag 与 digest。
3. 未引入生产 Java 文件超 1200 行，行数门禁已通过。
