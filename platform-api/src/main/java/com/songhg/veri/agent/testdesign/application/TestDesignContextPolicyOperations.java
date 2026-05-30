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

    public static Map<String, Object> snapshot() {
        TestDesignContextPolicyOperationsResponse response = response();
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
        snapshot.put("aggregateOnly", response.aggregateOnly());
        return snapshot;
    }
}
