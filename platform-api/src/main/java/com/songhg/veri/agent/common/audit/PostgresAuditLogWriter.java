package com.songhg.veri.agent.common.audit;

import com.songhg.veri.agent.common.audit.mapper.AuditMapper;
import com.songhg.veri.agent.common.trace.TraceContext;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Profile("db")
@Component
public class PostgresAuditLogWriter implements AuditLogWriter {

    private final AuditMapper mapper;

    public PostgresAuditLogWriter(AuditMapper mapper) {
        this.mapper = mapper;
    }

    @Override
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
                jsonOrDefault(record.afterJson(), "{\"name\":\"" + escapeJson(record.targetName()) + "\"}"),
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

    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
