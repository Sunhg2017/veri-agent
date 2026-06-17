package com.songhg.veri.agent.reporting.application.view;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;

@Schema(description = "Aggregate defect draft diff between two report snapshots")
public record ReportCompareDefectDraftDiffResponse(
        @Schema(description = "Whether the defect draft section changed")
        boolean changed,
        @Schema(description = "Defect draft count in the baseline report")
        long baselineCount,
        @Schema(description = "Defect draft count in the current report")
        long currentCount,
        @Schema(description = "Baseline draft counts grouped by draft status")
        Map<String, Long> baselineStatusCounts,
        @Schema(description = "Current draft counts grouped by draft status")
        Map<String, Long> currentStatusCounts
) {
}
