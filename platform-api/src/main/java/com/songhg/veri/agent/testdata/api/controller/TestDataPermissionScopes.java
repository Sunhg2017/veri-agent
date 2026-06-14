package com.songhg.veri.agent.testdata.api.controller;

final class TestDataPermissionScopes {

    static final String DATA_SET_REQUEST = "@testDataPermissionScopeResolver.dataSetRequest(#command)";
    static final String DATA_SET_LIST = "@testDataPermissionScopeResolver.dataSetList(#request)";
    static final String DATA_SET = "@testDataPermissionScopeResolver.dataSet(#id)";

    private TestDataPermissionScopes() {
    }
}
