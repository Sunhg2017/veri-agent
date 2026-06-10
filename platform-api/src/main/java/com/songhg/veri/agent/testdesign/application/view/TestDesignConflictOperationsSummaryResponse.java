package com.songhg.veri.agent.testdesign.application.view;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * 资产冲突运营台聚合概览。
 */
public record TestDesignConflictOperationsSummaryResponse(
        @Schema(description = "当前筛选条件下的冲突总数，不受处理状态筛选影响")
        long totalCount,
        @Schema(description = "未处理冲突数")
        long openCount,
        @Schema(description = "已处理冲突数")
        long resolvedCount,
        @Schema(description = "需人工复核的重复冲突数")
        long duplicateReviewCount,
        @Schema(description = "最近冲突发生时间")
        Instant latestConflictAt
) {
}
