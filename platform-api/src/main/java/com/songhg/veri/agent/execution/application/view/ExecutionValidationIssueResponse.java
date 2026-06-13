package com.songhg.veri.agent.execution.application.view;

import io.swagger.v3.oas.annotations.media.Schema;

public record ExecutionValidationIssueResponse(
        @Schema(description = "Stable validation code")
        String code,
        @Schema(description = "Node key when issue is node-scoped")
        String nodeKey,
        @Schema(description = "Severity")
        String severity,
        @Schema(description = "Human-readable summary")
        String message
) {
}
