package com.songhg.veri.agent.auth.infrastructure.mapper;

import java.util.UUID;

public record AuthUserRow(
        UUID userId,
        String username,
        String displayName,
        String email,
        String passwordHash,
        boolean mustChangePassword,
        long authVersion,
        String roleCodes
) {
}
