package com.songhg.veri.agent.execution.application.command;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.Map;

public record UpdateExecutionPlanCommand(
        @Schema(description = "Execution plan name")
        @Size(max = 128) String name,
        @Schema(description = "Project environment key selected for execution")
        @Size(max = 128) String environmentKey,
        @Schema(description = "Optional plan description")
        @Size(max = 512) String description,
        @Schema(description = "Target plan status")
        String status,
        @Schema(description = "Trigger policy summary")
        Map<String, Object> triggerPolicy,
        @Valid ExecutionDagCommand dag
) {
}
