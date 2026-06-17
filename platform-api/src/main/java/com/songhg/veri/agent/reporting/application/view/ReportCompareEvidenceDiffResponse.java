package com.songhg.veri.agent.reporting.application.view;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;

@Schema(description = "Aggregate evidence manifest diff between two report snapshots")
public record ReportCompareEvidenceDiffResponse(
        @Schema(description = "Whether the evidence section changed")
        boolean changed,
        @Schema(description = "Evidence manifest count in the baseline report")
        long baselineCount,
        @Schema(description = "Evidence manifest count in the current report")
        long currentCount,
        @Schema(description = "Added aggregate manifest keys in current report")
        List<String> addedManifestKeys,
        @Schema(description = "Removed aggregate manifest keys from baseline report")
        List<String> removedManifestKeys,
        @Schema(description = "Baseline evidence counts grouped by source work package")
        Map<String, Long> baselineSourceWpCounts,
        @Schema(description = "Current evidence counts grouped by source work package")
        Map<String, Long> currentSourceWpCounts,
        @Schema(description = "Baseline evidence counts grouped by source type")
        Map<String, Long> baselineSourceTypeCounts,
        @Schema(description = "Current evidence counts grouped by source type")
        Map<String, Long> currentSourceTypeCounts
) {
}
