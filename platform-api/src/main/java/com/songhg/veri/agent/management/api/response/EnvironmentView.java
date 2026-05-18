package com.songhg.veri.agent.management.api.response;

public record EnvironmentView(
        String name,
        String cluster,
        String endpoint,
        String status
) {
}
