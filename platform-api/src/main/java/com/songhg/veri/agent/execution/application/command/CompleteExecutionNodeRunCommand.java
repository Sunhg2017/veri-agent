package com.songhg.veri.agent.execution.application.command;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;
import java.util.UUID;

public record CompleteExecutionNodeRunCommand(
        @Schema(description = "Node run ID to complete")
        UUID nodeRunId,
        @NotBlank
        @Size(max = 128)
        @Schema(description = "Queue claim token")
        String claimToken,
        @NotBlank
        @Size(max = 32)
        @Schema(description = "Terminal node status")
        String status,
        @Size(max = 64)
        @Schema(description = "Sanitized error code")
        String errorCode,
        @Size(max = 512)
        @Schema(description = "Sanitized error summary")
        String errorSummary,
        @Schema(description = "Sanitized node result summary")
        Map<String, Object> resultSummary
) {
    public CompleteExecutionNodeRunCommand withNodeRunId(UUID targetNodeRunId) {
        return new CompleteExecutionNodeRunCommand(
                targetNodeRunId,
                claimToken,
                status,
                errorCode,
                errorSummary,
                resultSummary
        );
    }
}
