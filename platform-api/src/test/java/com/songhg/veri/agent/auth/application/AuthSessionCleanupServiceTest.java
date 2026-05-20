package com.songhg.veri.agent.auth.application;

import com.songhg.veri.agent.auth.domain.AuthSessionDraft;
import com.songhg.veri.agent.auth.infrastructure.InMemoryAuthSessionStore;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AuthSessionCleanupServiceTest {

    @Test
    void removesExpiredAndRevokedSessionsOutsideRetentionWindow() {
        InMemoryAuthSessionStore store = new InMemoryAuthSessionStore();
        UUID userId = UUID.fromString("00000000-0000-0000-0000-000000000101");
        Instant now = Instant.parse("2030-05-20T00:00:00Z");
        UUID expiredSession = UUID.fromString("00000000-0000-0000-0000-000000000201");
        UUID activeSession = UUID.fromString("00000000-0000-0000-0000-000000000202");
        UUID revokedSession = UUID.fromString("00000000-0000-0000-0000-000000000203");

        store.create(session(expiredSession, userId, now.minusSeconds(120)));
        store.create(session(activeSession, userId, now.plusSeconds(300)));
        store.create(session(revokedSession, userId, now.plusSeconds(300)));
        store.revoke(revokedSession, userId, "test revoke");

        AuthSessionCleanupService cleanupService = new AuthSessionCleanupService(
                store,
                new AuthProperties("secret", 30, true, 60),
                new SimpleMeterRegistry(),
                Clock.fixed(now, ZoneOffset.UTC)
        );

        int deleted = cleanupService.cleanupNow();

        assertThat(deleted).isEqualTo(2);
        assertThat(store.isActive(expiredSession, userId, 1, now)).isFalse();
        assertThat(store.isActive(revokedSession, userId, 1, now)).isFalse();
        assertThat(store.isActive(activeSession, userId, 1, now)).isTrue();
    }

    @Test
    void skipsScheduledCleanupWhenDisabled() {
        CountingStore store = new CountingStore();
        AuthSessionCleanupService cleanupService = new AuthSessionCleanupService(
                store,
                new AuthProperties("secret", 30, false, 60),
                new SimpleMeterRegistry(),
                Clock.fixed(Instant.parse("2026-05-20T00:00:00Z"), ZoneOffset.UTC)
        );

        cleanupService.cleanupExpiredSessions();

        assertThat(store.cleanupCalls).isZero();
    }

    private AuthSessionDraft session(UUID sessionId, UUID userId, Instant expiresAt) {
        return new AuthSessionDraft(
                sessionId,
                userId,
                "access-" + sessionId,
                "refresh-" + sessionId,
                1,
                expiresAt
        );
    }

    private static class CountingStore extends InMemoryAuthSessionStore {

        private int cleanupCalls;

        @Override
        public int cleanupExpiredSessions(Instant expiresBefore, Instant revokedBefore) {
            cleanupCalls++;
            return super.cleanupExpiredSessions(expiresBefore, revokedBefore);
        }
    }
}
