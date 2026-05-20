package com.songhg.veri.agent.asset.api.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TestCaseResponse(
        UUID id,
        String code,
        String title,
        String description,
        UUID requirementId,
        UUID apiId,
        String source,
        String sourceRef,
        String status,
        String priority,
        String tags,
        List<TestCaseStepResponse> steps,
        Instant createdAt,
        Instant updatedAt
) {
}
