package com.songhg.veri.agent.management.application;

import com.songhg.veri.agent.auth.application.AuthUserPrincipal;
import com.songhg.veri.agent.common.api.PageQuery;
import com.songhg.veri.agent.common.api.PageResponse;
import com.songhg.veri.agent.management.api.request.CreateSettingRequest;
import com.songhg.veri.agent.management.api.request.UpdateSettingRequest;
import com.songhg.veri.agent.management.api.response.SettingView;

public interface SettingOperations {

    PageResponse<SettingView> settings(PageQuery pageQuery);

    SettingView setting(String key);

    SettingView createSetting(CreateSettingRequest request, AuthUserPrincipal actor);

    SettingView updateSetting(String key, UpdateSettingRequest request, AuthUserPrincipal actor);

    SettingView changeSettingStatus(String key, String status, AuthUserPrincipal actor);
}
