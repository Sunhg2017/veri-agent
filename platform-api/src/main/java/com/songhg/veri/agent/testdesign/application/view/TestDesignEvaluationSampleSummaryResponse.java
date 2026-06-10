package com.songhg.veri.agent.testdesign.application.view;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * Aggregate WP5 sample-maintenance readiness summary.
 */
public record TestDesignEvaluationSampleSummaryResponse(
        @Schema(description = "样本总数")
        long totalCount,
        @Schema(description = "候选样本数量")
        long candidateCount,
        @Schema(description = "golden 样本数量")
        long goldenCount,
        @Schema(description = "冻结基线样本数量")
        long frozenCount,
        @Schema(description = "废弃样本数量")
        long deprecatedCount,
        @Schema(description = "基线版本数量")
        long baselineVersionCount,
        @Schema(description = "最近样本更新时间")
        Instant latestUpdatedAt,
        @Schema(description = "样本维护流程是否可用")
        boolean sampleMaintenanceReady,
        @Schema(description = "是否存在可校准 golden/frozen 基线")
        boolean baselineReady
) {
}
