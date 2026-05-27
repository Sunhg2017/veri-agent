package com.songhg.veri.agent.testdesign.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.common.event.PlatformEventEnvelope;
import com.songhg.veri.agent.common.event.PlatformEventHandler;
import com.songhg.veri.agent.testdesign.application.event.TestDesignGenerationRequestedEvent;
import org.springframework.stereotype.Component;

@Component
public class TestDesignGenerationRequestedEventHandler implements PlatformEventHandler {

    private final ObjectMapper objectMapper;
    private final TestDesignService service;

    public TestDesignGenerationRequestedEventHandler(ObjectMapper objectMapper, TestDesignService service) {
        this.objectMapper = objectMapper;
        this.service = service;
    }

    @Override
    public String eventType() {
        return TestDesignGenerationRequestedEvent.EVENT_TYPE;
    }

    @Override
    public void handle(PlatformEventEnvelope event) {
        TestDesignGenerationRequestedEvent payload = event.payloadAs(
                objectMapper,
                TestDesignGenerationRequestedEvent.class
        );
        service.processQueuedTask(payload.taskId());
    }
}
