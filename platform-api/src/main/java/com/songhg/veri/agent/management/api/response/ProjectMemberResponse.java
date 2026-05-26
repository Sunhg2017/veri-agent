package com.songhg.veri.agent.management.api.response;

import io.swagger.v3.oas.annotations.media.Schema;

public record ProjectMemberResponse(
        @Schema(description = "用户名")
        String username,
        @Schema(description = "用户显示名")
        String displayName,
        @Schema(description = "角色名称或角色编码")
        String role,
        @Schema(description = "成员类型")
        String memberType,
        @Schema(description = "业务状态")
        String status
) {
}
