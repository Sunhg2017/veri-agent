package com.songhg.veri.agent.documentinput.application;

import jakarta.validation.constraints.NotBlank;

public record IgnoreDocumentCandidateRequest(
        @NotBlank
        String reason,
        Long version
) {
}
