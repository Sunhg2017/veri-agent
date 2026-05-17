package com.songhg.veri.agent.management.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateEnvironmentRequest(
        @Size(max = 32)
        @Pattern(regexp = "^[A-Za-z0-9_-]*$")
        String code,

        @NotBlank
        @Size(max = 64)
        String name,

        @Size(max = 64)
        String project,

        @Size(max = 64)
        String application,

        @JsonProperty("scope_type")
        @Pattern(regexp = "^(|PROJECT|APPLICATION)$")
        String scopeType,

        @JsonProperty("env_type")
        @Pattern(regexp = "^(|DEV|TEST|STAGING|PREPROD|PROD)$")
        String envType,

        @JsonProperty("web_url")
        @Size(max = 512)
        String webUrl,

        @JsonProperty("api_base_url")
        @Size(max = 512)
        String apiBaseUrl
) {
}
