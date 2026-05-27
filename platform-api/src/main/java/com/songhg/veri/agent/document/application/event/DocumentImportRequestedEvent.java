package com.songhg.veri.agent.document.application.event;

import java.util.UUID;

public record DocumentImportRequestedEvent(UUID importId) {

    public static final String EVENT_TYPE = "document-input.import.requested";
}
