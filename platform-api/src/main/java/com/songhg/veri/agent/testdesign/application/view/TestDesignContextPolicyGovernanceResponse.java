package com.songhg.veri.agent.testdesign.application.view;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * WP5 上下文装配策略治理状态快照
 */
public record TestDesignContextPolicyGovernanceResponse(
        @Schema(description = "上下文策略治理版本")
        String policyVersion,
        @Schema(description = "当前策略来源")
        String policySource,
        @Schema(description = "治理就绪状态")
        String governanceStatus,
        @Schema(description = "策略变更模式")
        String changeMode,
        @Schema(description = "是否支持项目级策略覆盖")
        boolean projectOverrideSupported,
        @Schema(description = "是否支持环境级策略覆盖")
        boolean environmentOverrideSupported,
        @Schema(description = "策略变更是否要求审批")
        boolean changeApprovalRequired,
        @Schema(description = "策略审批流是否已就绪")
        boolean changeApprovalWorkflowReady,
        @Schema(description = "任务创建时是否固化策略快照")
        boolean effectiveAtTaskCreation,
        @Schema(description = "是否只暴露聚合治理状态")
        boolean aggregateOnly
) {
}
