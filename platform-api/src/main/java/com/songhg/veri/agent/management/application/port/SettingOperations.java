package com.songhg.veri.agent.management.application.port;

import com.songhg.veri.agent.auth.application.AuthUserPrincipal;
import com.songhg.veri.agent.common.api.PageQuery;
import com.songhg.veri.agent.common.api.PageResponse;
import com.songhg.veri.agent.management.application.command.CreateSettingCommand;
import com.songhg.veri.agent.management.application.command.UpdateSettingCommand;
import com.songhg.veri.agent.management.application.view.SettingView;

/**
 * Platform setting management use cases. Settings can affect runtime behavior, so changes must be
 * traceable and status transitions must go through explicit authorization.
 */
public interface SettingOperations {

    /**
     * Lists platform settings.
     */
    PageResponse<SettingView> settings(PageQuery pageQuery);

    /**
     * Returns one setting by key.
     */
    SettingView setting(String key);

    /**
     * Creates a setting with actor attribution.
     */
    SettingView createSetting(CreateSettingCommand request, AuthUserPrincipal actor);

    /**
     * Updates setting metadata or value while preserving the setting key.
     */
    SettingView updateSetting(String key, UpdateSettingCommand request, AuthUserPrincipal actor);

    /**
     * Applies the requested setting status after the caller passes permission checks.
     */
    SettingView changeSettingStatus(String key, String status, AuthUserPrincipal actor);
}
