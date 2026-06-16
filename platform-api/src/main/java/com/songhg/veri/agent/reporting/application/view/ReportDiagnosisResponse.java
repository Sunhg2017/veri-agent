package com.songhg.veri.agent.reporting.application.view;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Schema(description = "WP10 latest failure diagnosis response")
public record ReportDiagnosisResponse(
        UUID id,
        UUID reportId,
        String status,
        Map<String, Object> classification,
        Object rootCauseCandidates,
        BigDecimal confidence,
        boolean manualReviewRequired,
        String modelInvocationDigest,
        String errorCode,
        Object aiDiagnosisReady,
        Object modelInvoked,
        Object classificationOnly,
        Object redactionPolicy,
        Object diagnosisContext,
        Instant createdAt,
        Instant updatedAt
) {
}
