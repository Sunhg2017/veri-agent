# WP10 报告与失败诊断 - M7B 诊断上下文脱敏专项门禁交付说明

| 项目 | 内容 |
|---|---|
| 里程碑 | M7B 诊断上下文脱敏专项门禁 |
| 当前口径 | 收紧 WP10 发送 WP2 前的 bounded diagnosis context 脱敏边界，并新增离线专项评测脚本接入 quality gate |
| 日期 | 2026-06-17 |

## 1. 目标和范围

M7B 目标是把“敏感内容真实拦截专项评测”从待办推进为可重复执行的门禁。评测直接构造包含危险 key 和危险文本的 report summary、evidence manifest、rule diagnosis，验证 `ReportDiagnosisContextBuilder` 在发送 WP2 前只保留 aggregate-only 安全上下文，且不会把 raw prompt、runner stdout/stderr、request/response body、webhook payload、secretRef、Authorization 或 lease token 等样本传入模型上下文。

本轮范围：

1. 收紧 `ReportDiagnosisContextBuilder` 的 unsafe key 过滤规则，覆盖 payload/raw/prompt/response/request/body/stdout/stderr/screenshot/video/sourceCode 等原始产物字段。
2. 对普通备注字段中的原始产物标记执行整值替换为 `[REDACTED_CONTEXT]`，避免危险值尾随残留。
3. 新增 `ReportDiagnosisContextRedactionEvaluationTest`，覆盖危险 key、嵌套 map、summaryKeys、redactionFlags 和普通备注中的真实禁止样本。
4. 新增 `scripts/wp10_diagnosis_redaction_eval.sh` 并接入 `scripts/wp10_quality_gate.sh`，支持 `WP10_SKIP_DIAGNOSIS_REDACTION_EVAL=1` 显式跳过。
5. 更新 WP10 研发拆解、测试策略和 README 索引。

## 2. 非目标范围

1. 不改变 WP2 `ModelInvocationService` 契约，不调用真实 provider。
2. 不新增诊断分类 bucket，不改变 `RuleFailureClassifier` 规则语义。
3. 不补 WP3/WP5 evidence adapter。
4. 不实现外部缺陷系统写入、PDF/Word 报告或趋势报表。

## 3. 覆盖项

| 样本族 | 当前期望 |
|---|---|
| raw prompt / raw response | 原始 key 被过滤；普通备注命中后整值替换为 `[REDACTED_CONTEXT]`。 |
| runner stdout / stderr | 原始 key 被过滤；文本样本不进入 bounded context。 |
| request body / response body | 原始 key 被过滤；文本样本和值不进入 bounded context。 |
| webhook payload | 原始 key 被过滤；文本样本和值不进入 bounded context。 |
| secretRef / Authorization / Bearer | key 过滤或 `SensitiveTextSanitizer` 脱敏，不保留原文。 |
| lease token | 普通备注命中后整值替换，不保留真实样本。 |
| 策略字段 | `rawPromptIncluded=false` 等安全策略字段允许保留，避免误杀红线证明字段。 |

## 4. 风险和回滚

| 风险 | 缓解 |
|---|---|
| 过滤规则过宽导致上下文信息减少 | 只过滤原始产物和凭据类字段；分类、状态、digest、aggregate-only policy 仍保留。 |
| 普通备注命中敏感标记后丢失排障线索 | 命中即整值替换，优先保证模型输入安全；规则分类和 evidence digest 仍可用于人工追踪。 |
| CI 临时需要跳过专项评测 | 可用 `WP10_SKIP_DIAGNOSIS_REDACTION_EVAL=1` 显式跳过，并在准出说明记录风险。 |

回滚方式：回退本次 M7B commit；或仅移除 M7B 脚本接入并保留生产过滤增强。若回退生产过滤，必须保留 M7A/M7B 评测失败证据并重新评估 WP2 输入安全风险。

## 5. 验收入口和结果

```bash
bash scripts/wp10_diagnosis_redaction_eval.sh
bash scripts/wp10_quality_gate.sh
```

本轮实际验证结果：

| 命令 | 结果 |
|---|---|
| `bash scripts/wp10_diagnosis_redaction_eval.sh` | 通过，`ReportDiagnosisContextRedactionEvaluationTest` 1 test passed，覆盖 10 类上下文禁止样本。 |
| `mvn -B -pl platform-api -Dtest=ReportDiagnosisContextRedactionEvaluationTest,ReportControllerTest test` | 通过，8 tests，0 failures/errors，覆盖专项评测和报告诊断主链路回归。 |
| `bash -n scripts/wp10_diagnosis_redaction_eval.sh scripts/wp10_quality_gate.sh && git diff --check` | 通过。 |
| `bash scripts/platform_api_java_line_guard.sh` | 通过，Platform API 生产 Java 文件均不超过 1200 行。 |
| `bash scripts/wp10_quality_gate.sh` | 通过，包含 M7A/M7B 评测、既有 WP10 smoke、后端/OpenAPI 测试、前端 Vitest、Playwright smoke、前端 build 和合并 DB validation。 |
| `mvn -B -pl platform-api test` | 通过，654 tests，0 failures，0 errors，0 skipped。 |
| `cd portal-web && npm test` | 通过，27 files / 199 tests passed。 |
| `cd portal-web && npm run build` | 通过，存在既有 Vite chunk size 和 dynamic import/static import 警告。 |

本轮修改 Java 生产代码 `ReportDiagnosisContextBuilder`，已在新增核心过滤逻辑附近补充方法级注释；`bash scripts/platform_api_java_line_guard.sh` 已通过。《阿里巴巴 Java 开发手册》人工自查结论：命名、常量、异常、集合、日志、安全和测试覆盖未发现新增阻断项，核心逻辑注释已补充。

## 6. 五角色结论

| 角色 | 结论 | 说明 |
|---|---|---|
| 资深项目经理 | 通过 | 范围限定为诊断上下文脱敏门禁和小幅生产过滤增强，验证和回滚路径清晰。 |
| 资深产品经理 | 通过 | 强化报告诊断“不给模型原始敏感内容”的安全承诺，不改变用户功能主路径。 |
| 资深服务端架构师 | 通过 | 生产过滤逻辑集中在 context builder，接口契约不变，后端回归和行数门禁通过。 |
| 资深前端工程师 | 无影响 | 本轮不改前端页面和 API 响应契约。 |
| 资深质量工程师 | 通过 | 专项评测已接入 quality gate，完整后端、前端、构建和 DB validation 均通过。 |
