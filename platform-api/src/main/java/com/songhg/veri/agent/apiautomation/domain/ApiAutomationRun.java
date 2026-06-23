package com.songhg.veri.agent.apiautomation.domain;

import java.time.Instant;
import java.util.UUID;

public record ApiAutomationRun(
        UUID id,
        String projectId,
        UUID bundleId,
        String environmentId,
        String baseUrlDigest,
        String baseUrlHost,
        String status,
        int timeoutSeconds,
        int caseCount,
        String traceId,
        String runnerMode,
        String externalRunId,
        String errorCode,
        String errorSummary,
        String createdBy,
        String updatedBy,
        Instant startedAt,
        Instant completedAt,
        Instant createdAt,
        Instant updatedAt
) {

    public ApiAutomationRun(
            UUID id,
            String projectId,
            UUID bundleId,
            String environmentId,
            String baseUrlDigest,
            String baseUrlHost,
            String status,
            int timeoutSeconds,
            int caseCount,
            String traceId,
            String runnerMode,
            String errorCode,
            String errorSummary,
            String createdBy,
            String updatedBy,
            Instant startedAt,
            Instant completedAt,
            Instant createdAt,
            Instant updatedAt
    ) {
        this(
                id,
                projectId,
                bundleId,
                environmentId,
                baseUrlDigest,
                baseUrlHost,
                status,
                timeoutSeconds,
                caseCount,
                traceId,
                runnerMode,
                null,
                errorCode,
                errorSummary,
                createdBy,
                updatedBy,
                startedAt,
                completedAt,
                createdAt,
                updatedAt
        );
    }
}
