# WP10 报告与失败诊断 - M7A 诊断质量评测门禁交付说明

| 项目 | 内容 |
|---|---|
| 里程碑 | M7A 诊断质量评测门禁 |
| 当前口径 | 新增离线规则诊断质量评测脚本，覆盖典型失败分类、安全输出和 WP10 quality gate 接入 |
| 日期 | 2026-06-17 |

## 1. 目标和范围

M7A 目标是把 WP10 诊断质量评测从文档占位推进为可重复执行的离线门禁。评测直接使用 aggregate-only evidence manifest 调用 `RuleFailureClassifier`，不启动真实后端、不调用 WP2 模型、不读取 WP8/WP9 原始表，验证规则 fallback 在典型失败场景下保持稳定且不输出禁止字段。

本轮范围：

1. 新增 `RuleFailureClassifierQualityEvaluationTest`，覆盖 timeout、dependency blocked、runner disabled、account locked 和 webhook idempotency conflict 五类样例。
2. 新增 `scripts/wp10_diagnosis_quality_eval.sh`，作为 WP10 诊断质量评测入口。
3. `scripts/wp10_quality_gate.sh` 纳入诊断质量评测，并支持 `WP10_SKIP_DIAGNOSIS_EVAL=1` 显式跳过。
4. 更新 WP10 研发拆解、测试策略和 README 索引。

## 2. 非目标范围

1. 不新增生产分类 bucket，不改变 `RuleFailureClassifier` 生产规则。
2. 不调用真实 WP2 模型，不评测 provider 输出质量。
3. 不补 WP3/WP5 evidence adapter。
4. 不实现外部缺陷系统写入、PDF/Word 报告或趋势报表。

## 3. 覆盖项

| 样例 | 当前期望 | 说明 |
|---|---|---|
| timeout | `TIMEOUT` | 命中 timeout status/errorCode，置信度 0.7600。 |
| dependency blocked | `DEPENDENCY_BLOCKED` | 命中 blocked status/errorCode，置信度 0.7300。 |
| runner disabled | `RUNNER_FAILURE` | 命中 runner errorCode/runnerType，置信度 0.6400。 |
| account locked | `TEST_DATA_ACCOUNT` | 命中 WP8 account lease/accountStatus，置信度 0.6800。 |
| webhook idempotency conflict | `UNKNOWN` | 当前无 webhook 专项 bucket，评测锁定安全 fallback 边界，置信度 0.4500。 |

安全扫描覆盖 `secret://`、`Authorization`、`Bearer`、`lease token`、`raw prompt`、`raw response`、`runner stdout/stderr` 和 `webhook payload`。评测允许 `rawPromptStored=false` 等安全策略键，但禁止原始敏感样本进入分类和诊断摘要 JSON。

## 4. 风险和回滚

| 风险 | 缓解 |
|---|---|
| 离线评测被误认为真实 AI 质量评测 | 文档明确 M7A 只覆盖规则 fallback；真实 WP2/provider 质量仍按后续专项推进。 |
| webhook 幂等冲突被误标为已专项分类 | 当前期望为 `UNKNOWN` fallback，后续若新增 webhook bucket，需要同步更新评测语料和阈值。 |
| CI 环境临时需要跳过诊断评测 | 可用 `WP10_SKIP_DIAGNOSIS_EVAL=1` 显式跳过，并在准出说明记录风险。 |

回滚方式：回退本次 M7A commit；生产代码和 WP10 运行时行为不依赖该离线评测脚本。

## 5. 验收入口和结果

```bash
bash scripts/wp10_diagnosis_quality_eval.sh
bash scripts/wp10_quality_gate.sh
```

本轮实际验证结果：

| 命令 | 结果 |
|---|---|
| `bash scripts/wp10_diagnosis_quality_eval.sh` | 通过，`RuleFailureClassifierQualityEvaluationTest` 1 test passed，覆盖 5 个离线样例。 |
| `mvn -B -pl platform-api -Dtest=RuleFailureClassifierQualityEvaluationTest test` | 通过，1 test，0 failures/errors。 |
| `bash -n scripts/wp10_diagnosis_quality_eval.sh scripts/wp10_quality_gate.sh && git diff --check` | 通过。 |
| `bash scripts/platform_api_java_line_guard.sh` | 通过，Platform API 生产 Java 文件均不超过 1200 行。 |
| `bash scripts/wp10_quality_gate.sh` | 通过，包含 M7A 诊断质量评测、既有 WP10 smoke、后端/OpenAPI 测试、前端 Vitest、Playwright smoke、前端 build 和合并 DB validation。 |
| `mvn -B -pl platform-api test` | 通过，653 tests，0 failures，0 errors，0 skipped；收尾阶段有 surefire fork JVM 退出警告但 Maven `BUILD SUCCESS`。 |
| `cd portal-web && npm test` | 通过，27 files / 199 tests passed。 |
| `cd portal-web && npm run build` | 通过，存在既有 Vite chunk size 和 dynamic import/static import 警告。 |

本轮新增 Java 测试代码，不修改 Java 生产代码；未引入新的核心生产逻辑或核心方法注释要求。《阿里巴巴 Java 开发手册》相关人工自查结论为无新增生产 Java 规范风险。

## 6. 五角色结论

| 角色 | 结论 | 说明 |
|---|---|---|
| 资深项目经理 | 通过 | M7A 范围限定为离线评测和门禁接入，回滚清晰。 |
| 资深产品经理 | 通过 | 典型失败场景覆盖诊断主路径，webhook 冲突明确为当前 fallback 边界。 |
| 资深服务端架构师 | 通过 | 不改生产规则，复用 aggregate-only evidence manifest 和现有 classifier 契约。 |
| 资深前端工程师 | 无影响 | 本轮不改前端页面；前端继续消费既有诊断响应。 |
| 资深质量工程师 | 通过 | 新增可重复执行评测脚本并接入 WP10 quality gate，覆盖安全输出扫描。 |
