package com.songhg.veri.agent.asset.api.request;

import jakarta.validation.constraints.NotBlank;

public record UpdateRequirementRequest(
        @NotBlank String title,
        String description,
        String status,
        String priority,
        String tags
) {
}
