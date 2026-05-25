package com.songhg.veri.agent.testdesign.application.view;

import java.util.List;

public record TestDesignCandidateBatchActionResponse(
        String action,
        int total,
        int succeededCount,
        int failedCount,
        List<TestDesignCandidateBatchActionItemResponse> items
) {
}
