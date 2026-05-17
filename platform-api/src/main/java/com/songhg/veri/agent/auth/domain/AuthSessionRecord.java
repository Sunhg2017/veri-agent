package com.songhg.veri.agent.auth.domain;

import java.time.Instant;
import java.util.UUID;

public record AuthSessionRecord(
        UUID sessionId,
        UUID userId,
        String refreshTokenHash,
        long authVersion,
        Instant expiresAt,
        boolean revoked
) {
    public boolean activeAt(Instant now) {
        return !revoked && expiresAt.isAfter(now);
    }
}
