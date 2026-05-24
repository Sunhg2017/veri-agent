package com.songhg.veri.agent.management.api.request;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateApplicationRequest(
        @Size(max = 64)
        String name,

        @Pattern(regexp = "^(|WEB_ADMIN|HTTP_API|MIXED|OTHER)$")
        String appType,

        @Size(max = 512)
        String defaultWebUrl,

        @Size(max = 512)
        String defaultApiBaseUrl,

        @Pattern(regexp = "^(|PUBLIC|INTERNAL|CONFIDENTIAL|STRICT)$")
        String sensitivityLevel,

        Boolean allowPublicModel
) {
}
