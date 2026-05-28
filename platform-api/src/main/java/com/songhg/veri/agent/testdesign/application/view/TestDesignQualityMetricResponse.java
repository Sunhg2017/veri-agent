package com.songhg.veri.agent.testdesign.application.view;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * WP5 任务级质量运营指标，只返回计数和比例，不承载候选正文。
 */
public record TestDesignQualityMetricResponse(
        @Schema(description = "指标编码")
        String code,
        @Schema(description = "指标数量")
        long count,
        @Schema(description = "占全部候选的百分比，保留两位小数")
        double percent
) {
}
