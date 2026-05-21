package com.songhg.veri.agent.common.secret;

import java.time.Instant;

public record SecretProviderHealth(
        String providerCode,
        String providerType,
        boolean configured,
        String status,
        int timeoutSeconds,
        int maxAttempts,
        Instant checkedAt,
        String lastErrorMessage
) {

    public static SecretProviderHealth externalDisabled() {
        return new SecretProviderHealth(
                "external-vault-kms",
                "VAULT_KMS",
                false,
                "DISABLED",
                0,
                0,
                null,
                "外部 Vault/KMS SecretProvider 未启用"
        );
    }

    public static SecretProviderHealth externalHealthEndpointNotConfigured(int timeoutSeconds, int maxAttempts) {
        return new SecretProviderHealth(
                "external-vault-kms",
                "VAULT_KMS",
                true,
                "UNKNOWN",
                timeoutSeconds,
                maxAttempts,
                Instant.now(),
                "外部 Vault/KMS 健康检查端点未配置"
        );
    }

    public static SecretProviderHealth unsupported(String providerCode, String providerType) {
        return new SecretProviderHealth(
                providerCode,
                providerType,
                false,
                "UNSUPPORTED",
                0,
                0,
                null,
                null
        );
    }
}
