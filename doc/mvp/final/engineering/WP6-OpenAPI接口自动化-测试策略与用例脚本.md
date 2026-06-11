# WP6 OpenAPI 接口自动化 - 测试策略与用例脚本

| 项目 | 内容 |
|---|---|
| 工作包 | WP6 OpenAPI 接口自动化 |
| 角色产出 | 资深质量工程师 |
| 文档性质 | 测试策略、用例矩阵、脚本门禁和准出要求 |
| 当前口径 | WP6 P0 覆盖 OpenAPI 导入、API diff/sync、用例/脚本生成、受控试运行和结果采集 |
| 版本 | v0.1 |
| 日期 | 2026-06-11 |

## 1. 测试目标

1. 验证 OpenAPI 规格导入、解析、脱敏和 diff 稳定。
2. 验证 WP3 API 资产同步只通过应用服务并保持项目 scope。
3. 验证接口自动化用例和 Pytest 脚本包生成可审查、可追踪、可 fallback。
4. 验证 runner 默认关闭、显式开启后受 allowlist、timeout、case limit 和 secretRef 约束。
5. 验证运行结果采集不泄露请求响应正文、密钥、token 或环境变量。
6. 验证前端主链路状态、权限和错误提示完整。

## 2. 测试范围

| 范围 | 覆盖点 |
|---|---|
| Parser | JSON/YAML、path 参数、query/header/cookie 参数、requestBody、response schema、tags、operationId |
| 安全脱敏 | Authorization、apiKey、token、cookie、password、secret 示例值 |
| Diff/sync | 新增、匹配、变更、冲突、跳过、部分成功 |
| 生成 | 模型成功、WP2 阻断、模型非法输出、fallback、重复生成幂等 |
| 脚本包 | 静态校验、文件摘要、依赖清单、审批状态流 |
| Runner | disabled、allowlist 阻断、超时、失败、断言失败、跳过、取消 |
| 权限 | read/import/generate/review/execute/export 分权和项目隔离 |
| 前端 | helper normalize、payload、状态展示、权限按钮、结果摘要 |
| DB | 表、约束、索引、注释、runtime role 权限、审计事件种子 |

## 3. Fixture 要求

| Fixture | 用途 |
|---|---|
| `openapi-minimal.json` | 最小 GET/POST 成功解析 |
| `openapi-path-query.yaml` | path/query/header 参数解析 |
| `openapi-request-body.json` | JSON requestBody 和 2xx/4xx response |
| `openapi-secret-examples.json` | 敏感示例值脱敏 |
| `openapi-large.json` | 文件大小和 endpoint 上限 |
| `openapi-invalid.json` | 非法 schema 错误码 |
| `openapi-diff-v1.json` / `openapi-diff-v2.json` | diff 新增/变更/删除口径 |

## 4. 后端用例矩阵

| 用例 | 预期 |
|---|---|
| 导入合法 JSON OpenAPI | 创建 spec，状态 `UPLOADED/PARSED`，生成 endpoint snapshot |
| 导入合法 YAML OpenAPI | 与 JSON 解析结果一致 |
| 导入非 OpenAPI 3.x | 返回 `OPENAPI_PARSE_FAILED` |
| 导入超大规格 | 返回 `OPENAPI_TOO_LARGE` |
| 敏感示例值 | 入库 spec 和响应均不包含明文 secret/token |
| diff 已存在 API | 标记 `MATCHED/CHANGED`，不自动写资产 |
| sync 新 endpoint | 通过 WP3 应用服务创建 API，写审计 |
| sync 冲突 endpoint | 返回 `API_SYNC_CONFLICT`，可人工跳过 |
| 生成任务模型成功 | 保存 prompt、modelInvocationId、inputDigest 和脚本包 |
| WP2 策略阻断 | 任务失败或 fallback，错误可解释 |
| 模型输出非法 | 严格模式失败，fallback 模式生成模板草稿 |
| 未审批脚本包运行 | 默认拒绝或仅开发模式允许 |
| runner disabled | 返回 `RUNNER_DISABLED` |
| baseUrl 不在 allowlist | 返回 `RUNNER_TARGET_BLOCKED` |
| 运行超时 | 状态 `TIMEOUT`，用例结果可追踪 |
| 断言失败 | run 为 `FAILED`，结果只保存脱敏摘要 |
| 越权项目访问 | 返回 403，不泄露资源存在性 |

## 5. 前端用例矩阵

| 用例 | 预期 |
|---|---|
| 无 `apiAutomation:read` | 菜单隐藏，直达展示无权限态 |
| 导入表单缺字段 | 本地校验阻断，按钮不提交 |
| 规格列表 loading/empty/error | 状态展示正确 |
| diff 部分冲突 | 表格展示分类和逐项原因 |
| 生成 payload | 只包含 project/spec/API/case/coverage/generationMode |
| runner disabled | 运行入口禁用并展示策略摘要 |
| 运行结果聚合 | pass/fail/skip/error 和耗时展示正确 |
| 403/409/策略阻断 | 展示错误码、traceId 和脱敏 message |

## 6. 建议验证入口

正式实现后默认门禁：

```bash
mvn -B -pl platform-api test
```

```bash
cd portal-web && npm test
```

```bash
cd portal-web && npm run build
```

```bash
bash db/validation/run_wp1_db_validation.sh
```

WP6 专项门禁建议：

```bash
bash scripts/wp6_quality_gate.sh
```

发布或显式 runner smoke：

```bash
WP6_RUNNER_SMOKE=1 WP6_RUNNER_BASE_URL=http://127.0.0.1:8089 bash scripts/wp6_quality_gate.sh
```

## 7. WP6 Quality Gate 草案

`scripts/wp6_quality_gate.sh` 后续至少串联：

1. `mvn -B -pl platform-api -Dtest=ApiAutomation*Test test`
2. OpenAPI parser fixture tests。
3. WP6 DB validation。
4. `cd portal-web && npm test -- apiAutomation.test.ts apiAutomation*.test.ts --run`
5. `cd portal-web && npm run build`
6. 可选 runner smoke，默认关闭，发布模式强制显式选择 managed/external。

## 8. 准出标准

1. OpenAPI parser 不因非法输入崩溃，错误码稳定。
2. 导入、sync、生成、运行和导出均按项目 scope 鉴权。
3. 敏感示例、secretRef、token、cookie、完整请求响应正文和 stdout/stderr 全文不入库、不导出。
4. runner 默认关闭；开启后必须受 allowlist、timeout、case limit 和产物大小限制。
5. 后端、前端、构建、DB validation 和 WP6 专项 gate 按影响面通过。
6. 无法运行真实 runner 时，必须记录原因、风险和替代验证。

## 9. 启动前质量结论

本阶段只提交启动准备文档，不引入 Java/TS/DB 运行时代码。启动前质量关注点已覆盖，后续进入实现时必须先补 fixture、DB validation 和专项 gate，再扩大到 runner smoke。
