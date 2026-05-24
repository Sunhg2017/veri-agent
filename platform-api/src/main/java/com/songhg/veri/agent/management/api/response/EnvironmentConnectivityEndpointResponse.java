package com.songhg.veri.agent.management.api.response;

public record EnvironmentConnectivityEndpointResponse(
        String target,
        String url,
        String status,
        Long latencyMs,
        Integer statusCode,
        String message
) {
}
