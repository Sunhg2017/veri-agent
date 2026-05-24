package com.songhg.veri.agent.asset.application;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateAssetLifecycleRequest(
        @NotBlank
        String lifecycleStatus,
        @Size(max = 256)
        String reason
) {
}
