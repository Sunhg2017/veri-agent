package com.songhg.veri.agent.document.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.common.event.PlatformEventEnvelope;
import com.songhg.veri.agent.common.event.PlatformEventHandler;
import com.songhg.veri.agent.document.application.event.DocumentPublishRequestedEvent;
import org.springframework.stereotype.Component;

@Component
public class DocumentPublishRequestedEventHandler implements PlatformEventHandler {

    private final ObjectMapper objectMapper;
    private final DocumentRequirementPublishService publishService;

    public DocumentPublishRequestedEventHandler(
            ObjectMapper objectMapper,
            DocumentRequirementPublishService publishService
    ) {
        this.objectMapper = objectMapper;
        this.publishService = publishService;
    }

    @Override
    public String eventType() {
        return DocumentPublishRequestedEvent.EVENT_TYPE;
    }

    @Override
    public void handle(PlatformEventEnvelope event) {
        DocumentPublishRequestedEvent payload = event.payloadAs(objectMapper, DocumentPublishRequestedEvent.class);
        publishService.processQueuedPublish(payload.importId(), payload.candidateIds());
    }
}
