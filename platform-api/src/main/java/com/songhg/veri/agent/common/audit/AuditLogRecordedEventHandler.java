package com.songhg.veri.agent.common.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.common.event.PlatformEventEnvelope;
import com.songhg.veri.agent.common.event.PlatformEventHandler;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Profile("db")
@Component
public class AuditLogRecordedEventHandler implements PlatformEventHandler {

    public static final String EVENT_TYPE = "audit.log-recorded";

    private final AuditLogAppender appender;
    private final ObjectMapper objectMapper;

    public AuditLogRecordedEventHandler(AuditLogAppender appender, ObjectMapper objectMapper) {
        this.appender = appender;
        this.objectMapper = objectMapper;
    }

    @Override
    public String eventType() {
        return EVENT_TYPE;
    }

    @Override
    public void handle(PlatformEventEnvelope event) {
        appender.append(event.traceId(), entry(event));
    }

    private AuditLogEntry entry(PlatformEventEnvelope event) {
        try {
            return objectMapper.treeToValue(event.payload(), AuditLogEntry.class);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "审计事件载荷无法解析");
        }
    }
}
