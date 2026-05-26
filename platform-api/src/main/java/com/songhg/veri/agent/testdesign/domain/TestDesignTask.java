package com.songhg.veri.agent.testdesign.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * WP5 用例生成任务的领域快照。
 */
public record TestDesignTask(
        /** 主键 ID。 */
        UUID id,
        /** 所属项目 ID。 */
        String projectId,
        /** 标题。 */
        String title,
        /** 业务状态。 */
        String status,
        /** 需求 ID 列表。 */
        String requirementIds,
        /** 覆盖类型列表。 */
        String coverageTypes,
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
        /** 覆盖需求总数。 */
        int totalRequirements,
        /** 已生成候选数量。 */
        int generatedCount,
        /** 已确认数量。 */
        int confirmedCount,
        /** 已发布数量。 */
        int publishedCount,
        /** 错误摘要。 */
        String errorMessage,
        /** 请求发起人。 */
        String requestedBy,
        /** 幂等键。 */
        String idempotencyKey,
        /** 请求摘要。 */
        String requestDigest,
        /** 输入摘要。 */
        String inputDigest,
        /** 上下文摘要 JSON。 */
        String contextSummaryJson,
        /** 创建时间。 */
        Instant createdAt,
        /** 最近更新时间。 */
        Instant updatedAt
) {
}
