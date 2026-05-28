package com.songhg.veri.agent.testdesign.application.view;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * WP5 任务级质量摘要的分布项，只包含聚合计数和比例。
 */
public record TestDesignQualityDistributionItemResponse(
        @Schema(description = "分布标签，例如候选状态、覆盖类型或优先级")
        String label,
        @Schema(description = "匹配数量")
        long count,
        @Schema(description = "占全部候选的百分比，保留两位小数")
        double percent
) {
}
