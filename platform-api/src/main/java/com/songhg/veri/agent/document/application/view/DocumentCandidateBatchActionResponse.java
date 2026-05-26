package com.songhg.veri.agent.document.application.view;

import java.util.List;

public record DocumentCandidateBatchActionResponse(
        String action,
        int total,
        int succeededCount,
        int failedCount,
        List<DocumentCandidateBatchActionItemResponse> items
) {
}
