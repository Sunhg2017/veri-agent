package com.songhg.veri.agent.modelaccess.application;

import com.songhg.veri.agent.modelaccess.domain.InvocationStatus;
import com.songhg.veri.agent.common.api.PageQuery;
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
        PageQuery pageQuery
) {

    public int index() {
        return pageQuery.index();
    }

    public int size() {
        return pageQuery.size();
    }

    public int offset() {
        return pageQuery.offset();
    }
}
