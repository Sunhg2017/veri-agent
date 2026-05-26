package com.songhg.veri.agent.management.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

public record UpdateIntegrationRequest(
        @Schema(description = "名称，用于列表展示和人工识别。")
        @Size(max = 64)
        String name,
        @Schema(description = "集成分类。")
        @Size(max = 64)
        String category,
        @Schema(description = "作用域说明或作用域编码。")
        @Size(max = 64)
        String scope
) {
}
