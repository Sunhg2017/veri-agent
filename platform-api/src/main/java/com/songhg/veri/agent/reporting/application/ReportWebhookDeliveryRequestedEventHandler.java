package com.songhg.veri.agent.reporting.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.common.event.PlatformEventEnvelope;
import com.songhg.veri.agent.common.event.PlatformEventHandler;
import com.songhg.veri.agent.reporting.application.event.ReportWebhookDeliveryRequestedEvent;
import org.springframework.stereotype.Component;

@Component
public class ReportWebhookDeliveryRequestedEventHandler implements PlatformEventHandler {

    private final ObjectMapper objectMapper;
    private final ReportingWebhookDispatcher dispatcher;

    public ReportWebhookDeliveryRequestedEventHandler(
            ObjectMapper objectMapper,
            ReportingWebhookDispatcher dispatcher
    ) {
        this.objectMapper = objectMapper;
        this.dispatcher = dispatcher;
    }

    @Override
    public String eventType() {
        return ReportWebhookDeliveryRequestedEvent.EVENT_TYPE;
    }

    @Override
    public void handle(PlatformEventEnvelope event) {
        ReportWebhookDeliveryRequestedEvent payload = event.payloadAs(
                objectMapper,
                ReportWebhookDeliveryRequestedEvent.class
        );
        dispatcher.dispatch(payload.reportId(), payload.terminalStatus());
    }
}
