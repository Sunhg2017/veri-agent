package com.songhg.veri.agent.documentinput.application.command;

import jakarta.validation.constraints.NotBlank;

public record IgnoreDocumentCandidateRequest(
        @NotBlank
        String reason,
        Long version
) {
}
