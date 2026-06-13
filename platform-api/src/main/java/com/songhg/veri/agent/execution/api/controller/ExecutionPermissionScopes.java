package com.songhg.veri.agent.execution.api.controller;

final class ExecutionPermissionScopes {

    static final String PLAN_REQUEST = "@executionPermissionScopeResolver.planRequest(#command)";
    static final String PLAN_LIST = "@executionPermissionScopeResolver.planList(#request)";
    static final String PLAN = "@executionPermissionScopeResolver.plan(#id)";
    static final String RUN_LIST = "@executionPermissionScopeResolver.runList(#request)";
    static final String RUN = "@executionPermissionScopeResolver.run(#id)";

    private ExecutionPermissionScopes() {
    }
}
