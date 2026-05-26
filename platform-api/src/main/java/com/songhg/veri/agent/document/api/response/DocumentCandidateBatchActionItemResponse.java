package com.songhg.veri.agent.document.api.response;

import com.songhg.veri.agent.document.application.view.DocumentCandidateResponse;
import java.util.UUID;


public record DocumentCandidateBatchActionItemResponse(
        UUID candidateId,
        String result,
        DocumentCandidateResponse candidate,
        String errorCode,
        String errorMessage
) {
}
