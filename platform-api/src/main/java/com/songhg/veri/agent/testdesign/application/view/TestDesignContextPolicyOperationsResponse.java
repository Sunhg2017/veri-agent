package com.songhg.veri.agent.testdesign.application.view;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * WP5 上下文策略运营 v2 聚合快照
 */
public record TestDesignContextPolicyOperationsResponse(
        @Schema(description = "上下文策略运营版本")
        String policyVersion,
        @Schema(description = "当前运营模式")
        String operationMode,
        @Schema(description = "策略解析顺序")
        String policyResolutionOrder,
        @Schema(description = "策略缺口 fallback 行为")
        String policyFallbackBehavior,
        @Schema(description = "策略审批状态")
        String approvalStatus,
        @Schema(description = "项目级策略覆盖存储是否就绪")
        boolean projectOverrideStoreReady,
        @Schema(description = "环境级策略覆盖存储是否就绪")
        boolean environmentOverrideStoreReady,
        @Schema(description = "策略审批流是否就绪")
        boolean changeApprovalWorkflowReady,
        @Schema(description = "任务创建时是否固化生效策略快照")
        boolean effectivePolicySnapshotMaterialized,
        @Schema(description = "是否只暴露聚合运营状态")
        boolean aggregateOnly
) {
}
