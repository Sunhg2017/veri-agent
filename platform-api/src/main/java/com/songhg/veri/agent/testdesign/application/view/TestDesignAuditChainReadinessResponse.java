package com.songhg.veri.agent.testdesign.application.view;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Readiness item for the WP5 cross-WP audit-chain operations dashboard skeleton.
 */
public record TestDesignAuditChainReadinessResponse(
        @Schema(description = "检查编码")
        String code,
        @Schema(description = "检查名称")
        String label,
        @Schema(description = "是否已满足当前 aggregate-only 看板要求")
        boolean ready,
        @Schema(description = "状态语义")
        String tone,
        @Schema(description = "说明")
        String description
) {
}
