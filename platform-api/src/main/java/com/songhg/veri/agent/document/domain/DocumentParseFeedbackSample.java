package com.songhg.veri.agent.document.domain;

import java.time.Instant;
import java.util.UUID;

public record DocumentParseFeedbackSample(
        /** 主键 ID */
        UUID id,
        /** 候选 ID */
        UUID candidateId,
        /** 导入任务 ID */
        UUID importId,
        /** 所属项目 ID */
        String projectId,
        /** 来源类型 */
        String sourceType,
        /** 输入摘要 */
        String inputDigest,
        /** 来源引用摘要 */
        String sourceRefDigest,
        /** 来源片段摘要 */
        String sourceFragmentDigest,
        /** 解析来源 */
        String parseSource,
        /** 模型调用记录 ID */
        UUID modelInvocationId,
        /** 模型供应商名称 */
        String modelProviderName,
        /** 模型名称 */
        String modelName,
        /** 修正类型 */
        String correctionType,
        /** 变更字段 */
        String changedFields,
        /** 人工修正前的候选快照 JSON */
        String beforeSnapshotJson,
        /** 人工修正后的候选快照 JSON */
        String afterSnapshotJson,
        /** 治理状态 */
        String curationStatus,
        /** 创建人 */
        String createdBy,
        /** 创建时间 */
        Instant createdAt,
        /** 最近更新时间 */
        Instant updatedAt
) {
}
