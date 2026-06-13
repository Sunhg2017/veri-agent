package com.songhg.veri.agent.execution.application.command;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Map;

public record CreateExecutionPlanCommand(
        @Schema(description = "Project ID or code")
        @NotBlank String projectId,
        @Schema(description = "Execution plan name")
        @NotBlank @Size(max = 128) String name,
        @Schema(description = "Project environment key selected for execution")
        @NotBlank @Size(max = 128) String environmentKey,
        @Schema(description = "Optional plan description")
        @Size(max = 512) String description,
        @Schema(description = "Optional initial plan status, defaults to DRAFT")
        String status,
        @Schema(description = "Trigger policy summary")
        Map<String, Object> triggerPolicy,
        @NotNull @Valid ExecutionDagCommand dag
) {
}
