package com.songhg.veri.agent.asset.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateRequirementRequest(
        @NotBlank String title,
        String description,
        String status,
        String priority,
        @NotBlank
        String projectId,
        String tags
) {
}
