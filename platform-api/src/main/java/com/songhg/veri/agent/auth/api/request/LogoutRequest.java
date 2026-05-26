package com.songhg.veri.agent.auth.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

public record LogoutRequest(
        @Schema(description = "操作原因。")
        @Size(max = 128)
        String reason
) {
}
