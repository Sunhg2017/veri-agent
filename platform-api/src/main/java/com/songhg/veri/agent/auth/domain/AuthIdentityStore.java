package com.songhg.veri.agent.auth.domain;

import java.util.Optional;
import java.util.UUID;

public interface AuthIdentityStore {

    Optional<AuthUserRecord> findEnabledByUsername(String username);

    Optional<AuthUserRecord> findEnabledByUserId(UUID userId);

    void changePassword(UUID userId, String passwordHash, UUID updatedBy);
}
