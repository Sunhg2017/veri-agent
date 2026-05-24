package com.songhg.veri.agent.management.application.view;

public record EnvironmentConnectivityEndpointView(
        String target,
        String url,
        String status,
        Long latencyMs,
        Integer statusCode,
        String message
) {
}
