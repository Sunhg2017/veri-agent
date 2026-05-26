package com.songhg.veri.agent.management.application.view;

import io.swagger.v3.oas.annotations.media.Schema;

public record EnvironmentConnectivityEndpointView(
        @Schema(description = "探测目标")
        String target,
        @Schema(description = "URL 地址")
        String url,
        @Schema(description = "业务状态")
        String status,
        @Schema(description = "请求耗时，单位毫秒")
        Long latencyMs,
        @Schema(description = "HTTP 状态码")
        Integer statusCode,
        @Schema(description = "提示消息")
        String message
) {
}
