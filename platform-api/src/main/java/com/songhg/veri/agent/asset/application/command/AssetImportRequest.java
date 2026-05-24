package com.songhg.veri.agent.asset.application.command;

import jakarta.validation.constraints.NotBlank;

public record AssetImportRequest(
        @NotBlank String assetType,
        @NotBlank String format,
        @NotBlank String projectId,
        Boolean dryRun,
        @NotBlank String content
) {
}
