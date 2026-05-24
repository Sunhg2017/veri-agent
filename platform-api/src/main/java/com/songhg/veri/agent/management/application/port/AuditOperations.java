package com.songhg.veri.agent.management.application.port;

import com.songhg.veri.agent.auth.application.AuthUserPrincipal;
import com.songhg.veri.agent.common.api.PageQuery;
import com.songhg.veri.agent.common.api.PageResponse;
import com.songhg.veri.agent.management.application.query.AuditLogQuery;
import com.songhg.veri.agent.management.application.query.AuditOutboxQuery;
import com.songhg.veri.agent.management.application.view.AuditLogView;
import com.songhg.veri.agent.management.application.view.AuditOutboxView;

/**
 * Audit query use cases. These methods are read-only but still receive the actor so implementations
 * can preserve access scope and export accountability.
 */
public interface AuditOperations {

    /**
     * Searches immutable audit records with optional business filters.
     */
    PageResponse<AuditLogView> auditLogs(PageQuery pageQuery, AuditLogQuery query, AuthUserPrincipal actor);

    /**
     * Exports the filtered audit log set as CSV for compliance review.
     */
    String exportAuditLogsCsv(AuditLogQuery query, AuthUserPrincipal actor);

    /**
     * Lists pending or processed audit outbox events used for delivery diagnostics.
     */
    PageResponse<AuditOutboxView> auditOutbox(PageQuery pageQuery, AuditOutboxQuery query, AuthUserPrincipal actor);
}
