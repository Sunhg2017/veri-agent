package com.songhg.veri.agent.asset.application.view;

public record AssetExportPayload(
        String fileName,
        String contentType,
        byte[] content
) {
}
