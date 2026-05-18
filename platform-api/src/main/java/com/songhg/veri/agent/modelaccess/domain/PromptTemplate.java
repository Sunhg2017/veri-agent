package com.songhg.veri.agent.modelaccess.domain;

import java.time.Instant;
import java.util.UUID;

public record PromptTemplate(
        UUID id,
        String promptKey,
        String name,
        int version,
        String content,
        PromptStatus status,
        String changeNote,
        Instant createdAt,
        Instant updatedAt
) {
}
