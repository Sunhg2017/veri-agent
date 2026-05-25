package com.songhg.veri.agent.asset.application;

import com.songhg.veri.agent.asset.application.view.AssetImportItemResponse;
import java.util.List;
import java.util.UUID;

record ImportPlan(
        int row,
        String action,
        UUID id,
        String code,
        String status,
        String message,
        List<String> errors
) {
    static ImportPlan planned(int row, String action, UUID id, String code, String message) {
        return new ImportPlan(row, action, id, code, "PLANNED", message, List.of());
    }

    static ImportPlan failed(int row, String action, String message, List<String> errors) {
        return new ImportPlan(row, action, null, null, "FAILED", message, errors);
    }

    AssetImportItemResponse toResponse() {
        return new AssetImportItemResponse(row, action, id, code, status, message, errors);
    }
}
