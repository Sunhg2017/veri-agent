package com.songhg.veri.agent.execution.application.view;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;

public record ExecutionNodePolicyResponse(
        @Schema(description = "Node key")
        String key,
        @Schema(description = "Node type")
        String type,
        @Schema(description = "Dependency node keys")
        List<String> dependencies,
        @Schema(description = "Failure policy")
        String failurePolicy,
        @Schema(description = "Timeout seconds")
        int timeoutSeconds,
        @Schema(description = "Retry policy summary")
        Map<String, Object> retryPolicy,
        @Schema(description = "Sanitized input summary")
        Map<String, Object> inputSummary,
        @Schema(description = "Runner or integration type")
        String runnerType
) {
}
