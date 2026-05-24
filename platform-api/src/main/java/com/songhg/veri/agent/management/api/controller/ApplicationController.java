package com.songhg.veri.agent.management.api.controller;

import com.songhg.veri.agent.auth.application.AuthUserPrincipal;
import com.songhg.veri.agent.authorization.application.AuthorizationService;
import com.songhg.veri.agent.authorization.application.PermissionCodes;
import com.songhg.veri.agent.authorization.application.RequirePermission;
import com.songhg.veri.agent.common.api.PageResponse;
import com.songhg.veri.agent.common.openapi.ApiVersion;
import com.songhg.veri.agent.management.api.mapper.ManagementApiMapper;
import com.songhg.veri.agent.management.api.request.CreateApplicationRequest;
import com.songhg.veri.agent.management.api.request.ManagementPageRequest;
import com.songhg.veri.agent.management.api.request.ScopedUserRoleRequest;
import com.songhg.veri.agent.management.api.request.StatusChangeRequest;
import com.songhg.veri.agent.management.api.request.UpdateApplicationRequest;
import com.songhg.veri.agent.management.api.response.ApplicationView;
import com.songhg.veri.agent.management.api.response.ScopedUserRoleView;
import com.songhg.veri.agent.management.application.port.ApplicationOperations;
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
 * Application management endpoint. Ownership routes stay with applications because their scope is
 * application-local even though the returned shape is a generic scoped user-role view.
 */
@ApiVersion
@RestController
@RequestMapping("/api/v1/management")
public class ApplicationController {

    private final ApplicationOperations applicationOperations;
    private final AuthorizationService authorizationService;
    private final ManagementApiMapper mapper;

    public ApplicationController(
            ApplicationOperations applicationOperations,
            AuthorizationService authorizationService,
            ManagementApiMapper mapper
    ) {
        this.applicationOperations = applicationOperations;
        this.authorizationService = authorizationService;
        this.mapper = mapper;
    }

    @GetMapping("/applications")
    @RequirePermission(PermissionCodes.APPLICATION_READ)
    public PageResponse<ApplicationView> applications(
            @Valid ManagementPageRequest pageRequest,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        return mapper.toApplicationPage(applicationOperations.applications(pageRequest.toPageQuery(), principal));
    }

    @PostMapping("/applications")
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission(PermissionCodes.APPLICATION_CREATE)
    public ApplicationView createApplication(
            @Valid @RequestBody CreateApplicationRequest request,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        return mapper.toResponse(applicationOperations.createApplication(mapper.toCommand(request), principal));
    }

    @GetMapping("/applications/{key}")
    @RequirePermission(PermissionCodes.APPLICATION_READ)
    public ApplicationView application(
            @PathVariable String key,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        return mapper.toResponse(applicationOperations.application(key.trim()));
    }

    @PatchMapping("/applications/{key}")
    @RequirePermission(PermissionCodes.APPLICATION_EDIT)
    public ApplicationView updateApplication(
            @PathVariable String key,
            @Valid @RequestBody UpdateApplicationRequest request,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        return mapper.toResponse(applicationOperations.updateApplication(key.trim(), mapper.toCommand(request), principal));
    }

    @PatchMapping("/applications/{key}/status")
    public ApplicationView changeApplicationStatus(
            @PathVariable String key,
            @Valid @RequestBody StatusChangeRequest request,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        authorizationService.require(principal, PermissionCodes.applicationStatusPermission(request.status()));
        return mapper.toResponse(applicationOperations.changeApplicationStatus(key.trim(), request.status(), principal));
    }

    @GetMapping("/applications/{key}/owners")
    @RequirePermission(PermissionCodes.APPLICATION_READ)
    public PageResponse<ScopedUserRoleView> applicationOwners(
            @PathVariable String key,
            @Valid ManagementPageRequest pageRequest,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        return mapper.toScopedUserRolePage(applicationOperations.applicationOwners(key.trim(), pageRequest.toPageQuery()));
    }

    @PostMapping("/applications/{key}/owners")
    @RequirePermission(PermissionCodes.APPLICATION_OWNER_MANAGE)
    @RequirePermission(PermissionCodes.ROLE_BIND)
    public ScopedUserRoleView addApplicationOwner(
            @PathVariable String key,
            @Valid @RequestBody ScopedUserRoleRequest request,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        return mapper.toResponse(applicationOperations.addApplicationOwner(key.trim(), mapper.toCommand(request), principal));
    }

    @PostMapping("/applications/{key}/owners/{username}/remove")
    @RequirePermission(PermissionCodes.APPLICATION_OWNER_MANAGE)
    @RequirePermission(PermissionCodes.ROLE_UNBIND)
    public ScopedUserRoleView removeApplicationOwner(
            @PathVariable String key,
            @PathVariable String username,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        return mapper.toResponse(applicationOperations.removeApplicationOwner(key.trim(), username.trim(), principal));
    }
}
