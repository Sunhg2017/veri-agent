package com.songhg.veri.agent.asset.api.request;

import jakarta.validation.constraints.NotBlank;

public record CreateBusinessFlowRequest(
        @NotBlank String name,
        String description,
        Object flowJson,
        String priority,
        @NotBlank String projectId,
        String status
) {
}
