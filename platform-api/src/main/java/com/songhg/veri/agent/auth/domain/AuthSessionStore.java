package com.songhg.veri.agent.auth.domain;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface AuthSessionStore {

    void create(AuthSessionDraft draft);

    boolean isActive(UUID sessionId, UUID userId, long authVersion, Instant now);

    Optional<AuthSessionRecord> findByRefreshTokenHash(String refreshTokenHash);

    void revoke(UUID sessionId, UUID revokedBy, String reason);

    int cleanupExpiredSessions(Instant expiresBefore, Instant revokedBefore);
}
