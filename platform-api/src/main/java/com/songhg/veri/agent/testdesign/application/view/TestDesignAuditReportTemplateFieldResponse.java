package com.songhg.veri.agent.testdesign.application.view;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Whitelisted audit report template field.
 */
public record TestDesignAuditReportTemplateFieldResponse(
        @Schema(description = "字段编码")
        String code,
        @Schema(description = "字段名称")
        String label,
        @Schema(description = "字段来源")
        String source,
        @Schema(description = "导出模式")
        String exportMode,
        @Schema(description = "是否必填")
        boolean required,
        @Schema(description = "是否导出标识原值")
        boolean identifierValueExported,
        @Schema(description = "是否导出 payload 或正文")
        boolean payloadExported
) {
}
