package com.songhg.veri.agent.testdesign.application.view;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * WP5 任务报告归档治理策略聚合快照。
 */
public record TestDesignArchivePolicyResponse(
        @Schema(description = "归档策略版本")
        String policyVersion,
        @Schema(description = "报告保留天数，服务端按 1-3650 有界化")
        int retentionDays,
        @Schema(description = "归档存储策略")
        String storagePolicy,
        @Schema(description = "进入正式归档前是否需要审批")
        boolean approvalRequired,
        @Schema(description = "归档审批流是否就绪")
        boolean archiveApprovalWorkflowReady,
        @Schema(description = "是否允许归档外发")
        boolean externalSharingAllowed,
        @Schema(description = "归档保留策略是否已纳入跟踪")
        boolean retentionPolicyTracked,
        @Schema(description = "真实归档存储是否就绪")
        boolean archiveStorageReady,
        @Schema(description = "是否导出归档路径")
        boolean archivePathExported,
        @Schema(description = "是否导出归档备注")
        boolean archiveNotesExported,
        @Schema(description = "是否导出审批说明")
        boolean approvalNotesExported,
        @Schema(description = "是否导出工单 URL")
        boolean ticketUrlExported,
        @Schema(description = "是否只暴露聚合状态")
        boolean aggregateOnly
) {
}
