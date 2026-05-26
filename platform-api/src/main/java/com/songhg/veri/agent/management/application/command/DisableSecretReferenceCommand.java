package com.songhg.veri.agent.management.application.command;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record DisableSecretReferenceCommand(
        @Schema(description = "密钥引用地址，不包含明文密钥。")
        @NotBlank
        @Size(max = 128)
        @Pattern(regexp = "^secret://[A-Za-z0-9._~:/?#\\[\\]@!$&'()*+,;=%-]+$", message = "secretRef must start with secret://")
        String secretRef
) {
}
