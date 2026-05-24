package com.songhg.veri.agent.auth.application.command;

import jakarta.validation.constraints.Size;

public record LogoutRequest(
        @Size(max = 128)
        String reason
) {
}
