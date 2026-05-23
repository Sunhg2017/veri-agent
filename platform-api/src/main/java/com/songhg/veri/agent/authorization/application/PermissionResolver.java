package com.songhg.veri.agent.authorization.application;

import com.songhg.veri.agent.auth.application.AuthUserPrincipal;
import java.util.List;
import java.util.Set;

public interface PermissionResolver {

    Set<String> permissionsForRoles(List<String> roles);

    default boolean hasPermission(AuthUserPrincipal principal, String permission, ResourceScope scope) {
        if (principal == null || permission == null || permission.isBlank()) {
            return false;
        }
        return permissionsForRoles(principal.roles()).contains(permission);
    }
}
