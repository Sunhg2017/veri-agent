package com.songhg.veri.agent.testdesign.application.event;

import java.util.List;
import java.util.UUID;

public record TestDesignPublishRequestedEvent(
        UUID taskId,
        List<UUID> candidateIds
) {

    public static final String EVENT_TYPE = "test-design.publish.requested";
}
