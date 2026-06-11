package com.songhg.veri.agent.apiautomation.application.view;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record ApiAutomationCaseResponse(
        @Schema(description = "自动化用例草稿 ID")
        UUID id,
        UUID endpointSnapshotId,
        UUID assetApiId,
        UUID assetTestCaseId,
        String title,
        String httpMethod,
        String path,
        String coverageType,
        int expectedStatus,
        Map<String, Object> assertionSummary,
        Map<String, Object> requestTemplate,
        String source,
        String status,
        Instant createdAt,
        Instant updatedAt
) {
}
