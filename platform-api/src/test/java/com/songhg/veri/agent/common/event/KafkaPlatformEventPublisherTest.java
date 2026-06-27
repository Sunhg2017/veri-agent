package com.songhg.veri.agent.common.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.common.trace.TraceContext;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ObjectProvider;
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
    void fallsBackToDispatcherWhenKafkaPublishFails() throws Exception {
        KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
        CompletableFuture<SendResult<String, String>> failedSend = new CompletableFuture<>();
        failedSend.completeExceptionally(new IllegalStateException("kafka down"));
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(failedSend);

        AtomicReference<String> handledTraceId = new AtomicReference<>();
        CountDownLatch handledLatch = new CountDownLatch(1);
        PlatformEventHandler handler = new PlatformEventHandler() {
            @Override
            public String eventType() {
                return "test.kafka-fallback";
            }

            @Override
            public void handle(PlatformEventEnvelope event) {
                handledTraceId.set(TraceContext.getTraceId());
                handledLatch.countDown();
            }
        };
        KafkaPlatformEventPublisher publisher = new KafkaPlatformEventPublisher(
                kafkaTemplate,
                objectMapper,
                dispatcherProvider(handler)
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

        assertThat(handledLatch.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(handledTraceId.get()).isEqualTo("trc_kafka_fallback");
        publisher.shutdown();
    }

    private record Payload(String value) {
    }

    private ObjectProvider<PlatformEventDispatcher> dispatcherProvider(PlatformEventHandler handler) {
        PlatformEventDispatcher dispatcher = new PlatformEventDispatcher(java.util.List.of(handler));
        return new ObjectProvider<>() {
            @Override
            public PlatformEventDispatcher getObject() throws BeansException {
                return dispatcher;
            }

            @Override
            public PlatformEventDispatcher getObject(Object... args) throws BeansException {
                return dispatcher;
            }

            @Override
            public PlatformEventDispatcher getIfAvailable() throws BeansException {
                return dispatcher;
            }

            @Override
            public PlatformEventDispatcher getIfUnique() throws BeansException {
                return dispatcher;
            }
        };
    }
}
