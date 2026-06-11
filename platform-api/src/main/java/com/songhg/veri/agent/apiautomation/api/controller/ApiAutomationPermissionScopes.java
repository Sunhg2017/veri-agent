package com.songhg.veri.agent.apiautomation.api.controller;

final class ApiAutomationPermissionScopes {

    static final String SPEC_REQUEST = "@apiAutomationPermissionScopeResolver.specRequest(#command)";
    static final String SPEC_LIST = "@apiAutomationPermissionScopeResolver.specList(#request)";
    static final String SPEC = "@apiAutomationPermissionScopeResolver.spec(#id)";

    private ApiAutomationPermissionScopes() {
    }
}
