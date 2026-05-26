package com.songhg.veri.agent.document.application.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record CandidateBatchActionRequest(
        @NotBlank
        String action,
        List<UUID> candidateIds,
        List<CandidateBatchItemRequest> candidates,
        String reason
) {
    public record CandidateBatchItemRequest(
            @NotNull UUID id,
            Long version
    ) {
    }
}
