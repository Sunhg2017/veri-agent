package com.songhg.veri.agent.management.application.view;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record EnvironmentConnectivityCheckView(
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
