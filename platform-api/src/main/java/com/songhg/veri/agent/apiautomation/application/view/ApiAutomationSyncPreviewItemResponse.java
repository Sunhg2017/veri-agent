package com.songhg.veri.agent.apiautomation.application.view;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;
import java.util.UUID;

public record ApiAutomationSyncPreviewItemResponse(
        @Schema(description = "endpoint snapshot ID")
        UUID endpointId,
        UUID assetApiId,
        String httpMethod,
        String path,
        String diffStatus,
        @Schema(description = "预览动作：CREATE、UPDATE、REVIEW 或 SKIP")
        String action,
        String reason,
        @Schema(description = "即将写入 WP3 的聚合 payload 摘要；不包含完整 schema、请求响应正文或敏感值")
        Map<String, Object> payloadSummary
) {
}
