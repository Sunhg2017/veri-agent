package com.songhg.veri.agent.document.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.common.event.PlatformEventEnvelope;
import com.songhg.veri.agent.common.event.PlatformEventHandler;
import com.songhg.veri.agent.document.application.event.DocumentImportRequestedEvent;
import org.springframework.stereotype.Component;

@Component
public class DocumentImportRequestedEventHandler implements PlatformEventHandler {

    private final ObjectMapper objectMapper;
    private final DocumentImportService importService;

    public DocumentImportRequestedEventHandler(ObjectMapper objectMapper, DocumentImportService importService) {
        this.objectMapper = objectMapper;
        this.importService = importService;
    }

    @Override
    public String eventType() {
        return DocumentImportRequestedEvent.EVENT_TYPE;
    }

    @Override
    public void handle(PlatformEventEnvelope event) {
        DocumentImportRequestedEvent payload = event.payloadAs(objectMapper, DocumentImportRequestedEvent.class);
        importService.processQueuedImport(payload.importId());
    }
}
