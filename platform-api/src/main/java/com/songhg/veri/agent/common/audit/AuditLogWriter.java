package com.songhg.veri.agent.common.audit;

import com.songhg.veri.agent.auth.application.AuthUserPrincipal;

public interface AuditLogWriter {

    void record(AuditRecord record);

    static AuditRecord success(
            AuthUserPrincipal actor,
            String action,
            String resourceType,
            String resourceId,
            String targetName
    ) {
        return new AuditRecord(actor, action, resourceType, resourceId, "SUCCESS", null, targetName, null, null, null);
    }

    static AuditRecord changed(
            AuthUserPrincipal actor,
            String action,
            String resourceType,
            String resourceId,
            String targetName,
            String beforeJson,
            String afterJson,
            String diffJson
    ) {
        return new AuditRecord(actor, action, resourceType, resourceId, "SUCCESS", null, targetName, beforeJson, afterJson, diffJson);
    }

    static AuditRecord denied(
            AuthUserPrincipal actor,
            String action,
            String resourceType,
            String resourceId,
            String reason
    ) {
        return denied(actor, action, resourceType, resourceId, null, reason);
    }

    static AuditRecord denied(
            AuthUserPrincipal actor,
            String action,
            String resourceType,
            String resourceId,
            String targetName,
            String reason
    ) {
        return new AuditRecord(actor, action, resourceType, resourceId, "DENIED", reason, targetName, null, null, null);
    }

    static AuditRecord failed(
            AuthUserPrincipal actor,
            String action,
            String resourceType,
            String resourceId,
            String reason
    ) {
        return new AuditRecord(actor, action, resourceType, resourceId, "FAILED", reason, resourceId, null, null, null);
    }

    record AuditRecord(
            /** 操作人身份；系统任务可为空。 */
            AuthUserPrincipal actor,
            /** 审计动作编码。 */
            String action,
            /** 被操作资源类型。 */
            String resourceType,
            /** 被操作资源 ID。 */
            String resourceId,
            /** 操作结果，如 SUCCESS、DENIED、FAILED。 */
            String result,
            /** 失败或拒绝原因。 */
            String reason,
            /** 被操作目标展示名称。 */
            String targetName,
            /** 变更前 JSON 快照。 */
            String beforeJson,
            /** 变更后 JSON 快照。 */
            String afterJson,
            /** 字段级差异 JSON。 */
            String diffJson
    ) {
    }
}
