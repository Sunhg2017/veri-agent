package com.songhg.veri.agent.asset.api.request;

import jakarta.validation.constraints.NotBlank;

public record UpdateApiRequest(
        @NotBlank String summary,
        String description,
        @NotBlank String httpMethod,
        @NotBlank String path,
        String requestSchema,
        String responseSchema,
        String status
) {
}
