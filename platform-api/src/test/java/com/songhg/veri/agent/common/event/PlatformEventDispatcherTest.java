package com.songhg.veri.agent.common.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.common.trace.TraceContext;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import static org.assertj.core.api.Assertions.assertThat;

class PlatformEventDispatcherTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @AfterEach
    void tearDown() {
        MDC.remove(TraceContext.MDC_TRACE_ID);
        TraceContext.clear();
    }

    @Test
    void restoresEventTraceDuringHandlerAndPreviousTraceAfterDispatch() {
        AtomicReference<String> handledTrace = new AtomicReference<>();
        PlatformEventHandler handler = new PlatformEventHandler() {
            @Override
            public String eventType() {
                return "test.event";
            }

            @Override
            public void handle(PlatformEventEnvelope event) {
                handledTrace.set(TraceContext.getTraceId() + "|" + MDC.get(TraceContext.MDC_TRACE_ID));
            }
        };
        PlatformEventDispatcher dispatcher = new PlatformEventDispatcher(List.of(handler));
        PlatformEventEnvelope event = new PlatformEventEnvelope(
                "evt-1",
                "test.event",
                "agg-1",
                "trc_event_chain",
                Instant.now(),
                objectMapper.valueToTree(new Payload("ok"))
        );

        TraceContext.setTraceId("trc_before");
        MDC.put(TraceContext.MDC_TRACE_ID, "trc_before");
        dispatcher.dispatch(event);

        assertThat(handledTrace.get()).isEqualTo("trc_event_chain|trc_event_chain");
        assertThat(TraceContext.getTraceId()).isEqualTo("trc_before");
        assertThat(MDC.get(TraceContext.MDC_TRACE_ID)).isEqualTo("trc_before");
    }

    private record Payload(String value) {
    }
}
