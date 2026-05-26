package com.songhg.veri.agent.common.secret;

import java.time.Instant;

public record SecretProviderHealth(
        /** 密钥提供方编码。 */
        String providerCode,
        /** 密钥提供方类型。 */
        String providerType,
        /** 是否已经完成必要配置。 */
        boolean configured,
        /** 健康状态。 */
        String status,
        /** 健康检查超时时间，单位秒。 */
        int timeoutSeconds,
        /** 健康检查最大尝试次数。 */
        int maxAttempts,
        /** 最近一次检查时间。 */
        Instant checkedAt,
        /** 最近一次错误信息。 */
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
