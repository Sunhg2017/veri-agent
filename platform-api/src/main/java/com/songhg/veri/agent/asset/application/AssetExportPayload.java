package com.songhg.veri.agent.asset.application;

public record AssetExportPayload(
        String fileName,
        String contentType,
        byte[] content
) {
}
