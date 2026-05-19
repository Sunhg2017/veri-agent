package com.songhg.veri.agent.documentinput.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.UUID;

public record CandidateBatchActionRequest(
        @NotBlank
        String action,
        @NotEmpty
        List<UUID> candidateIds,
        String reason
) {
}
