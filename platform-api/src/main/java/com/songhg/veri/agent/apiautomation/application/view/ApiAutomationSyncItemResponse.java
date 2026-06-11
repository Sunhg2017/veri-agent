package com.songhg.veri.agent.apiautomation.application.view;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

public record ApiAutomationSyncItemResponse(
        @Schema(description = "endpoint snapshot ID")
        UUID endpointId,
        UUID assetApiId,
        String httpMethod,
        String path,
        String beforeStatus,
        String result,
        String message
) {
}
