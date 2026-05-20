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
        return denied(actor, action, resourceType, resourceId, resourceId, reason);
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
            AuthUserPrincipal actor,
            String action,
            String resourceType,
            String resourceId,
            String result,
            String reason,
            String targetName,
            String beforeJson,
            String afterJson,
            String diffJson
    ) {
    }
}
