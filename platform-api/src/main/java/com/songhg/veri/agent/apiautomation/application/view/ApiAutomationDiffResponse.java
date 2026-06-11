package com.songhg.veri.agent.apiautomation.application.view;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record ApiAutomationDiffResponse(
        @Schema(description = "OpenAPI spec ID")
        UUID specId,
        @Schema(description = "按 diffStatus 聚合的 endpoint 数量")
        Map<String, Integer> counts,
        @Schema(description = "带 WP3 匹配信息的 endpoint 列表")
        List<ApiAutomationEndpointSnapshotResponse> endpoints
) {
}
