package com.songhg.veri.agent.authorization.application;

import com.songhg.veri.agent.auth.application.AuthUserPrincipal;
import com.songhg.veri.agent.common.audit.AuditLogWriter;
import com.songhg.veri.agent.common.error.PlatformAccessDeniedException;
import com.songhg.veri.agent.modelaccess.security.ServicePrincipal;
import java.util.List;
import java.util.Set;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class AuthorizationService {

    private final PermissionResolver permissionResolver;
    private final AuditLogWriter auditLogWriter;

    public AuthorizationService(PermissionResolver permissionResolver, AuditLogWriter auditLogWriter) {
        this.permissionResolver = permissionResolver;
        this.auditLogWriter = auditLogWriter;
    }

    public void require(AuthUserPrincipal principal, String permission) {
        if (!hasPermission(principal, permission)) {
            auditLogWriter.record(AuditLogWriter.denied(
                    principal,
                    "权限校验",
                    "permission",
                    permission,
                    "缺少权限：" + permission
            ));
            throw new PlatformAccessDeniedException(permission);
        }
    }

    public AuthUserPrincipal requireCurrent(String permission) {
        Object principal = currentPrincipal();
        if (principal instanceof ServicePrincipal) {
            return null;
        }
        if (principal instanceof AuthUserPrincipal userPrincipal) {
            require(userPrincipal, permission);
            return userPrincipal;
        }
        throw new PlatformAccessDeniedException(permission);
    }

    public AuthUserPrincipal currentUserPrincipal() {
        Object principal = currentPrincipal();
        return principal instanceof AuthUserPrincipal userPrincipal ? userPrincipal : null;
    }

    public ServicePrincipal currentServicePrincipal() {
        Object principal = currentPrincipal();
        return principal instanceof ServicePrincipal servicePrincipal ? servicePrincipal : null;
    }

    public void require(AuthUserPrincipal principal, String permission, ResourceScope scope) {
        ResourceScope resourceScope = scope == null ? ResourceScope.platform() : scope;
        if (!hasPermission(principal, permission, resourceScope)) {
            auditLogWriter.record(AuditLogWriter.denied(
                    principal,
                    "资源权限校验",
                    "permission",
                    resourceScope.auditResourceId(permission),
                    "缺少资源权限：" + permission
            ));
            throw new PlatformAccessDeniedException(
                    permission,
                    resourceScope.scopeType(),
                    resourceScope.auditResourceId(permission)
            );
        }
    }

    public boolean hasPermission(AuthUserPrincipal principal, String permission) {
        if (principal == null || permission == null || permission.isBlank()) {
            return false;
        }
        return permissions(principal).contains(permission);
    }

    public boolean hasPermission(AuthUserPrincipal principal, String permission, ResourceScope scope) {
        if (principal == null || permission == null || permission.isBlank()) {
            return false;
        }
        return permissionResolver.hasPermission(principal, permission, scope == null ? ResourceScope.platform() : scope);
    }

    public Set<String> permissions(AuthUserPrincipal principal) {
        if (principal == null) {
            return Set.of();
        }
        return permissionResolver.permissionsForRoles(principal.roles());
    }

    public Set<String> permissions(List<String> roles) {
        return permissionResolver.permissionsForRoles(roles);
    }

    private Object currentPrincipal() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication == null ? null : authentication.getPrincipal();
    }
}
