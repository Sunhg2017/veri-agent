package com.songhg.veri.agent.document.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.common.event.PlatformEventEnvelope;
import com.songhg.veri.agent.common.event.PlatformEventHandler;
import com.songhg.veri.agent.document.application.event.DocumentWebhookAcceptedEvent;
import org.springframework.stereotype.Component;

@Component
public class DocumentWebhookAcceptedEventHandler implements PlatformEventHandler {

    private final ObjectMapper objectMapper;
    private final DocumentInputService inputService;

    public DocumentWebhookAcceptedEventHandler(ObjectMapper objectMapper, DocumentInputService inputService) {
        this.objectMapper = objectMapper;
        this.inputService = inputService;
    }

    @Override
    public String eventType() {
        return DocumentWebhookAcceptedEvent.EVENT_TYPE;
    }

    @Override
    public void handle(PlatformEventEnvelope event) {
        DocumentWebhookAcceptedEvent payload = event.payloadAs(objectMapper, DocumentWebhookAcceptedEvent.class);
        inputService.processWebhookEvent(payload.webhookEventId());
    }
}
