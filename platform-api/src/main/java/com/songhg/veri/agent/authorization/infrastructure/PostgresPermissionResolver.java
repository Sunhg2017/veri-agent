package com.songhg.veri.agent.authorization.infrastructure;

import com.songhg.veri.agent.authorization.application.PermissionResolver;
import com.songhg.veri.agent.authorization.infrastructure.mapper.PermissionMapper;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Profile("db")
@Component
public class PostgresPermissionResolver implements PermissionResolver {

    private final PermissionMapper mapper;

    public PostgresPermissionResolver(PermissionMapper mapper) {
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
}
