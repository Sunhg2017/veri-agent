# WP5 AI 用例生成与评审 - 研发任务拆解

| 项目 | 内容 |
|---|---|
| 工作包 | WP5 AI 用例生成与评审 |
| 角色产出 | 资深项目经理 |
| 文档性质 | 前期研发拆解、范围控制、里程碑和风险管理文档 |
| 当前口径 | 单个 `platform-api` Java 服务承载 WP1-WP5 领域模块；WP5 依赖 WP2 模型接入、WP3 测试资产、WP4 需求与文档输入 |
| 版本 | v0.1 |
| 日期 | 2026-05-25 |

## 1. 目标

WP5 目标是跑通“已确认需求资产 -> AI 生成测试场景和用例草案 -> 人工评审 -> 写入 WP3 测试用例资产 -> 建立追踪关系”的最小闭环。

本工作包不追求一次生成可执行自动化脚本，而是先把测试设计效率、评审质量和资产入库链路建立起来，为 WP6 接口自动化、WP7 UI/E2E、WP9 执行调度和 WP10 报告诊断提供稳定输入。

## 2. 范围

| 范围项 | 说明 |
|---|---|
| 生成任务 | 用户可基于项目、需求、API、页面、业务流和生成策略创建 AI 用例生成任务。 |
| 上下文装配 | 从 WP3 读取已确认需求、关联 API、页面、业务流和历史用例摘要；从 WP4 保留需求来源信息；通过 WP2 调用受控 Prompt。 |
| 用例候选 | 生成测试场景、用例标题、前置条件、步骤、预期结果、优先级、标签、覆盖依据和风险说明。 |
| 人工评审 | 支持候选编辑、确认、驳回、忽略、批量操作、重复检测和版本冲突保护。 |
| 写入 WP3 | 已确认候选写入 WP3 `TestCase`，默认 `source=AI_GENERATED`，保留 `sourceRef` 和模型调用追踪，并建立需求/API 追踪关系。 |
| 质量门禁 | 对模型输出结构、覆盖率、重复率、空步骤、断言完整性、权限覆盖和敏感信息泄露进行校验。 |
| 前端工作台 | 增加 WP5 用例生成入口，覆盖任务创建、候选评审、生成详情、入库结果和错误排查。 |
| 观测与审计 | 记录生成任务、模型调用、候选操作、入库动作、失败原因、traceId、promptKey/promptVersion 和成本摘要。 |

## 3. 非目标

| 非目标 | 说明 | 后续承接 |
|---|---|---|
| 可执行脚本生成 | 不生成 Pytest、Playwright、Postman、Karate 等可执行脚本。 | WP6、WP7 |
| 测试执行和调度 | 不创建执行计划、不触发任务、不采集执行结果。 | WP9 |
| 失败诊断和报告 | 不输出执行报告、缺陷草稿或发布风险结论。 | WP10 |
| 原型真实连接器 | 不新增 Figma、蓝湖、Axure 真实账号级连接器。 | WP12 或原型连接器专项 |
| 完全自动入库 | 不允许模型结果绕过人工评审直接写入 APPROVED 用例。 | 后续可在低风险项目灰度策略 |
| 多租户改造 | 不恢复租户表或租户字段。 | 不适用 |

## 4. 涉及模块

| 模块 | 影响 |
|---|---|
| `platform-api` | 新增 `testdesign` 或 `casegeneration` 领域模块；复用统一 envelope、权限、审计、错误码、分页和 MyBatis XML 规范。 |
| WP1 平台基础 | 使用项目上下文、资源作用域权限、审计写入、敏感级别和公开模型策略。 |
| WP2 模型接入 | 使用 `ModelAccessService` 调用 `wp5-test-case-design` Prompt，记录模型调用、预算、敏感内容阻断和 provider 信息。 |
| WP3 资产管理 | 读取需求/API/页面/业务流/历史用例；写入测试用例和追踪关系，不直连 WP3 表。 |
| WP4 文档输入 | 使用已发布需求的来源、验收标准和原文引用摘要作为生成依据，不直接读取导入原文快照。 |
| `portal-web` | 新增“用例生成”页面或资产库内入口；接入权限、loading/empty/error、traceId 和评审操作状态。 |
| `db/migration/wp1` | 新增 WP5 生成任务、候选、评审记录和评测样本表；保留前滚修复策略。 |
| `scripts` | 新增 WP5 quality gate、HTTP smoke 和 AI 生成质量评测脚本。 |

## 5. 权限和角色口径

建议新增 WP5 权限点：

| 权限点 | 用途 | 默认角色建议 |
|---|---|---|
| `testDesign:read` | 查看生成任务、候选和评测结果 | SuperAdmin、PlatformAdmin、ProjectOwner、AppOwner、Tester、Developer、Auditor |
| `testDesign:generate` | 创建生成任务、重试失败任务、触发模型生成 | SuperAdmin、PlatformAdmin、ProjectOwner、AppOwner、Tester |
| `testDesign:review` | 编辑、确认、驳回、忽略候选 | SuperAdmin、PlatformAdmin、ProjectOwner、AppOwner、Tester |
| `testDesign:publish` | 将确认候选写入 WP3 用例资产 | SuperAdmin、PlatformAdmin、ProjectOwner、AppOwner、Tester |
| `testDesign:export` | 导出候选、评审记录和质量评估摘要 | SuperAdmin、PlatformAdmin、ProjectOwner、Auditor |

后端接口必须以权限校验为准，前端菜单和按钮隐藏只用于体验优化。

## 6. Epic 拆解

### Epic 1：契约、权限和数据模型冻结

| Story | 优先级 | 交付物 | 验收标准 |
|---|---|---|---|
| WP5-1.1 | P0 | 权限点、审计事件、状态机和 API 草案 | 五角色评审通过；字段使用 camelCase；分页使用 `index/size`。 |
| WP5-1.2 | P0 | 生成任务、候选、评审记录、发布记录和评测样本表设计 | 不含 `tenant_id`；具备项目索引、状态索引、幂等索引和模型调用追踪字段。 |
| WP5-1.3 | P0 | Prompt key、输出 JSON Schema 和模型降级策略 | WP2 策略阻断时任务进入可解释失败或规则模板 fallback，不产生不可追踪候选。 |

### Epic 2：生成任务和上下文装配

| Story | 优先级 | 交付物 | 验收标准 |
|---|---|---|---|
| WP5-2.1 | P0 | 创建任务、查询任务、取消任务、重试任务 API | 越权项目拒绝；重复任务可幂等识别；失败返回 traceId 和稳定错误码。 |
| WP5-2.2 | P0 | WP3 上下文读取适配器 | 只通过 WP3 应用服务读取需求/API/页面/业务流/用例摘要；不直连表。 |
| WP5-2.3 | P0 | 上下文裁剪和脱敏策略 | 控制模型输入长度；敏感字段脱敏；记录 inputDigest 和上下文摘要。 |

### Epic 3：AI 用例生成和质量校验

| Story | 优先级 | 交付物 | 验收标准 |
|---|---|---|---|
| WP5-3.1 | P0 | 调用 WP2 `wp5-test-case-design` Prompt | 保存 modelInvocationId、provider、model、promptVersion、cost、latency。 |
| WP5-3.2 | P0 | 输出结构校验和候选生成 | 非法 JSON、空标题、空步骤、无预期结果必须阻断候选落库或标记失败。 |
| WP5-3.3 | P0 | 规则模板 fallback | 模型失败时可生成低置信度模板候选或任务失败；不伪装为模型输出。 |
| WP5-3.4 | P1 | 覆盖率和重复率评估 | 输出需求覆盖、API 覆盖、异常场景覆盖和重复候选摘要。 |

### Epic 4：人工评审和 WP3 入库

| Story | 优先级 | 交付物 | 验收标准 |
|---|---|---|---|
| WP5-4.1 | P0 | 候选列表、详情、编辑、确认、驳回、忽略、批量操作 API | 支持 `status/requirementId/priority/source/keyword` 筛选；批量操作有版本号保护。 |
| WP5-4.2 | P0 | 发布到 WP3 测试用例 | 写入 `DRAFT` 或 `REVIEWING` 用例；保留 `source=AI_GENERATED`、`sourceRef`、需求/API 关联和步骤。 |
| WP5-4.3 | P0 | 发布 dryRun 和冲突处理 | 已存在相同 `sourceRef` 或高相似用例时返回 `LINK_EXISTING`、`UPDATE` 或 `DUPLICATE_REVIEW_REQUIRED`。 |
| WP5-4.4 | P1 | 评审意见回流 | 保存人工编辑差异，形成后续 golden set 和 Prompt 优化样本。 |

### Epic 5：前端工作台

| Story | 优先级 | 交付物 | 验收标准 |
|---|---|---|---|
| WP5-5.1 | P0 | 新增用例生成导航和页面骨架 | 无读权限不显示入口；直接访问受后端鉴权保护。 |
| WP5-5.2 | P0 | 任务创建表单 | 支持选择项目、需求、策略、生成数量、覆盖维度和模型开关提示。 |
| WP5-5.3 | P0 | 候选评审列表和详情 | loading/empty/error、traceId、批量操作、编辑态保存和冲突提示完整。 |
| WP5-5.4 | P0 | 发布预览和入库结果 | 显示 dryRun 结果、重复风险、写入 WP3 的用例链接和失败明细。 |

### Epic 6：测试、脚本和准出

| Story | 优先级 | 交付物 | 验收标准 |
|---|---|---|---|
| WP5-6.1 | P0 | 后端单元、契约和集成测试 | `mvn -B -pl platform-api test` 覆盖主要状态、权限、模型 fallback 和 WP3 发布。 |
| WP5-6.2 | P0 | 前端测试和构建 | `cd portal-web && npm test`、`npm run build` 通过。 |
| WP5-6.3 | P0 | `scripts/wp5_quality_gate.sh` | 串联后端测试、前端测试、构建、DB validation、HTTP smoke 和 AI 质量评测；日常开发默认可跳过 smoke/AI 评测，`WP5_GATE_MODE=release` 或 `WP5_RELEASE_GATE=1` 发布准出模式必须显式启用 managed/external HTTP smoke，并设置 `WP5_RUN_AI_EVAL=1` 跑 golden set 基线。 |
| WP5-6.4 | P0 | `scripts/wp5_test_design_smoke.sh` | 已启动后端时可完成生成任务、候选评审、dryRun 和发布到 WP3 的主链路。 |
| WP5-6.5 | P1 | `scripts/wp5_case_generation_quality_eval.sh` | 用 golden set 度量覆盖率、断言完整性、重复率和有效步骤比例。 |

## 7. 里程碑

| 里程碑 | 目标 | 主要产出 | 准出标准 |
|---|---|---|---|
| M0 契约冻结 | 明确做什么、怎么存、怎么调用、怎么验收 | PRD、技术设计、接口契约、测试计划、前端设计 | 五角色评审无阻断；风险和非目标明确。 |
| M1 生成任务闭环 | 用户能创建任务并获得候选 | 任务 API、上下文装配、模型调用、候选落库 | 模型成功/失败/fallback 均可追踪。 |
| M2 评审入库闭环 | 候选可人工确认并写入 WP3 | 候选评审 API、发布 dryRun、WP3 入库和追踪关系 | 不重复入库，不越权，不绕过 WP3 状态流。 |
| M3 前端可用 | 管理台可完成主流程 | 用例生成工作台、候选评审、发布预览、错误态 | 主要用户路径可被测试脚本覆盖。 |
| M4 MVP 准出 | 文档、代码、脚本和验收材料齐备 | quality gate、smoke、AI 质量评测、发布准出记录 | 默认验证和 WP5 专项验证全部通过；发布准出必须执行 `WP5_GATE_MODE=release`，且 HTTP smoke 与 `WP5_RUN_AI_EVAL=1` golden set 基线均通过，除非有明确风险豁免和替代环境证据。 |

## 8. 风险与应对

| 风险 | 影响 | 触发信号 | 应对 |
|---|---|---|---|
| AI 输出质量不稳定 | 候选不可用，用户信任下降 | 空步骤、重复用例、断言泛化、覆盖遗漏 | 输出 JSON Schema 校验、规则 fallback、golden set 评测、人工评审必经。 |
| 上下文过长或敏感 | 成本升高，合规风险 | 模型输入超限、包含 token/隐私/生产数据 | 上下文裁剪、摘要、脱敏、WP2 敏感阻断和公开模型策略继承。 |
| WP3 入库重复或覆盖人工内容 | 资产污染 | 同一需求多次生成重复用例 | sourceRef 幂等、高相似重复检测、dryRun 预览、人工确认。 |
| 权限边界不清 | 越权生成或查看候选 | 跨项目查询、未授权发布 | 后端资源作用域校验；前端仅做体验层隐藏。 |
| 范围膨胀到脚本生成或执行 | 工期失控 | 要求生成 Playwright/Pytest 并执行 | 坚持 WP5 只产出测试用例资产，脚本生成进入 WP6/WP7。 |
| 模型调用成本失控 | 预算超限 | 大批量需求生成、重复重试 | 生成数量限制、任务幂等、WP2 预算策略、成本摘要展示。 |

## 9. 验收标准

1. WP5 前期文档已覆盖研发拆解、需求/PRD、技术设计与接口契约、前端页面设计、测试策略和用例脚本建议。
2. MVP 范围清晰限定为用例生成与评审入库，不包含脚本生成、执行调度和报告诊断。
3. 所有接口草案符合统一 envelope、camelCase、`index/size` 分页和 WP1 权限审计口径。
4. 所有模型调用仅通过 WP2，所有资产读写仅通过 WP3 应用服务，所有需求来源依赖 WP4 已发布资产。
5. 人工评审是候选写入 WP3 的必经步骤，模型结果不得直接发布为 APPROVED 用例。
6. 有明确的质量门禁、smoke、AI 质量评测和回归验证入口。
7. 后续代码实现涉及数据库、权限、审计、模型调用、文档输入来源或跨 WP 发布时，必须参考 `doc/mvp/final/engineering/WP1-WP4-统一发布准出清单.md` 扩大准出验证。

## 10. 回滚方式

前期文档阶段的回滚方式是还原本组 WP5 文档变更。后续代码阶段回滚遵循以下原则：

1. 数据库迁移按前滚修复优先，破坏性回滚需 DBA 审核。
2. 可通过 feature flag 关闭 WP5 前端入口和生成任务创建能力。
3. 已写入 WP3 的用例保留审计与版本历史，不批量物理删除；如需撤销，走 WP3 生命周期归档或废弃状态。
4. 模型 Prompt 或策略异常时可关闭 `WP5_MODEL_GENERATION_ENABLED`，保留人工用例维护能力。
