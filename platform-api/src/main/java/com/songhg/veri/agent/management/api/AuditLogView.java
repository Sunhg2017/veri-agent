package com.songhg.veri.agent.management.api;

public record AuditLogView(
        String time,
        String actor,
        String action,
        String target,
        String result
) {
}
