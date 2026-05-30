package com.songhg.veri.agent.testdesign.domain;

/**
 * WP5 cross-work-package audit-chain aggregate counters.
 *
 * <p>The aggregate deliberately contains only counts and boolean evidence signals. It must not carry audit row ids,
 * trace ids, model invocation ids, sourceRef values, candidate ids or WP3 asset ids because the read model is intended
 * for operations dashboards rather than forensic drill-down.
 */
public record TestDesignAuditChainAggregate(
        long wp1AuditEventCount,
        long wp1AuditSuccessCount,
        long wp1AuditFailureCount,
        long wp1AuditDeniedCount,
        long wp1TaskAuditEventCount,
        long wp1CandidateExportAuditEventCount,
        long wp1ReviewExportAuditEventCount,
        long wp1ReportExportAuditEventCount,
        long wp2InvocationCount,
        long wp2InvocationSucceededCount,
        long wp2InvocationFailedCount,
        long wp2InvocationBlockedCount,
        long wp2FallbackCount,
        long wp2InputTokenTotal,
        long wp2OutputTokenTotal,
        long wp2LatencyMsTotal,
        String wp2TotalCostText,
        long wp2JobCount,
        long wp2TraceSignalCount,
        long wp3PublishedCaseCount,
        long wp3TraceLinkCount,
        long auditOutboxPendingCount,
        long auditOutboxFailedCount,
        long auditOutboxDeadCount
) {
}
