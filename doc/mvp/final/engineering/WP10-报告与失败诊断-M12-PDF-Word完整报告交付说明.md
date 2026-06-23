# WP10 报告与失败诊断 - M12 PDF/Word 完整报告交付说明

| 项目 | 内容 |
|---|---|
| 工作包 | WP10 报告与失败诊断 |
| 里程碑 | M12 PDF/Word 完整报告 |
| 当前口径 | 在既有 JSON/Markdown 脱敏导出基础上，补齐 PDF/Word aggregate-only 完整报告、真实文件下载和前端导出入口；不引入原始 runner artifact、外部缺陷写入或附件打包下载 |
| 日期 | 2026-06-23 |

## 1. 本次完成

1. 扩展 `ReportExportService`，支持 `PDF` / `WORD` 导出类型。
2. 新增 `ReportDocumentRenderer`，基于 Apache POI 生成 docx，基于 PDFBox 生成 PDF。
3. 复用既有 `report_export_manifest`、opaque storage 和 `/api/v1/reports/{id}/exports/{exportId}/download` 下载链路，不新建旁路导出体系。
4. 完整报告内容扩大为 report snapshot、summary、evidence manifests、latest diagnosis、defect drafts、redaction policy。
5. 前端 `ReportsWorkbench` 新增 PDF/Word 导出按钮与文件名兜底逻辑。
6. 补齐控制器测试与前端 API 测试，覆盖 PDF/Word 导出与下载。

## 2. 设计边界

1. 完整报告仍是 aggregate-only 脱敏报告，不是原始证据包。
2. 不保存导出正文到数据库，只保存 manifest、digest、redaction policy 和下载文件。
3. 不导出 stdout/stderr、请求响应正文、webhook payload、raw prompt/raw response、secret、token、cookie、Authorization 或跨 WP 原始敏感值。
4. 不新增审批、留存、附件打包或外部分享机制。

## 3. 验证范围

1. `ReportControllerTest` 覆盖 JSON/Markdown/PDF/Word 导出与下载。
2. `portal-web/src/api/reports.test.ts` 覆盖 PDF/Word export normalization 与 endpoint 调用。
3. 仍需在完整准出阶段继续跑 Java 行数门禁、前端 build 和 WP10 质量门禁。

## 4. 五角色结论

| 角色 | 结论 | 说明 |
|---|---|---|
| 资深项目经理 | 通过 | 复用既有 manifest 和下载通道，范围受控，回滚边界清晰。 |
| 资深产品经理 | 通过 | 用户已可获得完整脱敏报告文件，且未越界到原始证据包或外部缺陷系统。 |
| 资深服务端架构师 | 通过 | 后端未新建旁路体系，仍保持 aggregate-only、digest-only 和 opaque storage 约束。 |
| 资深前端工程师 | 通过 | 工作台新增 PDF/Word 导出入口，保持现有权限与下载交互模型。 |
| 资深质量工程师 | 通过 | 已补齐后端/前端定向测试，剩余按准出范围继续执行。 |
