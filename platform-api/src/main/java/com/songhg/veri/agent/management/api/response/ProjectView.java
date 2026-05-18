package com.songhg.veri.agent.management.api.response;

public record ProjectView(
        String name,
        String department,
        String owner,
        int apps,
        String status
) {
}
