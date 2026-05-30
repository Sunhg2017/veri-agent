package com.songhg.veri.agent.testdesign.application.view;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Effective WP5 context policy snapshot after platform, project and environment resolution.
 */
public record TestDesignContextPolicyEffectiveResponse(
        @Schema(description = "项目 ID")
        String projectId,
        @Schema(description = "环境键；为空表示只解析项目级策略")
        String environmentKey,
        @Schema(description = "生效上下文裁剪上限")
        Map<String, Integer> contextLimits,
        @Schema(description = "已应用的覆盖作用域")
        List<String> appliedOverrideScopes,
        @Schema(description = "项目/环境覆盖状态分布")
        Map<String, Long> overrideStatusCounts,
        @Schema(description = "上下文装配安全边界")
        TestDesignContextAssemblyPolicyResponse contextAssemblyPolicy,
        @Schema(description = "上下文策略治理状态")
        TestDesignContextPolicyGovernanceResponse contextPolicyGovernance,
        @Schema(description = "上下文策略运营状态")
        TestDesignContextPolicyOperationsResponse contextPolicyOperations,
        @Schema(description = "是否导出策略正文")
        boolean policyBodyExported,
        @Schema(description = "是否导出策略 diff")
        boolean policyDiffPreviewExported,
        @Schema(description = "是否导出审批备注")
        boolean approvalNotesExported,
        @Schema(description = "是否导出工单 URL")
        boolean ticketUrlExported,
        @Schema(description = "是否只输出聚合与数字配置")
        boolean aggregateOnly,
        @Schema(description = "生成时间")
        Instant generatedAt
) {
}
