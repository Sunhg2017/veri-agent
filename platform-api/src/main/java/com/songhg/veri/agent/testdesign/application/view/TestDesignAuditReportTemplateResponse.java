package com.songhg.veri.agent.testdesign.application.view;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

/**
 * Fixed WP5 operations audit report template.
 */
public record TestDesignAuditReportTemplateResponse(
        @Schema(description = "所属项目 ID")
        String projectId,
        @Schema(description = "Prompt 模板标识")
        String promptKey,
        @Schema(description = "模板版本")
        String templateVersion,
        @Schema(description = "字段集版本")
        String fieldSetVersion,
        @Schema(description = "模板分区")
        List<TestDesignAuditReportTemplateSectionResponse> sections,
        @Schema(description = "是否支持聚合导出")
        boolean exportSupported,
        @Schema(description = "是否支持跨 WP 脱敏明细报表")
        boolean crossWpDetailReportSupported,
        @Schema(description = "是否支持模型观测聚合钻取")
        boolean modelObservationDrilldownSupported,
        @Schema(description = "是否导出标识原值")
        boolean identifierValuesExported,
        @Schema(description = "是否导出 payload 或正文")
        boolean payloadExported,
        @Schema(description = "是否导出操作者标识")
        boolean actorIdentifierExported,
        @Schema(description = "是否为聚合输出")
        boolean aggregateOnly,
        @Schema(description = "生成时间")
        Instant generatedAt
) {
}
