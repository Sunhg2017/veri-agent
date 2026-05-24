package com.songhg.veri.agent.management.api.controller;

import com.songhg.veri.agent.auth.application.AuthUserPrincipal;
import com.songhg.veri.agent.authorization.application.PermissionCodes;
import com.songhg.veri.agent.authorization.application.RequirePermission;
import com.songhg.veri.agent.common.api.PageResponse;
import com.songhg.veri.agent.common.openapi.ApiVersion;
import com.songhg.veri.agent.management.api.mapper.ManagementApiMapper;
import com.songhg.veri.agent.management.api.request.CreateRoleRequest;
import com.songhg.veri.agent.management.api.request.ManagementPageRequest;
import com.songhg.veri.agent.management.api.request.StatusChangeRequest;
import com.songhg.veri.agent.management.api.request.UpdateRoleRequest;
import com.songhg.veri.agent.management.api.response.PermissionView;
import com.songhg.veri.agent.management.api.response.RoleDetailView;
import com.songhg.veri.agent.management.api.response.RoleView;
import com.songhg.veri.agent.management.application.port.RoleOperations;
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
 * RBAC role endpoint. Privilege-escalation checks live with the role use case so controllers do
 * not need to assemble authorization context by hand.
 */
@ApiVersion
@RestController
@RequestMapping("/api/v1/management")
public class RoleController {

    private final RoleOperations roleOperations;
    private final ManagementApiMapper mapper;

    public RoleController(
            RoleOperations roleOperations,
            ManagementApiMapper mapper
    ) {
        this.roleOperations = roleOperations;
        this.mapper = mapper;
    }

    @GetMapping("/roles")
    @RequirePermission(PermissionCodes.ROLE_READ)
    public PageResponse<RoleView> roles(
            @Valid ManagementPageRequest pageRequest,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        return mapper.toRolePage(roleOperations.roles(pageRequest.toPageQuery()));
    }

    @GetMapping("/permissions")
    @RequirePermission(PermissionCodes.ROLE_READ)
    public PageResponse<PermissionView> permissions(
            @Valid ManagementPageRequest pageRequest,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        return mapper.toPermissionPage(roleOperations.permissions(pageRequest.toPageQuery()));
    }

    @GetMapping("/roles/{code}")
    @RequirePermission(PermissionCodes.ROLE_READ)
    public RoleDetailView role(
            @PathVariable String code,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        return mapper.toResponse(roleOperations.role(code.trim()));
    }

    @PostMapping("/roles")
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission(PermissionCodes.ROLE_CREATE)
    public RoleDetailView createRole(
            @Valid @RequestBody CreateRoleRequest request,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        return mapper.toResponse(roleOperations.createRole(mapper.toCommand(request), principal));
    }

    @PatchMapping("/roles/{code}")
    @RequirePermission(PermissionCodes.ROLE_EDIT)
    public RoleDetailView updateRole(
            @PathVariable String code,
            @Valid @RequestBody UpdateRoleRequest request,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        return mapper.toResponse(roleOperations.updateRole(code.trim(), mapper.toCommand(request), principal));
    }

    @PatchMapping("/roles/{code}/status")
    @RequirePermission(PermissionCodes.ROLE_EDIT)
    public RoleDetailView changeRoleStatus(
            @PathVariable String code,
            @Valid @RequestBody StatusChangeRequest request,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        return mapper.toResponse(roleOperations.changeRoleStatus(code.trim(), request.status(), principal));
    }
}
