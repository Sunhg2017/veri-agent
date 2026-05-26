package com.songhg.veri.agent.common.audit;

import com.songhg.veri.agent.common.trace.TraceContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Profile("db & !kafka")
@Component
public class JdbcAuditLogWriter implements AuditLogWriter {

    private final AuditLogAppender appender;

    public JdbcAuditLogWriter(AuditLogAppender appender) {
        this.appender = appender;
    }

    @Override
    public void record(AuditRecord record) {
        appender.append(TraceContext.getTraceId(), AuditLogEntry.from(record));
    }
}
