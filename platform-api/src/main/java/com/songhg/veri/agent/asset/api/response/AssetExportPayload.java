package com.songhg.veri.agent.asset.api.response;

public record AssetExportPayload(
        String fileName,
        String contentType,
        byte[] content
) {
}
