package com.songhg.veri.agent.management.api.request;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateEnvironmentRequest(
        @Size(max = 64)
        String name,

        @Pattern(regexp = "^(|DEV|TEST|STAGING|PREPROD|PROD)$")
        String envType,

        @Size(max = 512)
        String webUrl,

        @Size(max = 512)
        String apiBaseUrl
) {
}
