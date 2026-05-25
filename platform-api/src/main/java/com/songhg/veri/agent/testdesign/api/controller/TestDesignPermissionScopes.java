package com.songhg.veri.agent.testdesign.api.controller;

final class TestDesignPermissionScopes {

    static final String PROJECT_REQUEST = "@testDesignPermissionScopeResolver.project(#command.projectId())";
    static final String TASK_LIST = "@testDesignPermissionScopeResolver.taskList(#request)";
    static final String CANDIDATE_LIST = "@testDesignPermissionScopeResolver.candidateList(#request)";
    static final String TASK = "@testDesignPermissionScopeResolver.task(#id)";
    static final String CANDIDATE = "@testDesignPermissionScopeResolver.candidate(#id)";
    static final String CANDIDATE_BATCH = "@testDesignPermissionScopeResolver.candidateBatch(#command)";

    private TestDesignPermissionScopes() {
    }
}
