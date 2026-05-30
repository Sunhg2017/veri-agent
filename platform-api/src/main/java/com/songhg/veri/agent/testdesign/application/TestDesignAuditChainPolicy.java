package com.songhg.veri.agent.testdesign.application;

import com.songhg.veri.agent.testdesign.application.view.TestDesignAuditChainPolicyResponse;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Centralizes the WP5 audit-chain observability boundary.
 *
 * <p>The current slice writes WP1 audit events and exposes WP5 task-local audit summaries, but it does not yet provide
 * a cross-WP audit dashboard over WP1 audit_log, WP2 invocations and WP3 publish records. This aggregate snapshot makes
 * that boundary explicit for health checks, task diagnostics, model payloads and reports without exporting audit rows,
 * candidate identifiers, trace IDs, invocation IDs, sourceRef values or asset IDs.
 */
public final class TestDesignAuditChainPolicy {

    public static final String POLICY_VERSION = "wp5-audit-chain-policy-v1";
    public static final String CHAIN_MODE = "WP5_DOMAIN_AGGREGATE_WITH_WP1_AUDIT";
    public static final String EVENT_SOURCE = "TASK_REVIEW_PUBLISH_MODEL_REFERENCES";

    private TestDesignAuditChainPolicy() {
    }

    public static TestDesignAuditChainPolicyResponse response() {
        return new TestDesignAuditChainPolicyResponse(
                POLICY_VERSION,
                CHAIN_MODE,
                EVENT_SOURCE,
                true,
                true,
                true,
                true,
                true,
                true,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                true
        );
    }

    public static Map<String, Object> snapshot() {
        TestDesignAuditChainPolicyResponse response = response();
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("policyVersion", response.policyVersion());
        snapshot.put("chainMode", response.chainMode());
        snapshot.put("eventSource", response.eventSource());
        snapshot.put("wp1AuditEventWritten", response.wp1AuditEventWritten());
        snapshot.put("wp2InvocationReferenceTracked", response.wp2InvocationReferenceTracked());
        snapshot.put("wp3PublishReferenceTracked", response.wp3PublishReferenceTracked());
        snapshot.put("wp5DomainEventsTracked", response.wp5DomainEventsTracked());
        snapshot.put("projectScopeRequired", response.projectScopeRequired());
        snapshot.put("traceSignalTracked", response.traceSignalTracked());
        snapshot.put("auditEventDetailExported", response.auditEventDetailExported());
        snapshot.put("candidateIdentifierListExported", response.candidateIdentifierListExported());
        snapshot.put("platformAuditIdentifierExported", response.platformAuditIdentifierExported());
        snapshot.put("traceIdValueExported", response.traceIdValueExported());
        snapshot.put("modelInvocationIdValueExported", response.modelInvocationIdValueExported());
        snapshot.put("publishIdentifierValueExported", response.publishIdentifierValueExported());
        snapshot.put("crossWpAuditDashboardReady", response.crossWpAuditDashboardReady());
        snapshot.put("auditOutboxReplayDashboardReady", response.auditOutboxReplayDashboardReady());
        snapshot.put("aggregateOnly", response.aggregateOnly());
        return snapshot;
    }
}
