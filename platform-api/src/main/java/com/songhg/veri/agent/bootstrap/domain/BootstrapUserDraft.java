package com.songhg.veri.agent.bootstrap.domain;

public record BootstrapUserDraft(
        String username,
        String passwordHash,
        String displayName,
        String email,
        String roleCode,
        boolean mustChangePassword
) {
}

