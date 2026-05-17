package com.songhg.veri.agent.authorization.infrastructure;

import com.songhg.veri.agent.authorization.application.PermissionResolver;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

@Profile("db")
@Component
public class PostgresPermissionResolver implements PermissionResolver {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public PostgresPermissionResolver(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Set<String> permissionsForRoles(List<String> roles) {
        if (roles == null || roles.isEmpty()) {
            return Set.of();
        }
        List<String> permissions = jdbcTemplate.queryForList("""
                select distinct p.code
                from rbac_role r
                join rbac_role_permission rp on rp.role_id = r.id
                    and rp.effect = 'ALLOW'
                    and rp.deleted_at is null
                join rbac_permission p on p.id = rp.permission_id
                    and p.status = 'ENABLED'
                where r.code in (:roles)
                  and r.status = 'ENABLED'
                  and r.deleted_at is null
                order by p.code
                """,
                Map.of("roles", roles),
                String.class
        );
        return Set.copyOf(new TreeSet<>(permissions));
    }
}
