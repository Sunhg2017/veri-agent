package com.songhg.veri.agent.documentinput.api.request;

import jakarta.validation.constraints.NotBlank;

public record IgnoreDocumentCandidateRequest(
        @NotBlank
        String reason,
        Long version
) {
}
