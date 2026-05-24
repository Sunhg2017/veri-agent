package com.songhg.veri.agent.management.application;

import com.songhg.veri.agent.auth.application.AuthUserPrincipal;
import com.songhg.veri.agent.common.api.PageQuery;
import com.songhg.veri.agent.common.api.PageResponse;
import com.songhg.veri.agent.management.application.AuditLogView;
import com.songhg.veri.agent.management.application.AuditOutboxView;

public interface AuditOperations {

    PageResponse<AuditLogView> auditLogs(PageQuery pageQuery, AuditLogQuery query, AuthUserPrincipal actor);

    String exportAuditLogsCsv(AuditLogQuery query, AuthUserPrincipal actor);

    PageResponse<AuditOutboxView> auditOutbox(PageQuery pageQuery, AuditOutboxQuery query, AuthUserPrincipal actor);
}
