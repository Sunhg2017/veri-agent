package com.songhg.veri.agent.document.application.event;

import java.util.List;
import java.util.UUID;

public record DocumentPublishRequestedEvent(UUID importId, List<UUID> candidateIds) {

    public static final String EVENT_TYPE = "document-input.publish.requested";
}
