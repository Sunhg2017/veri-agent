package com.songhg.veri.agent.auth.application.view;

import java.util.UUID;

public record ChangePasswordResponse(
        boolean passwordChanged,
        boolean sessionInvalidated,
        UUID userId
) {
}
