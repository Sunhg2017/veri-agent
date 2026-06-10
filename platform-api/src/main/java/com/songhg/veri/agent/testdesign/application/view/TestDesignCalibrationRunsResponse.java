package com.songhg.veri.agent.testdesign.application.view;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * Paged WP5 calibration runs with the aggregate calibration readiness summary.
 */
public record TestDesignCalibrationRunsResponse(
        @Schema(description = "校准运行列表")
        List<TestDesignCalibrationRunResponse> items,
        @Schema(description = "分页页码")
        int index,
        @Schema(description = "每页条数")
        int size,
        @Schema(description = "满足筛选条件的总数")
        long total,
        @Schema(description = "长期校准汇总")
        TestDesignCalibrationSummaryResponse summary
) {
}
