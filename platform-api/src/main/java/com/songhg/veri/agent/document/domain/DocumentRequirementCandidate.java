package com.songhg.veri.agent.document.domain;

import java.time.Instant;
import java.util.UUID;

public record DocumentRequirementCandidate(
        /** 主键 ID */
        UUID id,
        /** 导入任务 ID */
        UUID importId,
        /** 所属项目 ID */
        String projectId,
        /** 标题 */
        String title,
        /** 业务说明 */
        String description,
        /** 优先级 */
        String priority,
        /** 验收标准 */
        String acceptanceCriteria,
        /** 标签 */
        String tags,
        /** 业务状态 */
        DocumentCandidateStatus status,
        /** 外部来源引用 */
        String sourceRef,
        /** 来源片段 */
        String sourceFragment,
        /** 外部需求 ID */
        String externalRequirementId,
        /** 置信度 */
        double confidence,
        /** 解析来源 */
        String parseSource,
        /** 模型调用记录 ID */
        UUID modelInvocationId,
        /** 模型供应商名称 */
        String modelProviderName,
        /** 模型名称 */
        String modelName,
        /** 需求资产 ID */
        UUID assetRequirementId,
        /** 错误摘要 */
        String errorMessage,
        /** 忽略原因 */
        String ignoredReason,
        /** 确认人 */
        String confirmedBy,
        /** 确认时间 */
        Instant confirmedAt,
        /** 版本号 */
        long version,
        /** 创建时间 */
        Instant createdAt,
        /** 最近更新时间 */
        Instant updatedAt
) {

    public DocumentRequirementCandidate(
            UUID id,
            UUID importId,
            String projectId,
            String title,
            String description,
            String priority,
            String acceptanceCriteria,
            String tags,
            DocumentCandidateStatus status,
            String sourceRef,
            String sourceFragment,
            String externalRequirementId,
            double confidence,
            UUID assetRequirementId,
            String errorMessage,
            String ignoredReason,
            String confirmedBy,
            Instant confirmedAt,
            long version,
            Instant createdAt,
            Instant updatedAt
    ) {
        this(
                id,
                importId,
                projectId,
                title,
                description,
                priority,
                acceptanceCriteria,
                tags,
                status,
                sourceRef,
                sourceFragment,
                externalRequirementId,
                confidence,
                "RULE",
                null,
                null,
                null,
                assetRequirementId,
                errorMessage,
                ignoredReason,
                confirmedBy,
                confirmedAt,
                version,
                createdAt,
                updatedAt
        );
    }
}
