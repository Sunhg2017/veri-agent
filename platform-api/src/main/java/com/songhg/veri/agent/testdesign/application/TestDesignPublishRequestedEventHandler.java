package com.songhg.veri.agent.testdesign.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.common.event.PlatformEventEnvelope;
import com.songhg.veri.agent.common.event.PlatformEventHandler;
import com.songhg.veri.agent.testdesign.application.event.TestDesignPublishRequestedEvent;
import org.springframework.stereotype.Component;

@Component
public class TestDesignPublishRequestedEventHandler implements PlatformEventHandler {

    private final ObjectMapper objectMapper;
    private final TestDesignPublishService publishService;

    public TestDesignPublishRequestedEventHandler(
            ObjectMapper objectMapper,
            TestDesignPublishService publishService
    ) {
        this.objectMapper = objectMapper;
        this.publishService = publishService;
    }

    @Override
    public String eventType() {
        return TestDesignPublishRequestedEvent.EVENT_TYPE;
    }

    @Override
    public void handle(PlatformEventEnvelope event) {
        TestDesignPublishRequestedEvent payload = event.payloadAs(
                objectMapper,
                TestDesignPublishRequestedEvent.class
        );
        publishService.processQueuedPublish(payload.taskId(), payload.candidateIds());
    }
}
