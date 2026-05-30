package com.songhg.veri.agent.testdesign.application.view;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * WP5 任务本域审计链聚合指标。
 */
public record TestDesignAuditSummaryMetricResponse(
        @Schema(description = "指标编码")
        String code,
        @Schema(description = "指标展示名称")
        String label,
        @Schema(description = "指标计数")
        long count,
        @Schema(description = "指标状态语义")
        String tone
) {
}
