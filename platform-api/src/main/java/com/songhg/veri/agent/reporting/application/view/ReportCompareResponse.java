package com.songhg.veri.agent.reporting.application.view;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

@Schema(description = "Aggregate compare response between a current report and a baseline report")
public record ReportCompareResponse(
        @Schema(description = "Current report ID")
        UUID reportId,
        @Schema(description = "Baseline report ID")
        UUID baselineReportId,
        @Schema(description = "Owning project scope ID shared by both reports")
        String projectId,
        @Schema(description = "Whether all aggregate sections are unchanged")
        boolean unchanged,
        @Schema(description = "Flattened list of changed compare fields")
        List<String> changedFields,
        @Schema(description = "Changed report metadata fields")
        List<ReportCompareFieldDiffResponse> metadataDiffs,
        @Schema(description = "Changed aggregate summary fields")
        List<ReportCompareFieldDiffResponse> summaryDiffs,
        @Schema(description = "Changed latest diagnosis fields")
        List<ReportCompareFieldDiffResponse> diagnosisDiffs,
        @Schema(description = "Changed evidence manifest aggregates")
        ReportCompareEvidenceDiffResponse evidenceDiff,
        @Schema(description = "Changed defect draft aggregates")
        ReportCompareDefectDraftDiffResponse defectDraftDiff
) {
}
