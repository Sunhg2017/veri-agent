package com.songhg.veri.agent.asset.api.response;

import java.util.List;

public record AssetImportResponse(
        String assetType,
        String format,
        boolean dryRun,
        int totalRows,
        int created,
        int updated,
        int skipped,
        int failed,
        List<AssetImportItemResponse> items
) {
}
