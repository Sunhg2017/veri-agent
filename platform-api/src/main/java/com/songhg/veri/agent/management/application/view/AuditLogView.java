package com.songhg.veri.agent.management.application.view;

public record AuditLogView(
        String time,
        String actor,
        String action,
        String target,
        String result
) {
}
