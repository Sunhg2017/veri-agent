package com.songhg.veri.agent.modelaccess.application;

import com.songhg.veri.agent.modelaccess.domain.InvocationStatus;
import java.time.Instant;
import java.util.UUID;

public record InvocationQuery(
        String projectId,
        String applicationId,
        String sensitivityLevel,
        InvocationStatus status,
        UUID providerId,
        String actorService,
        Instant startTime,
        Instant endTime,
        int page,
        int size
) {

    public int offset() {
        return Math.max(0, page) * Math.max(1, size);
    }
}
