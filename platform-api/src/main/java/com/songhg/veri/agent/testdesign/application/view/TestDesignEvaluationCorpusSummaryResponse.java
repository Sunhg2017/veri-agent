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
        @Schema(description = "维护样本总数")
        long maintainedSampleCount,
        @Schema(description = "golden 样本数量")
        long goldenSampleCount,
        @Schema(description = "冻结基线样本数量")
        long frozenSampleCount,
        @Schema(description = "废弃样本数量")
        long deprecatedSampleCount,
        @Schema(description = "维护样本基线版本数量")
        long baselineVersionCount,
        @Schema(description = "校准运行总数")
        long calibrationRunCount,
        @Schema(description = "最近校准状态")
        String latestCalibrationStatus,
        @Schema(description = "最近校准时间")
        Instant latestCalibrationAt,
        @Schema(description = "样本维护流程是否就绪")
        boolean sampleMaintenanceReady,
        @Schema(description = "长期校准基线是否就绪")
        boolean longTermCalibrationReady,
        @Schema(description = "评测语料运营后台是否就绪")
        boolean operationsConsoleReady,
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
