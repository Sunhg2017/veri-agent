package com.songhg.veri.agent.testdesign.application.event;

import java.util.UUID;

public record TestDesignGenerationRequestedEvent(
        UUID taskId
) {

    public static final String EVENT_TYPE = "test-design.generation.requested";
}
