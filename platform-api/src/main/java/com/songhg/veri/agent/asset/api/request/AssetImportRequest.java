package com.songhg.veri.agent.asset.api.request;

import jakarta.validation.constraints.NotBlank;

public record AssetImportRequest(
        @NotBlank String assetType,
        @NotBlank String format,
        @NotBlank String projectId,
        Boolean dryRun,
        @NotBlank String content
) {
}
