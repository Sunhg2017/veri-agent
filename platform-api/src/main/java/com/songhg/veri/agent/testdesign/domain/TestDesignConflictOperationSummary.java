package com.songhg.veri.agent.testdesign.domain;

import java.time.Instant;

/**
 * 资产冲突运营台聚合计数，只返回状态分布，不暴露候选明细。
 */
public record TestDesignConflictOperationSummary(
        long totalCount,
        long openCount,
        long resolvedCount,
        long duplicateReviewCount,
        Instant latestConflictAt
) {
}
