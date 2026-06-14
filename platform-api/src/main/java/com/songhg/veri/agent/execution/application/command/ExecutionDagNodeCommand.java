package com.songhg.veri.agent.execution.application.command;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;

public record ExecutionDagNodeCommand(
        @Schema(description = "Stable node key within a plan")
        @NotBlank @Size(max = 128) String key,
        @Schema(description = "Node type")
        @NotBlank @Size(max = 32) String type,
        @Schema(description = "Upstream dependency node keys")
        List<String> dependencies,
        @Schema(description = "Node input summary; secrets are redacted before persistence")
        Map<String, Object> input,
        @Schema(description = "Node timeout seconds")
        Integer timeoutSeconds,
        @Schema(description = "Failure policy")
        String failurePolicy,
        @Schema(description = "Retry policy summary")
        Map<String, Object> retryPolicy
) {
}
