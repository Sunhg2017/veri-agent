package com.songhg.veri.agent.management.infrastructure;

import com.songhg.veri.agent.auth.application.AuthUserPrincipal;
import com.songhg.veri.agent.common.audit.AuditLogWriter;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Profile("db")
@Service
public class PostgresManagementDeniedAuditRecorder {

    private final AuditLogWriter auditLogWriter;

    public PostgresManagementDeniedAuditRecorder(AuditLogWriter auditLogWriter) {
        this.auditLogWriter = auditLogWriter;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
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
