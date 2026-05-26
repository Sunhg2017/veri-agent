package com.songhg.veri.agent.management.application.view;

import io.swagger.v3.oas.annotations.media.Schema;

public record RoleView(
        @Schema(description = "业务编码，通常在同一资源范围内唯一。")
        String code,
        @Schema(description = "名称，用于列表展示和人工识别。")
        String name,
        @Schema(description = "权限或配置作用域类型。")
        String scopeType,
        @Schema(description = "业务状态。")
        String status,
        @Schema(description = "业务说明或补充描述。")
        String description
) {
}
