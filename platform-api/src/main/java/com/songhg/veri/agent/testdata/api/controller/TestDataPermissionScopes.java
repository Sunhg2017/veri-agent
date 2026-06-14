package com.songhg.veri.agent.testdata.api.controller;

final class TestDataPermissionScopes {

    static final String DATA_SET_REQUEST = "@testDataPermissionScopeResolver.dataSetRequest(#command)";
    static final String DATA_SET_LIST = "@testDataPermissionScopeResolver.dataSetList(#request)";
    static final String DATA_SET = "@testDataPermissionScopeResolver.dataSet(#id)";
    static final String ACCOUNT_POOL_REQUEST = "@testDataPermissionScopeResolver.accountPoolRequest(#command)";
    static final String ACCOUNT_POOL_LIST = "@testDataPermissionScopeResolver.accountPoolList(#request)";
    static final String ACCOUNT_POOL = "@testDataPermissionScopeResolver.accountPool(#id)";
    static final String ACCOUNT = "@testDataPermissionScopeResolver.pooledAccount(#id)";
    static final String LEASE_REQUEST = "@testDataPermissionScopeResolver.leaseRequest(#command)";
    static final String LEASE_LIST = "@testDataPermissionScopeResolver.leaseList(#request)";
    static final String LEASE = "@testDataPermissionScopeResolver.lease(#id)";
    static final String TASK_REQUEST = "@testDataPermissionScopeResolver.taskRequest(#command)";
    static final String TASK_LIST = "@testDataPermissionScopeResolver.taskList(#request)";
    static final String TASK = "@testDataPermissionScopeResolver.task(#id)";

    private TestDataPermissionScopes() {
    }
}
