package com.songhg.veri.agent.auth.api.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

public record ChangePasswordResponse(
        @Schema(description = "密码是否已修改成功")
        boolean passwordChanged,
        @Schema(description = "是否已使旧会话失效")
        boolean sessionInvalidated,
        @Schema(description = "用户 ID")
        UUID userId
) {
}
