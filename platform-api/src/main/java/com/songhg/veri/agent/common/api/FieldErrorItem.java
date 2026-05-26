package com.songhg.veri.agent.common.api;

import io.swagger.v3.oas.annotations.media.Schema;

public record FieldErrorItem(
        @Schema(description = "校验失败的字段路径。")
        String field,
        @Schema(description = "字段校验失败原因。")
        String reason
) {
}
