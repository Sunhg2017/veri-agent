package com.songhg.veri.agent.common.audit;

import com.songhg.veri.agent.common.audit.mapper.AuditMapper;
import com.songhg.veri.agent.common.trace.TraceContext;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Profile("db")
@Component
public class JdbcAuditLogWriter implements AuditLogWriter {

    private final AuditMapper mapper;

    public JdbcAuditLogWriter(AuditMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(AuditRecord record) {
        UUID actorId = record.actor() == null ? null : record.actor().userId();
        mapper.insertAuditLog(
                TraceContext.getTraceId(),
                actorId == null ? "SYSTEM" : "USER",
                actorId,
                record.action(),
                record.resourceType(),
                record.resourceId(),
                record.result(),
                jsonOrNull(record.beforeJson()),
                jsonOrDefault(record.afterJson(), defaultAfterJson(record, actorId)),
                jsonOrNull(record.diffJson()),
                record.reason()
        );
    }

    private String jsonOrNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private String jsonOrDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private String defaultAfterJson(AuditRecord record, UUID actorId) {
        StringBuilder json = new StringBuilder("{");
        boolean needsComma = false;
        if (StringUtils.hasText(record.targetName())) {
            json.append("\"name\":\"").append(escapeJson(record.targetName())).append("\"");
            needsComma = true;
        }
        if (StringUtils.hasText(record.resourceId())) {
            if (needsComma) {
                json.append(',');
            }
            json.append("\"resourceId\":\"").append(escapeJson(record.resourceId())).append("\"");
            needsComma = true;
        }
        if (needsComma) {
            json.append(',');
        }
        json.append("\"actorType\":\"").append(actorId == null ? "SYSTEM" : "USER").append("\"");
        if (actorId != null) {
            json.append(",\"actorUserId\":\"").append(actorId).append("\"");
        }
        json.append('}');
        return json.toString();
    }

    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
