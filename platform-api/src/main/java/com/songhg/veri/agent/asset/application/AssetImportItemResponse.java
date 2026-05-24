package com.songhg.veri.agent.asset.application;

import java.util.List;
import java.util.UUID;

public record AssetImportItemResponse(
        int row,
        String action,
        UUID id,
        String code,
        String status,
        String message,
        List<String> errors
) {
}
