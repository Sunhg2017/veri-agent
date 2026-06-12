package com.songhg.veri.agent.apiautomation.api.controller;

final class ApiAutomationPermissionScopes {

    static final String SPEC_REQUEST = "@apiAutomationPermissionScopeResolver.specRequest(#command)";
    static final String SPEC_LIST = "@apiAutomationPermissionScopeResolver.specList(#request)";
    static final String SPEC = "@apiAutomationPermissionScopeResolver.spec(#id)";
    static final String GENERATION_REQUEST = "@apiAutomationPermissionScopeResolver.generationRequest(#command)";
    static final String GENERATION_TASK = "@apiAutomationPermissionScopeResolver.generationTask(#id)";
    static final String SCRIPT_BUNDLE = "@apiAutomationPermissionScopeResolver.scriptBundle(#id)";
    static final String RUN_REQUEST = "@apiAutomationPermissionScopeResolver.runRequest(#command)";
    static final String RUN = "@apiAutomationPermissionScopeResolver.run(#id)";

    private ApiAutomationPermissionScopes() {
    }
}
