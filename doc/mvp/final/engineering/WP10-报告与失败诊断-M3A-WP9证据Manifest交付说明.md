# WP10 报告与失败诊断 - M3A WP9 证据 Manifest 交付说明

| 项目 | 内容 |
|---|---|
| 工作包 | WP10 报告与失败诊断 |
| 交付阶段 | M3A WP9 evidence manifest |
| 覆盖 Story | WP10-3.1、WP10-3.2、WP10-3.3 |
| 当前口径 | 基于 WP9 脱敏 run export 生成节点级 evidence manifest；报告详情返回 manifest 索引和 redaction flags |
| 日期 | 2026-06-17 |

## 1. 需求结论

本轮 WP10 推进完成 M3A 后端切片：`platform-api` 在 M2 报告生成、FAILED 重试和详情查询链路中持久化并返回 WP9 节点级 `report_evidence_manifest`。

本轮不实现 WP8/WP3/WP5 evidence adapter、规则/AI 失败诊断、缺陷草稿、JSON/Markdown 导出和 `portal-web` 报告工作台。这些继续按 WP10 后续 M3B-M6 推进。

## 2. 主要变更

| 模块 | 变更 |
|---|---|
| `platform-api` | 新增 `ReportEvidenceManifest`、强类型 `ReportEvidenceManifestResponse`，报告详情返回 manifest 列表。 |
| 生成链路 | 从 WP9 `ExecutionRunExportResponse.run.nodes` 生成 `sourceWp=WP9/sourceType=EXECUTION_NODE` manifest。 |
| 持久化 | 扩展 `ReportingRepository`、JDBC/MyBatis adapter 和 local 测试仓储，支持按 report 替换/读取 evidence manifest。 |
| 安全 | manifest 只保存 digest、summary key 白名单、节点状态/类型/attempt/duration 等 aggregate summary；过滤敏感 key，不保存 summary 值。 |
| Health | 新增 `wp9EvidenceManifestReady=true`，同时保留 `evidenceAggregationReady=false` 表示完整跨 WP 聚合仍未完成。 |
| 文档 | 更新 WP10 技术契约、README 索引和本交付说明。 |

## 3. 验收标准

1. 生成 READY 报告后，详情响应包含 WP9 节点级 `evidenceManifests`，数量受 `maxEvidenceItems` 控制。
2. 重复 `requestKey` 返回同一 report 和已持久化 manifest，不再次调用 WP9 export。
3. FAILED 报告重试会替换该 report 下的 WP9 manifest。
4. manifest 不包含 `resultSummary` 值、Authorization、secret、token、cookie、password、externalRunId 明文、errorSummary 或 raw runner payload。
5. `GET /api/v1/reports/health` 准确表达 WP9 manifest 已就绪、完整 evidence aggregation 未就绪。

## 4. 验证入口

| 命令 | 用途 |
|---|---|
| `mvn -B -pl platform-api -Dtest=ReportControllerTest test` | 验证生成、幂等、详情权限和 WP9 manifest 安全过滤。 |
| `bash scripts/wp10_report_smoke.sh` | 覆盖 WP10 report smoke、health 和 OpenAPI。 |
| `bash scripts/wp10_quality_gate.sh` | 聚合 WP10 脚本语法、Java 行数门禁、report smoke、后端契约测试和 DB validation。 |
| `bash scripts/platform_api_java_line_guard.sh` | Platform API Java 生产文件行数门禁。 |

本轮涉及数据库映射、权限详情读取和跨 WP 脱敏证据边界，需参考 `doc/mvp/final/engineering/WP1-WP4-统一发布准出清单.md` 并执行与影响面匹配的最小必要验证。

## 5. 风险与回滚

| 风险 | 处置 | 回滚 |
|---|---|---|
| WP9 export 节点字段变化 | 只消费稳定的节点元数据和 summary key 名称；缺失字段按 null/空列表处理 | 回退本次 commit 或临时关闭 `veri-agent.reporting.generate-enabled` |
| manifest 泄露敏感信息 | 不保存 resultSummary 值，敏感 key 名过滤，测试断言响应不含 Authorization/secret | 删除对应 report 记录级联清理 manifest 并回退 commit |
| 被误认为完整 evidence 聚合 | health 保留 `evidenceAggregationReady=false`，文档明确 WP8/WP3/WP5 adapter 未完成 | 保持 M3A 返回，仅隐藏后续 UI 入口 |

## 6. 五角色结论

| 角色 | 结论 | 说明 |
|---|---|---|
| 资深项目经理 | 通过 | M3A 范围限定 WP9 节点级 manifest，未抢跑诊断/导出/前端，回滚路径清晰。 |
| 资深产品经理 | 通过 | 报告详情开始呈现可追踪 evidence 索引，用户能看到节点状态和脱敏边界。 |
| 资深服务端架构师 | 通过 | 复用 WP9 脱敏 export，不直连 WP9 表；仓储接口可扩展到 WP8/WP3/WP5 adapter。 |
| 资深前端工程师 | 无影响 | 本轮未改 `portal-web`；后续页面可直接展示 `evidenceManifests` 强类型结构。 |
| 资深质量工程师 | 通过 | 已补 controller smoke 断言 manifest 数量、幂等复用和敏感 key 过滤。 |
