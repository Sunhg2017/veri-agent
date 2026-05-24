package com.songhg.veri.agent.asset.application.command;

import jakarta.validation.constraints.NotBlank;

public record UpdateRequirementRequest(
        @NotBlank String title,
        String description,
        String status,
        String priority,
        String tags
) {
}
