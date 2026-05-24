package com.songhg.veri.agent.management.application;

public record EnvironmentView(
        String name,
        String cluster,
        String endpoint,
        String status
) {
}
