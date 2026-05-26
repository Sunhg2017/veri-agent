package com.songhg.veri.agent.common.event;

import com.songhg.veri.agent.common.trace.TraceContext;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class PlatformEventDispatcher {

    private static final Logger log = LoggerFactory.getLogger(PlatformEventDispatcher.class);

    private final Map<String, List<PlatformEventHandler>> handlersByType;

    public PlatformEventDispatcher(List<PlatformEventHandler> handlers) {
        this.handlersByType = handlers.stream()
                .collect(Collectors.groupingBy(PlatformEventHandler::eventType));
    }

    /**
     * Dispatches an event with its original trace restored so async logs remain queryable by one traceId.
     */
    public void dispatch(PlatformEventEnvelope event) {
        try (TraceContext.TraceScope ignored = TraceContext.open(event.traceId())) {
            List<PlatformEventHandler> handlers = handlersByType.getOrDefault(event.eventType(), List.of());
            if (handlers.isEmpty()) {
                log.warn("No platform event handler registered, event_type={}, event_id={}",
                        event.eventType(), event.eventId());
                return;
            }
            for (PlatformEventHandler handler : handlers) {
                handler.handle(event);
            }
        }
    }
}
