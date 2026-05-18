package com.songhg.veri.agent.management.api.response;

public record AuditLogView(
        String time,
        String actor,
        String action,
        String target,
        String result
) {
}
