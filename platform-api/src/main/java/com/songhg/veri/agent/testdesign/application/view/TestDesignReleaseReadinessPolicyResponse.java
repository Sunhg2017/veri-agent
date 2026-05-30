package com.songhg.veri.agent.testdesign.application.view;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * WP5 发布准出审批与阻断策略聚合快照。
 */
public record TestDesignReleaseReadinessPolicyResponse(
        @Schema(description = "发布准出策略版本")
        String policyVersion,
        @Schema(description = "准出决策模式")
        String decisionMode,
        @Schema(description = "准出阈值来源")
        String thresholdSource,
        @Schema(description = "质量阈值是否已评估")
        boolean qualityThresholdEvaluated,
        @Schema(description = "是否只作为运营建议")
        boolean advisoryOnly,
        @Schema(description = "是否启用发布阻断")
        boolean publishBlockingEnabled,
        @Schema(description = "是否要求人工准出审批")
        boolean manualApprovalRequired,
        @Schema(description = "人工准出审批流是否就绪")
        boolean approvalWorkflowReady,
        @Schema(description = "是否允许自动发布")
        boolean autoPublishAllowed,
        @Schema(description = "发布是否仍要求候选已确认")
        boolean confirmedCandidateRequired,
        @Schema(description = "是否支持质量门禁覆盖例外")
        boolean qualityGateOverrideSupported,
        @Schema(description = "是否导出候选级准出证据")
        boolean candidateEvidenceExported,
        @Schema(description = "是否导出审批备注")
        boolean approvalNotesExported,
        @Schema(description = "是否导出阈值规则明细")
        boolean thresholdRuleDetailExported,
        @Schema(description = "是否只暴露聚合状态")
        boolean aggregateOnly
) {
}
