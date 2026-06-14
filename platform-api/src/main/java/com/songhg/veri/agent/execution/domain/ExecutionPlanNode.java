package com.songhg.veri.agent.execution.domain;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.springframework.util.StringUtils;

public record ExecutionPlanNode(
        UUID id,
        UUID planId,
        String nodeKey,
        String nodeType,
        String dependencyKeysCsv,
        String inputSummaryJson,
        String failurePolicy,
        int timeoutSeconds,
        String retryPolicyJson,
        Instant createdAt,
        Instant updatedAt
) {

    public List<String> dependencyKeys() {
        if (!StringUtils.hasText(dependencyKeysCsv)) {
            return List.of();
        }
        return Arrays.stream(dependencyKeysCsv.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
    }
}
