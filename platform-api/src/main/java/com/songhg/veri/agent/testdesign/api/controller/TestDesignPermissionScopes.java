package com.songhg.veri.agent.testdesign.api.controller;

final class TestDesignPermissionScopes {

    static final String PROJECT_REQUEST = "@testDesignPermissionScopeResolver.project(#command.projectId())";
    static final String TASK_LIST = "@testDesignPermissionScopeResolver.taskList(#request)";
    static final String PROMPT_TREND = "@testDesignPermissionScopeResolver.promptTrend(#request)";
    static final String EVALUATION_CORPUS_SUMMARY = "@testDesignPermissionScopeResolver.evaluationCorpusSummary(#request)";
    static final String SCOPE_SUMMARY = "@testDesignPermissionScopeResolver.scopeSummary(#request)";
    static final String CROSS_WP_OPERATIONS = "@testDesignPermissionScopeResolver.crossWpOperations(#request)";
    static final String AUDIT_OUTBOX_REQUEUE = "@testDesignPermissionScopeResolver.auditOutboxRequeue(#command)";
    static final String CONFLICT_OPERATIONS = "@testDesignPermissionScopeResolver.conflictOperations(#request)";
    static final String CANDIDATE_LIST = "@testDesignPermissionScopeResolver.candidateList(#request)";
    static final String TASK = "@testDesignPermissionScopeResolver.task(#id)";
    static final String CANDIDATE = "@testDesignPermissionScopeResolver.candidate(#id)";
    static final String CANDIDATE_BATCH = "@testDesignPermissionScopeResolver.candidateBatch(#command)";
    static final String TEMPLATE_LIST = "@testDesignPermissionScopeResolver.templateList(#request)";
    static final String TEMPLATE_REQUEST = "@testDesignPermissionScopeResolver.templateRequest(#command)";
    static final String TEMPLATE = "@testDesignPermissionScopeResolver.template(#id)";
    static final String EVALUATION_SAMPLE_LIST = "@testDesignPermissionScopeResolver.evaluationSampleList(#request)";
    static final String EVALUATION_SAMPLE_REQUEST = "@testDesignPermissionScopeResolver.evaluationSampleRequest(#command)";
    static final String EVALUATION_SAMPLE_FROM_CANDIDATE = "@testDesignPermissionScopeResolver.evaluationSampleFromCandidate(#command)";
    static final String EVALUATION_SAMPLE = "@testDesignPermissionScopeResolver.evaluationSample(#id)";
    static final String CALIBRATION_RUN_LIST = "@testDesignPermissionScopeResolver.calibrationRunList(#request)";
    static final String CALIBRATION_RUN_REQUEST = "@testDesignPermissionScopeResolver.calibrationRunRequest(#command)";
    static final String POLICY_PROJECT = "@testDesignPermissionScopeResolver.project(#projectId)";
    static final String POLICY_OVERRIDE = "@testDesignPermissionScopeResolver.contextPolicyOverride(#id)";
    static final String RELEASE_READINESS_APPROVAL = "@testDesignPermissionScopeResolver.releaseReadinessApproval(#id)";

    private TestDesignPermissionScopes() {
    }
}
