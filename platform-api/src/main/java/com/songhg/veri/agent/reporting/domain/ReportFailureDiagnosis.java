package com.songhg.veri.agent.reporting.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ReportFailureDiagnosis(
        UUID id,
        UUID reportId,
        String status,
        String classificationJson,
        String modelInvocationDigest,
        BigDecimal confidence,
        boolean manualReviewRequired,
        String diagnosisSummaryJson,
        String errorCode,
        Instant createdAt,
        Instant updatedAt
) {
}
