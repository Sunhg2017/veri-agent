package com.songhg.veri.agent.modelaccess.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.common.event.PlatformEventEnvelope;
import com.songhg.veri.agent.common.event.PlatformEventHandler;
import com.songhg.veri.agent.modelaccess.application.event.ModelInvocationJobRequestedEvent;
import org.springframework.stereotype.Component;

@Component
public class ModelInvocationJobRequestedEventHandler implements PlatformEventHandler {

    private final ObjectMapper objectMapper;
    private final ModelInvocationJobExecutor executor;

    public ModelInvocationJobRequestedEventHandler(ObjectMapper objectMapper, ModelInvocationJobExecutor executor) {
        this.objectMapper = objectMapper;
        this.executor = executor;
    }

    @Override
    public String eventType() {
        return ModelInvocationJobRequestedEvent.EVENT_TYPE;
    }

    @Override
    public void handle(PlatformEventEnvelope event) {
        ModelInvocationJobRequestedEvent payload = event.payloadAs(objectMapper, ModelInvocationJobRequestedEvent.class);
        executor.execute(payload.jobId());
    }
}
