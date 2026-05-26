package com.songhg.veri.agent.testdesign.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * WP5 候选人工评审或编辑操作的审计记录。
 */
public record TestDesignReviewRecord(
        /** 主键 ID。 */
        UUID id,
        /** 候选 ID。 */
        UUID candidateId,
        /** 任务 ID。 */
        UUID taskId,
        /** 所属项目 ID。 */
        String projectId,
        /** 操作动作。 */
        String action,
        /** 操作前状态。 */
        String beforeStatus,
        /** 操作后状态。 */
        String afterStatus,
        /** 评审人。 */
        String reviewer,
        /** 评审说明。 */
        String comment,
        /** 差异 JSON。 */
        String diffJson,
        /** 创建时间。 */
        Instant createdAt
) {
}
