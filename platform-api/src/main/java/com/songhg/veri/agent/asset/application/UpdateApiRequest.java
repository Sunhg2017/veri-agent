package com.songhg.veri.agent.asset.application;

import jakarta.validation.constraints.NotBlank;

public record UpdateApiRequest(
        @NotBlank String summary,
        String description,
        @NotBlank String httpMethod,
        @NotBlank String path,
        String version,
        String requestSchema,
        String responseSchema,
        String status
) {
}
