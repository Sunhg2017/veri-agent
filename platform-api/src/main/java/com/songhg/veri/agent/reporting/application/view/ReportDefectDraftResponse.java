package com.songhg.veri.agent.reporting.application.view;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Schema(description = "WP10 platform-local defect draft response")
public record ReportDefectDraftResponse(
        UUID id,
        UUID reportId,
        UUID diagnosisId,
        String status,
        String title,
        String reproductionSummary,
        String impactSummary,
        String prioritySuggestion,
        List<String> evidenceRefs,
        Map<String, Object> payloadPreview,
        String createdBy,
        String updatedBy,
        Instant createdAt,
        Instant updatedAt
) {
}
