package com.songhg.veri.agent.execution.application.command;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record DispatchExecutionNodeRunCommand(
        @Schema(description = "Node run ID to dispatch")
        UUID nodeRunId,
        @NotBlank
        @Size(max = 128)
        @Schema(description = "Queue claim token")
        String claimToken,
        @Size(max = 512)
        @Schema(description = "Optional runtime WP6 baseUrl; WP9 does not persist the raw value")
        String baseUrl,
        @Size(max = 128)
        @Schema(description = "Optional baseUrl reference, currently env:<environmentKey>")
        String baseUrlRef,
        @Size(max = 128)
        @Schema(description = "Optional runtime environment reference")
        String environmentId,
        @Schema(description = "Optional WP6 case IDs; defaults to the plan node caseIds or all bundle cases")
        List<UUID> caseIds,
        @Size(max = 10)
        @Schema(description = "Optional runtime secret refs passed to WP6; WP9 stores only the count")
        List<@Size(max = 256) String> secretRefs
) {
    public DispatchExecutionNodeRunCommand withNodeRunId(UUID targetNodeRunId) {
        return new DispatchExecutionNodeRunCommand(
                targetNodeRunId,
                claimToken,
                baseUrl,
                baseUrlRef,
                environmentId,
                caseIds,
                secretRefs
        );
    }
}
