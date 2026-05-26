package com.songhg.veri.agent.common.api;

import io.swagger.v3.oas.annotations.media.Schema;

public record ApiResponse<T>(
        @Schema(description = "业务响应码，成功时为 OK。")
        String code,
        @Schema(description = "响应消息，失败时返回可读错误摘要。")
        String message,
        @Schema(description = "链路追踪 ID，用于前后端和日志排障。")
        String traceId,
        @Schema(description = "业务响应数据。")
        T data
) {

    public static <T> ApiResponse<T> ok(T data, String traceId) {
        return new ApiResponse<>("OK", "success", traceId, data);
    }

    public static <T> ApiResponse<T> error(String code, String message, String traceId, T data) {
        return new ApiResponse<>(code, message, traceId, data);
    }
}
