package com.songhg.veri.agent.common.secret;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "veri-agent.secret")
public record SecretProviderProperties(
        /** 本地密钥提供方的主密钥材料。 */
        String localMasterKey,
        /** 本地主密钥版本，用于密文版本匹配。 */
        String localMasterKeyVersion,
        /** 外部 Vault/KMS 密钥解析地址。 */
        String externalResolveUrl,
        /** 调用外部密钥服务的认证令牌。 */
        String externalAuthToken,
        /** 外部密钥服务调用超时时间，单位秒。 */
        int externalTimeoutSeconds,
        /** 外部密钥服务最大重试次数。 */
        int externalMaxRetries,
        /** 外部密钥服务健康检查地址。 */
        String externalHealthUrl,
        /** 外部密钥服务请求签名密钥 ID。 */
        String externalSigningKeyId,
        /** 外部密钥服务请求签名密钥。 */
        String externalSigningSecret
) {
}
