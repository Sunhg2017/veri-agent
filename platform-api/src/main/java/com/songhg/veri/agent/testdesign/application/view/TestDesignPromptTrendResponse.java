package com.songhg.veri.agent.testdesign.application.view;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

/**
 * WP5 Prompt 版本质量趋势摘要，只暴露聚合指标。
 */
public record TestDesignPromptTrendResponse(
        @Schema(description = "所属项目 ID 过滤条件")
        String projectId,
        @Schema(description = "Prompt 模板标识过滤条件")
        String promptKey,
        @Schema(description = "纳入聚合的最近任务数量")
        long taskCount,
        @Schema(description = "纳入聚合的候选总数")
        long candidateCount,
        @Schema(description = "版本聚合桶")
        List<TestDesignPromptTrendBucketResponse> buckets,
        @Schema(description = "生成时间")
        Instant generatedAt
) {
}
