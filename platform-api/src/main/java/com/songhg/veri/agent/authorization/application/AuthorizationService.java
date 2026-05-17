package com.songhg.veri.agent.authorization.application;

import com.songhg.veri.agent.auth.application.AuthUserPrincipal;
import com.songhg.veri.agent.common.audit.AuditLogWriter;
import java.util.List;
import java.util.Set;
import org.springframework.security.access.AccessDeniedException;
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
            throw new AccessDeniedException("缺少权限：" + permission);
        }
    }

    public boolean hasPermission(AuthUserPrincipal principal, String permission) {
        if (principal == null || permission == null || permission.isBlank()) {
            return false;
        }
        return permissions(principal).contains(permission);
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
}
