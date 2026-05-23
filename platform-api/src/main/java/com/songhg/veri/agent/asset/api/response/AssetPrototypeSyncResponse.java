package com.songhg.veri.agent.asset.api.response;

import java.util.List;

public record AssetPrototypeSyncResponse(
        String source,
        boolean dryRun,
        int totalRows,
        int created,
        int updated,
        int skipped,
        int failed,
        List<AssetImportItemResponse> items
) {
}
