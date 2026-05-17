package com.songhg.veri.agent.management.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record StatusChangeRequest(
        @JsonProperty("status")
        @NotBlank
        @Pattern(regexp = "^(PREPARING|ACTIVE|ARCHIVED|DISABLED|ENABLED)$")
        String status
) {
}
