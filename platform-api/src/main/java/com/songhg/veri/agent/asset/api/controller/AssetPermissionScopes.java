package com.songhg.veri.agent.asset.api.controller;

final class AssetPermissionScopes {

    static final String PROJECT_REQUEST = "@assetPermissionScopeResolver.project(#request.projectId())";
    static final String PROJECT_QUERY = "@assetPermissionScopeResolver.project(#projectId)";
    static final String ASSET_LIST = "@assetPermissionScopeResolver.assetList(#request)";
    static final String REQUIREMENT = "@assetPermissionScopeResolver.requirement(#id)";
    static final String API = "@assetPermissionScopeResolver.api(#id)";
    static final String PAGE = "@assetPermissionScopeResolver.page(#id)";
    static final String BUSINESS_FLOW = "@assetPermissionScopeResolver.businessFlow(#id)";
    static final String TEST_CASE = "@assetPermissionScopeResolver.testCase(#id)";
    static final String TRACE_LINK_LIST = "@assetPermissionScopeResolver.traceLinkList(#request)";
    static final String CREATE_LINK = "@assetPermissionScopeResolver.requirement(#request.requirementId())";

    private AssetPermissionScopes() {
    }
}
