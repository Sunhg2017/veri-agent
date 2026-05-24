package com.songhg.veri.agent.documentinput.application.command;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;

public record UpdateDocumentCandidateRequest(
        @NotBlank
        String title,
        String description,
        String priority,
        String acceptanceCriteria,
        JsonNode tags,
        Long version
) {
}
