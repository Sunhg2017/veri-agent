package com.songhg.veri.agent.management.api.controller;

import com.songhg.veri.agent.auth.application.AuthUserPrincipal;
import com.songhg.veri.agent.authorization.application.PermissionCodes;
import com.songhg.veri.agent.authorization.application.RequirePermission;
import com.songhg.veri.agent.common.api.PageResponse;
import com.songhg.veri.agent.common.openapi.ApiVersion;
import com.songhg.veri.agent.management.api.mapper.ManagementApiMapper;
import com.songhg.veri.agent.management.api.request.CreateEnvironmentRequest;
import com.songhg.veri.agent.management.api.request.ManagementPageRequest;
import com.songhg.veri.agent.management.api.request.ScopedUserRoleRequest;
import com.songhg.veri.agent.management.api.request.StatusChangeRequest;
import com.songhg.veri.agent.management.api.request.UpdateEnvironmentRequest;
import com.songhg.veri.agent.management.api.response.EnvironmentConnectivityCheckView;
import com.songhg.veri.agent.management.api.response.EnvironmentView;
import com.songhg.veri.agent.management.api.response.ScopedUserRoleView;
import com.songhg.veri.agent.management.application.port.EnvironmentOperations;
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
 * Environment management endpoint. Connectivity checks remain explicit operations because they
 * have external I/O side effects and are audited separately from normal reads.
 */
@ApiVersion
@RestController
@RequestMapping("/api/v1/management")
public class EnvironmentController {

    private final EnvironmentOperations environmentOperations;
    private final ManagementApiMapper mapper;

    public EnvironmentController(
            EnvironmentOperations environmentOperations,
            ManagementApiMapper mapper
    ) {
        this.environmentOperations = environmentOperations;
        this.mapper = mapper;
    }

    @GetMapping("/environments")
    @RequirePermission(PermissionCodes.ENVIRONMENT_READ)
    public PageResponse<EnvironmentView> environments(
            @Valid ManagementPageRequest pageRequest,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        return mapper.toEnvironmentPage(environmentOperations.environments(pageRequest.toPageQuery(), principal));
    }

    @PostMapping("/environments")
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission(PermissionCodes.ENVIRONMENT_CREATE)
    public EnvironmentView createEnvironment(
            @Valid @RequestBody CreateEnvironmentRequest request,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        return mapper.toResponse(environmentOperations.createEnvironment(mapper.toCommand(request), principal));
    }

    @GetMapping("/environments/{key}")
    @RequirePermission(PermissionCodes.ENVIRONMENT_READ)
    public EnvironmentView environment(
            @PathVariable String key,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        return mapper.toResponse(environmentOperations.environment(key.trim()));
    }

    @PatchMapping("/environments/{key}")
    @RequirePermission(PermissionCodes.ENVIRONMENT_EDIT)
    public EnvironmentView updateEnvironment(
            @PathVariable String key,
            @Valid @RequestBody UpdateEnvironmentRequest request,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        return mapper.toResponse(environmentOperations.updateEnvironment(key.trim(), mapper.toCommand(request), principal));
    }

    @PatchMapping("/environments/{key}/status")
    public EnvironmentView changeEnvironmentStatus(
            @PathVariable String key,
            @Valid @RequestBody StatusChangeRequest request,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        return mapper.toResponse(environmentOperations.changeEnvironmentStatus(key.trim(), request.status(), principal));
    }

    @GetMapping("/environments/{key}/connectivity-check")
    @RequirePermission(PermissionCodes.ENVIRONMENT_READ)
    public EnvironmentConnectivityCheckView environmentConnectivityCheck(
            @PathVariable String key,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        return mapper.toResponse(environmentOperations.environmentConnectivityCheck(key.trim()));
    }

    @PostMapping("/environments/{key}/connectivity-check")
    @RequirePermission(PermissionCodes.ENVIRONMENT_EDIT)
    public EnvironmentConnectivityCheckView checkEnvironmentConnectivity(
            @PathVariable String key,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        return mapper.toResponse(environmentOperations.checkEnvironmentConnectivity(key.trim(), principal));
    }

    @GetMapping("/environments/{key}/users")
    @RequirePermission(PermissionCodes.ENVIRONMENT_READ)
    public PageResponse<ScopedUserRoleView> environmentUsers(
            @PathVariable String key,
            @Valid ManagementPageRequest pageRequest,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        return mapper.toScopedUserRolePage(environmentOperations.environmentUsers(key.trim(), pageRequest.toPageQuery()));
    }

    @PostMapping("/environments/{key}/users")
    @RequirePermission(PermissionCodes.ENVIRONMENT_USER_MANAGE)
    @RequirePermission(PermissionCodes.ROLE_BIND)
    public ScopedUserRoleView addEnvironmentUser(
            @PathVariable String key,
            @Valid @RequestBody ScopedUserRoleRequest request,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        return mapper.toResponse(environmentOperations.addEnvironmentUser(key.trim(), mapper.toCommand(request), principal));
    }

    @PostMapping("/environments/{key}/users/{username}/remove")
    @RequirePermission(PermissionCodes.ENVIRONMENT_USER_MANAGE)
    @RequirePermission(PermissionCodes.ROLE_UNBIND)
    public ScopedUserRoleView removeEnvironmentUser(
            @PathVariable String key,
            @PathVariable String username,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        return mapper.toResponse(environmentOperations.removeEnvironmentUser(key.trim(), username.trim(), principal));
    }
}
