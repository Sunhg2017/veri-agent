package com.songhg.veri.agent.management.api.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record RoleDetailResponse(
        @Schema(description = "业务编码，通常在同一资源范围内唯一。")
        String code,
        @Schema(description = "名称，用于列表展示和人工识别。")
        String name,
        @Schema(description = "权限或配置作用域类型。")
        String scopeType,
        @Schema(description = "业务状态。")
        String status,
        @Schema(description = "业务说明或补充描述。")
        String description,
        @Schema(description = "是否系统内置。")
        boolean system,
        @Schema(description = "是否内置角色或配置。")
        boolean builtin,
        @Schema(description = "乐观锁或资产版本号，用于并发控制和审计追踪。")
        long version,
        @Schema(description = "权限编码列表。")
        List<String> permissionCodes
) {
}
