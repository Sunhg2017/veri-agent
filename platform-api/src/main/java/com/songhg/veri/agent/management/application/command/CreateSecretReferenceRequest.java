package com.songhg.veri.agent.management.application.command;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public record CreateSecretReferenceRequest(
        @NotBlank
        @Size(max = 128)
        @Pattern(regexp = "^secret://[A-Za-z0-9._~:/?#\\[\\]@!$&'()*+,;=%-]+$", message = "secretRef must start with secret://")
        String secretRef,

        @Size(max = 64)
        String providerCode,

        @NotBlank
        @Size(max = 64)
        @Pattern(regexp = "^[A-Z][A-Z0-9_:-]{2,63}$", message = "purpose must be uppercase")
        String purpose,

        @NotBlank
        @Pattern(regexp = "^(CONFIG|PROJECT|APPLICATION|ENVIRONMENT)$", message = "scopeType is not supported")
        String scopeType,

        @NotNull
        UUID scopeId,

        @NotBlank
        @Size(min = 8, max = 8192)
        @JsonAlias("secretValue")
        String value,

        @Size(max = 64)
        String secretVersion,

        Instant expiresAt
) {
}
