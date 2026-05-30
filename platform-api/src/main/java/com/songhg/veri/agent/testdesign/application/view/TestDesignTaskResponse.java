package com.songhg.veri.agent.testdesign.application.view;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * WP5 用例生成任务的接口出参
 */
public record TestDesignTaskResponse(
        @Schema(description = "主键 ID")
        UUID id,
        @Schema(description = "所属项目 ID，用于权限 scope、筛选和数据隔离")
        String projectId,
        @Schema(description = "标题，用于页面展示和关键字检索")
        String title,
        @Schema(description = "业务状态")
        String status,
        @Schema(description = "需求 ID 列表")
        List<UUID> requirementIds,
        @Schema(description = "覆盖类型列表")
        List<String> coverageTypes,
        @Schema(description = "Prompt 模板标识")
        String promptKey,
        @Schema(description = "Prompt 模板版本")
        String promptVersion,
        @Schema(description = "模型调用记录 ID")
        UUID modelInvocationId,
        @Schema(description = "模型供应商名称")
        String modelProviderName,
        @Schema(description = "模型名称")
        String modelName,
        @Schema(description = "任务覆盖的需求总数")
        int totalRequirements,
        @Schema(description = "已生成候选数量")
        int generatedCount,
        @Schema(description = "已确认数量")
        int confirmedCount,
        @Schema(description = "已发布数量")
        int publishedCount,
        @Schema(description = "错误摘要")
        String errorMessage,
        @Schema(description = "请求发起人")
        String requestedBy,
        @Schema(description = "幂等键，用于重复请求回放和并发去重")
        String idempotencyKey,
        @Schema(description = "脱敏输入摘要")
        String inputDigest,
        @Schema(description = "脱敏模型观测摘要")
        TestDesignModelObservationResponse modelObservation,
        @Schema(description = "上下文装配策略安全边界快照")
        TestDesignContextAssemblyPolicyResponse contextAssemblyPolicy,
        @Schema(description = "上下文策略治理状态快照")
        TestDesignContextPolicyGovernanceResponse contextPolicyGovernance,
        @Schema(description = "上下文策略运营 v2 聚合快照")
        TestDesignContextPolicyOperationsResponse contextPolicyOperations,
        @Schema(description = "权限与资源作用域策略聚合快照")
        TestDesignScopePolicyResponse scopePolicy,
        @Schema(description = "脱敏上下文摘要")
        Map<String, Object> contextSummary,
        @Schema(description = "创建时间")
        Instant createdAt,
        @Schema(description = "最近更新时间")
        Instant updatedAt
) {
}
