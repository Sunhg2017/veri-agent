# WP4 高保真解析专项评估

| 项目 | 内容 |
|---|---|
| 覆盖任务 | `WP4-B5 高保真解析专项` |
| 适用阶段 | P2 专项评估与后续 worker/解析器选型 |
| 当前结论 | 评估方案已冻结；当前不改变 WP4 Word/PDF/OCR 文本抽取链路 |
| 日期 | 2026-05-23 |

## 1. 目标、范围与非目标

目标：

1. 明确 Word/PDF/OCR 从“文本抽取”演进到“结构/版式/多模态线索保留”的能力边界。
2. 给出表格结构、图片语义、页眉页脚、批注/修订、附件抽取的分层路线、样本集和评测指标。
3. 保证后续引入专业解析组件或异步 worker 时，不破坏当前 `/api/v1/document-input/imports`、候选确认、WP2 模型辅助解析和 WP3 发布链路。

范围：

- 评估当前 `DocumentContentExtractor` 的 Word/PDF/OCR 文本抽取基线。
- 定义高保真解析的能力分层、数据契约建议、worker 边界、样本集和准出命令。
- 说明 P2 高保真能力如何与现有 golden corpus、人工确认、traceId、审计和错误体验衔接。

非目标：

- 不在本轮实现表格高保真抽取、图片理解、页眉页脚去重、批注/修订读取或附件落库。
- 不新增数据库迁移、外部依赖、异步队列、对象存储或 worker 镜像。
- 不承诺高保真解析自动发布需求；所有解析结果仍必须进入候选确认。

回滚方式：本轮只新增评估文档并更新里程碑状态，回滚相应文档提交即可；运行时行为无变化。

## 2. 当前解析基线

当前 WP4 已具备以下能力：

| 类型 | 当前实现 | 已有保护 |
|---|---|---|
| Word | 使用 Apache POI 抽取 doc/docx 文本，支持 base64、data URL 和 multipart 上传入口 | MIME/魔数校验、二进制大小上限、恶意文件扫描预留、解析失败可读错误 |
| PDF | 使用 PDFBox 抽取文本型 PDF；无文本时可转 OCR 或给出扫描件提示 | PDF 页数上限、解析耗时上限、MIME/魔数校验、扫描 PDF 明确失败或走 OCR |
| OCR | 支持本地命令 OCR 和 HTTP 隔离 worker；生产可关闭本地 fallback | timeout、并发上限、最大输出长度、worker token 脱敏、健康摘要 |
| AI 解析质量 | `wp4-ai-parse-eval` corpus 覆盖 TEXT/MARKDOWN/WORD/PDF/OCR/CUSTOM_API 六类输入 | 标题召回、优先级准确率、验收标准覆盖率、行业/难度/覆盖标签约束 |

当前不保留复杂版式结构。现有 `ExtractedDocumentContent.text` 是后续规则解析和模型解析的输入，因此当前链路对表格、图片、页眉页脚、批注和附件只保留为普通文本或完全丢弃。

## 3. 高保真能力分层

| 层级 | 名称 | 能力 | 是否进入当前完成口径 |
|---|---|---|---|
| L0 | 文本抽取 | 抽取可复制文本或 OCR 文本，进入现有候选解析 | 已完成 |
| L1 | 结构保留 | 保留章节、列表、表格行列、页码、页眉页脚标记、批注摘要、附件清单 | 本专项推荐的第一阶段 |
| L2 | 版式定位 | 保留页面坐标、块级阅读顺序、表格单元格坐标、图片区域、旋转页和跨页表格关系 | 第二阶段，建议异步 worker |
| L3 | 多模态语义 | 对图片、截图、流程图、手写批注做语义理解，并与文本候选合并 | 第三阶段，需模型和人工复核闭环 |

推荐先落 L1，再按样本和业务收益评估 L2/L3。L2/L3 不应进入 `platform-api` 同步请求内执行，避免复杂文件拖垮主服务。

## 4. 能力专项边界

### 4.1 表格结构

当前问题：

- Word/PDF 表格会被压平成文本，行列关系和表头语义不稳定。
- 跨页表格、合并单元格、金额/状态矩阵容易被规则解析误判。

L1 建议：

- 将 Word 表格输出为 Markdown table 或结构化 `tables[]`。
- 表格片段进入 `sourceFragment`，候选 tags 增加 `table` 或业务领域标签。
- 保留表头、行号、列名、单元格文本、页码/章节路径。

L2 建议：

- PDF 表格由 worker 输出 cell bounding box、page number、row/column span。
- 跨页表格通过相同表头、连续页码和坐标规则合并。

验收样本：

- Word 合并单元格审批矩阵。
- PDF 跨页结算表。
- Markdown/Word/PDF 同一表格语义对齐样本。

### 4.2 图片语义

当前问题：

- Word 内嵌图片、PDF 截图、扫描件中的流程图语义不进入候选。
- OCR 只能返回文字，不能说明图片代表页面、流程图、状态机还是签章。

L1 建议：

- 抽取图片清单：序号、所在页/段落、尺寸、MIME、摘要 digest。
- 对图片不做语义理解时，在候选或导入记录中提示“存在未解析图片”。

L2/L3 建议：

- 图片语义理解走异步 worker：先 OCR/版式检测，再由受控模型生成图片说明。
- 图片说明只能作为候选补充，不得绕过人工确认。
- 图片二进制落对象存储或受控临时区，数据库只保存引用和 digest。

验收样本：

- Word 中嵌入登录流程图。
- PDF 中嵌入页面截图和标注。
- OCR 图片包含手写异常说明。

### 4.3 页眉页脚

当前问题：

- 页眉页脚可能重复进入正文，导致候选重复或污染标题。
- 页脚页码、保密声明、模板编号可能被误识别为需求。

L1 建议：

- Word 解析显式标记 header/footer 文本。
- PDF 解析通过重复位置、重复文本、页码模式和短文本规则识别疑似页眉页脚。
- 默认从需求候选正文中降权或排除页眉页脚，但在原文片段中保留可追溯标记。

验收样本：

- 带公司保密页眉的 Word 需求。
- 带模板编号和页码页脚的 PDF。
- 页眉中含真实业务模块名的边界样本。

### 4.4 批注、修订与附件

当前问题：

- Word 批注和修订记录通常承载评审意见、审批人、待确认项。
- 附件可能包含补充接口清单、截图或测试数据，但当前链路不抽取。

L1 建议：

- 批注输出为 `comments[]`，包含作者、时间、锚点文本、批注内容摘要。
- 修订输出为 `revisions[]`，包含插入/删除/格式变更类型和脱敏摘要。
- 附件输出为 `attachments[]` 清单，包含文件名、MIME、大小、digest、是否已解析。
- 批注、修订和附件默认只作为候选上下文，不自动生成已确认需求。

L2 建议：

- 附件解析走递归 worker，并设置最大递归深度、总大小、总文件数和超时。
- 附件内容与主文档分别生成 sourceRef，发布到 WP3 时保留父子来源关系。

验收样本：

- Word 批注含审批意见。
- Word 修订中删除旧验收标准、插入新标准。
- Word/PDF 附件包含 API 清单。

## 5. 建议数据契约

当前 `ExtractedDocumentContent` 可保持兼容；后续新增结构化解析时建议扩展为旁路 metadata，不强迫现有解析器立即消费：

```json
{
  "text": "归一后的文本",
  "sourceKind": "WORD",
  "metadataVersion": "wp4-parse-metadata-v1",
  "sections": [
    {
      "path": "1. 登录需求",
      "pageStart": 1,
      "pageEnd": 2,
      "textDigest": "sha256:..."
    }
  ],
  "tables": [
    {
      "id": "tbl-1",
      "sectionPath": "2. 审批矩阵",
      "pageStart": 3,
      "headers": ["角色", "审批动作", "SLA"],
      "rows": [["Owner", "Approve", "4h"]],
      "sourceFragment": "| 角色 | 审批动作 | SLA |"
    }
  ],
  "images": [
    {
      "id": "img-1",
      "page": 4,
      "mimeType": "image/png",
      "digest": "sha256:...",
      "semanticStatus": "NOT_PARSED"
    }
  ],
  "comments": [
    {
      "anchorText": "高风险操作",
      "author": "masked:user",
      "text": "需要补充二次确认"
    }
  ],
  "attachments": [
    {
      "fileName": "api-list.xlsx",
      "mimeType": "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
      "digest": "sha256:...",
      "parseStatus": "SKIPPED"
    }
  ],
  "warnings": [
    {
      "code": "IMAGE_NOT_PARSED",
      "message": "文档包含 2 张未解析图片"
    }
  ]
}
```

兼容规则：

1. `text` 仍是现有规则解析和模型解析的主输入。
2. `metadataVersion` 缺失时按当前 L0 文本抽取处理。
3. `warnings` 必须透传到导入记录或候选上下文，前端展示可操作提示和 `traceId`。
4. 结构化 metadata 不得包含密钥、完整本地路径、外部系统 token 或不可脱敏个人敏感信息。

## 6. 引擎与架构路线

| 方案 | 优点 | 风险 | 建议结论 |
|---|---|---|---|
| 在 `platform-api` 内扩展 POI/PDFBox | 复用当前依赖，开发和测试成本低 | 复杂版式、图片语义和附件递归会增加主服务 CPU/内存风险 | 只用于 L1 的轻量结构抽取 |
| 独立解析 worker | 可隔离 CPU/内存/崩溃风险，可按文件类型扩展引擎 | 需要队列、对象存储、超时、重试和部署运维 | L2/L3 推荐路线 |
| 专业文档解析服务 | 表格、版式和图片语义能力更强 | 成本、数据出境、敏感内容、可观测和供应商锁定风险 | 仅在私有化或合规评审通过后接入 |
| 纯模型多模态解析 | 对图片和复杂语义友好 | 成本高、可重复性弱、幻觉风险，需要人工确认 | 只作为候选辅助，不作为权威解析 |

推荐架构：

1. L1 在现有抽取器上增量扩展，并保持同步请求的大小、页数和耗时上限。
2. L2/L3 新增异步 `document_parse_job` 或复用后续任务框架；`platform-api` 只负责创建任务、查询状态、接收 metadata 和写审计。
3. 大文件、附件和图片二进制进入对象存储或受控临时存储，数据库只保存 digest、引用和脱敏摘要。
4. 解析 worker 结果必须进入候选确认，发布 WP3 时只写已确认字段和来源追踪。

## 7. 评测样本集

建议新增 `wp4-high-fidelity-corpus`，至少覆盖：

| 样本 | 类型 | 覆盖点 | 期望 |
|---|---|---|---|
| `word-approval-merged-table` | WORD | 合并单元格、审批矩阵、批注 | 表头/行列关系可还原，批注不丢失 |
| `word-revisions-attachment` | WORD | 修订、附件清单 | 修订摘要可追踪，附件只保存 digest 和清单 |
| `pdf-settlement-cross-page-table` | PDF | 跨页表格、重复页眉页脚 | 表格连续性识别，页眉页脚不污染候选 |
| `pdf-rotated-page-screenshot` | PDF | 旋转页、截图、图注 | 页面阅读顺序稳定，图片进入未解析 warning |
| `ocr-handwritten-exception` | OCR | 手写备注、低置信度 | 候选保留低置信度标签，必须人工确认 |
| `mixed-doc-with-nested-appendix` | WORD/PDF | 附件、递归限制 | 附件超限时给出 warning，不阻断主文档文本抽取 |

样本元数据建议包含：

- `caseId`
- `sourceType`
- `industry`
- `difficulty`
- `coverageTags`
- `fixtureFile`
- `expectedTables`
- `expectedComments`
- `expectedWarnings`
- `expectedRequirements`

## 8. 评测指标

| 指标 | 阈值建议 | 说明 |
|---|---|---|
| `text_recall` | `>= 0.95` | 关键需求文本召回率 |
| `table_header_accuracy` | `>= 0.90` | 表头识别准确率 |
| `table_cell_recall` | `>= 0.85` | 核心单元格召回率 |
| `header_footer_suppression` | `>= 0.90` | 重复页眉页脚正确降权或排除比例 |
| `comment_anchor_recall` | `>= 0.80` | 批注锚点与文本关联召回 |
| `attachment_inventory_accuracy` | `>= 0.95` | 附件文件名、MIME、大小、digest 清单准确率 |
| `warning_precision` | `>= 0.90` | 未解析图片/附件/低置信度 warning 准确率 |
| `no_auto_publish` | `= 1.00` | 高保真/多模态结果不得绕过候选确认 |

质量门禁建议新增脚本：

```bash
bash scripts/wp4_high_fidelity_parse_eval.sh
```

该脚本后续应先执行 fixtures 解析，再检查 metadata、warnings 和候选字段。低于阈值时阻断解析器或 worker 变更。

## 9. 安全与合规边界

1. 图片、附件和原文快照可能包含敏感数据，默认不在 API 响应、日志和审计中回显原文或二进制。
2. 外部解析服务必须通过 WP1 SecretProvider 管理凭证，不能在 WP4 配置中保存明文。
3. 附件递归解析必须限制总大小、文件数、递归深度和总耗时。
4. 解析失败摘要不得包含本地文件路径、worker endpoint、token、secretRef 完整值或用户隐私原文。
5. 高保真 metadata 进入模型解析前必须做长度限制和敏感内容检测，继续复用 WP2 模型策略、预算和调用审计。

## 10. 验收口径

本专项评估完成后的验收标准：

1. 已明确当前 L0 文本抽取边界，不把高保真能力误宣称为已上线。
2. 已定义表格结构、图片语义、页眉页脚、批注/修订、附件抽取的 L1/L2/L3 路线。
3. 已给出兼容现有 `text` 输入的 metadata 契约建议，后续不会破坏当前候选确认和 WP3 发布链路。
4. 已定义样本集、指标和后续 quality gate 脚本入口建议。
5. 已参考 `WP1-WP4-统一发布准出清单.md`：本轮仅文档和里程碑状态变更，无运行时、数据库、权限 seed 或前端行为变更；后续一旦新增解析代码、worker 或 schema，必须追加 `mvn -B -pl platform-api test`、`bash scripts/wp4_binary_document_smoke.sh`、`bash scripts/wp4_ai_parse_quality_eval.sh` 和新增高保真评测脚本。

## 11. 后续落地建议

1. 第一阶段扩展 Word 表格、批注和页眉页脚 metadata，同时保持 `text` 兼容。
2. 第二阶段建立 `wp4-high-fidelity-corpus` fixtures 和 `scripts/wp4_high_fidelity_parse_eval.sh`。
3. 第三阶段把 PDF 表格/版式和图片语义放入隔离 worker，不在 `platform-api` 同步解析路径内执行。
4. 第四阶段将人工纠错样本与高保真 corpus 绑定，形成结构化解析和模型辅助解析的双门禁。
