package com.songhg.veri.agent.auth.infrastructure;

import com.songhg.veri.agent.auth.domain.AuthIdentityStore;
import com.songhg.veri.agent.auth.domain.AuthUserRecord;
import com.songhg.veri.agent.auth.infrastructure.mapper.AuthMapper;
import com.songhg.veri.agent.auth.infrastructure.mapper.AuthUserRow;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Profile("db")
@Repository
public class JdbcAuthIdentityStore implements AuthIdentityStore {

    private final AuthMapper mapper;

    public JdbcAuthIdentityStore(AuthMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<AuthUserRecord> findEnabledByUsername(String username) {
        return Optional.ofNullable(mapper.findEnabledUserByUsername(username)).map(this::toRecord);
    }

    @Override
    public Optional<AuthUserRecord> findEnabledByUserId(UUID userId) {
        return Optional.ofNullable(mapper.findEnabledUserById(userId)).map(this::toRecord);
    }

    @Override
    public void changePassword(UUID userId, String passwordHash, UUID updatedBy) {
        mapper.changePassword(userId, passwordHash, updatedBy);
    }

    private AuthUserRecord toRecord(AuthUserRow row) {
        return new AuthUserRecord(
                row.userId(),
                row.username(),
                row.displayName(),
                row.email(),
                row.passwordHash(),
                row.mustChangePassword(),
                row.authVersion(),
                splitRoles(row.roleCodes())
        );
    }

    private List<String> splitRoles(String roleCodes) {
        if (roleCodes == null || roleCodes.isBlank()) {
            return List.of();
        }
        return Arrays.stream(roleCodes.split(","))
                .filter(role -> !role.isBlank())
                .sorted()
                .toList();
    }
}
