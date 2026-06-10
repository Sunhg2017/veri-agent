package com.songhg.veri.agent.testdesign.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * 资产冲突运营台的扁平查询投影，聚合正式发布冲突、候选当前状态和任务摘要。
 */
public record TestDesignConflictOperationRecord(
        UUID publishRecordId,
        UUID taskId,
        UUID candidateId,
        String projectId,
        UUID requirementId,
        UUID assetCaseId,
        boolean dryRun,
        String action,
        String result,
        String errorMessage,
        String publishedBy,
        Instant recordCreatedAt,
        String taskTitle,
        String taskStatus,
        String candidateTitle,
        String candidateStatus,
        long candidateVersion,
        UUID candidateAssetCaseId,
        boolean resolved
) {
}
