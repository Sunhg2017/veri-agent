package com.songhg.veri.agent.management.api;

public record EnvironmentView(
        String name,
        String cluster,
        String endpoint,
        String status
) {
}
