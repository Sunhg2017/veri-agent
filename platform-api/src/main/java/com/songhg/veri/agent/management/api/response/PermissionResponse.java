package com.songhg.veri.agent.management.api.response;

import io.swagger.v3.oas.annotations.media.Schema;

public record PermissionResponse(
        @Schema(description = "业务编码，通常在同一资源范围内唯一。")
        String code,
        @Schema(description = "资源类型。")
        String resourceType,
        @Schema(description = "操作类型或动作编码。")
        String action,
        @Schema(description = "权限支持的作用域掩码。")
        String scopeMask,
        @Schema(description = "业务说明或补充描述。")
        String description,
        @Schema(description = "业务状态。")
        String status
) {
}
