package com.songhg.veri.agent.management.api.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record EnvironmentConnectivityCheckResponse(
        @Schema(description = "环境名称或环境编码。")
        String environment,
        @Schema(description = "健康检查或连通性状态。")
        String status,
        @Schema(description = "检查时间。")
        String checkedAt,
        @Schema(description = "请求耗时，单位毫秒。")
        Long latencyMs,
        @Schema(description = "提示消息。")
        String message,
        @Schema(description = "链路追踪 ID。")
        String traceId,
        @Schema(description = "环境连通性端点列表。")
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
