package com.songhg.veri.agent.testdesign.application.view;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * WP5 评测语料运营策略聚合快照。
 */
public record TestDesignEvaluationCorpusPolicyResponse(
        @Schema(description = "评测语料策略版本")
        String policyVersion,
        @Schema(description = "评测语料模式")
        String corpusMode,
        @Schema(description = "质量门禁评测模式")
        String qualityGateMode,
        @Schema(description = "质量阈值来源")
        String thresholdSource,
        @Schema(description = "是否要求项目作用域隔离")
        boolean projectScopeRequired,
        @Schema(description = "是否要求 golden set 基线")
        boolean goldenSetBaselineRequired,
        @Schema(description = "AI 质量评测脚本是否就绪")
        boolean qualityEvalScriptReady,
        @Schema(description = "质量门禁是否已接入可选 AI 评测")
        boolean qualityGateIntegrated,
        @Schema(description = "是否跟踪准出状态分布")
        boolean readinessDistributionTracked,
        @Schema(description = "是否跟踪 Prompt 版本")
        boolean promptVersionTracked,
        @Schema(description = "评测语料是否按项目隔离")
        boolean evaluationCorpusProjectIsolated,
        @Schema(description = "样本维护流程是否就绪")
        boolean sampleMaintenanceReady,
        @Schema(description = "长期校准基线是否就绪")
        boolean longTermCalibrationReady,
        @Schema(description = "评测语料运营后台是否就绪")
        boolean operationsConsoleReady,
        @Schema(description = "是否导出评测语料行")
        boolean corpusRowExported,
        @Schema(description = "是否导出候选正文")
        boolean candidateBodyExported,
        @Schema(description = "是否导出评审评论")
        boolean reviewCommentExported,
        @Schema(description = "是否导出 Prompt 正文")
        boolean promptBodyExported,
        @Schema(description = "是否只暴露聚合状态")
        boolean aggregateOnly
) {
}
