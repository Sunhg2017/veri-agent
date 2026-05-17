package com.songhg.veri.agent.management.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateSettingRequest(
        @Size(max = 64)
        String name,

        @Size(max = 256)
        String value,

        @JsonProperty("scope_type")
        @Pattern(regexp = "^(|SYSTEM|PROJECT|APPLICATION|ENVIRONMENT)$")
        String scopeType
) {
}
