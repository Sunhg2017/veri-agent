package com.songhg.veri.agent.testdesign.application.view;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 批量人工处理 WP5 发布冲突的接口出参。
 */
public record TestDesignConflictBatchResolveResponse(
        @Schema(description = "批量动作编码")
        String action,
        @Schema(description = "本次处理总数")
        int total,
        @Schema(description = "处理成功数量")
        int succeededCount,
        @Schema(description = "处理失败数量")
        int failedCount,
        @Schema(description = "逐项处理结果列表")
        List<TestDesignConflictBatchResolveItemResponse> items
) {
}
