package com.songhg.veri.agent.modelaccess.domain;

import java.time.Instant;
import java.util.UUID;

public record PromptTemplate(
        /** 主键 ID */
        UUID id,
        /** Prompt 模板标识 */
        String promptKey,
        /** 名称 */
        String name,
        /** 版本号 */
        int version,
        /** 内容正文 */
        String content,
        /** 业务状态 */
        PromptStatus status,
        /** 模板变更说明 */
        String changeNote,
        /** 是否高风险提示词 */
        boolean highRisk,
        /** 提示词审批状态 */
        PromptApprovalStatus approvalStatus,
        /** 审批人 */
        String approvedBy,
        /** 审批时间 */
        Instant approvedAt,
        /** 审批备注 */
        String approvalNote,
        /** 创建时间 */
        Instant createdAt,
        /** 最近更新时间 */
        Instant updatedAt
) {
}
