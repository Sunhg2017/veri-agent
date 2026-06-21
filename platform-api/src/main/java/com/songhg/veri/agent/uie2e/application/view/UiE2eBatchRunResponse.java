package com.songhg.veri.agent.uie2e.application.view;

import java.util.List;

public record UiE2eBatchRunResponse(
        String projectId,
        int requestedCount,
        int createdCount,
        int replayedCount,
        int failedCount,
        List<UiE2eBatchRunItemResponse> items
) {
}
