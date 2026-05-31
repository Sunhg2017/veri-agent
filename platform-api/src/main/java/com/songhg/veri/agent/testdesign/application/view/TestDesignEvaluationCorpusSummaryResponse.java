package com.songhg.veri.agent.testdesign.application.view;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

/**
 * WP5 evaluation corpus operations summary with aggregate-only quality and feedback signals.
 */
public record TestDesignEvaluationCorpusSummaryResponse(
        @Schema(description = "所属项目 ID 过滤条件")
        String projectId,
        @Schema(description = "Prompt 模板标识过滤条件")
        String promptKey,
        @Schema(description = "评测语料运营策略快照")
        TestDesignEvaluationCorpusPolicyResponse policy,
        @Schema(description = "纳入聚合的最近已完成任务数量")
        long taskCount,
        @Schema(description = "纳入聚合的候选总数")
        long candidateCount,
        @Schema(description = "纳入聚合的 Prompt 版本桶数量")
        long promptVersionCount,
        @Schema(description = "Prompt 版本准出状态分布，按版本桶数量聚合")
        List<TestDesignQualityDistributionItemResponse> readinessDistribution,
        @Schema(description = "人工修正、驳回或忽略形成的 Prompt 调优信号数量")
        long feedbackSignalCount,
        @Schema(description = "具备调优信号的候选数量")
        long sampleCandidateCount,
        @Schema(description = "调优信号中带说明的数量")
        long sampleExplanationCount,
        @Schema(description = "样本说明覆盖率，百分比")
        double sampleExplanationCoveragePercent,
        @Schema(description = "是否只暴露聚合状态")
        boolean aggregateOnly,
        @Schema(description = "是否导出评测语料行")
        boolean corpusRowExported,
        @Schema(description = "是否导出候选正文")
        boolean candidateBodyExported,
        @Schema(description = "是否导出评审评论")
        boolean reviewCommentExported,
        @Schema(description = "是否导出 Prompt 正文")
        boolean promptBodyExported,
        @Schema(description = "生成时间")
        Instant generatedAt
) {
}
