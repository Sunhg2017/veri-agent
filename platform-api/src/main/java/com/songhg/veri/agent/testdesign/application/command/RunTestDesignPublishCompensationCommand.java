package com.songhg.veri.agent.testdesign.application.command;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Requests a bounded manual run of the WP5 publish compensation backend.
 */
public record RunTestDesignPublishCompensationCommand(
        @Schema(description = "所属项目 ID")
        @NotBlank String projectId,
        @Schema(description = "Prompt 模板标识过滤条件")
        @Size(max = 128) String promptKey,
        @Schema(description = "单次最多处理候选数，范围 1-100")
        @Min(1) @Max(100) Integer maxItems,
        @Schema(description = "运行原因，服务端脱敏和截断后写入审计")
        @Size(max = 500) String reason
) {
}
