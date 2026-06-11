package com.songhg.veri.agent.apiautomation.application.view;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;

public record ApiAutomationHealthResponse(
        @Schema(description = "服务标识")
        String service,
        @Schema(description = "状态")
        String status,
        @Schema(description = "支持的 OpenAPI 主版本")
        List<String> supportedOpenApiVersions,
        @Schema(description = "规格大小上限")
        int specMaxBytes,
        @Schema(description = "endpoint 数量上限")
        int endpointMaxCount,
        @Schema(description = "runner 是否启用")
        boolean runnerEnabled,
        @Schema(description = "runner 默认超时")
        int runnerTimeoutSeconds,
        @Schema(description = "runner 默认用例上限")
        int runnerMaxCases,
        @Schema(description = "WP2 Prompt key")
        String promptKey,
        @Schema(description = "模型失败是否允许模板兜底")
        boolean modelFallbackEnabled,
        @Schema(description = "当前功能边界")
        Map<String, Object> policy
) {
}
