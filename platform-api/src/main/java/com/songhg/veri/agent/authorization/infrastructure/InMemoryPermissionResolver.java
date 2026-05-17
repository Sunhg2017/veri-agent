package com.songhg.veri.agent.authorization.infrastructure;

import com.songhg.veri.agent.authorization.application.PermissionResolver;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Profile("local")
@Component
public class InMemoryPermissionResolver implements PermissionResolver {

    @Override
    public Set<String> permissionsForRoles(List<String> roles) {
        if (roles == null || roles.isEmpty()) {
            return Set.of();
        }
        Set<String> permissions = new LinkedHashSet<>();
        roles.stream()
                .map(BuiltinPermissionCatalog.ROLE_PERMISSIONS::get)
                .filter(rolePermissions -> rolePermissions != null && !rolePermissions.isEmpty())
                .flatMap(Collection::stream)
                .sorted()
                .forEach(permissions::add);
        return Set.copyOf(permissions);
    }
}
