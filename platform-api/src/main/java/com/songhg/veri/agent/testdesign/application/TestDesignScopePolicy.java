package com.songhg.veri.agent.testdesign.application;

import com.songhg.veri.agent.testdesign.application.view.TestDesignScopePolicyResponse;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Centralizes the WP5 permission and resource-scope operating contract.
 *
 * <p>The current slice enforces project scope from task and candidate ownership instead of exposing configurable
 * role-rule details. This aggregate snapshot keeps health checks, task diagnostics, model payloads and reports aligned
 * on the same boundary while avoiding candidate ID lists, role matrices or service-token values in exports.
 */
public final class TestDesignScopePolicy {

    public static final String POLICY_VERSION = "wp5-scope-policy-v1";
    public static final String SCOPE_MODEL = "PROJECT_RESOURCE_SCOPE";
    public static final String LIST_FALLBACK_SCOPE = "PLATFORM_WHEN_PROJECT_FILTER_ABSENT";

    private TestDesignScopePolicy() {
    }

    public static TestDesignScopePolicyResponse response() {
        return new TestDesignScopePolicyResponse(
                POLICY_VERSION,
                SCOPE_MODEL,
                LIST_FALLBACK_SCOPE,
                true,
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
                true
        );
    }

    public static Map<String, Object> snapshot() {
        TestDesignScopePolicyResponse response = response();
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("policyVersion", response.policyVersion());
        snapshot.put("scopeModel", response.scopeModel());
        snapshot.put("listFallbackScope", response.listFallbackScope());
        snapshot.put("taskProjectScopeRequired", response.taskProjectScopeRequired());
        snapshot.put("candidateProjectScopeRequired", response.candidateProjectScopeRequired());
        snapshot.put("batchCandidateProjectScopeRequired", response.batchCandidateProjectScopeRequired());
        snapshot.put("publishProjectScopeRequired", response.publishProjectScopeRequired());
        snapshot.put("asyncTaskProjectScopeRecovered", response.asyncTaskProjectScopeRecovered());
        snapshot.put("smokeProjectScopeRequired", response.smokeProjectScopeRequired());
        snapshot.put("evaluationCorpusProjectIsolated", response.evaluationCorpusProjectIsolated());
        snapshot.put("evaluationCorpusOperationsReady", response.evaluationCorpusOperationsReady());
        snapshot.put("crossWpScopeDashboardReady", response.crossWpScopeDashboardReady());
        snapshot.put("candidateIdentifierListExported", response.candidateIdentifierListExported());
        snapshot.put("roleRuleDetailExported", response.roleRuleDetailExported());
        snapshot.put("serviceTokenValueExported", response.serviceTokenValueExported());
        snapshot.put("aggregateOnly", response.aggregateOnly());
        return snapshot;
    }
}
