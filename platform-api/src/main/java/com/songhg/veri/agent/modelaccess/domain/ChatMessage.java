package com.songhg.veri.agent.modelaccess.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChatMessage(
        /** 角色。 */
        @NotBlank String role,
        /** 内容正文。 */
        @NotBlank @Size(max = 12000) String content
) {
}
