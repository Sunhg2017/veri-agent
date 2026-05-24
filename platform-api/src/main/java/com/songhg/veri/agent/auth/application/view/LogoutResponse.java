package com.songhg.veri.agent.auth.application.view;

import java.util.UUID;

public record LogoutResponse(
        boolean revoked,
        UUID sessionId
) {
}
