package com.songhg.veri.agent.management.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateApplicationRequest(
        @Size(max = 32)
        @Pattern(regexp = "^[A-Za-z0-9_-]*$")
        String code,

        @NotBlank
        @Size(max = 64)
        String name,

        @JsonProperty("project")
        @Size(max = 64)
        String project,

        @JsonProperty("app_type")
        @Pattern(regexp = "^(|Web|Backend|Frontend|Mobile|Service|API)$")
        String appType,

        @JsonProperty("default_web_url")
        @Size(max = 512)
        String defaultWebUrl,

        @JsonProperty("default_api_base_url")
        @Size(max = 512)
        String defaultApiBaseUrl,

        @JsonProperty("sensitivity_level")
        @Pattern(regexp = "^(|PUBLIC|INTERNAL|CONFIDENTIAL|STRICT)$")
        String sensitivityLevel,

        @JsonProperty("allow_public_model")
        Boolean allowPublicModel
) {
}
