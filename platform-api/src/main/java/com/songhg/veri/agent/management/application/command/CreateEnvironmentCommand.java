package com.songhg.veri.agent.management.application.command;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateEnvironmentCommand(
        @Schema(description = "业务编码，通常在同一资源范围内唯一")
        @Size(max = 32)
        @Pattern(regexp = "^[A-Za-z0-9_-]*$")
        String code,
        @Schema(description = "名称，用于列表展示和人工识别")
        @NotBlank
        @Size(max = 64)
        String name,
        @Schema(description = "所属项目编码或名称")
        @Size(max = 64)
        String project,
        @Schema(description = "所属应用编码或名称")
        @Size(max = 64)
        String application,
        @Schema(description = "权限或配置作用域类型")
        @Pattern(regexp = "^(|PROJECT|APPLICATION)$")
        String scopeType,
        @Schema(description = "环境类型")
        @Pattern(regexp = "^(|DEV|TEST|STAGING|PREPROD|PROD)$")
        String envType,
        @Schema(description = "Web 访问地址")
        @Size(max = 512)
        String webUrl,
        @Schema(description = "API 基础地址")
        @Size(max = 512)
        String apiBaseUrl
) {
}
