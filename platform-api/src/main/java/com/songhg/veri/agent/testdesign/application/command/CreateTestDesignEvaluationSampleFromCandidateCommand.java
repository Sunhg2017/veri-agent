package com.songhg.veri.agent.testdesign.application.command;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/**
 * Converts a reviewed WP5 candidate into a maintained evaluation sample.
 */
public record CreateTestDesignEvaluationSampleFromCandidateCommand(
        @Schema(description = "候选 ID")
        UUID candidateId,
        @Schema(description = "样本编号；为空时生成")
        String sampleKey,
        @Schema(description = "样本状态：CANDIDATE/GOLDEN/FROZEN/DEPRECATED")
        String status,
        @Schema(description = "基线版本")
        String baselineVersion,
        @Schema(description = "维护备注，最多 1000 字")
        String maintenanceNote
) {
}
