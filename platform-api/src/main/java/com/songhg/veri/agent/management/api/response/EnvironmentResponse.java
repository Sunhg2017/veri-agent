package com.songhg.veri.agent.management.api.response;

public record EnvironmentResponse(
        String name,
        String cluster,
        String endpoint,
        String status
) {
}
