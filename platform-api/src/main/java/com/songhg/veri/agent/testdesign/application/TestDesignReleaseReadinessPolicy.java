package com.songhg.veri.agent.testdesign.application;

import com.songhg.veri.agent.testdesign.application.view.TestDesignReleaseReadinessPolicyResponse;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Centralizes the WP5 release-readiness decision boundary used by diagnostics and model context.
 *
 * <p>The current slice evaluates quality thresholds but does not yet turn those signals into a publish-blocking
 * approval workflow. This snapshot makes that advisory-only boundary explicit across API responses, task context,
 * model payloads and reports without exporting candidate evidence, approval notes or threshold rule details.
 */
public final class TestDesignReleaseReadinessPolicy {

    public static final String POLICY_VERSION = "wp5-release-readiness-policy-v1";
    public static final String DECISION_MODE = "ADVISORY_QUALITY_GATE";
    public static final String THRESHOLD_SOURCE = "DEPLOY_CONFIG";

    private TestDesignReleaseReadinessPolicy() {
    }

    public static TestDesignReleaseReadinessPolicyResponse response() {
        return new TestDesignReleaseReadinessPolicyResponse(
                POLICY_VERSION,
                DECISION_MODE,
                THRESHOLD_SOURCE,
                true,
                true,
                false,
                true,
                false,
                false,
                true,
                false,
                false,
                false,
                false,
                true
        );
    }

    public static Map<String, Object> snapshot() {
        TestDesignReleaseReadinessPolicyResponse response = response();
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("policyVersion", response.policyVersion());
        snapshot.put("decisionMode", response.decisionMode());
        snapshot.put("thresholdSource", response.thresholdSource());
        snapshot.put("qualityThresholdEvaluated", response.qualityThresholdEvaluated());
        snapshot.put("advisoryOnly", response.advisoryOnly());
        snapshot.put("publishBlockingEnabled", response.publishBlockingEnabled());
        snapshot.put("manualApprovalRequired", response.manualApprovalRequired());
        snapshot.put("approvalWorkflowReady", response.approvalWorkflowReady());
        snapshot.put("autoPublishAllowed", response.autoPublishAllowed());
        snapshot.put("confirmedCandidateRequired", response.confirmedCandidateRequired());
        snapshot.put("qualityGateOverrideSupported", response.qualityGateOverrideSupported());
        snapshot.put("candidateEvidenceExported", response.candidateEvidenceExported());
        snapshot.put("approvalNotesExported", response.approvalNotesExported());
        snapshot.put("thresholdRuleDetailExported", response.thresholdRuleDetailExported());
        snapshot.put("aggregateOnly", response.aggregateOnly());
        return snapshot;
    }
}
