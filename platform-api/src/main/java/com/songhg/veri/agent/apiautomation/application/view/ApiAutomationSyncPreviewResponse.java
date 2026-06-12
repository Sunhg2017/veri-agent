package com.songhg.veri.agent.apiautomation.application.view;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record ApiAutomationSyncPreviewResponse(
        @Schema(description = "OpenAPI spec ID")
        UUID specId,
        @Schema(description = "按预览动作聚合的 endpoint 数量")
        Map<String, Integer> counts,
        List<ApiAutomationSyncPreviewItemResponse> items,
        List<ApiAutomationEndpointSnapshotResponse> endpoints,
        @Schema(description = "预览安全策略摘要")
        Map<String, Object> policy
) {
}
