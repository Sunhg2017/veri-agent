package com.songhg.veri.agent.management.application.command;

import com.fasterxml.jackson.annotation.JsonAlias;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public record CreateSecretReferenceCommand(
        @Schema(description = "密钥引用地址，不包含明文密钥")
        @NotBlank
        @Size(max = 128)
        @Pattern(regexp = "^secret://[A-Za-z0-9._~:/?#\\[\\]@!$&'()*+,;=%-]+$", message = "secretRef must start with secret://")
        String secretRef,
        @Schema(description = "密钥或模型供应商编码")
        @Size(max = 64)
        String providerCode,
        @Schema(description = "密钥用途")
        @NotBlank
        @Size(max = 64)
        @Pattern(regexp = "^[A-Z][A-Z0-9_:-]{2,63}$", message = "purpose must be uppercase")
        String purpose,
        @Schema(description = "权限或配置作用域类型")
        @NotBlank
        @Pattern(regexp = "^(CONFIG|PROJECT|APPLICATION|ENVIRONMENT)$", message = "scopeType is not supported")
        String scopeType,
        @Schema(description = "权限或配置作用域 ID")
        @NotNull
        UUID scopeId,
        @Schema(description = "配置值或密钥值；密钥请求中可出现明文，响应必须脱敏")
        @NotBlank
        @Size(min = 8, max = 8192)
        @JsonAlias("secretValue")
        String value,
        @Schema(description = "密钥版本号")
        @Size(max = 64)
        String secretVersion,
        @Schema(description = "过期时间")
        Instant expiresAt
) {
}
