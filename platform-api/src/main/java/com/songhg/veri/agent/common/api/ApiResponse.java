package com.songhg.veri.agent.common.api;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ApiResponse<T>(
        String code,
        String message,
        @JsonProperty("trace_id") String traceId,
        T data
) {

    public static <T> ApiResponse<T> ok(T data, String traceId) {
        return new ApiResponse<>("OK", "success", traceId, data);
    }

    public static <T> ApiResponse<T> error(String code, String message, String traceId, T data) {
        return new ApiResponse<>(code, message, traceId, data);
    }
}

