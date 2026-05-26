package com.songhg.veri.agent.testdesign.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * WP5 候选测试用例的领域快照。
 */
public record TestDesignCandidate(
        /** 主键 ID。 */
        UUID id,
        /** 任务 ID。 */
        UUID taskId,
        /** 所属项目 ID。 */
        String projectId,
        /** 关联需求 ID。 */
        UUID requirementId,
        /** 关联 API 资产 ID。 */
        UUID apiId,
        /** 标题。 */
        String title,
        /** 业务说明。 */
        String description,
        /** 覆盖类型。 */
        String coverageType,
        /** 优先级。 */
        String priority,
        /** 业务状态。 */
        String status,
        /** 前置条件。 */
        String preconditions,
        /** 步骤 JSON。 */
        String stepsJson,
        /** 预期结果。 */
        String expectedResult,
        /** 标签。 */
        String tags,
        /** 去重键。 */
        String duplicateKey,
        /** 置信度。 */
        double confidence,
        /** Prompt 模板标识。 */
        String promptKey,
        /** Prompt 模板版本。 */
        String promptVersion,
        /** 模型调用记录 ID。 */
        UUID modelInvocationId,
        /** 模型供应商名称。 */
        String modelProviderName,
        /** 模型名称。 */
        String modelName,
        /** 测试用例资产 ID。 */
        UUID assetCaseId,
        /** 评审意见。 */
        String reviewComment,
        /** 驳回原因。 */
        String rejectedReason,
        /** 忽略原因。 */
        String ignoredReason,
        /** 错误摘要。 */
        String errorMessage,
        /** 确认人。 */
        String confirmedBy,
        /** 确认时间。 */
        Instant confirmedAt,
        /** 版本号。 */
        long version,
        /** 创建时间。 */
        Instant createdAt,
        /** 最近更新时间。 */
        Instant updatedAt
) {
}
