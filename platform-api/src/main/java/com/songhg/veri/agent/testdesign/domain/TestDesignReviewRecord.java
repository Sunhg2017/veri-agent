package com.songhg.veri.agent.testdesign.domain;

import java.time.Instant;
import java.util.UUID;

public record TestDesignReviewRecord(
        UUID id,
        UUID candidateId,
        UUID taskId,
        String projectId,
        String action,
        String beforeStatus,
        String afterStatus,
        String reviewer,
        String comment,
        String diffJson,
        Instant createdAt
) {
}
