package com.songhg.veri.agent.asset.application;

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
