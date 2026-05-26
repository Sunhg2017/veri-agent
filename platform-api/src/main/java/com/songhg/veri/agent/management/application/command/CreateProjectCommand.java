package com.songhg.veri.agent.management.application.command;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateProjectCommand(
        @Schema(description = "业务编码，通常在同一资源范围内唯一。")
        @Size(max = 32)
        @Pattern(regexp = "^[A-Za-z0-9_-]*$")
        String code,
        @Schema(description = "名称，用于列表展示和人工识别。")
        @NotBlank
        @Size(max = 64)
        String name,
        @Schema(description = "敏感级别，用于模型调用和数据出域策略。")
        @Pattern(regexp = "^(|PUBLIC|INTERNAL|CONFIDENTIAL|STRICT)$")
        String sensitivityLevel,
        @Schema(description = "是否允许使用公网模型处理该范围数据。")
        Boolean allowPublicModel
) {
}
