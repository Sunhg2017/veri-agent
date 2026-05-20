package com.songhg.veri.agent.auth.infrastructure;

import com.songhg.veri.agent.auth.domain.AuthSessionDraft;
import com.songhg.veri.agent.auth.domain.AuthSessionRecord;
import com.songhg.veri.agent.auth.domain.AuthSessionStore;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Profile("local")
@Repository
public class InMemoryAuthSessionStore implements AuthSessionStore {

    private final Map<UUID, MutableSession> sessions = new ConcurrentHashMap<>();

    @Override
    public void create(AuthSessionDraft draft) {
        sessions.put(draft.sessionId(), new MutableSession(draft, false));
    }

    @Override
    public boolean isActive(UUID sessionId, UUID userId, long authVersion, Instant now) {
        MutableSession session = sessions.get(sessionId);
        return session != null
                && session.draft().userId().equals(userId)
                && session.draft().authVersion() == authVersion
                && !session.revoked()
                && session.draft().expiresAt().isAfter(now);
    }

    @Override
    public Optional<AuthSessionRecord> findByRefreshTokenHash(String refreshTokenHash) {
        return sessions.values()
                .stream()
                .filter(session -> session.draft().refreshTokenHash().equals(refreshTokenHash))
                .findFirst()
                .map(this::toRecord);
    }

    @Override
    public void revoke(UUID sessionId, UUID revokedBy, String reason) {
        sessions.computeIfPresent(sessionId, (id, session) -> new MutableSession(session.draft(), true, Instant.now()));
    }

    @Override
    public int cleanupExpiredSessions(Instant expiresBefore, Instant revokedBefore) {
        int before = sessions.size();
        sessions.entrySet().removeIf(entry -> {
            MutableSession session = entry.getValue();
            boolean expired = !session.draft().expiresAt().isAfter(expiresBefore);
            boolean revokedAndOldEnough = session.revokedAt() != null && !session.revokedAt().isAfter(revokedBefore);
            return expired || revokedAndOldEnough;
        });
        return before - sessions.size();
    }

    private AuthSessionRecord toRecord(MutableSession session) {
        return new AuthSessionRecord(
                session.draft().sessionId(),
                session.draft().userId(),
                session.draft().refreshTokenHash(),
                session.draft().authVersion(),
                session.draft().expiresAt(),
                session.revoked()
        );
    }

    private record MutableSession(AuthSessionDraft draft, boolean revoked, Instant revokedAt) {

        private MutableSession(AuthSessionDraft draft, boolean revoked) {
            this(draft, revoked, null);
        }
    }
}
