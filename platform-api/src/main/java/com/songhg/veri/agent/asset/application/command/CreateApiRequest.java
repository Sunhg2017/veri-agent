package com.songhg.veri.agent.asset.application.command;

import jakarta.validation.constraints.NotBlank;

public record CreateApiRequest(
        @NotBlank String summary,
        String description,
        @NotBlank String httpMethod,
        @NotBlank String path,
        String version,
        String requestSchema,
        String responseSchema,
        @NotBlank
        String projectId,
        String status
) {
}
