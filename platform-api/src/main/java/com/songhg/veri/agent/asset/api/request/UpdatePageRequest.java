package com.songhg.veri.agent.asset.api.request;

import jakarta.validation.constraints.NotBlank;

public record UpdatePageRequest(
        @NotBlank String name,
        String urlPattern,
        String source,
        String sourceRef,
        String sourceVersion,
        Object componentTree,
        String screenshotUrl,
        String status
) {
}
