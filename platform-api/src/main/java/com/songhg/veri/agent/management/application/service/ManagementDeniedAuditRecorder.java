package com.songhg.veri.agent.management.application.service;

import com.songhg.veri.agent.auth.application.AuthUserPrincipal;
import com.songhg.veri.agent.common.audit.AuditLogWriter;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ManagementDeniedAuditRecorder {

    private final AuditLogWriter auditLogWriter;

    public ManagementDeniedAuditRecorder(AuditLogWriter auditLogWriter) {
        this.auditLogWriter = auditLogWriter;
    }

    public void recordProjectStatusDenied(
            AuthUserPrincipal actor,
            UUID projectId,
            String projectName,
            String currentStatus,
            String nextStatus
    ) {
        auditLogWriter.record(AuditLogWriter.denied(
                actor,
                "项目状态拒绝",
                "project",
                projectId.toString(),
                projectName,
                "项目状态流不允许: " + currentStatus + "->" + nextStatus
        ));
    }
}
