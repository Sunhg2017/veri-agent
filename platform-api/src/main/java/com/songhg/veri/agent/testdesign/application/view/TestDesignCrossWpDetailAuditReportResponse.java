package com.songhg.veri.agent.testdesign.application.view;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

/**
 * Cross-WP redacted detail audit report.
 */
public record TestDesignCrossWpDetailAuditReportResponse(
        @Schema(description = "所属项目 ID")
        String projectId,
        @Schema(description = "Prompt 模板标识")
        String promptKey,
        @Schema(description = "模板版本")
        String templateVersion,
        @Schema(description = "明细行数")
        long rowCount,
        @Schema(description = "脱敏明细行")
        List<TestDesignCrossWpAuditDetailRowResponse> rows,
        @Schema(description = "是否支持脱敏明细报表")
        boolean detailReportSupported,
        @Schema(description = "是否导出审计事件原始明细")
        boolean rawAuditEventExported,
        @Schema(description = "是否导出标识原值")
        boolean identifierValuesExported,
        @Schema(description = "是否导出 traceId 原值")
        boolean traceIdValueExported,
        @Schema(description = "是否导出模型调用 ID 原值")
        boolean modelInvocationIdValueExported,
        @Schema(description = "是否导出发布 sourceRef 或资产 ID")
        boolean publishIdentifierValueExported,
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
