package com.songhg.veri.agent.management.application;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record RotateSecretReferenceRequest(
        @NotBlank
        @Size(max = 128)
        @Pattern(regexp = "^secret://[A-Za-z0-9._~:/?#\\[\\]@!$&'()*+,;=%-]+$", message = "secretRef must start with secret://")
        String secretRef,

        @NotBlank
        @Size(min = 8, max = 8192)
        @JsonAlias("secretValue")
        String value,

        @Size(max = 64)
        String secretVersion,

        Instant expiresAt
) {
}
