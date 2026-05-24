package com.songhg.veri.agent.management.application.view;

public record ProjectView(
        String name,
        String department,
        String owner,
        int apps,
        String status
) {
}
