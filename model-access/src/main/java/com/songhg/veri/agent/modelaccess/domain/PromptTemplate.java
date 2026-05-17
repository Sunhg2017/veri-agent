package com.songhg.veri.agent.modelaccess.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;

public record PromptTemplate(
        UUID id,
        @JsonProperty("prompt_key")
        String promptKey,
        String name,
        int version,
        String content,
        PromptStatus status,
        @JsonProperty("change_note")
        String changeNote,
        @JsonProperty("created_at")
        Instant createdAt,
        @JsonProperty("updated_at")
        Instant updatedAt
) {
}
