package com.songhg.veri.agent.common.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.common.event.PlatformEventEnvelope;
import com.songhg.veri.agent.common.event.PlatformEventProperties;
import com.songhg.veri.agent.common.event.PlatformEventPublisher;
import com.songhg.veri.agent.common.trace.TraceContext;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Profile("db & kafka")
@Component
public class KafkaAuditLogWriter implements AuditLogWriter {

    private final PlatformEventPublisher eventPublisher;
    private final PlatformEventProperties eventProperties;
    private final ObjectMapper objectMapper;

    public KafkaAuditLogWriter(
            PlatformEventPublisher eventPublisher,
            PlatformEventProperties eventProperties,
            ObjectMapper objectMapper
    ) {
        this.eventPublisher = eventPublisher;
        this.eventProperties = eventProperties;
        this.objectMapper = objectMapper;
    }

    @Override
    public void record(AuditRecord record) {
        AuditLogEntry entry = AuditLogEntry.from(record);
        String traceId = TraceContext.getOrCreateTraceId();
        eventPublisher.publish(
                eventProperties.auditLogRecordedTopic(),
                new PlatformEventEnvelope(
                        "evt_audit_" + UUID.randomUUID().toString().replace("-", ""),
                        AuditLogRecordedEventHandler.EVENT_TYPE,
                        aggregateId(entry),
                        traceId,
                        Instant.now(),
                        objectMapper.valueToTree(entry)
                ),
                Duration.ZERO
        );
    }

    private String aggregateId(AuditLogEntry entry) {
        if (entry.resourceId() != null && !entry.resourceId().isBlank()) {
            return entry.resourceId();
        }
        return entry.action();
    }
}
