package com.songhg.veri.agent.reporting.application.view;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Field-level aggregate diff between a baseline report and the current report")
public record ReportCompareFieldDiffResponse(
        @Schema(description = "Changed field name inside the compare section")
        String field,
        @Schema(description = "Aggregate-only value captured from the baseline report snapshot")
        Object baselineValue,
        @Schema(description = "Aggregate-only value captured from the current report snapshot")
        Object currentValue
) {
}
