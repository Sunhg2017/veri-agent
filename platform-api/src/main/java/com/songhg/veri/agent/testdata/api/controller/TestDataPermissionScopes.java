package com.songhg.veri.agent.testdata.api.controller;

final class TestDataPermissionScopes {

    static final String DATA_SET_REQUEST = "@testDataPermissionScopeResolver.dataSetRequest(#command)";
    static final String DATA_SET_LIST = "@testDataPermissionScopeResolver.dataSetList(#request)";
    static final String DATA_SET = "@testDataPermissionScopeResolver.dataSet(#id)";
    static final String ACCOUNT_POOL_REQUEST = "@testDataPermissionScopeResolver.accountPoolRequest(#command)";
    static final String ACCOUNT_POOL_LIST = "@testDataPermissionScopeResolver.accountPoolList(#request)";
    static final String ACCOUNT_POOL = "@testDataPermissionScopeResolver.accountPool(#id)";
    static final String ACCOUNT = "@testDataPermissionScopeResolver.pooledAccount(#id)";

    private TestDataPermissionScopes() {
    }
}
