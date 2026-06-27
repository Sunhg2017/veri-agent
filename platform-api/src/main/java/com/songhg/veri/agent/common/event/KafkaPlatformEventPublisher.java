package com.songhg.veri.agent.common.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.common.trace.TraceContext;
import jakarta.annotation.PreDestroy;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@Profile("kafka")
public class KafkaPlatformEventPublisher implements PlatformEventPublisher {

    public static final String EVENT_TYPE_HEADER = "X-Platform-Event-Type";

    private static final Logger log = LoggerFactory.getLogger(KafkaPlatformEventPublisher.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<PlatformEventDispatcher> dispatcherProvider;
    private final ScheduledThreadPoolExecutor delayExecutor;

    public KafkaPlatformEventPublisher(
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            ObjectProvider<PlatformEventDispatcher> dispatcherProvider
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.dispatcherProvider = dispatcherProvider;
        this.delayExecutor = new ScheduledThreadPoolExecutor(1, new KafkaDelayThreadFactory());
        this.delayExecutor.setRemoveOnCancelPolicy(true);
    }

    @Override
    public void publish(String topic, PlatformEventEnvelope event, Duration delay) {
        long delayMs = Math.max(0, delay == null ? 0 : delay.toMillis());
        if (delayMs == 0) {
            send(topic, event);
            return;
        }
        delayExecutor.schedule(() -> send(topic, event), delayMs, TimeUnit.MILLISECONDS);
    }

    private void send(String topic, PlatformEventEnvelope event) {
        try (TraceContext.TraceScope ignored = TraceContext.open(event.traceId())) {
            ProducerRecord<String, String> record = new ProducerRecord<>(topic, event.aggregateId(), json(event));
            record.headers().add(TraceContext.TRACE_ID_HEADER, event.traceId().getBytes(StandardCharsets.UTF_8));
            record.headers().add(EVENT_TYPE_HEADER, event.eventType().getBytes(StandardCharsets.UTF_8));
            kafkaTemplate.send(record).whenComplete((result, exception) -> {
                try (TraceContext.TraceScope callbackScope = TraceContext.open(event.traceId())) {
                    if (exception != null) {
                        log.error("Kafka platform event publish failed topic={}, event_type={}, event_id={}",
                                topic, event.eventType(), event.eventId(), exception);
                        // 仅在 Kafka 发布失败时再解析 dispatcher，避免启动期提前拉起回退链路。
                        dispatcherProvider.getObject().dispatch(event);
                        return;
                    }
                    log.info("Kafka platform event published topic={}, partition={}, offset={}, event_type={}, event_id={}",
                            result.getRecordMetadata().topic(),
                            result.getRecordMetadata().partition(),
                            result.getRecordMetadata().offset(),
                            event.eventType(),
                            event.eventId());
                }
            });
        }
    }

    private String json(PlatformEventEnvelope event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "事件载荷无法序列化: " + event.eventType());
        }
    }

    @PreDestroy
    public void shutdown() {
        delayExecutor.shutdownNow();
    }

    private static final class KafkaDelayThreadFactory implements ThreadFactory {

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "platform-kafka-event-delay");
            thread.setDaemon(true);
            return thread;
        }
    }
}
