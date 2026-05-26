package com.songhg.veri.agent.authorization.infrastructure;

import com.songhg.veri.agent.auth.application.AuthUserPrincipal;
import com.songhg.veri.agent.authorization.application.PermissionResolver;
import com.songhg.veri.agent.authorization.application.ResourceScope;
import com.songhg.veri.agent.authorization.infrastructure.mapper.PermissionMapper;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Profile("db & !redis")
@Component
public class JdbcPermissionResolver implements PermissionResolver {

    private final PermissionMapper mapper;

    public JdbcPermissionResolver(PermissionMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Set<String> permissionsForRoles(List<String> roles) {
        if (roles == null || roles.isEmpty()) {
            return Set.of();
        }
        List<String> permissions = mapper.permissionsForRoles(roles);
        return Set.copyOf(new TreeSet<>(permissions));
    }

    @Override
    public boolean hasPermission(AuthUserPrincipal principal, String permission, ResourceScope scope) {
        if (principal == null || !StringUtils.hasText(permission)) {
            return false;
        }
        ResourceScope resourceScope = scope == null ? ResourceScope.platform() : scope;
        UUID scopeId = null;
        if (!resourceScope.isPlatform()) {
            Optional<UUID> parsedScopeId = uuid(resourceScope.scopeId());
            if (parsedScopeId.isEmpty()) {
                return false;
            }
            scopeId = parsedScopeId.get();
        }
        return mapper.hasPermissionForScope(
                principal.userId(),
                permission.trim(),
                resourceScope.scopeType(),
                scopeId
        );
    }

    private Optional<UUID> uuid(String value) {
        if (!StringUtils.hasText(value)) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(value.trim()));
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }
}
