package com.songhg.veri.agent.management.application.view;

import io.swagger.v3.oas.annotations.media.Schema;

public record IntegrationView(
        @Schema(description = "配置键。")
        String key,
        @Schema(description = "名称，用于列表展示和人工识别。")
        String name,
        @Schema(description = "集成分类。")
        String category,
        @Schema(description = "作用域说明或作用域编码。")
        String scope,
        @Schema(description = "业务状态。")
        String status
) {
}
