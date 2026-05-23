package com.songhg.veri.agent.auth.infrastructure;

import com.songhg.veri.agent.auth.domain.AuthSessionDraft;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryAuthSessionStoreTest {

    @Test
    void findsRefreshTokenByIndexAndCleansStaleIndexEntries() {
        InMemoryAuthSessionStore store = new InMemoryAuthSessionStore();
        UUID userId = UUID.fromString("00000000-0000-0000-0000-000000000101");
        UUID sessionId = UUID.fromString("00000000-0000-0000-0000-000000000201");

        store.create(new AuthSessionDraft(
                sessionId,
                userId,
                "access-token-hash",
                "refresh-token-hash",
                1,
                Instant.parse("2030-05-23T00:00:00Z")
        ));

        assertThat(store.findByRefreshTokenHash("refresh-token-hash"))
                .hasValueSatisfying(record -> assertThat(record.sessionId()).isEqualTo(sessionId));

        int deleted = store.cleanupExpiredSessions(
                Instant.parse("2030-05-23T00:00:01Z"),
                Instant.parse("2030-05-23T00:00:01Z")
        );

        assertThat(deleted).isEqualTo(1);
        assertThat(store.findByRefreshTokenHash("refresh-token-hash")).isEmpty();
    }

    @Test
    void replacingSessionRemovesOldRefreshTokenIndex() {
        InMemoryAuthSessionStore store = new InMemoryAuthSessionStore();
        UUID userId = UUID.fromString("00000000-0000-0000-0000-000000000101");
        UUID sessionId = UUID.fromString("00000000-0000-0000-0000-000000000201");

        store.create(session(sessionId, userId, "old-refresh-token-hash"));
        store.create(session(sessionId, userId, "new-refresh-token-hash"));

        assertThat(store.findByRefreshTokenHash("old-refresh-token-hash")).isEmpty();
        assertThat(store.findByRefreshTokenHash("new-refresh-token-hash")).isPresent();
    }

    private AuthSessionDraft session(UUID sessionId, UUID userId, String refreshTokenHash) {
        return new AuthSessionDraft(
                sessionId,
                userId,
                "access-token-hash-" + refreshTokenHash,
                refreshTokenHash,
                1,
                Instant.parse("2030-05-23T00:00:00Z")
        );
    }
}
