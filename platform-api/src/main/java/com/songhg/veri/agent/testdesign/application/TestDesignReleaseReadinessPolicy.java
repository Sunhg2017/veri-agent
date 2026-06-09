package com.songhg.veri.agent.testdesign.application;

import com.songhg.veri.agent.testdesign.application.view.TestDesignReleaseReadinessPolicyResponse;
import com.songhg.veri.agent.testdesign.config.TestDesignProperties;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Centralizes the WP5 release-readiness decision boundary used by diagnostics and model context.
 *
 * <p>The default slice stays advisory-only for compatibility. Deployments can enable aggregate quality blocking for
 * official publish; blocked tasks require a task-scoped approved exception whose aggregate readiness digest still
 * matches the current quality summary. The snapshot is reused across API responses, task context, model payloads and
 * reports without exporting candidate evidence, approval notes or threshold rule details.
 */
public final class TestDesignReleaseReadinessPolicy {

    public static final String POLICY_VERSION = "wp5-release-readiness-policy-v1";
    public static final String ADVISORY_DECISION_MODE = "ADVISORY_QUALITY_GATE";
    public static final String BLOCKING_DECISION_MODE = "BLOCKING_QUALITY_GATE";
    public static final String THRESHOLD_SOURCE = "DEPLOY_CONFIG";

    private TestDesignReleaseReadinessPolicy() {
    }

    public static TestDesignReleaseReadinessPolicyResponse response(TestDesignProperties properties) {
        boolean publishBlockingEnabled = properties != null
                && properties.releaseReadinessPublishBlockingEnabled();
        return new TestDesignReleaseReadinessPolicyResponse(
                POLICY_VERSION,
                publishBlockingEnabled ? BLOCKING_DECISION_MODE : ADVISORY_DECISION_MODE,
                THRESHOLD_SOURCE,
                true,
                !publishBlockingEnabled,
                publishBlockingEnabled,
                true,
                true,
                false,
                true,
                true,
                false,
                false,
                false,
                true
        );
    }

    public static TestDesignReleaseReadinessPolicyResponse response() {
        return response(null);
    }

    public static Map<String, Object> snapshot(TestDesignProperties properties) {
        TestDesignReleaseReadinessPolicyResponse response = response(properties);
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

    public static Map<String, Object> snapshot() {
        return snapshot(null);
    }
}
