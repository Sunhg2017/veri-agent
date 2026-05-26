package com.songhg.veri.agent.common.event;

import com.songhg.veri.agent.common.trace.TraceContext;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!kafka")
public class LocalPlatformEventPublisher implements PlatformEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(LocalPlatformEventPublisher.class);

    private final PlatformEventDispatcher dispatcher;
    private final ScheduledThreadPoolExecutor executor;

    public LocalPlatformEventPublisher(PlatformEventDispatcher dispatcher, PlatformEventProperties properties) {
        this.dispatcher = dispatcher;
        this.executor = new ScheduledThreadPoolExecutor(
                properties.safeLocalWorkerThreads(),
                new LocalEventThreadFactory()
        );
        this.executor.setRemoveOnCancelPolicy(true);
    }

    @Override
    public void publish(String topic, PlatformEventEnvelope event, Duration delay) {
        long delayMs = Math.max(0, delay == null ? 0 : delay.toMillis());
        executor.schedule(() -> {
            try (TraceContext.TraceScope ignored = TraceContext.open(event.traceId())) {
                log.info("Dispatching local platform event topic={}, event_type={}, event_id={}",
                        topic, event.eventType(), event.eventId());
                try {
                    dispatcher.dispatch(event);
                } catch (RuntimeException exception) {
                    log.error("Local platform event dispatch failed topic={}, event_type={}, event_id={}",
                            topic, event.eventType(), event.eventId(), exception);
                }
            }
        }, delayMs, TimeUnit.MILLISECONDS);
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }

    private static final class LocalEventThreadFactory implements ThreadFactory {

        private int index = 0;

        @Override
        public synchronized Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "platform-local-event-" + (++index));
            thread.setDaemon(true);
            return thread;
        }
    }
}
