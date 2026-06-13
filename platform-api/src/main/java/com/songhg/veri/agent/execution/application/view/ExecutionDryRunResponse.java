package com.songhg.veri.agent.execution.application.view;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record ExecutionDryRunResponse(
        @Schema(description = "Plan ID")
        UUID planId,
        @Schema(description = "Whether DAG and resource checks passed")
        boolean valid,
        @Schema(description = "Plan DAG digest")
        String dagDigest,
        @Schema(description = "Node policy summaries")
        List<ExecutionNodePolicyResponse> nodes,
        @Schema(description = "Validation issues")
        List<ExecutionValidationIssueResponse> issues,
        @Schema(description = "Safety and dry-run policy")
        Map<String, Object> policy
) {
}
