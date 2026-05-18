package com.songhg.veri.agent.auth.infrastructure;

import com.songhg.veri.agent.auth.domain.AuthSessionDraft;
import com.songhg.veri.agent.auth.domain.AuthSessionRecord;
import com.songhg.veri.agent.auth.domain.AuthSessionStore;
import com.songhg.veri.agent.auth.infrastructure.mapper.AuthMapper;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Profile("db")
@Repository
public class PostgresAuthSessionStore implements AuthSessionStore {

    private final AuthMapper mapper;

    public PostgresAuthSessionStore(AuthMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void create(AuthSessionDraft draft) {
        mapper.createSession(draft);
    }

    @Override
    public boolean isActive(UUID sessionId, UUID userId, long authVersion, Instant now) {
        return mapper.isSessionActive(sessionId, userId, authVersion, now);
    }

    @Override
    public Optional<AuthSessionRecord> findByRefreshTokenHash(String refreshTokenHash) {
        return Optional.ofNullable(mapper.findByRefreshTokenHash(refreshTokenHash));
    }

    @Override
    public void revoke(UUID sessionId, UUID revokedBy, String reason) {
        mapper.revokeSession(sessionId, revokedBy, reason);
    }
}
