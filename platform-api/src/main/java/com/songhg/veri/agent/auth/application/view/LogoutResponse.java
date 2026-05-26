package com.songhg.veri.agent.auth.application.view;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

public record LogoutResponse(
        @Schema(description = "会话或令牌是否已撤销。")
        boolean revoked,
        @Schema(description = "会话 ID。")
        UUID sessionId
) {
}
