package com.songhg.veri.agent.common.audit;

import java.util.ArrayList;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Profile("local")
@Component
public class InMemoryAuditLogWriter implements AuditLogWriter {

    private static final List<AuditRecord> RECORDS = new ArrayList<>();

    public InMemoryAuditLogWriter() {
        synchronized (RECORDS) {
            RECORDS.clear();
        }
    }

    @Override
    public void record(AuditRecord record) {
        synchronized (RECORDS) {
            RECORDS.add(0, record);
        }
    }

    public static List<AuditRecord> records() {
        synchronized (RECORDS) {
            return List.copyOf(RECORDS);
        }
    }
}
