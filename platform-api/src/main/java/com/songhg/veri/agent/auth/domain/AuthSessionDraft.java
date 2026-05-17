package com.songhg.veri.agent.auth.domain;

import java.time.Instant;
import java.util.UUID;

public record AuthSessionDraft(
        UUID sessionId,
        UUID userId,
        String accessTokenHash,
        String refreshTokenHash,
        long authVersion,
        Instant expiresAt
) {
}
