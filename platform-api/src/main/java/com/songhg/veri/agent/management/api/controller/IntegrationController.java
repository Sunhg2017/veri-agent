package com.songhg.veri.agent.management.api.controller;

import com.songhg.veri.agent.auth.application.AuthUserPrincipal;
import com.songhg.veri.agent.authorization.application.PermissionCodes;
import com.songhg.veri.agent.authorization.application.RequirePermission;
import com.songhg.veri.agent.common.api.PageResponse;
import com.songhg.veri.agent.common.openapi.ApiVersion;
import com.songhg.veri.agent.management.api.mapper.ManagementApiMapper;
import com.songhg.veri.agent.management.api.request.CreateIntegrationRequest;
import com.songhg.veri.agent.management.api.request.ManagementPageRequest;
import com.songhg.veri.agent.management.api.request.StatusChangeRequest;
import com.songhg.veri.agent.management.api.request.UpdateIntegrationRequest;
import com.songhg.veri.agent.management.api.response.IntegrationView;
import com.songhg.veri.agent.management.application.port.IntegrationOperations;
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
 * Integration configuration endpoint. The service keeps integration records in the configuration
 * store, while this controller exposes them as a dedicated resource to callers.
 */
@ApiVersion
@RestController
@RequestMapping("/api/v1/management")
public class IntegrationController {

    private final IntegrationOperations integrationOperations;
    private final ManagementApiMapper mapper;

    public IntegrationController(IntegrationOperations integrationOperations, ManagementApiMapper mapper) {
        this.integrationOperations = integrationOperations;
        this.mapper = mapper;
    }

    @GetMapping("/integrations")
    @RequirePermission(PermissionCodes.CONFIG_READ)
    public PageResponse<IntegrationView> integrations(
            @Valid ManagementPageRequest pageRequest,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        return mapper.toIntegrationPage(integrationOperations.integrations(pageRequest.toPageQuery()));
    }

    @PostMapping("/integrations")
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission(PermissionCodes.CONFIG_EDIT)
    public IntegrationView createIntegration(
            @Valid @RequestBody CreateIntegrationRequest request,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        return mapper.toResponse(integrationOperations.createIntegration(mapper.toCommand(request), principal));
    }

    @GetMapping("/integrations/{key}")
    @RequirePermission(PermissionCodes.CONFIG_READ)
    public IntegrationView integration(
            @PathVariable String key,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        return mapper.toResponse(integrationOperations.integration(key.trim()));
    }

    @PatchMapping("/integrations/{key}")
    @RequirePermission(PermissionCodes.CONFIG_EDIT)
    public IntegrationView updateIntegration(
            @PathVariable String key,
            @Valid @RequestBody UpdateIntegrationRequest request,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        return mapper.toResponse(integrationOperations.updateIntegration(key.trim(), mapper.toCommand(request), principal));
    }

    @PatchMapping("/integrations/{key}/status")
    @RequirePermission(PermissionCodes.CONFIG_EDIT)
    public IntegrationView changeIntegrationStatus(
            @PathVariable String key,
            @Valid @RequestBody StatusChangeRequest request,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        return mapper.toResponse(integrationOperations.changeIntegrationStatus(key.trim(), request.status(), principal));
    }
}
