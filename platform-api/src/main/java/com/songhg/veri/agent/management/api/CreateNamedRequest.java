package com.songhg.veri.agent.management.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateNamedRequest(
        @NotBlank
        @Size(max = 64)
        String name
) {
}
