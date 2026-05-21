package com.songhg.veri.agent.management.api.response;

import java.util.List;

public record EnvironmentConnectivityCheckView(
        String environment,
        String status,
        String checkedAt,
        Long latencyMs,
        String message,
        String traceId,
        List<EnvironmentConnectivityEndpointView> endpoints
) {
    public static EnvironmentConnectivityCheckView notChecked(String environment) {
        return new EnvironmentConnectivityCheckView(
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
