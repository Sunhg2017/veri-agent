package com.songhg.veri.agent.management.api.response;

import java.util.List;

public record EnvironmentConnectivityCheckResponse(
        String environment,
        String status,
        String checkedAt,
        Long latencyMs,
        String message,
        String traceId,
        List<EnvironmentConnectivityEndpointResponse> endpoints
) {
    public static EnvironmentConnectivityCheckResponse notChecked(String environment) {
        return new EnvironmentConnectivityCheckResponse(
                environment,
                "SKIPPED",
                "",
                null,
                "尚未执行连通性检查",
                "",
                List.of()
        );
    }
}
