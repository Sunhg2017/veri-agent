package com.songhg.veri.agent.common.audit;

import java.util.UUID;

public record AuditLogEntry(
        /** 操作人用户 ID；系统任务为空 */
        UUID actorUserId,
        /** 审计动作编码 */
        String action,
        /** 被操作资源类型 */
        String resourceType,
        /** 被操作资源 ID */
        String resourceId,
        /** 操作结果，如 SUCCESS、DENIED、FAILED */
        String result,
        /** 失败或拒绝原因 */
        String reason,
        /** 被操作目标展示名称 */
        String targetName,
        /** 变更前 JSON 快照 */
        String beforeJson,
        /** 变更后 JSON 快照 */
        String afterJson,
        /** 字段级差异 JSON */
        String diffJson
) {

    public static AuditLogEntry from(AuditLogWriter.AuditRecord record) {
        return new AuditLogEntry(
                record.actor() == null ? null : record.actor().userId(),
                record.action(),
                record.resourceType(),
                record.resourceId(),
                record.result(),
                record.reason(),
                record.targetName(),
                record.beforeJson(),
                record.afterJson(),
                record.diffJson()
        );
    }
}
