package com.songhg.veri.agent.management.application;

public record EnvironmentConnectivityEndpointView(
        String target,
        String url,
        String status,
        Long latencyMs,
        Integer statusCode,
        String message
) {
}
