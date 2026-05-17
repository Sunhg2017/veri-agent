package com.songhg.veri.agent.management.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateApplicationRequest(
        @Size(max = 64)
        String name,

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
