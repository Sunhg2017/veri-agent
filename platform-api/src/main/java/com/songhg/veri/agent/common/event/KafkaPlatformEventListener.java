package com.songhg.veri.agent.common.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.common.trace.TraceContext;
import java.nio.charset.StandardCharsets;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@Profile("kafka")
public class KafkaPlatformEventListener {

    private static final Logger log = LoggerFactory.getLogger(KafkaPlatformEventListener.class);

    private final ObjectMapper objectMapper;
    private final PlatformEventDispatcher dispatcher;

    public KafkaPlatformEventListener(ObjectMapper objectMapper, PlatformEventDispatcher dispatcher) {
        this.objectMapper = objectMapper;
        this.dispatcher = dispatcher;
    }

    @KafkaListener(
            topics = {
                    "${veri-agent.events.kafka.topics.model-invocation-job-requested:veri-agent.model-invocation-job-requested}",
                    "${veri-agent.events.kafka.topics.audit-log-recorded:veri-agent.audit-log-recorded}",
                    "${veri-agent.events.kafka.topics.document-input-import-requested:veri-agent.document-input-import-requested}",
                    "${veri-agent.events.kafka.topics.document-input-publish-requested:veri-agent.document-input-publish-requested}",
                    "${veri-agent.events.kafka.topics.document-input-webhook-accepted:veri-agent.document-input-webhook-accepted}",
                    "${veri-agent.events.kafka.topics.test-design-generation-requested:veri-agent.test-design-generation-requested}",
                    "${veri-agent.events.kafka.topics.test-design-publish-requested:veri-agent.test-design-publish-requested}"
            },
            groupId = "${veri-agent.events.kafka.consumer-group:platform-api}"
    )
    public void onMessage(ConsumerRecord<String, String> record) {
        PlatformEventEnvelope event = parse(record.value());
        try (TraceContext.TraceScope ignored = TraceContext.open(event.traceId())) {
            log.info("Kafka platform event received topic={}, partition={}, offset={}, event_type={}, event_id={}, header_trace_id={}",
                    record.topic(),
                    record.partition(),
                    record.offset(),
                    event.eventType(),
                    event.eventId(),
                    header(record, TraceContext.TRACE_ID_HEADER));
            dispatcher.dispatch(event);
        }
    }

    private PlatformEventEnvelope parse(String value) {
        try {
            return objectMapper.readValue(value, PlatformEventEnvelope.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Kafka platform event payload is invalid", exception);
        }
    }

    private String header(ConsumerRecord<String, String> record, String name) {
        Header header = record.headers().lastHeader(name);
        return header == null ? "" : new String(header.value(), StandardCharsets.UTF_8);
    }
}
