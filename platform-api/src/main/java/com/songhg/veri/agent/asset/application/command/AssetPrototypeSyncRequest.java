package com.songhg.veri.agent.asset.application.command;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record AssetPrototypeSyncRequest(
        @NotBlank String projectId,
        @NotBlank String source,
        String connectorRef,
        String sourceVersion,
        Boolean dryRun,
        @NotEmpty List<@Valid PageItem> pages
) {
    public record PageItem(
            @NotBlank String name,
            String urlPattern,
            String sourceRef,
            String sourceVersion,
            Object componentTree,
            String screenshotUrl,
            String status
    ) {
    }
}
