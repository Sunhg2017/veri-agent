package com.songhg.veri.agent.execution.application.view;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record ExecutionPlanNodeResponse(
        @Schema(description = "Plan node ID")
        UUID id,
        String key,
        String type,
        List<String> dependencies,
        Map<String, Object> inputSummary,
        String failurePolicy,
        int timeoutSeconds,
        Map<String, Object> retryPolicy,
        Instant createdAt,
        Instant updatedAt
) {
}
