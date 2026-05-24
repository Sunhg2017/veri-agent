package com.songhg.veri.agent.management.api.controller;

import com.songhg.veri.agent.auth.application.AuthUserPrincipal;
import com.songhg.veri.agent.authorization.application.PermissionCodes;
import com.songhg.veri.agent.authorization.application.RequirePermission;
import com.songhg.veri.agent.common.api.PageResponse;
import com.songhg.veri.agent.common.openapi.ApiVersion;
import com.songhg.veri.agent.management.api.mapper.ManagementApiMapper;
import com.songhg.veri.agent.management.api.request.CreateSettingRequest;
import com.songhg.veri.agent.management.api.request.ManagementPageRequest;
import com.songhg.veri.agent.management.api.request.StatusChangeRequest;
import com.songhg.veri.agent.management.api.request.UpdateSettingRequest;
import com.songhg.veri.agent.management.api.response.SettingView;
import com.songhg.veri.agent.management.application.port.SettingOperations;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Platform setting endpoint. Sensitive value checks and audit writes live in the setting operation
 * service so every caller shares the same guardrails.
 */
@ApiVersion
@RestController
@RequestMapping("/api/v1/management")
public class SettingController {

    private final SettingOperations settingOperations;
    private final ManagementApiMapper mapper;

    public SettingController(SettingOperations settingOperations, ManagementApiMapper mapper) {
        this.settingOperations = settingOperations;
        this.mapper = mapper;
    }

    @GetMapping("/settings")
    @RequirePermission(PermissionCodes.CONFIG_READ)
    public PageResponse<SettingView> settings(
            @Valid ManagementPageRequest pageRequest,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        return mapper.toSettingPage(settingOperations.settings(pageRequest.toPageQuery()));
    }

    @PostMapping("/settings")
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission(PermissionCodes.CONFIG_EDIT)
    public SettingView createSetting(
            @Valid @RequestBody CreateSettingRequest request,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        return mapper.toResponse(settingOperations.createSetting(mapper.toCommand(request), principal));
    }

    @GetMapping("/settings/{key}")
    @RequirePermission(PermissionCodes.CONFIG_READ)
    public SettingView setting(
            @PathVariable String key,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        return mapper.toResponse(settingOperations.setting(key.trim()));
    }

    @PatchMapping("/settings/{key}")
    @RequirePermission(PermissionCodes.CONFIG_EDIT)
    public SettingView updateSetting(
            @PathVariable String key,
            @Valid @RequestBody UpdateSettingRequest request,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        return mapper.toResponse(settingOperations.updateSetting(key.trim(), mapper.toCommand(request), principal));
    }

    @PatchMapping("/settings/{key}/status")
    @RequirePermission(PermissionCodes.CONFIG_EDIT)
    public SettingView changeSettingStatus(
            @PathVariable String key,
            @Valid @RequestBody StatusChangeRequest request,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        return mapper.toResponse(settingOperations.changeSettingStatus(key.trim(), request.status(), principal));
    }
}
