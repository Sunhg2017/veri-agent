package com.songhg.veri.agent.documentinput.api.response;

import com.songhg.veri.agent.documentinput.application.view.DocumentCandidateBatchActionItemResponse;
import java.util.List;


public record DocumentCandidateBatchActionResponse(
        String action,
        int total,
        int succeededCount,
        int failedCount,
        List<DocumentCandidateBatchActionItemResponse> items
) {
}
