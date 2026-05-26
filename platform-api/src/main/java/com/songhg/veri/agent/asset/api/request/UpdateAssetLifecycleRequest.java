package com.songhg.veri.agent.asset.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateAssetLifecycleRequest(
        @Schema(description = "资产生命周期状态，例如 ACTIVE、ARCHIVED、DELETED。")
        @NotBlank
        String lifecycleStatus,
        @Schema(description = "操作原因。")
        @Size(max = 256)
        String reason
) {
}
