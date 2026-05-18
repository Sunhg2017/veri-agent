package com.songhg.veri.agent.asset.api.request;

import jakarta.validation.constraints.NotBlank;

public record CreateApiRequest(
        @NotBlank String summary,
        String description,
        @NotBlank String httpMethod,
        @NotBlank String path,
        String requestSchema,
        String responseSchema,
        @NotBlank
        String projectId,
        String status
) {
}
