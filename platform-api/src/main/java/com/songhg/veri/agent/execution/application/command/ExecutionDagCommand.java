package com.songhg.veri.agent.execution.application.command;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record ExecutionDagCommand(
        @Schema(description = "DAG nodes")
        @NotEmpty @Size(max = 100) List<@Valid ExecutionDagNodeCommand> nodes
) {
}
