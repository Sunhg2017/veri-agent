package com.songhg.veri.agent.uie2e.api.controller;

final class UiE2ePermissionScopes {

    static final String SCENE_REQUEST = "@uiE2ePermissionScopeResolver.sceneRequest(#command)";
    static final String SCENE_LIST = "@uiE2ePermissionScopeResolver.sceneList(#request)";
    static final String SCENE = "@uiE2ePermissionScopeResolver.scene(#id)";
    static final String BUNDLE_REQUEST = "@uiE2ePermissionScopeResolver.bundleRequest(#command)";
    static final String BUNDLE_LIST = "@uiE2ePermissionScopeResolver.bundleList(#request)";
    static final String BUNDLE = "@uiE2ePermissionScopeResolver.bundle(#id)";
    static final String RUN_REQUEST = "@uiE2ePermissionScopeResolver.runRequest(#command)";
    static final String RUN_LIST = "@uiE2ePermissionScopeResolver.runList(#request)";
    static final String RUN = "@uiE2ePermissionScopeResolver.run(#id)";
    static final String FLAKY_REQUEST = "@uiE2ePermissionScopeResolver.flakyRequest(#command)";
    static final String FLAKY_LIST = "@uiE2ePermissionScopeResolver.flakyList(#request)";

    private UiE2ePermissionScopes() {
    }
}
