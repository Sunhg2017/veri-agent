package com.songhg.veri.agent.auth.application;

import java.util.UUID;

public record ChangePasswordResponse(
        boolean passwordChanged,
        boolean sessionInvalidated,
        UUID userId
) {
}
