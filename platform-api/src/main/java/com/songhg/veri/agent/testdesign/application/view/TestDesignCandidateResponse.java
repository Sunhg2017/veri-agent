package com.songhg.veri.agent.testdesign.application.view;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * WP5 候选测试用例的接口出参。
 */
public record TestDesignCandidateResponse(
        @Schema(description = "主键 ID。")
        UUID id,
        @Schema(description = "任务 ID。")
        UUID taskId,
        @Schema(description = "所属项目 ID，用于权限 scope、筛选和数据隔离。")
        String projectId,
        @Schema(description = "关联需求 ID。")
        UUID requirementId,
        @Schema(description = "关联 API 资产 ID。")
        UUID apiId,
        @Schema(description = "标题，用于页面展示和关键字检索。")
        String title,
        @Schema(description = "业务说明或补充描述。")
        String description,
        @Schema(description = "覆盖类型。")
        String coverageType,
        @Schema(description = "优先级。")
        String priority,
        @Schema(description = "候选生命周期状态。")
        String status,
        @Schema(description = "执行前置条件。")
        String preconditions,
        @Schema(description = "测试步骤列表。")
        List<TestDesignStepResponse> steps,
        @Schema(description = "预期结果。")
        String expectedResult,
        @Schema(description = "标签，多个值用列表或逗号分隔文本表达。")
        List<String> tags,
        @Schema(description = "去重键。")
        String duplicateKey,
        @Schema(description = "解析或生成置信度，范围 0 到 1。")
        double confidence,
        @Schema(description = "Prompt 模板标识。")
        String promptKey,
        @Schema(description = "Prompt 模板版本。")
        String promptVersion,
        @Schema(description = "模型调用记录 ID。")
        UUID modelInvocationId,
        @Schema(description = "模型供应商名称。")
        String modelProviderName,
        @Schema(description = "模型名称。")
        String modelName,
        @Schema(description = "发布或匹配到的 WP3 测试用例资产 ID。")
        UUID assetCaseId,
        @Schema(description = "评审意见。")
        String reviewComment,
        @Schema(description = "驳回原因。")
        String rejectedReason,
        @Schema(description = "忽略原因。")
        String ignoredReason,
        @Schema(description = "错误摘要。")
        String errorMessage,
        @Schema(description = "确认人。")
        String confirmedBy,
        @Schema(description = "确认时间。")
        Instant confirmedAt,
        @Schema(description = "乐观锁或资产版本号，用于并发控制和审计追踪。")
        long version,
        @Schema(description = "创建时间。")
        Instant createdAt,
        @Schema(description = "最近更新时间。")
        Instant updatedAt
) {
}
