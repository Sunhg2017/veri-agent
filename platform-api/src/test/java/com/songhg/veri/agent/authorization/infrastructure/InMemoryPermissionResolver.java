package com.songhg.veri.agent.authorization.infrastructure;

import com.songhg.veri.agent.auth.application.AuthUserPrincipal;
import com.songhg.veri.agent.authorization.application.PermissionResolver;
import com.songhg.veri.agent.authorization.application.ResourceScope;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Profile("local")
@Primary
@Component
public class InMemoryPermissionResolver implements PermissionResolver {

    @Override
    public Set<String> permissionsForRoles(List<String> roles) {
        if (roles == null || roles.isEmpty()) {
            return Set.of();
        }
        Set<String> permissions = new LinkedHashSet<>();
        roles.stream()
                .map(InMemoryPermissionResolver::roleCode)
                .map(BuiltinPermissionCatalog.ROLE_PERMISSIONS::get)
                .filter(rolePermissions -> rolePermissions != null && !rolePermissions.isEmpty())
                .flatMap(Collection::stream)
                .sorted()
                .forEach(permissions::add);
        return Set.copyOf(permissions);
    }

    @Override
    public boolean hasPermission(AuthUserPrincipal principal, String permission, ResourceScope scope) {
        if (principal == null || !StringUtils.hasText(permission) || principal.roles() == null) {
            return false;
        }
        ResourceScope resourceScope = scope == null ? ResourceScope.platform() : scope;
        return principal.roles().stream()
                .anyMatch(role -> roleAllows(role, permission, resourceScope));
    }

    private static boolean roleAllows(String role, String permission, ResourceScope scope) {
        String code = roleCode(role);
        Set<String> permissions = BuiltinPermissionCatalog.ROLE_PERMISSIONS.get(code);
        if (permissions == null || !permissions.contains(permission)) {
            return false;
        }
        RoleScope roleScope = roleScope(role);
        if (roleScope == null) {
            return platformRole(code) || !scope.isPlatform();
        }
        if ("PLATFORM".equals(roleScope.scopeType())) {
            return true;
        }
        return !scope.isPlatform()
                && roleScope.scopeType().equals(scope.scopeType())
                && roleScope.scopeId().equalsIgnoreCase(scope.scopeId());
    }

    private static boolean platformRole(String roleCode) {
        return "SuperAdmin".equals(roleCode) || "PlatformAdmin".equals(roleCode);
    }

    private static String roleCode(String role) {
        if (!StringUtils.hasText(role)) {
            return "";
        }
        int index = role.indexOf('@');
        return index < 0 ? role.trim() : role.substring(0, index).trim();
    }

    private static RoleScope roleScope(String role) {
        if (!StringUtils.hasText(role)) {
            return null;
        }
        int index = role.indexOf('@');
        if (index < 0 || index == role.length() - 1) {
            return null;
        }
        String rawScope = role.substring(index + 1).trim();
        int separator = rawScope.indexOf(':');
        if (separator < 0) {
            return new RoleScope("PROJECT", rawScope);
        }
        String scopeType = rawScope.substring(0, separator).trim().toUpperCase(Locale.ROOT);
        String scopeId = rawScope.substring(separator + 1).trim();
        if (!StringUtils.hasText(scopeType) || !StringUtils.hasText(scopeId)) {
            return null;
        }
        return new RoleScope(scopeType, scopeId);
    }

    private record RoleScope(String scopeType, String scopeId) {
    }
}
