package com.songhg.veri.agent.document.application.command;

import jakarta.validation.constraints.NotBlank;

public record IgnoreDocumentCandidateRequest(
        @NotBlank
        String reason,
        Long version
) {
}
