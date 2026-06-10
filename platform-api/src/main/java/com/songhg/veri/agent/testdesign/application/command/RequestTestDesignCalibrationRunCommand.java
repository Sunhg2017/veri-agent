package com.songhg.veri.agent.testdesign.application.command;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * Starts a WP5 prompt calibration run against the maintained golden sample baseline.
 */
public record RequestTestDesignCalibrationRunCommand(
        @Schema(description = "所属项目 ID")
        @NotBlank String projectId,
        @Schema(description = "Prompt 模板标识")
        String promptKey,
        @Schema(description = "Prompt 版本")
        String promptVersion,
        @Schema(description = "基线版本")
        String baselineVersion,
        @Schema(description = "运行模式：MANUAL/PROMPT_CHANGE/SCHEDULED/BASELINE_FREEZE")
        String runMode,
        @Schema(description = "运行备注，最多 1000 字")
        String notes
) {
}
