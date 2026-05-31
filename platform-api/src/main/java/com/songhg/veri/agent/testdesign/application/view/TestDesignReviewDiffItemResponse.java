package com.songhg.veri.agent.testdesign.application.view;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * WP5 候选评审字段级差异预览。
 */
public record TestDesignReviewDiffItemResponse(
        @Schema(description = "变更字段")
        String field,
        @Schema(description = "操作前脱敏预览")
        String before,
        @Schema(description = "操作后脱敏预览")
        String after
) {
}
