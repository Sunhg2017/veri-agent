package com.songhg.veri.agent.management.application.command;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CreateRoleCommand(
        @Schema(description = "业务编码，通常在同一资源范围内唯一")
        @NotBlank
        @Size(max = 64)
        @Pattern(regexp = "^[A-Za-z][A-Za-z0-9_-]{2,63}$")
        String code,
        @Schema(description = "名称，用于列表展示和人工识别")
        @NotBlank
        @Size(max = 64)
        String name,
        @Schema(description = "权限或配置作用域类型")
        @NotBlank
        @Pattern(regexp = "^(PLATFORM|DEPARTMENT|PROJECT|APPLICATION|ENVIRONMENT)$")
        String scopeType,
        @Schema(description = "业务说明或补充描述")
        @Size(max = 512)
        String description,
        @Schema(description = "权限编码列表")
        @NotEmpty
        @Size(max = 128)
        List<
        @NotBlank
        @Size(max = 128)
        @Pattern(regexp = "^[A-Za-z][A-Za-z0-9:_-]*$")
        String
        > permissionCodes
) {
}
