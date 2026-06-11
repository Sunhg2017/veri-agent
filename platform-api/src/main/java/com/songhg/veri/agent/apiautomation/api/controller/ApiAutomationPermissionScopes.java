package com.songhg.veri.agent.apiautomation.api.controller;

final class ApiAutomationPermissionScopes {

    static final String SPEC_REQUEST = "@apiAutomationPermissionScopeResolver.specRequest(#command)";
    static final String SPEC_LIST = "@apiAutomationPermissionScopeResolver.specList(#request)";
    static final String SPEC = "@apiAutomationPermissionScopeResolver.spec(#id)";
    static final String GENERATION_REQUEST = "@apiAutomationPermissionScopeResolver.generationRequest(#command)";
    static final String GENERATION_TASK = "@apiAutomationPermissionScopeResolver.generationTask(#id)";

    private ApiAutomationPermissionScopes() {
    }
}
