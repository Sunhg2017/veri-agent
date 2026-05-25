package com.songhg.veri.agent.testdesign.application.view;

import java.util.UUID;

public record TestDesignCandidateBatchActionItemResponse(
        UUID candidateId,
        String result,
        TestDesignCandidateResponse candidate,
        String errorCode,
        String errorMessage
) {
}
