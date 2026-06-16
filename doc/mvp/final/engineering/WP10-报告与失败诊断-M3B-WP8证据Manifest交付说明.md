# WP10 报告与失败诊断 - M3B WP8 证据 Manifest 交付说明

| 项目 | 内容 |
|---|---|
| 工作包 | WP10 报告与失败诊断 |
| 交付阶段 | M3B WP8 evidence manifest |
| 覆盖 Story | WP10-3.1、WP10-3.2、WP10-3.5 |
| 当前口径 | 从 WP9 节点摘要识别 WP8 引用，通过 WP8 跨 WP 契约生成 aggregate-only manifest |
| 日期 | 2026-06-17 |

## 1. 需求结论

本轮 WP10 推进完成 M3B 后端切片：`platform-api` 在报告生成和 FAILED 重试链路中，从 WP9 脱敏 run export 的节点摘要识别 WP8 引用，并通过 `TestDataCrossWpReferenceService#reportEvidence` 获取 WP8 聚合证据，最终持久化并返回 `sourceWp=WP8` evidence manifest。

本轮不实现 WP3/WP5 evidence adapter、规则/AI 失败诊断、缺陷草稿、JSON/Markdown 导出和 `portal-web` 报告工作台。当前真实 WP9 账号租借链路稳定产出 `accountLeaseRef`，因此验收重点覆盖账号租借证据；`dataSetRef` 和 `cleanupTaskRef` 入口已按同一白名单预留，等待后续节点摘要稳定产出。

## 2. 范围和非目标

| 类型 | 内容 |
|---|---|
| 目标 | 从 WP9 `resultSummary` 白名单识别 `dataSetRef(s)`、`testDataSetRef(s)`、`accountLeaseRef(s)`、`cleanupTaskRef(s)` 和 `testDataCleanupTaskRef(s)`。 |
| 目标 | 通过 WP8 应用层契约读取 refs、状态、计数、digest、summary keys 和脱敏账号摘要。 |
| 目标 | 生成 `TEST_DATA_SET`、`ACCOUNT_LEASE`、`CLEANUP_TASK` 三类 WP8 manifest，并统一受 `maxEvidenceItems` 限制。 |
| 非目标 | 不直连 WP8 表，不读取 record payload、cleanup payload、secretRef 原文、lease token 或账号凭据。 |
| 非目标 | 不改变 WP8/WP9 状态机，不补做 WP3/WP5 证据聚合，不启动诊断/导出/前端专项。 |

## 3. 主要变更

| 模块 | 变更 |
|---|---|
| `ReportService` | 注入可选 WP8 cross-WP service；从 WP9 节点摘要抽取 WP8 refs；通过 WP8 契约校验项目 scope 和引用有效性。 |
| Evidence manifest | 新增 `sourceWp=WP8`、`sourceType=TEST_DATA_SET/ACCOUNT_LEASE/CLEANUP_TASK` manifest 生成逻辑。 |
| 安全脱敏 | WP8 manifest 只保存 digest、状态、计数、summary keys、scope summary key、时间戳和 redaction flags；账号租借 holderRef、accountRef、accountPoolRef 均为 digest。 |
| Health | 新增 `wp8EvidenceManifestReady=true`，同时保留 `evidenceAggregationReady=false` 表示 WP3/WP5 与完整 redaction scan 未完成。 |
| 测试 | `ReportControllerTest` 覆盖 WP8 account lease manifest、幂等回放不重复调用 WP8、敏感字段不出现在响应中。 |
| 文档 | 更新 WP10 技术契约、研发任务拆解和本交付说明。 |

## 4. 验收标准

1. 生成 READY 报告后，详情响应包含 WP9 节点 manifest 和已识别 WP8 引用的 manifest。
2. 重复 `executionRunId + requestKey` 返回既有 report 和已持久化 manifest，不重复调用 WP9 export 或 WP8 evidence contract。
3. WP8 引用即使因 `maxEvidenceItems` 无法落入 manifest，也必须先经过 WP8 契约做项目 scope 和引用有效性校验。
4. WP8 manifest 不包含 `accountLeaseRef` 原文、holderRef 原文、账号 key/displayName、scopeSummary 值、secret/token/cookie/password/Authorization、`secret://` 原文、record payload 或 cleanup payload。
5. `GET /api/v1/reports/health` 返回 `wp8EvidenceManifestReady=true`，并继续返回 `evidenceAggregationReady=false`。

## 5. 验证入口

| 命令 | 用途 |
|---|---|
| `mvn -B -pl platform-api -Dtest=ReportControllerTest,ReportingHealthControllerTest test` | 验证 WP8 manifest、幂等、详情权限和 health readiness。 |
| `bash scripts/wp10_report_smoke.sh` | 覆盖 WP10 report smoke、health 和 OpenAPI。 |
| `bash scripts/wp10_quality_gate.sh` | 聚合 WP10 脚本语法、Java 行数门禁、report smoke、后端契约测试和 DB validation。 |
| `bash scripts/platform_api_java_line_guard.sh` | Platform API Java 生产文件行数门禁。 |

本轮涉及 Java 生产代码、跨 WP 契约、权限详情读取和报告持久化，需参考 `doc/mvp/final/engineering/WP1-WP4-统一发布准出清单.md` 并执行与影响面匹配的最小必要验证。

## 6. 风险与回滚

| 风险 | 处置 | 回滚 |
|---|---|---|
| WP9 节点摘要引用字段不稳定 | 使用白名单字段和 UUID 解析，未知或非法值忽略；真实稳定链路先覆盖 `accountLeaseRef` | 回退本次 commit 或暂时不在 WP9 节点摘要输出 WP8 refs |
| WP8 evidence 泄露敏感字段 | WP10 再次做 manifest 白名单和 digest 化，不保存账号 key/displayName、holderRef 原文或 scopeSummary 值 | 删除对应 report 快照后回退 commit |
| WP8 契约不可用导致报告生成失败 | 仅在存在 WP8 引用时要求 WP8 service 可用；无引用报告仍按 WP9 manifest 生成 | 临时关闭 `veri-agent.reporting.generate-enabled` 或回退本次 commit |
| 被误认为完整 evidence 聚合 | health 保留 `evidenceAggregationReady=false`，文档明确 WP3/WP5 adapter 未完成 | 保持 M3B 后端返回，仅隐藏后续 UI 入口 |

## 7. 五角色结论

| 角色 | 结论 | 说明 |
|---|---|---|
| 资深项目经理 | 通过 | M3B 范围限定 WP8 evidence adapter，不抢跑诊断、导出或前端，回滚路径清晰。 |
| 资深产品经理 | 通过 | 报告详情可展示测试数据/账号租借证据来源和脱敏边界，提升发布准出可追踪性。 |
| 资深服务端架构师 | 通过 | 复用 WP8 应用层跨 WP 契约，不直连 WP8 表；项目 scope 和引用有效性由 WP8 服务校验。 |
| 资深前端工程师 | 无影响 | 本轮未改 `portal-web`；后续页面可继续按 `evidenceManifests` 强类型结构分组展示 WP8/WP9。 |
| 资深质量工程师 | 通过 | 已补 controller smoke 断言 WP8 manifest、幂等复用、health readiness 和敏感字段不回显。 |
