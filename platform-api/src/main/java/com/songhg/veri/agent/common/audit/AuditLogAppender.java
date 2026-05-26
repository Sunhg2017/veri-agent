package com.songhg.veri.agent.common.audit;

import com.songhg.veri.agent.common.audit.mapper.AuditMapper;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Profile("db")
@Component
public class AuditLogAppender {

    private final AuditMapper mapper;

    public AuditLogAppender(AuditMapper mapper) {
        this.mapper = mapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void append(String traceId, AuditLogEntry entry) {
        UUID actorId = entry.actorUserId();
        mapper.insertAuditLog(
                traceId,
                actorId == null ? "SYSTEM" : "USER",
                actorId,
                entry.action(),
                entry.resourceType(),
                entry.resourceId(),
                entry.result(),
                jsonOrNull(entry.beforeJson()),
                jsonOrDefault(entry.afterJson(), defaultAfterJson(entry, actorId)),
                jsonOrNull(entry.diffJson()),
                entry.reason()
        );
    }

    private String jsonOrNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private String jsonOrDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private String defaultAfterJson(AuditLogEntry entry, UUID actorId) {
        StringBuilder json = new StringBuilder("{");
        boolean needsComma = false;
        if (StringUtils.hasText(entry.targetName())) {
            json.append("\"name\":\"").append(escapeJson(entry.targetName())).append("\"");
            needsComma = true;
        }
        if (StringUtils.hasText(entry.resourceId())) {
            if (needsComma) {
                json.append(',');
            }
            json.append("\"resourceId\":\"").append(escapeJson(entry.resourceId())).append("\"");
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
