package com.songhg.veri.agent.apiautomation.application.command;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateApiAutomationSpecCommand(
        @NotBlank
        @Schema(description = "项目 ID 或项目编码")
        String projectId,
        @NotBlank
        @Schema(description = "来源类型：TEXT/UPLOAD/URL")
        String sourceType,
        @NotBlank
        @Size(max = 128)
        @Schema(description = "OpenAPI 源名称")
        String name,
        @Size(max = 64)
        @Schema(description = "版本标签")
        String versionLabel,
        @Size(max = 512)
        @Schema(description = "外部来源引用，URL 来源仅保存脱敏引用")
        String sourceRef,
        @NotBlank
        @Schema(description = "OpenAPI JSON/YAML 内容；P0 不主动远程拉取 URL")
        String content
) {
}
