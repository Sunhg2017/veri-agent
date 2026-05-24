package com.songhg.veri.agent.management.application;

public record ProjectView(
        String name,
        String department,
        String owner,
        int apps,
        String status
) {
}
