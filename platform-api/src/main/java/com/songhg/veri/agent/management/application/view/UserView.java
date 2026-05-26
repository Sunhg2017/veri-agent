package com.songhg.veri.agent.management.application.view;

import io.swagger.v3.oas.annotations.media.Schema;

public record UserView(
        @Schema(description = "用户名。")
        String username,
        @Schema(description = "用户显示名。")
        String displayName,
        @Schema(description = "邮箱地址。")
        String email,
        @Schema(description = "角色名称或角色编码。")
        String role,
        @Schema(description = "所属部门。")
        String department,
        @Schema(description = "业务状态。")
        String status,
        @Schema(description = "最近活跃时间。")
        String lastSeen
) {
}
