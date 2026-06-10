package com.songhg.veri.agent.testdesign.application.view;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * Audit report template section with bounded aggregate fields.
 */
public record TestDesignAuditReportTemplateSectionResponse(
        @Schema(description = "模板分区编码")
        String code,
        @Schema(description = "模板分区名称")
        String label,
        @Schema(description = "分区说明")
        String description,
        @Schema(description = "字段白名单")
        List<TestDesignAuditReportTemplateFieldResponse> fields
) {
}
