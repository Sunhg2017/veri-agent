package com.songhg.veri.agent.testdesign.application.view;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/**
 * Aggregate line-integrity storage summary for one report archive.
 */
public record TestDesignReportArchiveIntegrityResponse(
        @Schema(description = "归档记录 ID")
        UUID archiveId,
        @Schema(description = "报告行数")
        long reportRowCount,
        @Schema(description = "已索引行数")
        long indexedRowCount,
        @Schema(description = "摘要算法")
        String digestAlgorithm,
        @Schema(description = "是否存储链式完整性索引")
        boolean chainIntegrityStored,
        @Schema(description = "是否导出行级完整性值")
        boolean rowIntegrityValueExported,
        @Schema(description = "是否导出行内容摘要")
        boolean rowContentSummaryExported,
        @Schema(description = "是否导出报告内容")
        boolean archiveContentExported,
        @Schema(description = "是否为聚合输出")
        boolean aggregateOnly
) {
}
