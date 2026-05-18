package com.songhg.veri.agent.auth.api.response;

import java.util.UUID;

public record LogoutResponse(
        boolean revoked,
        UUID sessionId
) {
}
