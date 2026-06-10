package com.songhg.veri.agent.testdesign.application.view;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 资产冲突运营台分页响应。
 */
public record TestDesignConflictOperationsResponse(
        @Schema(description = "当前页数据列表")
        List<TestDesignConflictOperationItemResponse> items,
        @Schema(description = "分页页码，从 0 开始")
        int index,
        @Schema(description = "每页条数")
        int size,
        @Schema(description = "满足当前处理状态筛选的数据总数")
        long total,
        @Schema(description = "当前筛选条件下的聚合概览")
        TestDesignConflictOperationsSummaryResponse summary
) {
}
