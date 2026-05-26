package com.songhg.veri.agent.management.api.response;

import io.swagger.v3.oas.annotations.media.Schema;

public record EnvironmentResponse(
        @Schema(description = "名称，用于列表展示和人工识别")
        String name,
        @Schema(description = "集群或环境分组")
        String cluster,
        @Schema(description = "环境访问入口")
        String endpoint,
        @Schema(description = "业务状态")
        String status
) {
}
