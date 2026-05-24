package com.songhg.veri.agent.management.api.controller;

import com.songhg.veri.agent.auth.application.AuthUserPrincipal;
import com.songhg.veri.agent.authorization.application.PermissionCodes;
import com.songhg.veri.agent.authorization.application.RequirePermission;
import com.songhg.veri.agent.common.api.PageResponse;
import com.songhg.veri.agent.common.openapi.ApiVersion;
import com.songhg.veri.agent.management.api.mapper.ManagementApiMapper;
import com.songhg.veri.agent.management.api.request.CreateNamedRequest;
import com.songhg.veri.agent.management.api.request.ManagementPageRequest;
import com.songhg.veri.agent.management.api.request.ResetPasswordRequest;
import com.songhg.veri.agent.management.api.request.RoleBindingRequest;
import com.songhg.veri.agent.management.api.request.UpdateUserRequest;
import com.songhg.veri.agent.management.api.response.UserView;
import com.songhg.veri.agent.management.application.port.RoleOperations;
import com.songhg.veri.agent.management.application.port.UserOperations;
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
 * User account endpoint. Role assignment is kept beside user routes to preserve the existing URL
 * contract while delegating the role mutation to the RBAC operation service.
 */
@ApiVersion
@RestController
@RequestMapping("/api/v1/management")
public class UserController {

    private final UserOperations userOperations;
    private final RoleOperations roleOperations;
    private final ManagementApiMapper mapper;

    public UserController(
            UserOperations userOperations,
            RoleOperations roleOperations,
            ManagementApiMapper mapper
    ) {
        this.userOperations = userOperations;
        this.roleOperations = roleOperations;
        this.mapper = mapper;
    }

    @GetMapping("/users")
    @RequirePermission(PermissionCodes.USER_READ)
    public PageResponse<UserView> users(
            @Valid ManagementPageRequest pageRequest,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        return mapper.toUserPage(userOperations.users(pageRequest.toPageQuery()));
    }

    @GetMapping("/users/{username}")
    @RequirePermission(PermissionCodes.USER_READ)
    public UserView user(
            @PathVariable String username,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        return mapper.toResponse(userOperations.user(username.trim()));
    }

    @PatchMapping("/users/{username}")
    @RequirePermission(PermissionCodes.USER_EDIT)
    public UserView updateUser(
            @PathVariable String username,
            @Valid @RequestBody UpdateUserRequest request,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        return mapper.toResponse(userOperations.updateUser(username.trim(), mapper.toCommand(request), principal));
    }

    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission(PermissionCodes.USER_CREATE)
    public UserView createUser(
            @Valid @RequestBody CreateNamedRequest request,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        return mapper.toResponse(userOperations.createUser(request.name().trim(), principal));
    }

    @PostMapping("/users/{username}/enable")
    @RequirePermission(PermissionCodes.USER_ENABLE)
    public UserView enableUser(
            @PathVariable String username,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        return mapper.toResponse(userOperations.enableUser(username.trim(), principal));
    }

    @PostMapping("/users/{username}/disable")
    @RequirePermission(PermissionCodes.USER_DISABLE)
    public UserView disableUser(
            @PathVariable String username,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        return mapper.toResponse(userOperations.disableUser(username.trim(), principal));
    }

    @PostMapping("/users/{username}/lock")
    @RequirePermission(PermissionCodes.USER_LOCK)
    public UserView lockUser(
            @PathVariable String username,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        return mapper.toResponse(userOperations.lockUser(username.trim(), principal));
    }

    @PostMapping("/users/{username}/unlock")
    @RequirePermission(PermissionCodes.USER_UNLOCK)
    public UserView unlockUser(
            @PathVariable String username,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        return mapper.toResponse(userOperations.unlockUser(username.trim(), principal));
    }

    @PostMapping("/users/{username}/reset-password")
    @RequirePermission(PermissionCodes.USER_RESET_PASSWORD)
    public UserView resetUserPassword(
            @PathVariable String username,
            @Valid @RequestBody ResetPasswordRequest request,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        return mapper.toResponse(userOperations.resetUserPassword(username.trim(), request.newPassword(), principal));
    }

    @PostMapping("/users/{username}/roles")
    @RequirePermission(PermissionCodes.USER_ASSIGN_ROLE)
    @RequirePermission(PermissionCodes.ROLE_BIND)
    public UserView assignUserRole(
            @PathVariable String username,
            @Valid @RequestBody RoleBindingRequest request,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        return mapper.toResponse(roleOperations.assignUserRole(username.trim(), request.roleCode().trim(), principal));
    }

    @PostMapping("/users/{username}/roles/unassign")
    @RequirePermission(PermissionCodes.USER_ASSIGN_ROLE)
    @RequirePermission(PermissionCodes.ROLE_UNBIND)
    public UserView unassignUserRole(
            @PathVariable String username,
            @Valid @RequestBody RoleBindingRequest request,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        return mapper.toResponse(roleOperations.unassignUserRole(username.trim(), request.roleCode().trim(), principal));
    }
}
