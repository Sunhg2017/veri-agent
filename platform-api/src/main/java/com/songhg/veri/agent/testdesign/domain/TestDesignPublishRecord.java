package com.songhg.veri.agent.testdesign.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * WP5 候选发布到 WP3 测试用例资产时的单候选记录
 */
public record TestDesignPublishRecord(
        /** 主键 ID */
        UUID id,
        /** 任务 ID */
        UUID taskId,
        /** 候选 ID */
        UUID candidateId,
        /** 所属项目 ID */
        String projectId,
        /** 关联需求 ID */
        UUID requirementId,
        /** 测试用例资产 ID */
        UUID assetCaseId,
        /** 是否预演 */
        boolean dryRun,
        /** 操作动作 */
        String action,
        /** 处理结果 */
        String result,
        /** 错误摘要 */
        String errorMessage,
        /** 发布人 */
        String publishedBy,
        /** 创建时间 */
        Instant createdAt
) {
}
