package com.songhg.veri.agent.apiautomation.application.view;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;

public record ApiAutomationSpecDetailResponse(
        @Schema(description = "规格摘要")
        ApiAutomationSpecResponse spec,
        @Schema(description = "解析摘要，不包含原始请求/响应正文")
        Map<String, Object> parseSummary,
        @Schema(description = "endpoint snapshot 列表")
        List<ApiAutomationEndpointSnapshotResponse> endpoints
) {
}
