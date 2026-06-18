package com.songhg.veri.agent.uie2e.api.controller;

final class UiE2ePermissionScopes {

    static final String SCENE_REQUEST = "@uiE2ePermissionScopeResolver.sceneRequest(#command)";
    static final String SCENE_LIST = "@uiE2ePermissionScopeResolver.sceneList(#request)";
    static final String SCENE = "@uiE2ePermissionScopeResolver.scene(#id)";

    private UiE2ePermissionScopes() {
    }
}
