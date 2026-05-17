package com.songhg.veri.agent.management.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateEnvironmentRequest(
        @Size(max = 64)
        String name,

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
