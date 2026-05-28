package com.songhg.veri.agent.testdesign.application.view;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * WP5 任务质量准出摘要，用于前端运营提示和报告归档。
 */
public record TestDesignQualityReadinessResponse(
        @Schema(description = "总体准出状态，PASSED、WARNING 或 BLOCKED")
        String status,
        @Schema(description = "阻断项数量")
        long blockingCount,
        @Schema(description = "风险提示项数量")
        long warningCount,
        @Schema(description = "逐项准出检查")
        List<TestDesignQualityReadinessCheckResponse> checks
) {
}
