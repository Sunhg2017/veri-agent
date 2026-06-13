package com.songhg.veri.agent.execution.api.controller;

final class ExecutionPermissionScopes {

    static final String PLAN_REQUEST = "@executionPermissionScopeResolver.planRequest(#command)";
    static final String PLAN_LIST = "@executionPermissionScopeResolver.planList(#request)";
    static final String PLAN = "@executionPermissionScopeResolver.plan(#id)";

    private ExecutionPermissionScopes() {
    }
}
