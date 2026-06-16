package com.songhg.veri.agent.reporting.domain;

import java.time.Instant;
import java.util.UUID;

public record ReportDefectDraft(
        UUID id,
        UUID reportId,
        UUID diagnosisId,
        String status,
        String title,
        String reproductionSummary,
        String impactSummary,
        String prioritySuggestion,
        String evidenceRefsJson,
        String payloadPreviewJson,
        String createdBy,
        String updatedBy,
        Instant createdAt,
        Instant updatedAt
) {
}
