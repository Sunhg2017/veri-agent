package com.songhg.veri.agent.auth.infrastructure;

import com.songhg.veri.agent.auth.domain.AuthIdentityStore;
import com.songhg.veri.agent.auth.domain.AuthUserRecord;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Profile("local")
@Primary
@Repository
public class InMemoryAuthIdentityStore implements AuthIdentityStore {

    private final Map<UUID, MutableUser> usersById = new ConcurrentHashMap<>();
    private final Map<String, UUID> userIdsByUsername = new ConcurrentHashMap<>();

    public UUID seedUser(
            String username,
            String passwordHash,
            String displayName,
            String email,
            boolean mustChangePassword,
            List<String> roles
    ) {
        UUID existingUserId = userIdsByUsername.get(username);
        UUID userId = existingUserId == null ? UUID.randomUUID() : existingUserId;
        MutableUser user = new MutableUser(
                userId,
                username,
                displayName,
                email,
                passwordHash,
                mustChangePassword,
                1,
                List.copyOf(roles)
        );
        usersById.put(userId, user);
        userIdsByUsername.put(username, userId);
        return userId;
    }

    @Override
    public Optional<AuthUserRecord> findEnabledByUsername(String username) {
        UUID userId = userIdsByUsername.get(username);
        if (userId == null) {
            return Optional.empty();
        }
        return findEnabledByUserId(userId);
    }

    @Override
    public Optional<AuthUserRecord> findEnabledByUserId(UUID userId) {
        return Optional.ofNullable(usersById.get(userId)).map(this::toRecord);
    }

    @Override
    public void changePassword(UUID userId, String passwordHash, UUID updatedBy) {
        usersById.computeIfPresent(userId, (id, current) -> new MutableUser(
                current.userId(),
                current.username(),
                current.displayName(),
                current.email(),
                passwordHash,
                false,
                current.authVersion() + 1,
                current.roles()
        ));
    }

    private AuthUserRecord toRecord(MutableUser user) {
        return new AuthUserRecord(
                user.userId(),
                user.username(),
                user.displayName(),
                user.email(),
                user.passwordHash(),
                user.mustChangePassword(),
                user.authVersion(),
                user.roles()
        );
    }

    private record MutableUser(
            UUID userId,
            String username,
            String displayName,
            String email,
            String passwordHash,
            boolean mustChangePassword,
            long authVersion,
            List<String> roles
    ) {
    }
}
