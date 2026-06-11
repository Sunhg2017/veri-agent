package com.songhg.veri.agent.apiautomation.domain;

import java.time.Instant;
import java.util.UUID;

public record ApiAutomationCase(
        UUID id,
        UUID taskId,
        String projectId,
        UUID specId,
        UUID endpointSnapshotId,
        UUID assetApiId,
        UUID assetTestCaseId,
        String title,
        String httpMethod,
        String path,
        String coverageType,
        int expectedStatus,
        String assertionSummaryJson,
        String requestTemplateJson,
        String source,
        String status,
        Instant createdAt,
        Instant updatedAt
) {
}
