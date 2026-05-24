package com.songhg.veri.agent.documentinput.application.view;

import java.util.UUID;

public record DocumentCandidateBatchActionItemResponse(
        UUID candidateId,
        String result,
        DocumentCandidateResponse candidate,
        String errorCode,
        String errorMessage
) {
}
