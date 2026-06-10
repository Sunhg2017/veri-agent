package com.songhg.veri.agent.testdesign.domain;

/**
 * Project-level WP5 cross-WP operations aggregate counters.
 *
 * <p>The aggregate is intentionally count-only. It never carries audit row ids, outbox payloads, trace ids, invocation
 * ids, candidate ids, sourceRef values or WP3 asset ids because the operations dashboard is for readiness and replay
 * governance rather than forensic drill-down.
 */
public record TestDesignCrossWpOperationsAggregate(
        long taskCount,
        long candidateCount,
        long publishRecordCount,
        long projectBucketCount,
        long candidateScopeMismatchCount,
        long publishScopeMismatchCount,
        long modelInvocationReferenceCount,
        long publishProjectScopeRecordCount,
        long wp1AuditEventCount,
        long wp1AuditSuccessCount,
        long wp1AuditFailureCount,
        long wp1AuditDeniedCount,
        long wp2InvocationCount,
        long wp2InvocationSucceededCount,
        long wp2InvocationFailedCount,
        long wp2InvocationBlockedCount,
        long wp2FallbackCount,
        long wp2TraceSignalCount,
        long wp3PublishedCaseCount,
        long wp3TraceLinkCount,
        long auditOutboxTotalCount,
        long auditOutboxPendingCount,
        long auditOutboxProcessingCount,
        long auditOutboxDoneCount,
        long auditOutboxFailedCount,
        long auditOutboxDeadCount
) {
    public long scopeMismatchCount() {
        return candidateScopeMismatchCount + publishScopeMismatchCount;
    }

    public long replayEligibleOutboxCount() {
        return auditOutboxFailedCount + auditOutboxDeadCount;
    }
}
