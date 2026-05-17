package com.songhg.veri.agent.auth.domain;

import java.util.List;
import java.util.UUID;

public record AuthUserRecord(
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
