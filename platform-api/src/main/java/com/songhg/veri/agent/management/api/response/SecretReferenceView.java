package com.songhg.veri.agent.management.api.response;

public record SecretReferenceView(
        String id,
        String secretRef,
        String providerCode,
        String providerType,
        String purpose,
        String scopeType,
        String scopeId,
        String maskedValue,
        String secretVersion,
        String status,
        String rotatedAt,
        String expiresAt,
        String createdAt,
        String updatedAt
) {
}
