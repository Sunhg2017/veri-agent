package com.songhg.veri.agent.uie2e.application.view;

import java.util.List;

public record UiE2eRunSummaryBackfillResponse(
        String projectId,
        int requestedCount,
        int updatedCount,
        int unchangedCount,
        int failedCount,
        List<UiE2eRunSummaryBackfillItemResponse> items
) {
}
