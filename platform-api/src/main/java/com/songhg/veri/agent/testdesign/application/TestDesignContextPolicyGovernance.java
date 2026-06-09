package com.songhg.veri.agent.testdesign.application;

import com.songhg.veri.agent.testdesign.application.view.TestDesignContextPolicyGovernanceResponse;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Centralizes the WP5 context policy governance snapshot used by health checks, tasks and reports.
 *
 * <p>Health checks keep the platform-default fallback honest, while project/environment snapshots report the bounded
 * override store and work-order approval workflow. Sharing this snapshot prevents reports, model payloads and API
 * diagnostics from drifting into different operating narratives.
 */
public final class TestDesignContextPolicyGovernance {

    public static final String POLICY_VERSION = "wp5-context-policy-v1";
    public static final String POLICY_SOURCE = "PLATFORM_DEFAULT";
    public static final String GOVERNANCE_STATUS = "PLATFORM_DEFAULT_ONLY";
    public static final String CHANGE_MODE = "DEPLOY_CONFIG_CHANGE";

    private TestDesignContextPolicyGovernance() {
    }

    public static TestDesignContextPolicyGovernanceResponse response() {
        return response(null);
    }

    public static TestDesignContextPolicyGovernanceResponse response(
            TestDesignContextPolicyService.EffectiveContextPolicySnapshot effectivePolicy
    ) {
        if (effectivePolicy == null) {
            return new TestDesignContextPolicyGovernanceResponse(
                    POLICY_VERSION,
                    POLICY_SOURCE,
                    GOVERNANCE_STATUS,
                    CHANGE_MODE,
                    false,
                    false,
                    true,
                    false,
                    true,
                    true
            );
        }
        return new TestDesignContextPolicyGovernanceResponse(
                POLICY_VERSION,
                effectivePolicy.approvedOverrideApplied() ? "PROJECT_ENVIRONMENT_OVERRIDE" : POLICY_SOURCE,
                effectivePolicy.approvedOverrideApplied() ? "OVERRIDE_APPROVED" : "OVERRIDE_STORE_READY",
                "WORK_ORDER_APPROVAL",
                effectivePolicy.projectOverrideStoreReady(),
                effectivePolicy.environmentOverrideStoreReady(),
                true,
                effectivePolicy.changeApprovalWorkflowReady(),
                true,
                true
        );
    }

    public static Map<String, Object> snapshot() {
        return snapshot(null);
    }

    public static Map<String, Object> snapshot(TestDesignContextPolicyService.EffectiveContextPolicySnapshot effectivePolicy) {
        TestDesignContextPolicyGovernanceResponse response = response(effectivePolicy);
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("policyVersion", response.policyVersion());
        snapshot.put("policySource", response.policySource());
        snapshot.put("governanceStatus", response.governanceStatus());
        snapshot.put("changeMode", response.changeMode());
        snapshot.put("projectOverrideSupported", response.projectOverrideSupported());
        snapshot.put("environmentOverrideSupported", response.environmentOverrideSupported());
        snapshot.put("changeApprovalRequired", response.changeApprovalRequired());
        snapshot.put("changeApprovalWorkflowReady", response.changeApprovalWorkflowReady());
        snapshot.put("effectiveAtTaskCreation", response.effectiveAtTaskCreation());
        snapshot.put("approvedOverrideApplied", effectivePolicy != null && effectivePolicy.approvedOverrideApplied());
        snapshot.put("appliedOverrideScopes", effectivePolicy == null
                ? java.util.List.of("PLATFORM_DEFAULT")
                : effectivePolicy.appliedOverrideScopes());
        snapshot.put("aggregateOnly", response.aggregateOnly());
        return snapshot;
    }
}
