package com.songhg.veri.agent.testdesign.application.view;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Cross-WP audit-chain aggregate metric without operational identifiers.
 */
public record TestDesignAuditChainMetricResponse(
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
