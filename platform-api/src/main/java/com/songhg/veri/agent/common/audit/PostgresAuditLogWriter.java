package com.songhg.veri.agent.common.audit;

import com.songhg.veri.agent.common.trace.TraceContext;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

@Profile("db")
@Component
public class PostgresAuditLogWriter implements AuditLogWriter {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public PostgresAuditLogWriter(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void record(AuditRecord record) {
        UUID actorId = record.actor() == null ? null : record.actor().userId();
        jdbcTemplate.update("""
                insert into audit_log (
                    trace_id,
                    actor_type,
                    actor_user_id,
                    action,
                    resource_type,
                    resource_id,
                    scope_type,
                    scope_id,
                    result,
                    before_json,
                    after_json,
                    diff_json,
                    reason
                )
                values (
                    :traceId,
                    :actorType,
                    :actorId,
                    :action,
                    :resourceType,
                    :resourceId,
                    'PLATFORM',
                    null,
                    :result,
                    cast(:beforeJson as jsonb),
                    cast(:afterJson as jsonb),
                    cast(:diffJson as jsonb),
                    :reason
                )
                """,
                new MapSqlParameterSource()
                        .addValue("traceId", TraceContext.getTraceId())
                        .addValue("actorType", actorId == null ? "SYSTEM" : "USER")
                        .addValue("actorId", actorId)
                        .addValue("action", record.action())
                        .addValue("resourceType", record.resourceType())
                        .addValue("resourceId", record.resourceId())
                        .addValue("result", record.result())
                        .addValue("beforeJson", jsonOrNull(record.beforeJson()))
                        .addValue("afterJson", jsonOrDefault(record.afterJson(), "{\"name\":\"" + escapeJson(record.targetName()) + "\"}"))
                        .addValue("diffJson", jsonOrNull(record.diffJson()))
                        .addValue("reason", record.reason())
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
