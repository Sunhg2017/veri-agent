package com.songhg.veri.agent.testdesign.application.command;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Moves a maintained sample through candidate, golden, frozen and deprecated states.
 */
public record TransitionTestDesignEvaluationSampleCommand(
        @Schema(description = "目标状态：CANDIDATE/GOLDEN/FROZEN/DEPRECATED")
        String status,
        @Schema(description = "基线版本；纳入 GOLDEN/FROZEN 时建议提供")
        String baselineVersion,
        @Schema(description = "维护备注，最多 1000 字")
        String maintenanceNote
) {
}
