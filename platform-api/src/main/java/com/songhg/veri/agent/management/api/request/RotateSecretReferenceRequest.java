package com.songhg.veri.agent.management.api.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record RotateSecretReferenceRequest(
        @Schema(description = "密钥引用地址，不包含明文密钥。")
        @NotBlank
        @Size(max = 128)
        @Pattern(regexp = "^secret://[A-Za-z0-9._~:/?#\\[\\]@!$&'()*+,;=%-]+$", message = "secretRef must start with secret://")
        String secretRef,
        @Schema(description = "配置值或密钥值；密钥请求中可出现明文，响应必须脱敏。")
        @NotBlank
        @Size(min = 8, max = 8192)
        @JsonAlias("secretValue")
        String value,
        @Schema(description = "密钥版本号。")
        @Size(max = 64)
        String secretVersion,
        @Schema(description = "过期时间。")
        Instant expiresAt
) {
}
