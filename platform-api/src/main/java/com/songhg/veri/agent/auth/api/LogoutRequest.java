package com.songhg.veri.agent.auth.api;

import jakarta.validation.constraints.Size;

public record LogoutRequest(
        @Size(max = 128)
        String reason
) {
}
