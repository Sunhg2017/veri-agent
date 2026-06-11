package com.songhg.veri.agent.apiautomation.application.view;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record ApiAutomationSyncResponse(
        @Schema(description = "OpenAPI spec ID")
        UUID specId,
        @Schema(description = "按同步结果聚合的 endpoint 数量")
        Map<String, Integer> counts,
        List<ApiAutomationSyncItemResponse> items,
        List<ApiAutomationEndpointSnapshotResponse> endpoints
) {
}
