package com.songhg.veri.agent.management.application;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record StatusChangeRequest(
        @NotBlank
        @Pattern(regexp = "^(PREPARING|ACTIVE|ARCHIVED|DISABLED|ENABLED)$")
        String status
) {
}
