package com.songhg.veri.agent.asset.application;

import jakarta.validation.constraints.NotBlank;

public record CreatePageRequest(
        @NotBlank String name,
        String urlPattern,
        String source,
        String sourceRef,
        String sourceVersion,
        Object componentTree,
        String screenshotUrl,
        @NotBlank String projectId,
        String status
) {
}
