package com.songhg.veri.agent.management.application.security;

import com.songhg.veri.agent.auth.application.AuthUserPrincipal;
import com.songhg.veri.agent.authorization.application.AuthorizationService;
import com.songhg.veri.agent.authorization.application.PermissionCodes;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * Centralizes management-specific authorization decisions that depend on command payloads.
 */
@Service
public class ManagementAuthorizationGuard {

    private final AuthorizationService authorizationService;

    public ManagementAuthorizationGuard(AuthorizationService authorizationService) {
        this.authorizationService = authorizationService;
    }

    public void requireDepartmentStatus(AuthUserPrincipal actor, String status) {
        authorizationService.require(actor, PermissionCodes.departmentStatusPermission(status));
    }

    public void requireProjectStatus(AuthUserPrincipal actor, String status) {
        authorizationService.require(actor, PermissionCodes.projectStatusPermission(status));
    }

    public void requireApplicationStatus(AuthUserPrincipal actor, String status) {
        authorizationService.require(actor, PermissionCodes.applicationStatusPermission(status));
    }

    public void requireEnvironmentStatus(AuthUserPrincipal actor, String status) {
        authorizationService.require(actor, PermissionCodes.environmentStatusPermission(status));
    }

    public Set<String> assignablePermissions(AuthUserPrincipal actor) {
        return authorizationService.permissions(actor);
    }
}
