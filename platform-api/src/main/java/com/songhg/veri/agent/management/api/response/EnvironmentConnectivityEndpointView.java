package com.songhg.veri.agent.management.api.response;

public record EnvironmentConnectivityEndpointView(
        String target,
        String url,
        String status,
        Long latencyMs,
        Integer statusCode,
        String message
) {
}
