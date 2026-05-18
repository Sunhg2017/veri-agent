package com.songhg.veri.agent.asset.api.request;

import jakarta.validation.constraints.NotBlank;

public record CreatePageRequest(
        @NotBlank String name,
        String urlPattern,
        String source,
        String sourceRef,
        Object componentTree,
        String screenshotUrl,
        @NotBlank String projectId,
        String status
) {
}
