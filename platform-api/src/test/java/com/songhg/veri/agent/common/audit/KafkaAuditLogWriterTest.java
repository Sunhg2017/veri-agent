package com.songhg.veri.agent.common.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.common.event.PlatformEventEnvelope;
import com.songhg.veri.agent.common.event.PlatformEventProperties;
import com.songhg.veri.agent.common.event.PlatformEventPublisher;
import com.songhg.veri.agent.common.trace.TraceContext;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class KafkaAuditLogWriterTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @AfterEach
    void tearDown() {
        TraceContext.clear();
    }

    @Test
    void publishesAuditRecordWithCurrentTraceId() {
        AtomicReference<String> topic = new AtomicReference<>();
        AtomicReference<PlatformEventEnvelope> envelope = new AtomicReference<>();
        PlatformEventPublisher publisher = (publishedTopic, event, delay) -> {
            topic.set(publishedTopic);
            envelope.set(event);
            assertThat(delay).isEqualTo(Duration.ZERO);
        };
        KafkaAuditLogWriter writer = new KafkaAuditLogWriter(
                publisher,
                new PlatformEventProperties(1, null),
                objectMapper
        );

        try (TraceContext.TraceScope ignored = TraceContext.open("trc_audit_async")) {
            writer.record(AuditLogWriter.denied(
                    null,
                    "权限校验",
                    "permission",
                    "asset:manage",
                    "缺少权限"
            ));
        }

        assertThat(topic.get()).isEqualTo("veri-agent.audit-log-recorded");
        assertThat(envelope.get().eventType()).isEqualTo(AuditLogRecordedEventHandler.EVENT_TYPE);
        assertThat(envelope.get().traceId()).isEqualTo("trc_audit_async");
        AuditLogEntry entry = objectMapper.convertValue(envelope.get().payload(), AuditLogEntry.class);
        assertThat(entry.result()).isEqualTo("DENIED");
        assertThat(entry.reason()).isEqualTo("缺少权限");
    }
}
