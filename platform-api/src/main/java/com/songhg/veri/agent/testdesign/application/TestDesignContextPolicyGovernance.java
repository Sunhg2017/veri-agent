package com.songhg.veri.agent.testdesign.application;

import com.songhg.veri.agent.testdesign.application.view.TestDesignContextPolicyGovernanceResponse;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Centralizes the WP5 context policy governance snapshot used by health checks, tasks and reports.
 *
 * <p>The current enterprise increment is intentionally honest about scope: context clipping is enforced by platform
 * defaults, while project/environment overrides and approval workflow are not ready yet. Keeping this as a shared
 * snapshot prevents the report, model payload and API diagnostics from drifting into different operating narratives.
 */
public final class TestDesignContextPolicyGovernance {

    public static final String POLICY_VERSION = "wp5-context-policy-v1";
    public static final String POLICY_SOURCE = "PLATFORM_DEFAULT";
    public static final String GOVERNANCE_STATUS = "PLATFORM_DEFAULT_ONLY";
    public static final String CHANGE_MODE = "DEPLOY_CONFIG_CHANGE";

    private TestDesignContextPolicyGovernance() {
    }

    public static TestDesignContextPolicyGovernanceResponse response() {
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

    public static Map<String, Object> snapshot() {
        TestDesignContextPolicyGovernanceResponse response = response();
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
        snapshot.put("aggregateOnly", response.aggregateOnly());
        return snapshot;
    }
}
