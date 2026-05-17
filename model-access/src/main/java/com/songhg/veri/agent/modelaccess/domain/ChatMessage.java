package com.songhg.veri.agent.modelaccess.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChatMessage(
        @NotBlank String role,
        @NotBlank @Size(max = 12000) String content
) {
}
