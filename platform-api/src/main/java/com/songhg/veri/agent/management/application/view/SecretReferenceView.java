package com.songhg.veri.agent.management.application.view;

import io.swagger.v3.oas.annotations.media.Schema;

public record SecretReferenceView(
        @Schema(description = "主键 ID。")
        String id,
        @Schema(description = "密钥引用地址，不包含明文密钥。")
        String secretRef,
        @Schema(description = "密钥或模型供应商编码。")
        String providerCode,
        @Schema(description = "供应商类型。")
        String providerType,
        @Schema(description = "密钥用途。")
        String purpose,
        @Schema(description = "权限或配置作用域类型。")
        String scopeType,
        @Schema(description = "权限或配置作用域 ID。")
        String scopeId,
        @Schema(description = "脱敏后的密钥展示值。")
        String maskedValue,
        @Schema(description = "密钥版本号。")
        String secretVersion,
        @Schema(description = "业务状态。")
        String status,
        @Schema(description = "密钥最近轮换时间。")
        String rotatedAt,
        @Schema(description = "过期时间。")
        String expiresAt,
        @Schema(description = "创建时间。")
        String createdAt,
        @Schema(description = "最近更新时间。")
        String updatedAt
) {
}
