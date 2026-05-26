package com.songhg.veri.agent.common.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.common.trace.TraceContext;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class KafkaPlatformEventPublisherTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @AfterEach
    void tearDown() {
        TraceContext.clear();
    }

    @Test
    void fallsBackToDispatcherWhenKafkaPublishFails() {
        KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
        CompletableFuture<SendResult<String, String>> failedSend = new CompletableFuture<>();
        failedSend.completeExceptionally(new IllegalStateException("kafka down"));
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(failedSend);

        AtomicReference<String> handledTraceId = new AtomicReference<>();
        PlatformEventHandler handler = new PlatformEventHandler() {
            @Override
            public String eventType() {
                return "test.kafka-fallback";
            }

            @Override
            public void handle(PlatformEventEnvelope event) {
                handledTraceId.set(TraceContext.getTraceId());
            }
        };
        KafkaPlatformEventPublisher publisher = new KafkaPlatformEventPublisher(
                kafkaTemplate,
                objectMapper,
                new PlatformEventDispatcher(List.of(handler))
        );
        PlatformEventEnvelope event = new PlatformEventEnvelope(
                "evt-kafka-fallback",
                "test.kafka-fallback",
                "agg-1",
                "trc_kafka_fallback",
                Instant.now(),
                objectMapper.valueToTree(new Payload("ok"))
        );

        publisher.publish("test-topic", event);

        assertThat(handledTraceId.get()).isEqualTo("trc_kafka_fallback");
        publisher.shutdown();
    }

    private record Payload(String value) {
    }
}
