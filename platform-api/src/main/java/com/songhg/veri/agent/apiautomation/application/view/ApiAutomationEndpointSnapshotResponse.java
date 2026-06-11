package com.songhg.veri.agent.apiautomation.application.view;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

public record ApiAutomationEndpointSnapshotResponse(
        @Schema(description = "endpoint snapshot ID")
        UUID id,
        String serviceName,
        String operationId,
        String httpMethod,
        String path,
        String summary,
        String tags,
        int parameterCount,
        boolean requestBodyPresent,
        String responseStatuses,
        String schemaDigest,
        String diffStatus
) {
}
