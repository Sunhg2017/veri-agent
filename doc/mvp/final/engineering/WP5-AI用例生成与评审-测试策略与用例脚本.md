# WP5 AI 用例生成与评审 - 测试策略与用例脚本

| 项目 | 内容 |
|---|---|
| 工作包 | WP5 AI 用例生成与评审 |
| 角色产出 | 资深质量工程师 |
| 文档性质 | 测试策略、功能用例、安全和可测试性建议、脚本设计 |
| 当前口径 | 已纳入 WP5 后端、前端、DB validation、Prompt 趋势、准出运营摘要、显式上下文装配、上下文裁剪策略配置、任务诊断上下文策略摘要、任务报告上下文聚合行和任务报告导出安全扫描的自动化验证入口 |
| 版本 | v1.2 |
| 日期 | 2026-05-30 |

## 1. 测试目标

验证 WP5 能在权限受控、模型可追踪、人工可评审、资产可回滚的前提下，将 WP3 需求资产转化为测试用例候选，并在人工确认后可靠写入 WP3 测试用例资产。

测试重点不是证明模型“聪明”，而是证明平台链路可控、可解释、可追踪、可回归。

## 2. 测试范围

| 范围 | 覆盖 |
|---|---|
| 功能测试 | 任务创建、任务查询、模型生成、候选评审、发布 dryRun、发布到 WP3、发布记录。 |
| 权限测试 | `testDesign:read/generate/review/publish/export` 的页面、按钮和接口鉴权。 |
| 状态流测试 | 任务状态、候选状态、非法状态流和版本冲突。 |
| 集成测试 | WP1 权限审计、WP2 模型调用、WP3 资产读写、WP4 来源需求。 |
| 安全测试 | 越权项目、敏感信息脱敏、模型输入泄露、错误摘要脱敏、导出脱敏。 |
| 可用性测试 | loading、empty、error、partial success、traceId 和下一步建议。 |
| 质量评测 | 覆盖率、断言完整性、重复率、有效步骤比例和低置信候选比例。 |
| 性能边界 | 单任务需求数、候选数、批量操作大小和上下文裁剪。 |

## 3. 非范围

| 非范围 | 说明 |
|---|---|
| 脚本执行正确性 | WP5 不生成或执行自动化脚本。 |
| Worker 调度 | 不测试执行队列、任务 DAG 或并发 worker。 |
| 缺陷系统同步 | 不创建禅道/Jira 缺陷。 |
| 第三方原型连接器 | 不测试 Figma、蓝湖、Axure 真实拉取。 |
| 模型供应商真实质量横评 | WP5 只评估当前 Prompt 和样本集输出，不承担厂商 benchmark。 |

## 4. 测试数据准备

| 数据 | 说明 |
|---|---|
| 项目 | `project-001`，用户具备 WP5 和 WP3 权限。 |
| 需求资产 | 至少 6 条，覆盖登录、权限、列表查询、表单保存、导入导出、审计日志。 |
| API 资产 | 至少 4 条，覆盖 GET/POST/PUT/DELETE 和错误响应。 |
| 页面资产 | 至少 3 条，覆盖登录页、列表页、表单页。 |
| 业务流 | 至少 2 条，覆盖正常流和审批/异常流。 |
| 历史用例 | 至少 5 条，用于重复风险检测。 |
| 低质输入 | 空验收标准、超长需求、敏感字段、歧义需求、重复需求。 |

## 5. 功能测试用例

| ID | 优先级 | 用例 | 预期 |
|---|---|---|---|
| WP5-FUNC-001 | P0 | 有权限用户选择单条需求创建生成任务 | 返回任务 ID，状态进入 `RUNNING/SUCCEEDED`，写审计。 |
| WP5-FUNC-002 | P0 | 批量选择多条同项目需求创建任务 | 生成候选按 requirementId 归属，任务摘要统计正确。 |
| WP5-FUNC-003 | P0 | 选择跨项目需求创建任务 | 返回校验失败或越权，任务不创建。 |
| WP5-FUNC-003A | P0 | 需求已关联 API、页面和业务流 | `contextSummary.linkedAssetsByRequirement` 包含三类资产脱敏摘要，且只通过 WP3 应用服务读取。 |
| WP5-FUNC-003B | P0 | 创建任务时显式传入 API、页面和业务流 ID | `contextSummary.explicitAssets` 包含三类资产计数、ID 和脱敏摘要；`inputDigest` 与幂等 request digest 覆盖这些显式上下文 ID。 |
| WP5-FUNC-003C | P0 | 配置 `veri-agent.test-design.context-*` 裁剪策略 | 健康接口返回生效限制；`contextSummary.limits` 和模型请求 `contextPacking` 使用同一套生效值。 |
| WP5-FUNC-003D | P0 | 导出任务全量报告 | CSV 只输出 `context/contextPolicy` 聚合计数和裁剪上限，不输出显式资产 ID、schema、页面树、流程 JSON、需求正文、历史用例步骤或原始 Prompt。 |
| WP5-FUNC-003E | P0 | 导出任务全量报告治理信息 | CSV 包含 `exportGovernance` 聚合行，声明 aggregate-only、候选正文/评审评论/模型载荷/上下文正文/trace 明细不导出，且安全扫描状态为 `PASSED`。 |
| WP5-FUNC-004 | P0 | 模型输出合法 JSON | 候选落库，包含标题、步骤、预期、优先级和来源依据。 |
| WP5-FUNC-005 | P0 | 模型输出非法 JSON | 任务失败或 fallback，错误码为模型输出非法，不产生脏候选。 |
| WP5-FUNC-006 | P0 | WP2 敏感内容阻断 | 任务展示阻断摘要，保存 traceId，不绕过 WP2。 |
| WP5-FUNC-006A | P0 | `generationMode=MODEL` 调用 WP2 本地模型 | 任务和候选带 `modelInvocationId/provider/model`，任务诊断只展示白名单观测字段。 |
| WP5-FUNC-006B | P0 | `generationMode=MODEL_WITH_FALLBACK` 且 Prompt 不可用 | 任务成功降级模板，保留脱敏降级提示，不写入原始模型响应。 |
| WP5-FUNC-007 | P0 | 编辑候选标题、步骤和预期 | 候选版本递增，评审记录保存 before/after/diff。 |
| WP5-FUNC-008 | P0 | 确认候选 | 状态变为 `CONFIRMED`，确认人和时间可查。 |
| WP5-FUNC-009 | P0 | 驳回候选未填原因 | 返回字段校验错误。 |
| WP5-FUNC-010 | P0 | 忽略重复候选 | 状态变为 `IGNORED`，不进入发布池。 |
| WP5-FUNC-011 | P0 | 批量确认携带正确版本 | 所选候选全部确认，返回逐条结果。 |
| WP5-FUNC-012 | P0 | 批量确认携带旧版本 | 返回版本冲突，前端提示刷新。 |
| WP5-FUNC-013 | P0 | 发布 dryRun | 返回 CREATE/LINK_EXISTING/DUPLICATE_REVIEW_REQUIRED/SKIPPED 明细。 |
| WP5-FUNC-014 | P0 | 发布已确认候选 | WP3 新增测试用例，步骤完整，候选状态为 `PUBLISHED`。 |
| WP5-FUNC-015 | P0 | 发布未确认候选 | 返回非法状态，候选不写入 WP3。 |
| WP5-FUNC-016 | P0 | WP3 写入失败或部分成功 | 发布记录失败，候选保留 `assetCaseId/errorMessage`；重试能按 `sourceRef` 找回既有用例并补建 trace link。 |
| WP5-FUNC-016A | P0 | 批量处理发布冲突 | 按候选项目 scope 校验 `testDesign:publish`，逐项校验版本和目标用例需求追踪，返回成功/失败明细。 |
| WP5-FUNC-017 | P1 | 任务重试 | 失败任务可重试，保留历史错误和新 traceId。 |
| WP5-FUNC-018 | P1 | 取消运行中任务 | 任务进入 `CANCELLED`，不继续生成候选。 |

## 6. 权限和安全测试

| ID | 优先级 | 用例 | 预期 |
|---|---|---|---|
| WP5-SEC-001 | P0 | 无 `testDesign:read` 访问页面 | 前端无入口，直接访问显示权限不足，接口 403。 |
| WP5-SEC-002 | P0 | 无 `testDesign:generate` 创建任务 | 按钮不可见，接口 403。 |
| WP5-SEC-003 | P0 | 无 `testDesign:review` 编辑候选 | 按钮不可见，接口 403。 |
| WP5-SEC-004 | P0 | 无 `testDesign:publish` 发布候选 | 按钮不可见，接口 403。 |
| WP5-SEC-005 | P0 | 查询未授权项目任务 | 返回 403 或过滤为空，不能泄露任务存在性。 |
| WP5-SEC-006 | P0 | Prompt 上下文包含 token/手机号/身份证 | 模型输入和日志脱敏，或被 WP2 敏感策略阻断。 |
| WP5-SEC-007 | P0 | 模型错误返回包含原文 | 页面和日志只展示脱敏摘要。 |
| WP5-SEC-008 | P1 | 导出候选和评审记录 | 不含完整模型输入、密钥、token 或隐私字段。 |
| WP5-SEC-009 | P1 | 导出任务报告和查看任务诊断 | 只展示上下文计数、键摘要和裁剪策略数字，不展示原始上下文正文或资产内部结构。 |
| WP5-SEC-010 | P1 | 任务报告导出出现未脱敏 secret/token/Bearer 或原始 Prompt 标记 | 服务端最终安全扫描阻断导出，返回敏感内容阻断错误，不写出不合规 CSV。 |

## 7. 前端测试用例

| ID | 优先级 | 用例 | 预期 |
|---|---|---|---|
| WP5-FE-001 | P0 | 任务列表 loading/empty/error | 三类状态均展示正确。 |
| WP5-FE-002 | P0 | 创建任务表单字段校验 | 必填、数量范围、需求选择均有字段级提示。 |
| WP5-FE-003 | P0 | 候选编辑增删步骤、上移下移 | 顺序稳定，保存 payload 正确。 |
| WP5-FE-004 | P0 | 批量确认冲突 | 保留选择状态并提示刷新。 |
| WP5-FE-005 | P0 | dryRun 部分成功 | 展示成功、重复、失败明细和冲突摘要。 |
| WP5-FE-006 | P0 | 发布成功跳转 WP3 | 能打开对应 WP3 用例追踪视图。 |
| WP5-FE-007 | P0 | 403 响应 | 展示权限不足和 traceId。 |
| WP5-FE-008 | P1 | 100 条候选列表 | 无文本重叠、按钮不挤压，支持每页 10/20/50、上一页/下一页和跨页发布选择保留。 |
| WP5-FE-009 | P1 | 按任务和候选维度筛选后选择性发布 | 只提交勾选候选；未勾选时发布全部可发布候选，发布范围计数清晰。 |
| WP5-FE-010 | P1 | 候选编辑字段级质量提示 | 保存前提示标题、步骤数量、逐步预期、最终预期、敏感文本和同需求重复标题问题。 |
| WP5-FE-011 | P1 | 候选列表批量确认、驳回和忽略 | 只提交勾选且可评审候选；驳回/忽略要求评审意见；部分失败展示摘要并保留失败项选择。 |
| WP5-FE-012 | P1 | 批量评审和发布二次确认摘要 | 确认前展示操作、范围、候选预览、评审意见和风险提示；取消不触发接口。 |
| WP5-FE-013 | P1 | Prompt 趋势准出分布 | 顶部展示阻断版本、风险版本和准出分布；列表仍展示每个版本的准出状态、阻断数和风险数。 |
| WP5-FE-014 | P1 | 任务诊断上下文策略摘要 | 展示关联资产、显式资产、历史用例、需求描述、验收标准和资产摘要上限；不展示显式资产 ID 或上下文正文。 |

## 8. AI 质量评测指标

建议新增 `scripts/wp5_case_generation_quality_eval.sh`，以固定 golden set 运行：

| 指标 | 阈值建议 | 说明 |
|---|---|---|
| requirementCoverageRate | `>= 0.85` | 需求关键点被候选覆盖比例。 |
| assertionCompletenessRate | `>= 0.95` | 步骤含 expectedResult 的比例。 |
| effectiveStepRate | `>= 0.90` | 非空、可执行描述步骤比例。 |
| duplicateCandidateRate | `<= 0.15` | 同任务重复候选比例。 |
| priorityAccuracyRate | `>= 0.75` | 优先级与 golden set 预期一致比例。 |
| securityLeakCount | `= 0` | 输出不得泄露敏感字段。 |

golden set 至少覆盖：

1. 登录成功和失败。
2. 权限菜单和按钮。
3. 列表筛选、分页和空态。
4. 表单创建、编辑、校验和取消。
5. 导入导出。
6. 审计日志。
7. 异常输入、边界值和重复提交。

## 9. 建议脚本

### 9.1 `scripts/wp5_quality_gate.sh`

```bash
#!/usr/bin/env bash
set -euo pipefail

mvn -B -pl platform-api test

cd portal-web
npm test
npm run build
cd -

bash db/validation/run_wp1_db_validation.sh

if [[ "${WP5_RUN_HTTP_SMOKE:-0}" == "1" ]]; then
  # 未设置 WP5_SMOKE_BASE_URL 时，quality gate 会自启动临时 PostgreSQL 和 db profile platform-api。
  bash scripts/wp5_managed_http_smoke.sh
fi

if [[ "${WP5_RUN_HTTP_SMOKE:-0}" == "external" ]]; then
  bash scripts/wp5_test_design_smoke.sh
fi

if [[ "${WP5_RUN_AI_EVAL:-0}" == "1" ]]; then
  bash scripts/wp5_case_generation_quality_eval.sh
fi
```

### 9.2 `scripts/wp5_test_design_smoke.sh`

```bash
#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${WP5_SMOKE_BASE_URL:-http://127.0.0.1:8080}"
TOKEN="${WP5_SERVICE_TOKEN:-local-test-design-token}"
PROJECT_ID="${WP5_SMOKE_PROJECT_ID:-project-001}"

# 1. 查询 WP5 健康。
# 2. 通过 WP1 管理接口准备并激活项目，解析真实 project resourceId。
# 3. 准备 WP3 需求资产。
# 4. 创建生成任务。
# 5. 查询候选并批量确认。
# 6. 调用 publish-dry-run。
# 7. 正式 publish。
# 8. 查询 WP3 test-cases 和 trace link 确认资产写入。
```

### 9.3 `scripts/wp5_case_generation_quality_eval.sh`

```bash
#!/usr/bin/env bash
set -euo pipefail

TASK="${WP5_MODEL_EVAL_TASK:-case-design}"
CORPUS="${WP5_MODEL_EVAL_CORPUS:-wp5-case-generation-v1}"

# 1. 加载 golden corpus。
# 2. 使用固定 promptKey/promptVersion 调用生成器。
# 3. 计算覆盖率、断言完整性、重复率、优先级准确率和敏感泄露。
# 4. 任一 P0 阈值不达标时退出非 0。
```

脚本实现阶段必须避免打印完整需求原文、模型输入、token 和密钥。

## 10. 可测试性建议

| 对象 | 建议 |
|---|---|
| 服务端 | 任务执行和模型调用解耦，模型调用接口可注入 fake provider，方便稳定测试。 |
| 服务端 | 候选状态流集中在单一 domain/service 中，避免 controller 分散判断。 |
| 服务端 | 发布 dryRun 和正式发布复用同一计划生成逻辑，减少预览和执行不一致。 |
| 服务端 | 所有批量接口返回逐条结果，便于定位部分成功。 |
| 前端 | API client 类型完整，错误态统一抽取 message 和 traceId。 |
| 前端 | 候选编辑器保留本地草稿，版本冲突时不丢用户输入。 |
| 安全 | 日志和导出使用白名单字段，不使用对象整体序列化。 |
| AI | golden set、promptKey、promptVersion 和 parserVersion 必须绑定，便于回归对比。 |

## 11. 准出命令

WP5 实现阶段默认准出入口：

```bash
mvn -B -pl platform-api test
cd portal-web && npm test
cd portal-web && npm run build
bash db/validation/run_wp1_db_validation.sh
bash scripts/wp5_quality_gate.sh
```

按影响面追加：

```bash
WP5_RUN_HTTP_SMOKE=1 bash scripts/wp5_quality_gate.sh
WP5_RUN_HTTP_SMOKE=external WP5_SMOKE_BASE_URL=http://127.0.0.1:8080 bash scripts/wp5_quality_gate.sh
WP5_RUN_AI_EVAL=1 bash scripts/wp5_quality_gate.sh
```

若只修改前期文档，可使用：

```bash
git diff --check
```

并说明未执行代码级验证的原因。
