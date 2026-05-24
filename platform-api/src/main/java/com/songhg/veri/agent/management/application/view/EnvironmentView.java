package com.songhg.veri.agent.management.application.view;

public record EnvironmentView(
        String name,
        String cluster,
        String endpoint,
        String status
) {
}
