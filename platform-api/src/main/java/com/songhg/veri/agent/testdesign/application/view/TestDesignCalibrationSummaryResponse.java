package com.songhg.veri.agent.testdesign.application.view;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * Aggregate long-term calibration readiness summary.
 */
public record TestDesignCalibrationSummaryResponse(
        @Schema(description = "校准运行总数")
        long totalRunCount,
        @Schema(description = "通过运行数")
        long passedRunCount,
        @Schema(description = "风险运行数")
        long warningRunCount,
        @Schema(description = "阻断运行数")
        long blockedRunCount,
        @Schema(description = "最近校准状态")
        String latestStatus,
        @Schema(description = "最近校准时间")
        Instant latestRunAt,
        @Schema(description = "长期校准流程是否可用")
        boolean longTermCalibrationReady,
        @Schema(description = "是否存在长期校准基线")
        boolean baselineReady
) {
}
