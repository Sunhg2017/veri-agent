package com.songhg.veri.agent.testdesign.application;

import com.songhg.veri.agent.testdesign.application.view.TestDesignContextPolicyOperationsResponse;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Centralizes the WP5 context policy operations snapshot used by task context, model payloads and reports.
 *
 * <p>The current enterprise stage keeps project/environment override rules and approval workflows explicit but not yet
 * operational. Persisting these fixed aggregate flags at task creation time lets diagnostics explain the effective
 * policy source without exporting override rules, approval notes, ticket URLs or policy bodies.
 */
public final class TestDesignContextPolicyOperations {

    public static final String POLICY_VERSION = "wp5-context-policy-operations-v2";
    public static final String OPERATION_MODE = "PLATFORM_DEFAULT_ONLY";
    public static final String POLICY_RESOLUTION_ORDER = "PLATFORM_DEFAULT_ONLY";
    public static final String POLICY_FALLBACK_BEHAVIOR = "DEPLOY_CONFIG_CHANGE_REQUIRED";
    public static final String APPROVAL_STATUS = "WORKFLOW_NOT_READY";

    private TestDesignContextPolicyOperations() {
    }

    public static TestDesignContextPolicyOperationsResponse response() {
        return response(null);
    }

    public static TestDesignContextPolicyOperationsResponse response(
            TestDesignContextPolicyService.EffectiveContextPolicySnapshot effectivePolicy
    ) {
        if (effectivePolicy == null) {
            return new TestDesignContextPolicyOperationsResponse(
                    POLICY_VERSION,
                    OPERATION_MODE,
                    POLICY_RESOLUTION_ORDER,
                    POLICY_FALLBACK_BEHAVIOR,
                    APPROVAL_STATUS,
                    false,
                    false,
                    false,
                    true,
                    true
            );
        }
        return new TestDesignContextPolicyOperationsResponse(
                POLICY_VERSION,
                TestDesignContextPolicyService.OPERATION_MODE,
                TestDesignContextPolicyService.POLICY_RESOLUTION_ORDER,
                TestDesignContextPolicyService.POLICY_FALLBACK_BEHAVIOR,
                TestDesignContextPolicyService.APPROVAL_STATUS,
                effectivePolicy.projectOverrideStoreReady(),
                effectivePolicy.environmentOverrideStoreReady(),
                effectivePolicy.changeApprovalWorkflowReady(),
                true,
                true
        );
    }

    public static Map<String, Object> snapshot() {
        return snapshot(null);
    }

    public static Map<String, Object> snapshot(TestDesignContextPolicyService.EffectiveContextPolicySnapshot effectivePolicy) {
        TestDesignContextPolicyOperationsResponse response = response(effectivePolicy);
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("policyVersion", response.policyVersion());
        snapshot.put("operationMode", response.operationMode());
        snapshot.put("policyResolutionOrder", response.policyResolutionOrder());
        snapshot.put("policyFallbackBehavior", response.policyFallbackBehavior());
        snapshot.put("approvalStatus", response.approvalStatus());
        snapshot.put("projectOverrideStoreReady", response.projectOverrideStoreReady());
        snapshot.put("environmentOverrideStoreReady", response.environmentOverrideStoreReady());
        snapshot.put("changeApprovalWorkflowReady", response.changeApprovalWorkflowReady());
        snapshot.put("effectivePolicySnapshotMaterialized", response.effectivePolicySnapshotMaterialized());
        snapshot.put("policyDiffPreviewExported", false);
        snapshot.put("approvalNotesExported", false);
        snapshot.put("ticketUrlExported", false);
        snapshot.put("projectOverrideRulesExported", false);
        snapshot.put("environmentOverrideRulesExported", false);
        snapshot.put("approvedOverrideApplied", effectivePolicy != null && effectivePolicy.approvedOverrideApplied());
        snapshot.put("appliedOverrideScopes", effectivePolicy == null
                ? java.util.List.of("PLATFORM_DEFAULT")
                : effectivePolicy.appliedOverrideScopes());
        snapshot.put("overrideStatusCounts", effectivePolicy == null ? Map.of() : effectivePolicy.overrideStatusCounts());
        snapshot.put("aggregateOnly", response.aggregateOnly());
        return snapshot;
    }
}
