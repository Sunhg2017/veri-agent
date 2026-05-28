package com.songhg.veri.agent.testdesign.application.view;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * WP5 任务质量准出检查项，只描述聚合阈值和当前值。
 */
public record TestDesignQualityReadinessCheckResponse(
        @Schema(description = "检查项编码")
        String code,
        @Schema(description = "检查项名称")
        String label,
        @Schema(description = "检查状态，PASSED 或 FAILED")
        String status,
        @Schema(description = "失败严重级别，BLOCKING 或 WARNING")
        String severity,
        @Schema(description = "当前聚合值")
        double currentValue,
        @Schema(description = "阈值")
        double thresholdValue,
        @Schema(description = "单位，COUNT 或 PERCENT")
        String unit,
        @Schema(description = "检查口径说明")
        String description
) {
}
